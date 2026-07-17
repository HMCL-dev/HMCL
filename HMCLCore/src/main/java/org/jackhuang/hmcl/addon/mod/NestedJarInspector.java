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
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
            List<NestedJar> children
    ) {
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
    public record ScanResult(List<NestedJar> tree, boolean truncated) {
        public static final ScanResult EMPTY = new ScanResult(List.of(), false);
    }

    /// Scans the full Jar-in-Jar tree of an already-open mod jar. Returns an empty result when the mod
    /// declares no nested jars (or isn't a Fabric/Quilt/Forge/NeoForge mod).
    public static ScanResult scan(ZipFileTree modTree) {
        List<String> childPaths = childJarPaths(modTree);
        if (childPaths.isEmpty())
            return ScanResult.EMPTY;
        boolean[] truncated = {false};
        List<NestedJar> tree = scanChildren(modTree, childPaths, 1, new int[]{MAX_NODES}, truncated);
        return new ScanResult(tree, truncated[0]);
    }

    /// Flattens every non-blank mod id in the tree (all depths) into {@code out}.
    public static void collectIds(List<NestedJar> tree, Set<String> out) {
        for (NestedJar node : tree) {
            if (node.id != null && !node.id.isBlank())
                out.add(node.id);
            collectIds(node.children, out);
        }
    }

    private static List<NestedJar> scanChildren(ZipFileTree parentTree, List<String> childPaths, int depth, int[] budget, boolean[] truncated) {
        List<NestedJar> result = new ArrayList<>();
        for (String childPath : childPaths) {
            if (budget[0] <= 0) {
                LOG.warning("Jar-in-Jar node budget exhausted; stopping scan at " + childPath);
                truncated[0] = true;
                break;
            }
            budget[0]--;

            Path temp = null;
            try {
                if (parentTree.getEntry(childPath) == null) {
                    result.add(fallback(childPath));
                    continue;
                }
                temp = Files.createTempFile("hmcl-jij-", ".jar");
                parentTree.extractTo(childPath, temp);
                try (ZipFileTree childTree = CompressingUtils.openZipTree(temp)) {
                    Parsed m = parse(childTree);
                    List<String> grandchildPaths = depth < MAX_DEPTH ? childJarPaths(childTree) : List.of();
                    List<NestedJar> grandchildren = grandchildPaths.isEmpty()
                            ? List.of()
                            : scanChildren(childTree, grandchildPaths, depth + 1, budget, truncated);
                    result.add(m == null
                            ? new NestedJar(childPath, baseName(childPath), null, null, null, ModLoaderType.UNKNOWN, null, grandchildren)
                            : new NestedJar(childPath, baseName(childPath), m.id, m.name, m.version, m.loaderType, m.minecraftVersion, grandchildren));
                }
            } catch (IOException e) {
                LOG.warning("Failed to scan nested jar " + childPath, e);
                result.add(fallback(childPath));
            } finally {
                if (temp != null) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        return result;
    }

    private static NestedJar fallback(String path) {
        return new NestedJar(path, baseName(path), null, null, null, ModLoaderType.UNKNOWN, null, List.of());
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
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
                          ModLoaderType loaderType, @Nullable String minecraftVersion) {
    }

    private static @Nullable Parsed parse(ZipFileTree tree) {
        try {
            Parsed fabric = fromFabric(tree);
            if (fabric != null)
                return fabric;
            Parsed quilt = fromQuilt(tree);
            if (quilt != null)
                return quilt;
            return fromForge(tree);
        } catch (IOException e) {
            LOG.warning("Failed to read nested jar metadata", e);
            return null;
        }
    }

    private static @Nullable Parsed fromFabric(ZipFileTree tree) throws IOException {
        JsonObject root = readJson(tree, "fabric.mod.json");
        if (root == null)
            return null;
        String mc = root.get("depends") instanceof JsonObject depends && depends.has("minecraft")
                ? asVersionString(depends.get("minecraft")) : null;
        return new Parsed(asString(root, "id"), asString(root, "name"), cleanVersion(asString(root, "version")),
                ModLoaderType.FABRIC, mc);
    }

    private static @Nullable Parsed fromQuilt(ZipFileTree tree) throws IOException {
        JsonObject root = readJson(tree, "quilt.mod.json");
        if (root == null || !(root.get("quilt_loader") instanceof JsonObject ql))
            return null;
        String mc = null;
        if (ql.get("depends") instanceof JsonArray depends) {
            for (JsonElement e : depends) {
                if (e.isJsonObject() && e.getAsJsonObject().has("id")
                        && "minecraft".equals(e.getAsJsonObject().get("id").getAsString())) {
                    mc = e.getAsJsonObject().has("versions") ? asVersionString(e.getAsJsonObject().get("versions")) : null;
                    break;
                }
            }
        }
        String name = ql.get("metadata") instanceof JsonObject meta ? asString(meta, "name") : null;
        return new Parsed(asString(ql, "id"), name, cleanVersion(asString(ql, "version")), ModLoaderType.QUILT, mc);
    }

    private static @Nullable Parsed fromForge(ZipFileTree tree) throws IOException {
        boolean neo = tree.getEntry("META-INF/neoforge.mods.toml") != null;
        String tomlPath = neo ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml";
        if (tree.getEntry(tomlPath) == null)
            return null;

        TomlParseResult toml;
        try {
            toml = Toml.parse(tree.readTextEntry(tomlPath));
        } catch (Exception e) {
            return null;
        }
        if (toml.hasErrors())
            return null;

        String id = null, name = null, version = null, mc = null;
        TomlArray mods = toml.getArray("mods");
        if (mods != null && !mods.isEmpty() && mods.get(0) instanceof TomlTable mod) {
            id = mod.getString("modId");
            name = mod.getString("displayName");
            version = resolveForgeVersion(tree, mod.getString("version"));
        }
        if (id != null) {
            TomlArray deps = dependencies(toml, id);
            if (deps != null) {
                for (int i = 0; i < deps.size(); i++) {
                    if (deps.get(i) instanceof TomlTable dep && "minecraft".equals(dep.getString("modId"))) {
                        mc = dep.getString("versionRange");
                        break;
                    }
                }
            }
        }
        return new Parsed(id, name, version, neo ? ModLoaderType.NEO_FORGE : ModLoaderType.FORGE, mc);
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
        try {
            TomlArray arr = toml.getArray("dependencies." + modId);
            if (arr != null)
                return arr;
        } catch (Exception ignored) {
        }
        try {
            return toml.getArray("dependencies");
        } catch (Exception ignored) {
        }
        return null;
    }

    // ── helpers ───────────────────────────────────────────────────────
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
