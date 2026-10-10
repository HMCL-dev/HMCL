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
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.JsonAdapter;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModConflict;
import org.jackhuang.hmcl.addon.mod.ModDependency;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@Immutable
public final class ForgeNewModMetadata {
    /// Non-installable loader and platform dependency IDs.
    private static final Set<String> IGNORED_DEPENDENCIES = Set.of("minecraft", "forge", "neoforge");

    private final String modLoader;

    private final String loaderVersion;

    private final String logoFile;

    private final String license;

    private final List<Mod> mods;

    public ForgeNewModMetadata(String modLoader, String loaderVersion, String logoFile, String license, List<Mod> mods) {
        this.modLoader = modLoader;
        this.loaderVersion = loaderVersion;
        this.logoFile = logoFile;
        this.license = license;
        this.mods = mods;
    }

    public String getModLoader() {
        return modLoader;
    }

    public String getLoaderVersion() {
        return loaderVersion;
    }

    public String getLogoFile() {
        return logoFile;
    }

    public String getLicense() {
        return license;
    }

    public List<Mod> getMods() {
        return mods;
    }

    public static class Mod {
        private final String modId;
        private final String version;
        private final String displayName;
        private final String side;
        private final String displayURL;
        @JsonAdapter(AuthorDeserializer.class)
        private final String authors;
        private final String description;
        private final String logoFile;

        public Mod() {
            this("", "", "", "", "", "", "", "");
        }

        public Mod(String modId, String version, String displayName, String side, String displayURL, String authors, String description, String logoFile) {
            this.modId = modId;
            this.version = version;
            this.displayName = displayName;
            this.side = side;
            this.displayURL = displayURL;
            this.authors = authors;
            this.description = description;
            this.logoFile = logoFile;
        }

        public String getModId() {
            return modId;
        }

        public String getVersion() {
            return version;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSide() {
            return side;
        }

        public String getDisplayURL() {
            return displayURL;
        }

        public String getAuthors() {
            return authors;
        }

        public String getDescription() {
            return description;
        }

        public String getLogoFile() {
            return logoFile;
        }

        static final class AuthorDeserializer implements JsonDeserializer<String> {
            @Override
            public String deserialize(JsonElement authors, Type type, JsonDeserializationContext context) throws JsonParseException {
                if (authors == null || authors.isJsonNull()) {
                    return null;
                } else if (authors instanceof JsonPrimitive primitive) {
                    return primitive.getAsString();
                } else if (authors instanceof JsonArray array) {
                    var joiner = new StringJoiner(", ");
                    for (int i = 0; i < array.size(); i++) {
                        if (!(array.get(i) instanceof JsonPrimitive element)) {
                            return authors.toString();
                        }

                        joiner.add(element.getAsString());
                    }
                    return joiner.toString();
                }

                return authors.toString();
            }
        }
    }

    public static LocalModFile fromForgeFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException {
        return fromFile(modManager, modFile, tree, ModLoaderType.FORGE);
    }

    public static LocalModFile fromNeoForgeFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException {
        return fromFile(modManager, modFile, tree, ModLoaderType.NEO_FORGE);
    }

    private static LocalModFile fromFile(ModManager modManager, Path modFile, ZipFileTree tree, ModLoaderType modLoaderType) throws IOException {
        if (modLoaderType != ModLoaderType.FORGE && modLoaderType != ModLoaderType.NEO_FORGE) {
            throw new IOException("Invalid mod loader: " + modLoaderType);
        }

        if (modLoaderType == ModLoaderType.NEO_FORGE) {
            try {
                return fromFile0("META-INF/neoforge.mods.toml", modLoaderType, modManager, modFile, tree);
            } catch (Exception ignored) {
            }
        }

        try {
            return fromFile0("META-INF/mods.toml", modLoaderType, modManager, modFile, tree);
        } catch (Exception ignored) {
        }

        try {
            return fromEmbeddedMod(modManager, modFile, tree, modLoaderType);
        } catch (Exception ignored) {
        }

        throw new IOException("File " + modFile + " is not a Forge 1.13+ or NeoForge mod.");
    }

    private static LocalModFile fromFile0(
            String tomlPath,
            ModLoaderType modLoaderType,
            ModManager modManager,
            Path modFile,
            ZipFileTree tree) throws IOException, JsonParseException {
        ZipArchiveEntry modToml = tree.getEntry(tomlPath);
        if (modToml == null)
            throw new IOException("File " + modFile + " is not a Forge 1.13+ or NeoForge mod.");
        TomlParseResult tomlParseResult = Toml.parse(tree.readTextEntry(modToml));
        if (tomlParseResult.hasErrors()) {
            var ioException = new IOException("Mod " + modFile + " `%s` is malformed..".formatted(modToml.getName()));
            tomlParseResult.errors().forEach(ioException::addSuppressed);
            throw ioException;
        }
        ForgeNewModMetadata metadata = JsonUtils.GSON.fromJson(tomlParseResult.toJson(), ForgeNewModMetadata.class);
        if (metadata == null || metadata.getMods().isEmpty())
            throw new IOException("Mod " + modFile + " `%s` is malformed..".formatted(modToml.getName()));
        Mod mod = metadata.getMods().get(0);
        if (ModManager.isPlaceholderModId(mod.getModId())) {
            throw new IOException("Forge metadata contains an unexpanded mod id placeholder");
        }
        ZipArchiveEntry manifestMF = tree.getEntry("META-INF/MANIFEST.MF");
        String jarVersion = "";
        if (manifestMF != null) {
            try (InputStream is = tree.getInputStream(manifestMF)) {
                Manifest manifest = new Manifest(is);
                jarVersion = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            } catch (IOException e) {
                LOG.warning("Failed to parse MANIFEST.MF in file " + modFile);
            }
        }

        ModLoaderType type = analyzeLoader(tomlParseResult, mod.getModId(), modLoaderType);

        List<String> bundledMods = new ArrayList<>();
        ZipArchiveEntry jarInJar = tree.getEntry("META-INF/jarjar/metadata.json");
        if (jarInJar != null) {
            try {
                JarInJarMetadata jarInJarMetadata = JsonUtils.fromJsonFully(tree.getInputStream(jarInJar), JarInJarMetadata.class);
                if (jarInJarMetadata != null && jarInJarMetadata.jars() != null) {
                    for (EmbeddedJarMetadata jar : jarInJarMetadata.jars()) {
                        bundledMods.add(jar.path());
                    }
                }
            } catch (Exception e) {
                LOG.warning("Failed to parse Jar-in-Jar metadata for " + modFile, e);
            }
        }

        Set<String> declaredIds = new LinkedHashSet<>();
        Map<String, String> providedVersions = new LinkedHashMap<>();
        for (Mod declaredMod : metadata.getMods()) {
            if (StringUtils.isBlank(declaredMod.getModId())) {
                continue;
            }
            declaredIds.add(declaredMod.getModId());
            if (!declaredMod.getModId().equals(mod.getModId())) {
                providedVersions.put(declaredMod.getModId(), resolveDeclaredVersion(declaredMod, jarVersion));
            }
        }

        List<ModDependency> dependencies = new ArrayList<>();
        List<ModConflict> conflicts = new ArrayList<>();
        String minecraftConstraint = "";
        for (Mod declaredMod : metadata.getMods()) {
            for (Map<String, Object> dependency : parseDependencies(
                    tomlParseResult,
                    declaredMod.getModId(),
                    declaredMod == mod)) {
                if (!(dependency.get("modId") instanceof String depId)) {
                    continue;
                }
                String constraint = dependency.get("versionRange") instanceof String range ? range : "*";
                boolean required;
                if (dependency.get("mandatory") instanceof Boolean mandatory) {
                    required = mandatory;
                } else if (dependency.get("type") instanceof String depType) {
                    required = depType.equalsIgnoreCase("required");
                } else {
                    required = true;
                }
                boolean serverOnly = dependency.get("side") instanceof String side
                        && side.equalsIgnoreCase("server");
                if ("minecraft".equals(depId)) {
                    if (required && !serverOnly) {
                        minecraftConstraint = constraint;
                    }
                    continue;
                }
                if (IGNORED_DEPENDENCIES.contains(depId)
                        || declaredIds.contains(depId)
                        || serverOnly) {
                    continue;
                }
                if (dependency.get("type") instanceof String conflictType
                        && (conflictType.equalsIgnoreCase("incompatible")
                        || conflictType.equalsIgnoreCase("discouraged"))) {
                    conflicts.add(new ModConflict(
                            depId,
                            constraint,
                            conflictType.equalsIgnoreCase("incompatible"),
                            type));
                    continue;
                }
                dependencies.add(new ModDependency(depId, constraint, !required, type));
            }
        }

        String logoPath = StringUtils.isNotBlank(mod.getLogoFile()) ? mod.getLogoFile() : metadata.getLogoFile();

        return new LocalModFile(modManager, modManager.getLocalMod(mod.getModId(), type), modFile, mod.getDisplayName(), new LocalAddonFile.Description(mod.getDescription()),
                mod.getAuthors(), resolveDeclaredVersion(mod, jarVersion), minecraftConstraint,
                mod.getDisplayURL(),
                logoPath, bundledMods, dependencies, providedVersions, conflicts,
                !minecraftConstraint.isBlank());
    }

    /// Resolves Forge's file.jarVersion placeholder against the manifest implementation version.
    private static String resolveDeclaredVersion(Mod mod, String jarVersion) {
        String version = Objects.requireNonNullElse(mod.getVersion(), "");
        if (StringUtils.isNotBlank(jarVersion)) {
            version = version.replace("${file.jarVersion}", jarVersion);
        }
        return version.contains("${") ? "" : version;
    }

    private static LocalModFile fromEmbeddedMod(ModManager modManager, Path modFile, ZipFileTree tree, ModLoaderType modLoaderType) throws IOException {
        ZipArchiveEntry manifestFile = tree.getEntry("META-INF/MANIFEST.MF");
        if (manifestFile == null)
            throw new IOException("Missing MANIFEST.MF in file " + modFile);

        Manifest manifest;
        try (InputStream input = tree.getInputStream(manifestFile)) {
            manifest = new Manifest(input);
        }

        List<ZipArchiveEntry> embeddedModFiles = List.of();

        String embeddedDependenciesMod = manifest.getMainAttributes().getValue("Embedded-Dependencies-Mod");
        if (embeddedDependenciesMod != null) {
            ZipArchiveEntry embeddedModFile = tree.getEntry(embeddedDependenciesMod);
            if (embeddedModFile == null) {
                LOG.warning("Missing embedded-dependencies-mod: " + embeddedDependenciesMod);
                throw new IOException();
            }
            embeddedModFiles = List.of(embeddedModFile);
        } else {
            ZipArchiveEntry jarInJarMetadata = tree.getEntry("META-INF/jarjar/metadata.json");
            if (jarInJarMetadata != null) {
                JarInJarMetadata metadata = JsonUtils.fromJsonFully(tree.getInputStream(jarInJarMetadata), JarInJarMetadata.class);
                if (metadata == null)
                    throw new IOException("Invalid metadata file: " + jarInJarMetadata);

                metadata.validate();

                embeddedModFiles = new ArrayList<>();
                for (EmbeddedJarMetadata jar : metadata.jars) {
                    ZipArchiveEntry path = tree.getEntry(jar.path);
                    if (path != null) {
                        embeddedModFiles.add(path);
                    } else {
                        LOG.warning("Missing embedded-dependencies-mod: " + jar.path);
                    }
                }
            }
        }

        if (embeddedModFiles.isEmpty()) {
            throw new IOException("Missing embedded mods");
        }

        Path tempFile = Files.createTempFile("hmcl-", ".zip");
        try {
            for (ZipArchiveEntry embeddedModFile : embeddedModFiles) {
                tree.extractTo(embeddedModFile, tempFile);
                try (ZipFileTree embeddedTree = CompressingUtils.openZipTree(tempFile)) {
                    return fromFile(modManager, modFile, embeddedTree, modLoaderType);
                } catch (Exception ignored) {
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }

        throw new IOException();
    }

    private static List<Map<String, Object>> parseDependencies(TomlParseResult toml, String modID) {
        return parseDependencies(toml, modID, true);
    }

    /// Reads owner-specific dependencies and optionally accepts the legacy unscoped array form.
    private static List<Map<String, Object>> parseDependencies(
            TomlParseResult toml,
            String modID,
            boolean allowLegacyUnscoped) {
        try {
            TomlArray tomlArray = toml.getArray("dependencies." + modID);
            if (tomlArray != null) {
                return tomlArray.toList().stream().map(o -> ((TomlTable) o).toMap()).toList();
            }
        } catch (ClassCastException ignored) { // https://github.com/HMCL-dev/HMCL/issues/5068
        }

        try {
            TomlTable table = toml.getTable("dependencies");
            if (table != null) {
                TomlArray tomlArray = table.getArray(modID);
                if (tomlArray != null) {
                    return tomlArray.toList().stream().map(o -> ((TomlTable) o).toMap()).toList();
                }
            }
        } catch (Throwable ignored) {
        }

        if (allowLegacyUnscoped) {
            try {
                TomlArray tomlArray = toml.getArray("dependencies");
                if (tomlArray != null) {
                    return tomlArray.toList().stream().map(o -> ((TomlTable) o).toMap()).toList();
                }
            } catch (ClassCastException ignored) {
            }
        }

        return List.of();
    }

    private static ModLoaderType analyzeLoader(TomlParseResult toml, String modID, ModLoaderType loader) {
        List<Map<String, Object>> dependencies = parseDependencies(toml, modID);
        if (dependencies.isEmpty()) {
            return loader;
        }

        ModLoaderType result = null;
        loop:
        for (Map<String, Object> dependency : dependencies) {
            switch ((String) dependency.get("modId")) {
                case "forge":
                    result = ModLoaderType.FORGE;
                    break loop;
                case "neoforge":
                    result = ModLoaderType.NEO_FORGE;
                    break loop;
            }
        }

        if (result != null) {
            if (result != loader)
                LOG.warning("Loader mismatch for mod " + modID + ", found " + result + ", expecting " + loader);
            return result;
        } else {
            LOG.warning("Cannot determine the mod loader for mod " + modID + ", expected " + loader);
            return loader;
        }
    }

    @JsonSerializable
    private record JarInJarMetadata(List<EmbeddedJarMetadata> jars) implements Validation {
        @Override
        public void validate() throws JsonParseException {
            Validation.requireNonNull(jars, "jars");
            for (EmbeddedJarMetadata jar : jars) {
                jar.validate();
            }
        }
    }

    @JsonSerializable
    private record EmbeddedJarMetadata(
            String path,
            boolean isObfuscated
    ) implements Validation {
        @Override
        public void validate() throws JsonParseException {
            Validation.requireNonNull(path, "path");
        }
    }

}
