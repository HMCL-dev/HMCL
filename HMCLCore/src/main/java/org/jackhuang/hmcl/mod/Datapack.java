/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class Datapack {
    private final Path path;
    private final ObservableList<Pack> packs = FXCollections.observableArrayList();

    public Datapack(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public ObservableList<Pack> getPacks() {
        return packs;
    }

    public static void installPack(Path packSourcePath, Path targetDatapackDirectory) throws IOException {
        boolean isMultiple = false;
        try (FileSystem fs = CompressingUtils.readonly(packSourcePath).setAutoDetectEncoding(true).build()) {
            Path datapacks = fs.getPath("/datapacks");
            Path mcmeta = fs.getPath("pack.mcmeta");

            if (Files.exists(datapacks)) {
                isMultiple = true;
            } else if (Files.exists(mcmeta)) {
                isMultiple = false;
            } else {
                throw new IOException("Malformed datapack zip");
            }
            Set<String> packs;
            if (isMultiple) {
                packs = Files.list(datapacks).map(FileUtils::getNameWithoutExtension).collect(Collectors.toSet());
            } else {
                packs = new HashSet<>();
                packs.add(FileUtils.getNameWithoutExtension(packSourcePath));
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDatapackDirectory)) {
                for (Path d : stream) {
                    String name;
                    if (FileUtils.getExtension(d).equals(".disabled")) {
                        name = FileUtils.getNameWithoutExtension(d);
                    }
                    name = FileUtils.getNameWithoutExtension(d);
                    if (packs.contains(name)) {
                        if (Files.isDirectory(d)) {
                            FileUtils.deleteDirectory(d);
                        } else if (Files.isRegularFile(d)) {
                            Files.delete(d);
                        }
                    }
                }
            }
        }

        if (isMultiple) {
            new Unzipper(packSourcePath, targetDatapackDirectory)
                    .setReplaceExistentFile(true)
                    .setSubDirectory("/datapacks/")
                    .unzip();

            try (FileSystem dest = CompressingUtils.createWritableZipFileSystem(targetDatapackDirectory.getParent().resolve("resources.zip"));
                 FileSystem zip = CompressingUtils.createReadOnlyZipFileSystem(packSourcePath)) {
                Path resourcesZip = zip.getPath("resources.zip");
                if (Files.isRegularFile(resourcesZip)) {
                    Path temp = Files.createTempFile("hmcl", ".zip");
                    Files.copy(resourcesZip, temp, StandardCopyOption.REPLACE_EXISTING);
                    FileUtils.copyDirectory(resourcesZip, dest.getPath("/"));
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
            FileUtils.copyFile(packSourcePath, targetDatapackDirectory.resolve(FileUtils.getName(packSourcePath)));
        }

    }

    public void installPack(Path packSourcePath) throws IOException {
        installPack(packSourcePath, path);
        loadFromDir();
    }

    public void deletePack(Pack pack) throws IOException {
        Path subPath = pack.path;
        if (Files.isDirectory(subPath))
            FileUtils.deleteDirectory(subPath);
        else if (Files.isRegularFile(subPath))
            Files.delete(subPath);

        Platform.runLater(() -> packs.removeIf(p -> p.getId().equals(pack.getId())));
    }

    public void loadFromDir() {
        try {
            loadFromDir(path);
        } catch (Exception e) {
            LOG.warning("Failed to read datapacks " + path, e);
        }
    }

    private void loadFromDir(Path dir) throws IOException {
        List<Pack> info = new ArrayList<>();

        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
                for (Path subDir : directoryStream) {
                    if (Files.isDirectory(subDir)) {
                        Path mcmeta = subDir.resolve("pack.mcmeta");
                        Path mcmetaDisabled = subDir.resolve("pack.mcmeta.disabled");

                        if (!Files.exists(mcmeta) && !Files.exists(mcmetaDisabled))
                            continue;

                        boolean enabled = Files.exists(mcmeta);

                        try {
                            PackMcMeta packMcMeta = enabled ? JsonUtils.fromNonNullJson(Files.readString(mcmeta), PackMcMeta.class)
                                    : JsonUtils.fromNonNullJson(Files.readString(mcmetaDisabled), PackMcMeta.class);
                            info.add(new Pack(subDir, true, FileUtils.getName(subDir), packMcMeta.getPackInfo().getDescription(), this));
                        } catch (IOException | JsonParseException e) {
                            LOG.warning("Failed to read datapack " + subDir, e);
                        }
                    } else if (Files.isRegularFile(subDir)) {
                        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(subDir)) {
                            Path mcmeta = fs.getPath("pack.mcmeta");

                            if (!Files.exists(mcmeta)) {
                                continue;
                            }

                            String name = FileUtils.getName(subDir);
                            if (name.endsWith(".disabled")) {
                                name = StringUtils.removeSuffix(name, ".disabled");
                            }
                            if (!name.endsWith(".zip")) {
                                continue;
                            }
                            name = StringUtils.removeSuffix(name, ".zip");

                            PackMcMeta packMcMeta = JsonUtils.fromNonNullJson(Files.readString(mcmeta), PackMcMeta.class);
                            info.add(new Pack(subDir, false, name, packMcMeta.getPackInfo().getDescription(), this));
                        } catch (IOException | JsonParseException e) {
                            LOG.warning("Failed to read datapack " + subDir, e);
                        }
                    }
                }
            }
        }

        Platform.runLater(() -> this.packs.setAll(info));
    }

    public static class Pack {
        private Path path;
        private boolean isDirectory;
        private Path file;
        private final BooleanProperty active;
        private final String id;
        private final LocalModFile.Description description;
        private final Datapack datapack;

        public Pack(Path path, boolean isDirectory, String id, LocalModFile.Description description, Datapack datapack) {
            this.path = path;
            this.isDirectory = isDirectory;

            this.id = id;
            this.description = description;
            this.datapack = datapack;

            if (isDirectory) {
                file = Files.exists(path.resolve("pack.mcmeta")) ? path.resolve("pack.mcmeta") : path.resolve("pack.mcmeta.disabled");
            } else {
                file = path;
            }

            active = new SimpleBooleanProperty(this, "active", !DISABLED_EXT.equals(FileUtils.getExtension(file))) {
                @Override
                protected void invalidated() {
                    Path newFile = file, newPath = path;
                    if (DISABLED_EXT.equals(FileUtils.getExtension(file)) && this.get()) {
                        newFile = file.getParent().resolve(FileUtils.getNameWithoutExtension(file));
                        if (!isDirectory) {
                            newPath = newFile;
                        }
                    } else if (!this.get()) {
                        newFile = file.getParent().resolve(FileUtils.getName(file) + "." + DISABLED_EXT);
                        if (!isDirectory) {
                            newPath = newFile;
                        }
                    }
                    try {
                        Files.move(file, newFile);
                        Pack.this.file = newFile;
                        Pack.this.path = newPath;
                    } catch (IOException e) {
                        // Mod file is occupied.
                        LOG.warning("Unable to rename file " + file + " to " + newFile);
                    }

                }
            };
        }

        public String getId() {
            return id;
        }

        public LocalModFile.Description getDescription() {
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

        public Path getPath() {
            return path;
        }

        public boolean isDirectory() {
            return isDirectory;
        }
    }


    private static final String DISABLED_EXT = "disabled";
}
