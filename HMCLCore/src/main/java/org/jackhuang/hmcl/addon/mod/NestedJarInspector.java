/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.addon.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Recursive scanner for a mod's Jar-in-Jar tree.
///
/// Unlike the metadata readers in {@code addon.meta} (which build a full {@link LocalModFile} and
/// register a {@link LocalMod} on a {@link ModManager}), this only extracts what the mod list and the
/// dependency logic need: for every jar nested at any depth, its id/name/version/loader and the
/// Minecraft version it targets (for multi-version "wrapper" jars that bundle one copy per game
/// version). Nothing is added to any registry.
///
/// The scan runs eagerly at parse time (from {@link ModManager}), not on demand: mod-dependency
/// cascade and the bundled-dependency report both need the *complete, accurate* set of bundled mod
/// ids, which a lazy expand-time scan could not provide. The cost is paid once and travels with the
/// cached mod info (refreshes reuse it). Recursion is bounded by {@link #MAX_DEPTH} and the total node
/// count by {@link #MAX_NODES} to guard against pathological or malicious nesting.
public final class NestedJarInspector {
    /// How many layers deep the scan drills (direct children are layer 1).
    public static final int MAX_DEPTH = 4;
    /// Upper bound on nodes visited per top-level mod, so a jar bundling thousands of entries can't
    /// stall a refresh.
    public static final int MAX_NODES = 512;

    private NestedJarInspector() {
    }

    /// One node of the Jar-in-Jar tree, with its {@link #children} already populated.
    public record NestedJar(
            String path,                       // entry path within the immediate parent jar
            String fileName,                   // basename of path — display fallback
            @Nullable String id,
            @Nullable String name,
            @Nullable String version,
            ModLoaderType loaderType,
            @Nullable String minecraftVersion, // declared MC constraint, for multi-version grouping
            boolean minecraftConstraintRequired,
            @Unmodifiable List<ModDependency> dependencies,
            @Unmodifiable Map<String, String> providedVersions,
            @Unmodifiable List<ModConflict> conflicts,
            @Nullable String jijIdentifier,
            @Nullable String jijVersionRange,
            @Nullable String jijArtifactVersion,
            @Unmodifiable List<NestedJar> children
    ) {
        /// Creates an immutable nested node.
        public NestedJar {
            dependencies = List.copyOf(dependencies);
            providedVersions = Map.copyOf(providedVersions);
            conflicts = List.copyOf(conflicts);
            children = List.copyOf(children);
        }

        public String displayName() {
            return name != null && !name.isBlank() ? name : fileName;
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }
    }

    /// A full tree scan's outcome: the tree, plus whether the node budget cut it short. A truncated
    /// tree is still useful for display, but callers must NOT persist it as if it were complete —
    /// the host file's fingerprint wouldn't change, so the incompleteness would become permanent.
    public record ScanResult(@Unmodifiable List<NestedJar> tree, boolean truncated) {
        public static final ScanResult EMPTY = new ScanResult(List.of(), false);

        /// Creates an immutable scan result.
        public ScanResult {
            tree = List.copyOf(tree);
        }
    }

    /// Scans the full Jar-in-Jar tree of an already-open mod jar. Returns an empty result when the mod
    /// declares no nested jars (or isn't a Fabric/Quilt/Forge/NeoForge mod).
    public static ScanResult scan(ZipFileTree modTree) {
        return scan(modTree, Set.of());
    }

    /// Scans a complete tree while preferring metadata for the instance's active loader family.
    ///
    /// @param modTree the already-open top-level mod archive
    /// @param preferredLoaders active instance loader types in preference order
    /// @return the complete or truncated scan result
    public static ScanResult scan(ZipFileTree modTree, Set<ModLoaderType> preferredLoaders) {
        List<String> childPaths = childJarPaths(modTree);
        if (childPaths.isEmpty())
            return ScanResult.EMPTY;
        boolean[] truncated = {false};
        List<NestedJar> tree = scanChildren(
                modTree, childPaths, 1, new int[]{MAX_NODES}, truncated, Set.copyOf(preferredLoaders));
        return new ScanResult(tree, truncated[0]);
    }

    /// Flattens every non-blank mod id in the tree (all depths) into {@code out}.
    public static void collectIds(List<NestedJar> tree, Set<String> out) {
        for (NestedJar node : tree) {
            if (node.id != null && !node.id.isBlank())
                out.add(node.id);
            out.addAll(node.providedVersions().keySet());
            collectIds(node.children, out);
        }
    }

    private static List<NestedJar> scanChildren(
            ZipFileTree parentTree,
            List<String> childPaths,
            int depth,
            int[] budget,
            boolean[] truncated,
            Set<ModLoaderType> preferredLoaders) {
        List<NestedJar> result = new ArrayList<>();
        Map<String, JarJarInfo> jarJarInfo = jarJarInfo(parentTree);
        for (String childPath : childPaths) {
            if (budget[0] <= 0) {
                LOG.warning("Jar-in-Jar node budget exhausted; stopping scan at " + childPath);
                truncated[0] = true;
                break;
            }
            budget[0]--;
            @Nullable JarJarInfo declaration = jarJarInfo.get(childPath);

            Path temp = null;
            try {
                if (parentTree.getEntry(childPath) == null) {
                    truncated[0] = true;
                    result.add(fallback(childPath, declaration));
                    continue;
                }
                temp = Files.createTempFile("hmcl-jij-", ".jar");
                parentTree.extractTo(childPath, temp);
                try (ZipFileTree childTree = CompressingUtils.openZipTree(temp)) {
                    Parsed m = parse(childTree, preferredLoaders);
                    List<String> declaredGrandchildren = childJarPaths(childTree);
                    if (depth >= MAX_DEPTH && !declaredGrandchildren.isEmpty()) {
                        truncated[0] = true;
                        LOG.warning("Jar-in-Jar depth limit reached at " + childPath);
                    }
                    List<String> grandchildPaths = depth < MAX_DEPTH
                            ? declaredGrandchildren
                            : List.of();
                    List<NestedJar> grandchildren = grandchildPaths.isEmpty()
                            ? List.of()
                            : scanChildren(
                            childTree, grandchildPaths, depth + 1, budget, truncated, preferredLoaders);
                    result.add(m == null
                            ? new NestedJar(childPath, baseName(childPath), null, null, null,
                            ModLoaderType.UNKNOWN, null, false, List.of(), Map.of(), List.of(),
                            declaration == null ? null : declaration.identifier(),
                            declaration == null ? null : declaration.versionRange(),
                            declaration == null ? null : declaration.artifactVersion(),
                            grandchildren)
                            : new NestedJar(childPath, baseName(childPath), m.id, m.name, m.version,
                            m.loaderType, m.minecraftVersion, m.minecraftConstraintRequired,
                            m.dependencies, m.providedVersions,
                            m.conflicts,
                            declaration == null ? null : declaration.identifier(),
                            declaration == null ? null : declaration.versionRange(),
                            declaration == null ? null : declaration.artifactVersion(),
                            grandchildren));
                }
            } catch (Exception e) {
                truncated[0] = true;
                LOG.warning("Failed to scan nested jar " + childPath, e);
                result.add(fallback(childPath, declaration));
            } finally {
                if (temp != null) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static NestedJar fallback(String path, @Nullable JarJarInfo declaration) {
        return new NestedJar(path, baseName(path), null, null, null, ModLoaderType.UNKNOWN,
                null, false, List.of(), Map.of(), List.of(),
                declaration == null ? null : declaration.identifier(),
                declaration == null ? null : declaration.versionRange(),
                declaration == null ? null : declaration.artifactVersion(),
                List.of());
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /// Forge JarJar selection metadata associated with one nested path.
    private record JarJarInfo(
            @Nullable String identifier,
            @Nullable String versionRange,
            @Nullable String artifactVersion) {
    }

    /// Reads Forge JarJar coordinates and allowed/actual versions keyed by nested path.
    private static Map<String, JarJarInfo> jarJarInfo(ZipFileTree tree) {
        Map<String, JarJarInfo> result = new LinkedHashMap<>();
        try {
            JsonObject root = readJson(tree, "META-INF/jarjar/metadata.json");
            if (root == null || !(root.get("jars") instanceof JsonArray jars)) {
                return result;
            }
            for (JsonElement element : jars) {
                if (!(element instanceof JsonObject jar) || !jar.has("path")) {
                    continue;
                }
                @Nullable String identifier = null;
                if (jar.get("identifier") instanceof JsonObject id) {
                    @Nullable String group = asString(id, "group");
                    @Nullable String artifact = asString(id, "artifact");
                    if (group != null && artifact != null) {
                        identifier = group + ":" + artifact;
                    }
                }
                @Nullable String range = null;
                @Nullable String artifactVersion = null;
                if (jar.get("version") instanceof JsonObject version) {
                    range = asString(version, "range");
                    artifactVersion = asString(version, "artifactVersion");
                }
                result.put(jar.get("path").getAsString(), new JarJarInfo(identifier, range, artifactVersion));
            }
        } catch (IOException e) {
            LOG.warning("Failed to read Forge JarJar metadata", e);
        }
        return result;
    }

    /// Every nested-jar entry path a jar declares, across *all* mechanisms and independent of whether
    /// it has a parseable mods.toml: Fabric/Quilt `jars`, Forge JarJar metadata, and the manifest's
    /// {@code Embedded-Dependencies-Mod}. A bare "wrapper" jar (no mods.toml, just a manifest/JarJar
    /// pointer to the real mod) declares nested jars only through the last two, so we must not gate
    /// this on the metadata reader succeeding.
    private static List<String> childJarPaths(ZipFileTree tree) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        try {
            JsonObject fabric = readJson(tree, "fabric.mod.json");
            if (fabric != null && fabric.get("jars") instanceof JsonArray jars)
                for (JsonElement e : jars)
                    if (e.isJsonObject() && e.getAsJsonObject().has("file"))
                        paths.add(e.getAsJsonObject().get("file").getAsString());

            JsonObject quilt = readJson(tree, "quilt.mod.json");
            if (quilt != null && quilt.get("quilt_loader") instanceof JsonObject ql && ql.get("jars") instanceof JsonArray qjars)
                for (JsonElement e : qjars)
                    if (e.isJsonPrimitive())
                        paths.add(e.getAsString());

            JsonObject jarjar = readJson(tree, "META-INF/jarjar/metadata.json");
            if (jarjar != null && jarjar.get("jars") instanceof JsonArray jjars)
                for (JsonElement e : jjars)
                    if (e.isJsonObject() && e.getAsJsonObject().has("path"))
                        paths.add(e.getAsJsonObject().get("path").getAsString());

            var manifest = tree.getEntry("META-INF/MANIFEST.MF");
            if (manifest != null) {
                try (InputStream is = tree.getInputStream(manifest)) {
                    String embedded = new Manifest(is).getMainAttributes().getValue("Embedded-Dependencies-Mod");
                    if (embedded != null && !embedded.isBlank())
                        paths.add(embedded);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to read nested jar declarations", e);
        }
        return new ArrayList<>(paths);
    }

    // ── format detection (metadata only; child paths come from childJarPaths) ────────────
    private record Parsed(@Nullable String id, @Nullable String name, @Nullable String version,
                          ModLoaderType loaderType, @Nullable String minecraftVersion,
                          boolean minecraftConstraintRequired,
                          List<ModDependency> dependencies, Map<String, String> providedVersions,
                          List<ModConflict> conflicts) {
    }

    private static @Nullable Parsed parse(ZipFileTree tree, Set<ModLoaderType> preferredLoaders) {
        try {
            if (preferredLoaders.contains(ModLoaderType.NEO_FORGE)) {
                Parsed neoForge = fromForge(tree, ModLoaderType.NEO_FORGE);
                if (neoForge != null)
                    return neoForge;
            }
            if (preferredLoaders.contains(ModLoaderType.FORGE)
                    || preferredLoaders.contains(ModLoaderType.CLEANROOM)) {
                Parsed forge = fromForge(tree, ModLoaderType.FORGE);
                if (forge != null)
                    return forge;
            }
            if (preferredLoaders.contains(ModLoaderType.FABRIC)
                    || preferredLoaders.contains(ModLoaderType.LEGACY_FABRIC)) {
                Parsed fabric = fromFabric(tree);
                if (fabric != null)
                    return fabric;
            }
            if (preferredLoaders.contains(ModLoaderType.QUILT)) {
                Parsed quilt = fromQuilt(tree);
                if (quilt != null)
                    return quilt;
            }
            Parsed fabric = fromFabric(tree);
            if (fabric != null)
                return fabric;
            Parsed quilt = fromQuilt(tree);
            if (quilt != null)
                return quilt;
            return fromForge(tree, null);
        } catch (Exception e) {
            LOG.warning("Failed to read nested jar metadata", e);
            return null;
        }
    }

    /// Parses Fabric metadata, dependencies, and provided aliases from a nested jar.
    private static @Nullable Parsed fromFabric(ZipFileTree tree) throws IOException {
        JsonObject root = readJson(tree, "fabric.mod.json");
        if (root == null) {
            return null;
        }

        JsonObject dependencyObject = root.get("depends") instanceof JsonObject object ? object : null;
        String minecraft = dependencyObject != null && dependencyObject.has("minecraft")
                ? asVersionString(dependencyObject.get("minecraft"))
                : null;
        List<ModDependency> dependencies = new ArrayList<>();
        if (dependencyObject != null) {
            for (Map.Entry<String, JsonElement> entry : dependencyObject.entrySet()) {
                if (!isPlatformDependency(entry.getKey())) {
                    dependencies.add(new ModDependency(
                            entry.getKey(),
                            asVersionString(entry.getValue()) == null ? "*" : asVersionString(entry.getValue()),
                            false,
                            ModLoaderType.FABRIC));
                }
            }
        }
        addJsonDependencies(dependencies, root.get("recommends"), true, ModLoaderType.FABRIC);
        addJsonDependencies(dependencies, root.get("suggests"), true, ModLoaderType.FABRIC);

        @Nullable String version = cleanVersion(asString(root, "version"));
        Map<String, String> providedVersions = new LinkedHashMap<>();
        if (root.get("provides") instanceof JsonArray provided) {
            for (JsonElement element : provided) {
                if (element.isJsonPrimitive()) {
                    providedVersions.put(element.getAsString(), version == null ? "" : version);
                }
            }
        }
        List<ModConflict> conflicts = new ArrayList<>();
        addJsonConflicts(conflicts, root.get("breaks"), true, ModLoaderType.FABRIC);
        addJsonConflicts(conflicts, root.get("conflicts"), false, ModLoaderType.FABRIC);
        return new Parsed(
                usableModId(asString(root, "id")),
                asString(root, "name"),
                version,
                ModLoaderType.FABRIC,
                minecraft,
                minecraft != null,
                List.copyOf(dependencies),
                Map.copyOf(providedVersions),
                List.copyOf(conflicts));
    }

    /// Parses Quilt metadata, recursive version constraints, and provided aliases from a nested jar.
    private static @Nullable Parsed fromQuilt(ZipFileTree tree) throws IOException {
        JsonObject root = readJson(tree, "quilt.mod.json");
        if (root == null || !(root.get("quilt_loader") instanceof JsonObject loader)) {
            return null;
        }

        String minecraft = null;
        boolean minecraftRequired = false;
        List<ModDependency> dependencies = new ArrayList<>();
        if (loader.get("depends") instanceof JsonArray declaredDependencies) {
            for (JsonElement element : declaredDependencies) {
                @Nullable String id = null;
                String constraint = "*";
                boolean optional = false;
                boolean serverOnly = false;
                if (element.isJsonPrimitive()) {
                    id = element.getAsString();
                } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                    JsonObject dependency = element.getAsJsonObject();
                    id = dependency.get("id").getAsString();
                    if (dependency.has("versions")) {
                        @Nullable String parsed = asQuiltConstraint(dependency.get("versions"));
                        constraint = parsed == null ? "*" : parsed;
                    }
                    optional = dependency.has("optional") && dependency.get("optional").getAsBoolean();
                    serverOnly = dependency.has("environment")
                            && "dedicated_server".equalsIgnoreCase(
                            dependency.get("environment").getAsString());
                }
                if (serverOnly) {
                    continue;
                }
                if ("minecraft".equals(id)) {
                    minecraft = constraint;
                    minecraftRequired = !optional;
                } else if (id != null && !isPlatformDependency(id)) {
                    dependencies.add(new ModDependency(id, constraint, optional, ModLoaderType.QUILT));
                }
            }
        }

        @Nullable String version = cleanVersion(asString(loader, "version"));
        Map<String, String> providedVersions = new LinkedHashMap<>();
        if (loader.get("provides") instanceof JsonArray provided) {
            for (JsonElement element : provided) {
                if (element.isJsonPrimitive()) {
                    providedVersions.put(element.getAsString(), version == null ? "" : version);
                } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                    JsonObject providedObject = element.getAsJsonObject();
                    String providedVersion = providedObject.has("version")
                            ? providedObject.get("version").getAsString()
                            : version == null ? "" : version;
                    providedVersions.put(providedObject.get("id").getAsString(), providedVersion);
                }
            }
        }

        List<ModConflict> conflicts = new ArrayList<>();
        if (loader.get("breaks") instanceof JsonArray breaks) {
            for (JsonElement element : breaks) {
                @Nullable String conflictId = null;
                String constraint = "*";
                if (element.isJsonPrimitive()) {
                    conflictId = element.getAsString();
                } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                    JsonObject conflict = element.getAsJsonObject();
                    conflictId = conflict.get("id").getAsString();
                    if (conflict.has("versions")) {
                        @Nullable String parsed = asQuiltConstraint(conflict.get("versions"));
                        constraint = parsed == null ? "*" : parsed;
                    }
                }
                if (conflictId != null && !isPlatformDependency(conflictId)) {
                    conflicts.add(new ModConflict(conflictId, constraint, true, ModLoaderType.QUILT));
                }
            }
        }

        String name = loader.get("metadata") instanceof JsonObject metadata ? asString(metadata, "name") : null;
        return new Parsed(
                usableModId(asString(loader, "id")),
                name,
                version,
                ModLoaderType.QUILT,
                minecraft,
                minecraftRequired,
                List.copyOf(dependencies),
                Map.copyOf(providedVersions),
                List.copyOf(conflicts));
    }

    /// Parses Forge or NeoForge metadata, including multi-mod jars and Maven constraints.
    private static @Nullable Parsed fromForge(
            ZipFileTree tree,
            @Nullable ModLoaderType preferredLoader) throws IOException {
        boolean hasNeoForge = tree.getEntry("META-INF/neoforge.mods.toml") != null;
        boolean hasForge = tree.getEntry("META-INF/mods.toml") != null;
        boolean neoForge = preferredLoader == ModLoaderType.NEO_FORGE && hasNeoForge
                || !hasForge && hasNeoForge;
        String tomlPath = neoForge ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml";
        if (tree.getEntry(tomlPath) == null) {
            return null;
        }

        TomlParseResult toml;
        try {
            toml = Toml.parse(tree.readTextEntry(tomlPath));
        } catch (Exception ignored) {
            return null;
        }
        if (toml.hasErrors()) {
            return null;
        }

        ModLoaderType loader = neoForge ? ModLoaderType.NEO_FORGE : ModLoaderType.FORGE;
        @Nullable String id = null;
        @Nullable String name = null;
        @Nullable String version = null;
        @Nullable String minecraft = null;
        boolean minecraftRequired = false;
        Set<String> declaredIds = new LinkedHashSet<>();
        Map<String, String> providedVersions = new LinkedHashMap<>();
        TomlArray mods = toml.getArray("mods");
        if (mods != null) {
            for (int i = 0; i < mods.size(); i++) {
                if (!(mods.get(i) instanceof TomlTable mod)) {
                    continue;
                }
                @Nullable String declaredId = usableModId(mod.getString("modId"));
                if (declaredId == null || declaredId.isBlank()) {
                    continue;
                }
                @Nullable String declaredVersion = resolveForgeVersion(tree, mod.getString("version"));
                declaredIds.add(declaredId);
                if (id == null) {
                    id = declaredId;
                    name = mod.getString("displayName");
                    version = declaredVersion;
                } else {
                    providedVersions.put(declaredId, declaredVersion == null ? "" : declaredVersion);
                }
            }
        }

        List<ModDependency> parsedDependencies = new ArrayList<>();
        List<ModConflict> conflicts = new ArrayList<>();
        for (String declaringId : declaredIds) {
            @Nullable TomlArray dependencyArray = dependencies(toml, declaringId, declaringId.equals(id));
            if (dependencyArray == null) {
                continue;
            }
            for (int i = 0; i < dependencyArray.size(); i++) {
                if (!(dependencyArray.get(i) instanceof TomlTable dependency)) {
                    continue;
                }
                @Nullable String dependencyId = dependency.getString("modId");
                if (dependencyId == null) {
                    continue;
                }
                @Nullable String declaredConstraint = dependency.getString("versionRange");
                String constraint = declaredConstraint == null ? "*" : declaredConstraint;
                @Nullable Boolean mandatory = dependency.getBoolean("mandatory");
                @Nullable String type = dependency.getString("type");
                boolean required = mandatory != null
                        ? mandatory
                        : type == null || type.equalsIgnoreCase("required");
                @Nullable String side = dependency.getString("side");
                boolean serverOnly = "server".equalsIgnoreCase(side);
                if ("minecraft".equals(dependencyId)) {
                    minecraft = constraint;
                    minecraftRequired = required && !serverOnly;
                    continue;
                }
                if (isPlatformDependency(dependencyId) || declaredIds.contains(dependencyId)
                        || serverOnly) {
                    continue;
                }
                if (type != null && (type.equalsIgnoreCase("incompatible")
                        || type.equalsIgnoreCase("discouraged"))) {
                    conflicts.add(new ModConflict(
                            dependencyId,
                            constraint,
                            type.equalsIgnoreCase("incompatible"),
                            loader));
                    continue;
                }
                parsedDependencies.add(new ModDependency(dependencyId, constraint, !required, loader));
            }
        }

        return new Parsed(
                id,
                name,
                version,
                loader,
                minecraft,
                minecraftRequired,
                List.copyOf(parsedDependencies),
                Map.copyOf(providedVersions),
                List.copyOf(conflicts));
    }

    /// Nulls out a version still holding an unresolved build placeholder (e.g. Fabric's
    /// {@code ${version}}), so the UI and crash report show nothing rather than the raw token.
    private static @Nullable String cleanVersion(@Nullable String version) {
        return version != null && version.contains("${") ? null : version;
    }

    /// Forge mod versions are often the literal {@code ${file.jarVersion}}, resolved at build time
    /// from the jar manifest's Implementation-Version (mirrors ForgeNewModMetadata). If it can't be
    /// resolved, drop the version rather than show a raw placeholder.
    private static @Nullable String resolveForgeVersion(ZipFileTree tree, @Nullable String version) {
        if (version == null || !version.contains("${"))
            return version;
        if (version.contains("${file.jarVersion}")) {
            var manifest = tree.getEntry("META-INF/MANIFEST.MF");
            if (manifest != null) {
                try (InputStream is = tree.getInputStream(manifest)) {
                    String impl = new Manifest(is).getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                    if (impl != null && !impl.isBlank())
                        version = version.replace("${file.jarVersion}", impl);
                } catch (IOException ignored) {
                }
            }
        }
        return version.contains("${") ? null : version; // any placeholder left unresolved — hide it
    }

    private static @Nullable TomlArray dependencies(TomlParseResult toml, String modId) {
        return dependencies(toml, modId, true);
    }

    /// Reads owner-specific dependency tables and optionally accepts the legacy unscoped array form.
    private static @Nullable TomlArray dependencies(
            TomlParseResult toml,
            String modId,
            boolean allowLegacyUnscoped) {
        try {
            TomlArray arr = toml.getArray("dependencies." + modId);
            if (arr != null)
                return arr;
        } catch (Exception ignored) {
        }
        try {
            TomlTable table = toml.getTable("dependencies");
            if (table != null) {
                TomlArray array = table.getArray(modId);
                if (array != null) {
                    return array;
                }
            }
        } catch (Exception ignored) {
        }
        if (allowLegacyUnscoped) {
            try {
                return toml.getArray("dependencies");
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // ── helpers ───────────────────────────────────────────────────────
    /// Adds Fabric-style dependencies from an ID-to-version map.
    private static void addJsonDependencies(
            List<ModDependency> result,
            JsonElement declarations,
            boolean optional,
            ModLoaderType loader) {
        if (!(declarations instanceof JsonObject object)) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (isPlatformDependency(entry.getKey())) {
                continue;
            }
            @Nullable String constraint = asVersionString(entry.getValue());
            result.add(new ModDependency(
                    entry.getKey(), constraint == null ? "*" : constraint, optional, loader));
        }
    }

    /// Adds Fabric-style conflict declarations from an ID-to-version map.
    private static void addJsonConflicts(
            List<ModConflict> result,
            JsonElement declarations,
            boolean hard,
            ModLoaderType loader) {
        if (!(declarations instanceof JsonObject object)) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (isPlatformDependency(entry.getKey())) {
                continue;
            }
            @Nullable String constraint = asVersionString(entry.getValue());
            result.add(new ModConflict(
                    entry.getKey(), constraint == null ? "*" : constraint, hard, loader));
        }
    }

    /// Returns whether an ID names the game, runtime, or loader instead of an installable mod.
    private static boolean isPlatformDependency(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "minecraft", "java", "fabric", "fabricloader", "quilt_loader", "quilt_base",
                    "forge", "neoforge" -> true;
            default -> false;
        };
    }

    /// Returns a usable mod ID or `null` for an unexpanded template placeholder.
    private static @Nullable String usableModId(@Nullable String id) {
        return ModManager.isPlaceholderModId(id) ? null : id;
    }

    /// Converts Quilt's recursive any/all version form to OR/AND predicate text.
    private static @Nullable String asQuiltConstraint(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            List<String> alternatives = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                @Nullable String parsed = asQuiltConstraint(child);
                if (parsed != null) {
                    alternatives.add(parsed);
                }
            }
            return alternatives.isEmpty() ? null : String.join(" || ", alternatives);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("any")) {
                return asQuiltConstraint(object.get("any"));
            }
            if (object.get("all") instanceof JsonArray all) {
                List<String> terms = new ArrayList<>();
                for (JsonElement child : all) {
                    @Nullable String parsed = asQuiltConstraint(child);
                    if (parsed != null) {
                        terms.add(parsed);
                    }
                }
                return terms.isEmpty() ? null : String.join(" ", terms);
            }
        }
        return null;
    }

    private static @Nullable JsonObject readJson(ZipFileTree tree, String path) throws IOException {
        var entry = tree.getEntry(path);
        if (entry == null)
            return null;
        try {
            return JsonUtils.GSON.fromJson(tree.readTextEntry(entry), JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable String asString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /// A Minecraft constraint may be a single range string ("1.20.x", ">=26.1- <26.2-") or an array of
    /// them; render a readable value either way.
    private static @Nullable String asVersionString(JsonElement e) {
        if (e == null)
            return null;
        if (e.isJsonPrimitive())
            return e.getAsString();
        if (e.isJsonArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray())
                if (x.isJsonPrimitive())
                    parts.add(x.getAsString());
            return parts.isEmpty() ? null : String.join(" || ", parts);
        }
        return null;
    }
}
