package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import com.moandjiezana.toml.Toml;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

@Immutable
public final class ForgeNewModMetadata {

    private final String modLoader;

    private final String loaderVersion;

    private final String logoFile;

    private final String license;

    private final List<Mod> mods;

    public ForgeNewModMetadata() {
        this("", "", "", "", Collections.emptyList());
    }

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

    public static ModInfo fromFile(File modFile) throws IOException, JsonParseException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modFile.toPath())) {
            Path modstoml = fs.getPath("META-INF/mods.toml");
            if (Files.notExists(modstoml))
                throw new IOException("File " + modFile + " is not a Forge 1.13+ mod.");
            ForgeNewModMetadata metadata = new Toml().read(FileUtils.readText(modstoml)).to(ForgeNewModMetadata.class);
            if (metadata == null || metadata.getMods().isEmpty())
                throw new IOException("Mod " + modFile + " `mods.toml` is malformed..");
            Mod mod = metadata.getMods().get(0);
            Path manifestMF = fs.getPath("META-INF/MANIFEST.MF");
            String jarVersion = "";
            if (Files.exists(manifestMF)) {
                try {
                    Manifest manifest = new Manifest(Files.newInputStream(manifestMF));
                    jarVersion = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to parse MANIFEST.MF in file " + modFile.getPath());
                }
            }
            return new ModInfo(modFile, mod.getModId(), mod.getDisplayName(), new ModInfo.Description(mod.getDescription()),
                    mod.getAuthors(), mod.getVersion().replace("${file.jarVersion}", jarVersion), "",
                    mod.getDisplayURL(),
                    metadata.getLogoFile());
        }
    }
}
