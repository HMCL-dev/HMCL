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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Immutable
public final class QuiltModMetadata {
    private static final class QuiltLoader {
        private static final class Metadata {
            private final String name;
            private final String description;
            private final JsonObject contributors;
            private final String icon;
            private final JsonObject contact;

            public Metadata(String name, String description, JsonObject contributors, String icon, JsonObject contact) {
                this.name = name;
                this.description = description;
                this.contributors = contributors;
                this.icon = icon;
                this.contact = contact;
            }
        }

        private final String id;
        private final String version;
        private final Metadata metadata;
        // Quilt declares nested jars as a plain array of path strings (e.g. ["sub.jar"]), unlike
        // Fabric's array of {"file": "..."} objects. Modeling it as objects makes Gson throw and the
        // whole (otherwise valid) Quilt mod fall back to UNKNOWN, losing nested mods and dependencies.
        private final List<String> jars;
        private final JsonArray depends;
        /// Alternative capabilities exposed by the Quilt mod.
        private final JsonArray provides;
        /// Hard incompatibility declarations.
        private final JsonArray breaks;

        public QuiltLoader(String id, String version, Metadata metadata, List<String> jars,
                           JsonArray depends, JsonArray provides, JsonArray breaks) {
            this.id = id;
            this.version = version;
            this.metadata = metadata;
            this.jars = jars;
            this.depends = depends;
            this.provides = provides;
            this.breaks = breaks;
        }
    }

    // Loader/runtime/platform ids that are not shown as user-facing mod dependencies. Fabric API
    // (fabric-api) is deliberately NOT here: it is a real installable mod, so it must stay in the
    // dependency graph for the installed-status hint and the disable/remove cascade to work.
    private static final Set<String> IGNORED_DEPENDENCIES = Set.of("minecraft", "java", "quilt_loader", "quilt_base", "fabric");

    private final int schema_version;
    private final QuiltLoader quilt_loader;

    public QuiltModMetadata(int schemaVersion, QuiltLoader quiltLoader) {
        this.schema_version = schemaVersion;
        this.quilt_loader = quiltLoader;
    }

    public static LocalModFile fromFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException, JsonParseException {
        ZipArchiveEntry path = tree.getEntry("quilt.mod.json");
        if (path == null) {
            throw new IOException("File " + modFile + " is not a Quilt mod.");
        }

        QuiltModMetadata root = JsonUtils.fromNonNullJsonFully(tree.getInputStream(path), QuiltModMetadata.class);
        if (root.schema_version != 1) {
            throw new IOException("File " + modFile + " is not a supported Quilt mod.");
        }
        if (ModManager.isPlaceholderModId(root.quilt_loader.id)) {
            throw new IOException("Quilt metadata contains an unexpanded mod id placeholder");
        }

        String authors = root.quilt_loader.metadata.contributors == null ? ""
                : root.quilt_loader.metadata.contributors.entrySet().stream().map(entry -> String.format("%s (%s)", entry.getKey(), entry.getValue().getAsJsonPrimitive().getAsString())).collect(Collectors.joining(", "));
        String homepage = root.quilt_loader.metadata.contact == null ? ""
                : Optional.ofNullable(root.quilt_loader.metadata.contact.get("homepage")).map(jsonElement -> jsonElement.getAsJsonPrimitive().getAsString()).orElse("");
        List<String> bundledMods = root.quilt_loader.jars == null ? Collections.emptyList()
                : List.copyOf(root.quilt_loader.jars);

        List<ModDependency> dependencies = new ArrayList<>();
        String minecraftConstraint = "";
        if (root.quilt_loader.depends != null) {
            for (JsonElement element : root.quilt_loader.depends) {
                String id = null;
                String constraint = "*";
                boolean optional = false;
                boolean serverOnly = false;
                if (element.isJsonPrimitive()) {
                    id = element.getAsString();
                } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                    JsonObject dependency = element.getAsJsonObject();
                    id = dependency.getAsJsonPrimitive("id").getAsString();
                    if (dependency.has("versions")) {
                        constraint = dependencyConstraint(dependency.get("versions"));
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
                    if (!optional) {
                        minecraftConstraint = constraint;
                    }
                } else if (id != null && !IGNORED_DEPENDENCIES.contains(id)) {
                    dependencies.add(new ModDependency(id, constraint, optional, ModLoaderType.QUILT));
                }
            }
        }

        Map<String, String> providedVersions = new LinkedHashMap<>();
        if (root.quilt_loader.provides != null) {
            for (JsonElement element : root.quilt_loader.provides) {
                if (element.isJsonPrimitive()) {
                    providedVersions.put(element.getAsString(), root.quilt_loader.version);
                } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                    JsonObject provided = element.getAsJsonObject();
                    String id = provided.getAsJsonPrimitive("id").getAsString();
                    String version = provided.has("version")
                            ? provided.getAsJsonPrimitive("version").getAsString()
                            : root.quilt_loader.version;
                    providedVersions.put(id, version);
                }
            }
        }
        List<ModConflict> conflicts = new ArrayList<>();
        if (root.quilt_loader.breaks != null) {
            for (JsonElement element : root.quilt_loader.breaks) {
                String id = null;
                String constraint = "*";
                if (element.isJsonPrimitive()) {
                    id = element.getAsString();
                } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                    JsonObject conflict = element.getAsJsonObject();
                    id = conflict.get("id").getAsString();
                    if (conflict.has("versions")) {
                        constraint = dependencyConstraint(conflict.get("versions"));
                    }
                }
                if (id != null && !IGNORED_DEPENDENCIES.contains(id)) {
                    conflicts.add(new ModConflict(id, constraint, true, ModLoaderType.QUILT));
                }
            }
        }

        return new LocalModFile(
                modManager,
                modManager.getLocalMod(root.quilt_loader.id, ModLoaderType.QUILT),
                modFile,
                root.quilt_loader.metadata.name,
                new LocalAddonFile.Description(root.quilt_loader.metadata.description),
                authors,
                root.quilt_loader.version,
                minecraftConstraint,
                homepage,
                root.quilt_loader.metadata.icon,
                bundledMods,
                dependencies,
                providedVersions,
                conflicts,
                !minecraftConstraint.isBlank()
        );
    }

    /// Converts Quilt's recursive any/all version specifier to the matcher predicate syntax.
    private static String dependencyConstraint(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "*";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            List<String> alternatives = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                alternatives.add(dependencyConstraint(child));
            }
            return String.join(" || ", alternatives);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("any")) {
                return dependencyConstraint(object.get("any"));
            }
            if (object.has("all") && object.get("all").isJsonArray()) {
                List<String> terms = new ArrayList<>();
                for (JsonElement child : object.getAsJsonArray("all")) {
                    terms.add(dependencyConstraint(child));
                }
                return String.join(" ", terms);
            }
        }
        return "*";
    }
}
