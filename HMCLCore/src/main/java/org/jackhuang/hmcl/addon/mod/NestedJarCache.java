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
    private static final int FORMAT_VERSION = 1;

    private NestedJarCache() {
    }

    record Entry(long lastModified, long size, List<NestedJar> tree) {
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
                        if (path == null || !e.has("lastModified") || !e.has("size"))
                            continue;
                        result.put(path, new Entry(
                                e.get("lastModified").getAsLong(),
                                e.get("size").getAsLong(),
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
            if (!node.children().isEmpty())
                o.add("children", writeNodes(node.children()));
            arr.add(o);
        }
        return arr;
    }

    private static List<NestedJar> readNodes(JsonElement element) {
        List<NestedJar> result = new ArrayList<>();
        if (element instanceof JsonArray arr) {
            for (JsonElement el : arr) {
                if (!el.isJsonObject())
                    continue;
                JsonObject o = el.getAsJsonObject();
                List<NestedJar> children = o.has("children") ? readNodes(o.get("children")) : List.of();
                result.add(new NestedJar(
                        optString(o, "path"),
                        optString(o, "fileName"),
                        optString(o, "id"),
                        optString(o, "name"),
                        optString(o, "version"),
                        parseLoader(optString(o, "loader")),
                        optString(o, "mc"),
                        children));
            }
        }
        return result;
    }

    private static String optString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
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
