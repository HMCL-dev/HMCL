package org.jackhuang.hmcl.mod.modinfo;

import com.google.gson.JsonParseException;
import com.moandjiezana.toml.Toml;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@Immutable
public final class ForgeNewModMetadata {
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
        private final String authors;
        private final String description;

        public Mod() {
            this("", "", "", "", "", "", "");
        }

        public Mod(String modId, String version, String displayName, String side, String displayURL, String authors, String description) {
            this.modId = modId;
            this.version = version;
            this.displayName = displayName;
            this.side = side;
            this.displayURL = displayURL;
            this.authors = authors;
            this.description = description;
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
    }

    public static LocalModFile fromForgeFile(ModManager modManager, Path modFile, FileSystem fs) throws IOException {
        return fromFile(modManager, modFile, fs, ModLoaderType.FORGE);
    }

    public static LocalModFile fromNeoForgeFile(ModManager modManager, Path modFile, FileSystem fs) throws IOException {
        return fromFile(modManager, modFile, fs, ModLoaderType.NEO_FORGED);
    }

    private static LocalModFile fromFile(ModManager modManager, Path modFile, FileSystem fs, ModLoaderType modLoaderType) throws IOException {
        if (modLoaderType != ModLoaderType.FORGE && modLoaderType != ModLoaderType.NEO_FORGED) {
            throw new IOException("Invalid mod loader: " + modLoaderType);
        }

        if (modLoaderType == ModLoaderType.NEO_FORGED) {
            try {
                return fromFile0("META-INF/neoforge.mods.toml", modLoaderType, modManager, modFile, fs);
            } catch (Exception ignored) {
            }
        }

        try {
            return fromFile0("META-INF/mods.toml", modLoaderType, modManager, modFile, fs);
        } catch (Exception ignored) {
        }

        try {
            return fromEmbeddedMod(modManager, modFile, fs, modLoaderType);
        } catch (Exception ignored) {
        }

        throw new IOException("File " + modFile + " is not a Forge 1.13+ or NeoForge mod.");
    }

    private static LocalModFile fromFile0(
            String tomlPath,
            ModLoaderType modLoaderType,
            ModManager modManager,
            Path modFile,
            FileSystem fs) throws IOException, JsonParseException {
        Path modToml = fs.getPath(tomlPath);
        if (Files.notExists(modToml))
            throw new IOException("File " + modFile + " is not a Forge 1.13+ or NeoForge mod.");
        Toml toml = new Toml().read(Files.readString(modToml));
        ForgeNewModMetadata metadata = toml.to(ForgeNewModMetadata.class);
        if (metadata == null || metadata.getMods().isEmpty())
            throw new IOException("Mod " + modFile + " `mods.toml` is malformed..");
        Mod mod = metadata.getMods().get(0);
        Path manifestMF = fs.getPath("META-INF/MANIFEST.MF");
        String jarVersion = "";
        if (Files.exists(manifestMF)) {
            try (InputStream is = Files.newInputStream(manifestMF)) {
                Manifest manifest = new Manifest(is);
                jarVersion = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            } catch (IOException e) {
                LOG.warning("Failed to parse MANIFEST.MF in file " + modFile);
            }
        }

        ModLoaderType type = analyzeLoader(toml, mod.getModId(), modLoaderType);

        return new LocalModFile(modManager, modManager.getLocalMod(mod.getModId(), type), modFile, mod.getDisplayName(), new LocalModFile.Description(mod.getDescription()),
                mod.getAuthors(), jarVersion == null ? mod.getVersion() : mod.getVersion().replace("${file.jarVersion}", jarVersion), "",
                mod.getDisplayURL(),
                metadata.getLogoFile());
    }

    private static LocalModFile fromEmbeddedMod(ModManager modManager, Path modFile, FileSystem fs, ModLoaderType modLoaderType) throws IOException {
        Path manifestFile = fs.getPath("META-INF/MANIFEST.MF");
        if (!Files.isRegularFile(manifestFile))
            throw new IOException("Missing  MANIFEST.MF in file " + manifestFile);

        Manifest manifest;
        try (InputStream input = Files.newInputStream(manifestFile)) {
            manifest = new Manifest(input);
        }

        List<Path> embeddedModFiles = List.of();

        String embeddedDependenciesMod = manifest.getMainAttributes().getValue("Embedded-Dependencies-Mod");
        if (embeddedDependenciesMod != null) {
            Path embeddedModFile = fs.getPath(embeddedDependenciesMod);
            if (!Files.isRegularFile(embeddedModFile)) {
                LOG.warning("Missing embedded-dependencies-mod: " + embeddedModFile);
                throw new IOException();
            }
            embeddedModFiles = List.of(embeddedModFile);
        } else {
            Path jarInJarMetadata = fs.getPath("META-INF/jarjar/metadata.json");
            if (Files.isRegularFile(jarInJarMetadata)) {
                JarInJarMetadata metadata = JsonUtils.fromJsonFile(jarInJarMetadata, JarInJarMetadata.class);
                if (metadata == null)
                    throw new IOException("Invalid metadata file: " + jarInJarMetadata);

                metadata.validate();

                embeddedModFiles = new ArrayList<>();
                for (EmbeddedJarMetadata jar : metadata.jars) {
                    Path path = fs.getPath(jar.path);
                    if (Files.isRegularFile(path)) {
                        embeddedModFiles.add(path);
                    } else {
                        LOG.warning("Missing embedded-dependencies-mod: " + path);
                    }
                }
            }
        }

        if (embeddedModFiles.isEmpty()) {
            throw new IOException("Missing embedded mods");
        }

        Path tempFile = Files.createTempFile("hmcl-", ".zip");
        try {
            for (Path embeddedModFile : embeddedModFiles) {
                Files.copy(embeddedModFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
                try (FileSystem embeddedFs = CompressingUtils.createReadOnlyZipFileSystem(tempFile)) {
                    return fromFile(modManager, modFile, embeddedFs, modLoaderType);
                } catch (Exception ignored) {
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }

        throw new IOException();
    }

    private static ModLoaderType analyzeLoader(Toml toml, String modID, ModLoaderType loader) throws IOException {
        List<HashMap<String, Object>> dependencies = toml.getList("dependencies." + modID);
        if (dependencies == null) {
            try {
                dependencies = toml.getList("dependencies"); // ??? I have no idea why some of the Forge mods use [[dependencies]]
            } catch (ClassCastException e) {
                try {
                    Toml table = toml.getTable("dependencies");
                    if (table == null)
                        return loader;

                    dependencies = table.getList(modID);
                } catch (Throwable ignored) {
                }
            }

            if (dependencies == null) {
                return loader;
            }
        }

        ModLoaderType result = null;
        loop:
        for (HashMap<String, Object> dependency : dependencies) {
            switch ((String) dependency.get("modId")) {
                case "forge":
                    result = ModLoaderType.FORGE;
                    break loop;
                case "neoforge":
                    result = ModLoaderType.NEO_FORGED;
                    break loop;
            }
        }

        if (result == loader)
            return result;
        else if (result != null)
            throw new IOException("Loader mismatch");
        else {
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
