package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.util.*;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

public class Datapack {
    private boolean isMultiple;
    private final Path path;
    private final ObservableList<Pack> info = FXCollections.observableArrayList();

    public Datapack(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public ObservableList<Pack> getInfo() {
        return info;
    }

    public void installTo(Path worldPath) throws IOException {
        Path datapacks = worldPath.resolve("datapacks");

        Set<String> packs = new HashSet<>();
        for (Pack pack : info) packs.add(pack.getId());

        if (Files.isDirectory(datapacks))
            for (Path datapack : Files.newDirectoryStream(datapacks)) {
                if (packs.contains(FileUtils.getName(datapack)))
                    FileUtils.deleteDirectory(datapack.toFile());
            }

        if (isMultiple) {
            new Unzipper(path, worldPath).setReplaceExistentFile(true).unzip();
        } else {
            new Unzipper(path, worldPath.resolve("datapacks").resolve(FileUtils.getNameWithoutExtension(path))).unzip();
        }
    }

    public void deletePack(String pack) throws IOException {
        FileUtils.deleteDirectory(path.resolve(pack).toFile());
        Platform.runLater(() -> info.removeIf(p -> p.getId().equals(pack)));
    }

    public void loadFromZip() throws IOException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(path)) {
            Path datapacks = fs.getPath("/datapacks/");
            Path mcmeta = fs.getPath("pack.mcmeta");
            if (Files.exists(datapacks)) { // multiple datapacks
                isMultiple = true;
                loadFromDir(datapacks);
            } else if (Files.exists(mcmeta)) { // single datapack
                isMultiple = false;
                try {
                    PackMcMeta pack = JsonUtils.fromNonNullJson(FileUtils.readText(mcmeta), PackMcMeta.class);
                    info.add(new Pack(mcmeta, FileUtils.getNameWithoutExtension(path), pack.getPackInfo().getDescription(), this));
                } catch (IOException e) {
                    Logging.LOG.log(Level.WARNING, "Failed to read datapack " + path, e);
                }
            } else {
                throw new IOException("Malformed datapack zip");
            }
        }
    }

    public void loadFromDir() {
        try {
            loadFromDir(path);
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Failed to read datapacks " + path, e);
        }
    }

    private void loadFromDir(Path dir) throws IOException {
        List<Pack> info = new ArrayList<>();

        if (Files.isDirectory(dir))
            for (Path subDir : Files.newDirectoryStream(dir)) {
                Path mcmeta = subDir.resolve("pack.mcmeta");
                Path mcmetaDisabled = subDir.resolve("pack.mcmeta.disabled");

                if (!Files.exists(mcmeta) && !Files.exists(mcmetaDisabled))
                    continue;

                boolean enabled = Files.exists(mcmeta);

                try {
                    PackMcMeta pack = enabled ? JsonUtils.fromNonNullJson(FileUtils.readText(mcmeta), PackMcMeta.class)
                            : JsonUtils.fromNonNullJson(FileUtils.readText(mcmetaDisabled), PackMcMeta.class);
                    info.add(new Pack(enabled ? mcmeta : mcmetaDisabled, FileUtils.getName(subDir), pack.getPackInfo().getDescription(), this));
                } catch (IOException e) {
                    Logging.LOG.log(Level.WARNING, "Failed to read datapack " + subDir, e);
                }
            }

        this.info.setAll(info);
    }

    public static class Pack {
        private Path packMcMeta;
        private final BooleanProperty active;
        private final String id;
        private final String description;
        private final Datapack datapack;

        public Pack(Path packMcMeta, String id, String description, Datapack datapack) {
            this.packMcMeta = packMcMeta;
            this.id = id;
            this.description = description;
            this.datapack = datapack;

            active = new SimpleBooleanProperty(this, "active", !DISABLED_EXT.equals(FileUtils.getExtension(packMcMeta))) {
                @Override
                protected void invalidated() {
                    Path f = Pack.this.packMcMeta.toAbsolutePath(), newF;
                    if (DISABLED_EXT.equals(FileUtils.getExtension(f)))
                        newF = f.getParent().resolve(FileUtils.getNameWithoutExtension(f));
                    else
                        newF = f.getParent().resolve(FileUtils.getName(f) + "." + DISABLED_EXT);

                    try {
                        Files.move(f, newF);
                        Pack.this.packMcMeta = newF;
                    } catch (IOException e) {
                        // Mod file is occupied.
                        Logging.LOG.warning("Unable to rename file " + f + " to " + newF);
                    }
                }
            };
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public Datapack getDatapack() {
            return datapack;
        }

        public BooleanProperty activeProperty() {
            return active;
        }

        public boolean isActive() {
            return active.get();
        }

        public void setActive(boolean active) {
            this.active.set(active);
        }
    }

    private static class PackMcMeta implements Validation {

        @SerializedName("pack")
        private final PackInfo pack;

        public PackMcMeta() {
            this(new PackInfo());
        }

        public PackMcMeta(PackInfo packInfo) {
            this.pack = packInfo;
        }

        public PackInfo getPackInfo() {
            return pack;
        }

        @Override
        public void validate() throws JsonParseException {
            if (pack == null)
                throw new JsonParseException("pack cannot be null");
        }

        public static class PackInfo {
            @SerializedName("pack_format")
            private final int packFormat;

            @SerializedName("description")
            private final String description;

            public PackInfo() {
                this(0, "");
            }

            public PackInfo(int packFormat, String description) {
                this.packFormat = packFormat;
                this.description = description;
            }

            public int getPackFormat() {
                return packFormat;
            }

            public String getDescription() {
                return description;
            }
        }
    }

    private static final String DISABLED_EXT = "disabled";
}
