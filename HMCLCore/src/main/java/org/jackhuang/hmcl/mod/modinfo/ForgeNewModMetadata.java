package org.jackhuang.hmcl.mod.modinfo;

import com.google.gson.JsonParseException;
import com.moandjiezana.toml.Toml;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static LocalModFile fromFile(ModManager modManager, Path modFile, FileSystem fs) throws IOException, JsonParseException {
        Path modstoml = fs.getPath("META-INF/mods.toml");
        if (Files.notExists(modstoml))
            throw new IOException("File " + modFile + " is not a Forge 1.13+ or NeoForge mod.");
        Toml toml = new Toml().read(FileUtils.readText(modstoml));
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

        ModLoaderType modLoaderType = analyzeModLoader(toml, mod.getModId());
        if (modLoaderType == null) {
            throw new IOException("File " + modFile + " is not a Forge 1.13+ or NeoForge mod.");
        }

        return new LocalModFile(modManager, modManager.getLocalMod(mod.getModId(), modLoaderType), modFile, mod.getDisplayName(), new LocalModFile.Description(mod.getDescription()),
                mod.getAuthors(), jarVersion == null ? mod.getVersion() : mod.getVersion().replace("${file.jarVersion}", jarVersion), "",
                mod.getDisplayURL(),
                metadata.getLogoFile());
    }

    private static ModLoaderType analyzeModLoader(Toml toml, String modID) {
        List<HashMap<String, Object>> dependencies = toml.getList("dependencies." + modID);
        if (dependencies != null) {
            for (HashMap<String, Object> dependency : dependencies) {
                switch ((String) dependency.get("modId")) {
                    case "forge": return ModLoaderType.FORGE;
                    case "neoforge": return ModLoaderType.NEO_FORGED;
                }
            }
        }

        return null;
    }
}
