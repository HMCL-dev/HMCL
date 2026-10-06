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
package org.jackhuang.hmcl.addon.meta;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModConflict;
import org.jackhuang.hmcl.addon.mod.ModDependency;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Immutable
public final class FabricModMetadata {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String icon;
    private final List<FabricModAuthor> authors;
    private final Map<String, String> contact;
    private final List<FabricNestedJar> jars;
    private final Map<String, Object> depends;
    /// Alternative capability IDs exposed by this Fabric mod.
    private final List<String> provides;
    /// Hard incompatibility declarations.
    private final Map<String, Object> breaks;
    /// Soft incompatibility declarations.
    private final Map<String, Object> conflicts;
    /// Optional dependencies that should normally be present.
    private final Map<String, Object> recommends;
    /// Optional dependency suggestions retained for relation display.
    private final Map<String, Object> suggests;

    /// Non-installable loader and platform dependency IDs.
    private static final Set<String> IGNORED_DEPENDENCIES = Set.of("minecraft", "java", "fabricloader", "fabric");

    public FabricModMetadata() {
        this("", "", "", "", "", Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    public FabricModMetadata(String id, String name, String version, String icon, String description,
                             List<FabricModAuthor> authors, Map<String, String> contact,
                             List<FabricNestedJar> jars, Map<String, Object> depends,
                             List<String> provides, Map<String, Object> breaks,
                             Map<String, Object> conflicts, Map<String, Object> recommends,
                             Map<String, Object> suggests) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.icon = icon;
        this.description = description;
        this.authors = authors;
        this.contact = contact;
        this.jars = jars;
        this.depends = depends;
        this.provides = provides;
        this.breaks = breaks;
        this.conflicts = conflicts;
        this.recommends = recommends;
        this.suggests = suggests;
    }

    public static LocalModFile fromFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException, JsonParseException {
        ZipArchiveEntry mcmod = tree.getEntry("fabric.mod.json");
        if (mcmod == null)
            throw new IOException("File " + modFile + " is not a Fabric mod.");
        FabricModMetadata metadata = JsonUtils.fromNonNullJsonFully(tree.getInputStream(mcmod), FabricModMetadata.class);
        if (ModManager.isPlaceholderModId(metadata.id)) {
            throw new IOException("Fabric metadata contains an unexpanded mod id placeholder");
        }
        String authors = metadata.authors == null ? "" : metadata.authors.stream().map(author -> author.name).collect(Collectors.joining(", "));
        List<String> bundledMods = metadata.jars == null ? Collections.emptyList()
                : metadata.jars.stream().map(jar -> jar.file).toList();
        String minecraftConstraint = metadata.depends != null && metadata.depends.containsKey("minecraft")
                ? dependencyConstraint(metadata.depends.get("minecraft"))
                : "";
        List<ModDependency> dependencies = new ArrayList<>();
        if (metadata.depends != null) {
            for (Map.Entry<String, Object> entry : metadata.depends.entrySet()) {
                if (!IGNORED_DEPENDENCIES.contains(entry.getKey())) {
                    dependencies.add(new ModDependency(
                            entry.getKey(), dependencyConstraint(entry.getValue()), false, ModLoaderType.FABRIC));
                }
            }
        }
        addDependencies(dependencies, metadata.recommends, true);
        addDependencies(dependencies, metadata.suggests, true);
        Map<String, String> providedVersions = new LinkedHashMap<>();
        if (metadata.provides != null) {
            for (String provided : metadata.provides) {
                if (provided != null && !provided.isBlank()) {
                    providedVersions.put(provided, metadata.version);
                }
            }
        }
        List<ModConflict> conflicts = new ArrayList<>();
        addConflicts(conflicts, metadata.breaks, true);
        addConflicts(conflicts, metadata.conflicts, false);
        return new LocalModFile(modManager, modManager.getLocalMod(metadata.id, ModLoaderType.FABRIC), modFile, metadata.name, new LocalAddonFile.Description(metadata.description),
                authors, metadata.version, minecraftConstraint,
                metadata.contact != null ? metadata.contact.getOrDefault("homepage", "") : "", metadata.icon,
                bundledMods, dependencies, providedVersions, conflicts,
                !minecraftConstraint.isBlank());
    }

    /// Converts Fabric's string-or-array dependency value to its OR predicate representation.
    private static String dependencyConstraint(Object value) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast)
                    .collect(Collectors.joining(" || "));
        }
        return "*";
    }

    /// Adds dependencies from a Fabric metadata map while preserving optionality and constraints.
    private static void addDependencies(
            List<ModDependency> result,
            Map<String, Object> declarations,
            boolean optional) {
        if (declarations == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : declarations.entrySet()) {
            if (!IGNORED_DEPENDENCIES.contains(entry.getKey())) {
                result.add(new ModDependency(
                        entry.getKey(), dependencyConstraint(entry.getValue()), optional, ModLoaderType.FABRIC));
            }
        }
    }

    /// Adds Fabric hard or soft conflict declarations from a metadata map.
    private static void addConflicts(
            List<ModConflict> result,
            Map<String, Object> declarations,
            boolean hard) {
        if (declarations == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : declarations.entrySet()) {
            if (!IGNORED_DEPENDENCIES.contains(entry.getKey())) {
                result.add(new ModConflict(
                        entry.getKey(), dependencyConstraint(entry.getValue()), hard, ModLoaderType.FABRIC));
            }
        }
    }

    public static final class FabricNestedJar {
        @SerializedName("file")
        private final String file;

        /// Creates an empty value for Gson deserialization.
        public FabricNestedJar() {
            this.file = "";
        }
    }

    @JsonAdapter(FabricModAuthorSerializer.class)
    public static final class FabricModAuthor {
        private final String name;

        public FabricModAuthor() {
            this("");
        }

        public FabricModAuthor(String name) {
            this.name = name;
        }
    }

    public static final class FabricModAuthorSerializer implements JsonSerializer<FabricModAuthor>, JsonDeserializer<FabricModAuthor> {
        @Override
        public FabricModAuthor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return json.isJsonPrimitive() ? new FabricModAuthor(json.getAsString()) : new FabricModAuthor(json.getAsJsonObject().getAsJsonPrimitive("name").getAsString());
        }

        @Override
        public JsonElement serialize(FabricModAuthor src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.name);
        }
    }
}
