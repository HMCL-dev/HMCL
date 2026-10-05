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
import org.jackhuang.hmcl.addon.mod.NestedJarInspector.NestedJar;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// On-disk store of the per-instance Jar-in-Jar scan results, so the expensive deep scan (extracting
/// nested jars) is only paid once per mod change instead of on every launcher start.
///
/// One JSON file per instance, keyed by the mod file's path relative to the mods directory plus its
/// {@code mtime + size} fingerprint. Only the {@link NestedJar} tree is stored — top-level mod
/// metadata is still parsed normally by {@link ModManager}.
///
/// The tree is (de)serialized by hand through Gson's {@link JsonObject}/{@link JsonArray} model rather
/// than a record adapter: it avoids the reflection that native-image builds forbid, and keeps a
/// recursive shape trivial to read/write.
final class NestedJarCache {
    // Bump whenever the scanner's produced data changes (shape or values), so stale caches are
    // discarded and regenerated. v2: Forge ${file.jarVersion} placeholders resolved. v3: Fabric/Quilt
    // ${...} placeholders nulled out too. v4: nested jars declared via JarJar metadata /
    // Embedded-Dependencies-Mod (no mods.toml) are now discovered. v5 persists dependency constraints
    // and provided aliases used by the relation resolver. v6 adds Forge JarJar coordinates and
    // allowed/actual artifact versions for candidate selection. v7 keys parsed trees by the active
    // loader set so dual-descriptor jars cannot be reused across incompatible instance loaders.
    private static final int FORMAT_VERSION = 7;

    private NestedJarCache() {
    }

    record Entry(long lastModified, long size, String loaderKey, @Unmodifiable List<NestedJar> tree) {
        /// Creates an immutable persisted entry.
        Entry {
            tree = List.copyOf(tree);
        }
    }

    /// Reads the cache file. Returns an empty (mutable) map when the file is absent, unreadable, or of
    /// an unknown format version — the caller then simply rescans.
    static Map<String, Entry> load(Path file) {
        Map<String, Entry> result = new LinkedHashMap<>();
        if (!Files.isRegularFile(file))
            return result;
        try {
            JsonObject root = JsonUtils.GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !root.has("formatVersion") || root.get("formatVersion").getAsInt() != FORMAT_VERSION)
                return new LinkedHashMap<>();
            if (root.get("entries") instanceof JsonArray entries) {
                for (JsonElement el : entries) {
                    // Per-entry tolerance: one malformed entry (e.g. from a truncated write) only
                    // costs a rescan of that mod, not of the whole instance.
                    try {
                        if (!el.isJsonObject())
                            continue;
                        JsonObject e = el.getAsJsonObject();
                        String path = optString(e, "path");
                        if (path == null || !isSafeRelativeKey(path)
                                || !e.has("lastModified") || !e.has("size"))
                            continue;
                        result.put(path, new Entry(
                                e.get("lastModified").getAsLong(),
                                e.get("size").getAsLong(),
                                e.has("loaderKey") ? e.get("loaderKey").getAsString() : "",
                                readNodes(e.get("tree"))));
                    } catch (Exception entryEx) {
                        LOG.warning("Skipping malformed Jar-in-Jar cache entry in " + file, entryEx);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warning("Failed to read Jar-in-Jar cache " + file + ", ignoring", ex);
            return new LinkedHashMap<>();
        }
        return result;
    }

    /// Atomically writes the cache file (temp + move), creating parent directories as needed.
    static void save(Path file, Map<String, Entry> entries) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("formatVersion", FORMAT_VERSION);
            JsonArray arr = new JsonArray();
            for (Map.Entry<String, Entry> me : entries.entrySet()) {
                JsonObject e = new JsonObject();
                e.addProperty("path", me.getKey());
                e.addProperty("lastModified", me.getValue().lastModified());
                e.addProperty("size", me.getValue().size());
                e.addProperty("loaderKey", me.getValue().loaderKey());
                e.add("tree", writeNodes(me.getValue().tree()));
                arr.add(e);
            }
            root.add("entries", arr);

            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, JsonUtils.GSON.toJson(root));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            LOG.warning("Failed to write Jar-in-Jar cache " + file, ex);
        }
    }

    private static JsonArray writeNodes(List<NestedJar> nodes) {
        JsonArray arr = new JsonArray();
        for (NestedJar node : nodes) {
            JsonObject o = new JsonObject();
            o.addProperty("path", node.path());
            o.addProperty("fileName", node.fileName());
            if (node.id() != null)
                o.addProperty("id", node.id());
            if (node.name() != null)
                o.addProperty("name", node.name());
            if (node.version() != null)
                o.addProperty("version", node.version());
            o.addProperty("loader", node.loaderType().name());
            if (node.minecraftVersion() != null)
                o.addProperty("mc", node.minecraftVersion());
            o.addProperty("mcRequired", node.minecraftConstraintRequired());
            if (!node.dependencies().isEmpty()) {
                JsonArray dependencies = new JsonArray();
                for (ModDependency dependency : node.dependencies()) {
                    JsonObject declared = new JsonObject();
                    declared.addProperty("id", dependency.id());
                    declared.addProperty("constraint", dependency.versionConstraint());
                    declared.addProperty("optional", dependency.optional());
                    declared.addProperty("loader", dependency.declaringLoader().name());
                    dependencies.add(declared);
                }
                o.add("dependencies", dependencies);
            }
            if (!node.providedVersions().isEmpty()) {
                JsonObject provided = new JsonObject();
                node.providedVersions().forEach(provided::addProperty);
                o.add("provides", provided);
            }
            if (!node.conflicts().isEmpty()) {
                JsonArray conflicts = new JsonArray();
                for (ModConflict conflict : node.conflicts()) {
                    JsonObject declared = new JsonObject();
                    declared.addProperty("id", conflict.id());
                    declared.addProperty("constraint", conflict.versionConstraint());
                    declared.addProperty("hard", conflict.hard());
                    declared.addProperty("loader", conflict.declaringLoader().name());
                    conflicts.add(declared);
                }
                o.add("conflicts", conflicts);
            }
            if (node.jijIdentifier() != null)
                o.addProperty("jijIdentifier", node.jijIdentifier());
            if (node.jijVersionRange() != null)
                o.addProperty("jijVersionRange", node.jijVersionRange());
            if (node.jijArtifactVersion() != null)
                o.addProperty("jijArtifactVersion", node.jijArtifactVersion());
            if (!node.children().isEmpty())
                o.add("children", writeNodes(node.children()));
            arr.add(o);
        }
        return arr;
    }

    private static List<NestedJar> readNodes(JsonElement element) {
        List<NestedJar> result = new ArrayList<>();
        if (!(element instanceof JsonArray arr)) {
            throw new IllegalArgumentException("Jar-in-Jar cache tree is not an array");
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                throw new IllegalArgumentException("Jar-in-Jar cache node is not an object");
            }
            JsonObject o = el.getAsJsonObject();
            if (optString(o, "path") == null || optString(o, "fileName") == null) {
                throw new IllegalArgumentException("Jar-in-Jar cache node is missing its path");
            }
            List<NestedJar> children = o.has("children") ? readNodes(o.get("children")) : List.of();
            List<ModDependency> dependencies = readDependencies(o.get("dependencies"));
            Map<String, String> providedVersions = readProvidedVersions(o.get("provides"));
            List<ModConflict> conflicts = readConflicts(o.get("conflicts"));
            result.add(new NestedJar(
                    optString(o, "path"),
                    optString(o, "fileName"),
                    optString(o, "id"),
                    optString(o, "name"),
                    optString(o, "version"),
                    parseLoader(optString(o, "loader")),
                    optString(o, "mc"),
                    o.has("mcRequired") && o.get("mcRequired").getAsBoolean(),
                    dependencies,
                    providedVersions,
                    conflicts,
                    optString(o, "jijIdentifier"),
                    optString(o, "jijVersionRange"),
                    optString(o, "jijArtifactVersion"),
                    children));
        }
        return List.copyOf(result);
    }

    /// Reads dependency declarations from one cached node.
    private static List<ModDependency> readDependencies(JsonElement element) {
        List<ModDependency> result = new ArrayList<>();
        if (element == null) {
            return List.of();
        }
        if (!(element instanceof JsonArray dependencies)) {
            throw new IllegalArgumentException("Jar-in-Jar dependency cache is not an array");
        }
        for (JsonElement item : dependencies) {
            if (!(item instanceof JsonObject dependency)) {
                throw new IllegalArgumentException("Jar-in-Jar dependency cache item is malformed");
            }
            String id = optString(dependency, "id");
            if (id == null) {
                continue;
            }
            String constraint = optString(dependency, "constraint");
            boolean optional = dependency.has("optional") && dependency.get("optional").getAsBoolean();
            result.add(new ModDependency(
                    id,
                    constraint == null ? "*" : constraint,
                    optional,
                    parseLoader(optString(dependency, "loader"))));
        }
        return List.copyOf(result);
    }

    /// Reads provided capability versions from one cached node.
    private static Map<String, String> readProvidedVersions(JsonElement element) {
        Map<String, String> result = new LinkedHashMap<>();
        if (element == null) {
            return Map.of();
        }
        if (!(element instanceof JsonObject provided)) {
            throw new IllegalArgumentException("Jar-in-Jar provides cache is not an object");
        }
        for (Map.Entry<String, JsonElement> entry : provided.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                throw new IllegalArgumentException("Jar-in-Jar provided version is malformed");
            }
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return Map.copyOf(result);
    }

    /// Reads conflict declarations from one cached node.
    private static List<ModConflict> readConflicts(JsonElement element) {
        List<ModConflict> result = new ArrayList<>();
        if (element == null) {
            return List.of();
        }
        if (!(element instanceof JsonArray conflicts)) {
            throw new IllegalArgumentException("Jar-in-Jar conflict cache is not an array");
        }
        for (JsonElement item : conflicts) {
            if (!(item instanceof JsonObject conflict)) {
                throw new IllegalArgumentException("Jar-in-Jar conflict cache item is malformed");
            }
            String id = optString(conflict, "id");
            if (id == null) {
                continue;
            }
            String constraint = optString(conflict, "constraint");
            boolean hard = conflict.has("hard") && conflict.get("hard").getAsBoolean();
            result.add(new ModConflict(
                    id,
                    constraint == null ? "*" : constraint,
                    hard,
                    parseLoader(optString(conflict, "loader"))));
        }
        return List.copyOf(result);
    }

    private static String optString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /// Returns whether a persisted key remains inside the instance mods directory when resolved.
    private static boolean isSafeRelativeKey(String key) {
        try {
            Path path = Path.of(key.replace('/', java.io.File.separatorChar)).normalize();
            return !path.isAbsolute()
                    && path.getNameCount() > 0
                    && !"..".equals(path.getName(0).toString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static ModLoaderType parseLoader(String name) {
        if (name == null)
            return ModLoaderType.UNKNOWN;
        try {
            return ModLoaderType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ModLoaderType.UNKNOWN;
        }
    }
}
