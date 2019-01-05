/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;

import java.io.IOException;
import java.nio.file.*;
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
                if (Files.isDirectory(datapack) && packs.contains(FileUtils.getName(datapack)))
                    FileUtils.deleteDirectory(datapack.toFile());
                else if (Files.isRegularFile(datapack) && packs.contains(FileUtils.getNameWithoutExtension(datapack)))
                    Files.delete(datapack);
            }

        if (isMultiple) {
            new Unzipper(path, worldPath)
                    .setReplaceExistentFile(true)
                    .setFilter(new Unzipper.FileFilter() {
                        @Override
                        public boolean accept(Path destPath, boolean isDirectory, Path zipEntry, String entryPath) {
                            // We will merge resources.zip instead of replacement.
                            return !entryPath.equals("resources.zip");
                        }
                    })
                    .unzip();

            try (FileSystem dest = CompressingUtils.createWritableZipFileSystem(worldPath.resolve("resources.zip"));
                 FileSystem zip = CompressingUtils.createReadOnlyZipFileSystem(path)) {
                Path resourcesZip = zip.getPath("resources.zip");
                if (Files.isRegularFile(resourcesZip)) {
                    Path temp = Files.createTempFile("hmcl", ".zip");
                    Files.copy(resourcesZip, temp, StandardCopyOption.REPLACE_EXISTING);
                    try (FileSystem resources = CompressingUtils.createReadOnlyZipFileSystem(temp)) {
                        FileUtils.copyDirectory(resources.getPath("/"), dest.getPath("/"));
                    }
                }
                Path packMcMeta = dest.getPath("pack.mcmeta");
                Files.write(packMcMeta, Arrays.asList("{",
                        "\t\"pack\": {",
                        "\t\t\"pack_format\": 4,",
                        "\t\t\"description\": \"Modified by HMCL.\"",
                        "\t}",
                        "}"), StandardOpenOption.CREATE);


                Path packPng = dest.getPath("pack.png");
                if (Files.isRegularFile(packPng))
                    Files.delete(packPng);
            }
        } else {
            FileUtils.copyFile(path.toFile(), datapacks.resolve(FileUtils.getName(path)).toFile());
        }
    }

    public void deletePack(Pack pack) throws IOException {
        Path subPath = pack.file;
        if (Files.isDirectory(subPath))
            FileUtils.deleteDirectory(subPath.toFile());
        else if (Files.isRegularFile(subPath))
            Files.delete(subPath);

        Platform.runLater(() -> info.removeIf(p -> p.getId().equals(pack.getId())));
    }

    public void loadFromZip() throws IOException {
        try (FileSystem fs = CompressingUtils.readonly(path).setAutoDetectEncoding(true).build()) {
            Path datapacks = fs.getPath("/datapacks/");
            Path mcmeta = fs.getPath("pack.mcmeta");
            if (Files.exists(datapacks)) { // multiple datapacks
                isMultiple = true;
                loadFromDir(datapacks);
            } else if (Files.exists(mcmeta)) { // single datapack
                isMultiple = false;
                try {
                    PackMcMeta pack = JsonUtils.fromNonNullJson(FileUtils.readText(mcmeta), PackMcMeta.class);
                    info.add(new Pack(path, FileUtils.getNameWithoutExtension(path), pack.getPackInfo().getDescription(), this));
                } catch (IOException | JsonParseException e) {
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
                if (Files.isDirectory(subDir)) {
                    Path mcmeta = subDir.resolve("pack.mcmeta");
                    Path mcmetaDisabled = subDir.resolve("pack.mcmeta.disabled");

                    if (!Files.exists(mcmeta) && !Files.exists(mcmetaDisabled))
                        continue;

                    boolean enabled = Files.exists(mcmeta);

                    try {
                        PackMcMeta pack = enabled ? JsonUtils.fromNonNullJson(FileUtils.readText(mcmeta), PackMcMeta.class)
                                : JsonUtils.fromNonNullJson(FileUtils.readText(mcmetaDisabled), PackMcMeta.class);
                        info.add(new Pack(enabled ? mcmeta : mcmetaDisabled, FileUtils.getName(subDir), pack.getPackInfo().getDescription(), this));
                    } catch (IOException | JsonParseException e) {
                        Logging.LOG.log(Level.WARNING, "Failed to read datapack " + subDir, e);
                    }
                } else if (Files.isRegularFile(subDir)) {
                    try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(subDir)) {
                        Path mcmeta = fs.getPath("pack.mcmeta");

                        if (!Files.exists(mcmeta))
                            continue;

                        String name = FileUtils.getName(subDir);
                        boolean enabled = true;
                        if (name.endsWith(".disabled")) {
                            name = name.substring(0, name.length() - ".disabled".length());
                            enabled = false;
                        }
                        if (!name.endsWith(".zip"))
                            continue;
                        name = StringUtils.substringBeforeLast(name, ".zip");

                        PackMcMeta pack = JsonUtils.fromNonNullJson(FileUtils.readText(mcmeta), PackMcMeta.class);
                        info.add(new Pack(subDir, name, pack.getPackInfo().getDescription(), this));
                    } catch (IOException | JsonParseException e) {
                        Logging.LOG.log(Level.WARNING, "Failed to read datapack " + subDir, e);
                    }
                }
            }

        this.info.setAll(info);
    }

    public static class Pack {
        private Path file;
        private final BooleanProperty active;
        private final String id;
        private final String description;
        private final Datapack datapack;

        public Pack(Path file, String id, String description, Datapack datapack) {
            this.file = file;
            this.id = id;
            this.description = description;
            this.datapack = datapack;

            active = new SimpleBooleanProperty(this, "active", !DISABLED_EXT.equals(FileUtils.getExtension(file))) {
                @Override
                protected void invalidated() {
                    Path f = Pack.this.file.toAbsolutePath(), newF;
                    if (DISABLED_EXT.equals(FileUtils.getExtension(f)))
                        newF = f.getParent().resolve(FileUtils.getNameWithoutExtension(f));
                    else
                        newF = f.getParent().resolve(FileUtils.getName(f) + "." + DISABLED_EXT);

                    try {
                        Files.move(f, newF);
                        Pack.this.file = newF;
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
