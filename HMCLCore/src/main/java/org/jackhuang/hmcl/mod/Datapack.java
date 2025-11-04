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
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class Datapack {
    private static final String DISABLED_EXT = "disabled";
    private static final String ZIP_EXT = "zip";

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

    public static void installPack(Path sourceDatapackPath, Path targetDatapackDirectory) throws IOException {
        boolean containsMultiplePacks;
        Set<String> packs = new HashSet<>();
        try (FileSystem fs = CompressingUtils.readonly(sourceDatapackPath).setAutoDetectEncoding(true).build()) {
            Path datapacks = fs.getPath("datapacks");
            Path mcmeta = fs.getPath("pack.mcmeta");

            if (Files.exists(datapacks)) {
                containsMultiplePacks = true;
            } else if (Files.exists(mcmeta)) {
                containsMultiplePacks = false;
            } else {
                throw new IOException("Malformed datapack zip");
            }

            if (containsMultiplePacks) {
                try (Stream<Path> s = Files.list(datapacks)) {
                    packs = s.map(FileUtils::getNameWithoutExtension).collect(Collectors.toSet());
                }
            } else {
                packs.add(FileUtils.getNameWithoutExtension(sourceDatapackPath));
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDatapackDirectory)) {
                for (Path dir : stream) {
                    String packName = FileUtils.getName(dir);
                    if (FileUtils.getExtension(dir).equals(DISABLED_EXT)) {
                        packName = StringUtils.removeSuffix(packName, "." + DISABLED_EXT);
                    }
                    packName = FileUtils.getNameWithoutExtension(packName);
                    if (packs.contains(packName)) {
                        if (Files.isDirectory(dir)) {
                            FileUtils.deleteDirectory(dir);
                        } else if (Files.isRegularFile(dir)) {
                            Files.delete(dir);
                        }
                    }
                }
            }
        }

        if (!containsMultiplePacks) {
            FileUtils.copyFile(sourceDatapackPath, targetDatapackDirectory.resolve(FileUtils.getName(sourceDatapackPath)));
        } else {
            new Unzipper(sourceDatapackPath, targetDatapackDirectory)
                    .setReplaceExistentFile(true)
                    .setSubDirectory("/datapacks/")
                    .unzip();

            try (FileSystem outputResourcesZipFS = CompressingUtils.createWritableZipFileSystem(targetDatapackDirectory.getParent().resolve("resources.zip"));
                 FileSystem inputPackZipFS = CompressingUtils.createReadOnlyZipFileSystem(sourceDatapackPath)) {
                Path resourcesZip = inputPackZipFS.getPath("resources.zip");
                if (Files.isRegularFile(resourcesZip)) {
                    Path tempResourcesFile = Files.createTempFile("hmcl", ".zip");
                    try {
                        Files.copy(resourcesZip, tempResourcesFile, StandardCopyOption.REPLACE_EXISTING);
                        try (FileSystem resources = CompressingUtils.createReadOnlyZipFileSystem(tempResourcesFile)) {
                            FileUtils.copyDirectory(resources.getPath("/"), outputResourcesZipFS.getPath("/"));
                        }
                    } finally {
                        Files.deleteIfExists(tempResourcesFile);
                    }
                }
                Path packMcMeta = outputResourcesZipFS.getPath("pack.mcmeta");
                String metaContent = """
                        {
                            "pack": {
                                "pack_format": 4,
                                "description": "Modified by HMCL."
                            }
                        }
                        """;
                Files.writeString(packMcMeta, metaContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                Path packPng = outputResourcesZipFS.getPath("pack.png");
                if (Files.isRegularFile(packPng))
                    Files.delete(packPng);
            }
        }
    }

    public void installPack(Path sourcePackPath) throws IOException {
        installPack(sourcePackPath, path);
        loadFromDir();
    }

    public void deletePack(Pack packToDelete) throws IOException {
        Path pathToDelete = packToDelete.path;
        if (Files.isDirectory(pathToDelete))
            FileUtils.deleteDirectory(pathToDelete);
        else if (Files.isRegularFile(pathToDelete))
            Files.delete(pathToDelete);

        Platform.runLater(() -> packs.removeIf(p -> p.getId().equals(packToDelete.getId())));
    }

    public void loadFromDir() {
        try {
            loadFromDir(path);
        } catch (Exception e) {
            LOG.warning("Failed to read datapacks " + path, e);
        }
    }

    private void loadFromDir(Path dir) throws IOException {
        List<Pack> discoveredPacks;
        try (Stream<Path> stream = Files.list(dir)) {
            discoveredPacks = stream
                    .map(this::loadSinglePackFromPath)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(Pack::getId))
                    .collect(Collectors.toList());
        }
        Platform.runLater(() -> this.packs.setAll(discoveredPacks));
    }

    private Optional<Pack> loadSinglePackFromPath(Path path) {
        if (Files.isDirectory(path)) {
            Path mcmeta = path.resolve("pack.mcmeta");
            Path mcmetaDisabled = path.resolve("pack.mcmeta.disabled");

            if (!Files.exists(mcmeta) && !Files.exists(mcmetaDisabled))
                return Optional.empty();

            boolean enabled = Files.exists(mcmeta);
            Path targetPath = enabled ? mcmeta : mcmetaDisabled;
            try {
                PackMcMeta packMcMeta = JsonUtils.fromNonNullJson(Files.readString(targetPath), PackMcMeta.class);
                return Optional.of(new Pack(path, true, FileUtils.getName(path), packMcMeta.getPackInfo().getDescription(), this));
            } catch (IOException | JsonParseException e) {
                LOG.warning("Failed to read datapack " + path, e);
                return Optional.empty();
            }
        } else if (Files.isRegularFile(path)) {
            try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(path)) {
                Path mcmeta = fs.getPath("pack.mcmeta");

                if (!Files.exists(mcmeta)) {
                    return Optional.empty();
                }

                String packName = FileUtils.getName(path);
                if (FileUtils.getExtension(path).equals(DISABLED_EXT)) {
                    packName = FileUtils.getNameWithoutExtension(packName);
                }
                if (!FileUtils.getExtension(packName).equals(ZIP_EXT)) {
                    return Optional.empty();
                }
                packName = FileUtils.getNameWithoutExtension(packName);

                PackMcMeta packMcMeta = JsonUtils.fromNonNullJson(Files.readString(mcmeta), PackMcMeta.class);
                return Optional.of(new Pack(path, false, packName, packMcMeta.getPackInfo().getDescription(), this));
            } catch (IOException | JsonParseException e) {
                LOG.warning("Failed to read datapack " + path, e);
            }
        }

        return Optional.empty();
    }

    public static class Pack {
        private Path path;
        private final boolean isDirectory;
        private Path statusFile;
        private final BooleanProperty activeProperty;
        private final String id;
        private final LocalModFile.Description description;
        private final Datapack parentDatapack;

        public Pack(Path path, boolean isDirectory, String id, LocalModFile.Description description, Datapack parentDatapack) {
            this.path = path;
            this.isDirectory = isDirectory;
            this.id = id;
            this.description = description;
            this.parentDatapack = parentDatapack;

            this.statusFile = initializeStatusFile(path, isDirectory);
            this.activeProperty = initializeActiveProperty();
        }

        private Path initializeStatusFile(Path path, boolean isDirectory) {
            if (isDirectory) {
                Path mcmeta = path.resolve("pack.mcmeta");
                return Files.exists(mcmeta) ? mcmeta : path.resolve("pack.mcmeta.disabled");
            }
            return path;
        }

        private BooleanProperty initializeActiveProperty() {
            BooleanProperty property = new SimpleBooleanProperty(this, "active", !FileUtils.getExtension(this.statusFile).equals(DISABLED_EXT));
            property.addListener((obs, wasActive, isNowActive) -> {
                if (wasActive != isNowActive) {
                    handleFileRename(isNowActive);
                }
            });
            return property;
        }

        private void handleFileRename(boolean isNowActive) {
            Path newStatusFile = calculateNewStatusFilePath(isNowActive);
            if (statusFile.equals(newStatusFile)) {
                return;
            }
            try {
                Files.move(this.statusFile, newStatusFile);
                this.statusFile = newStatusFile;
                if (!this.isDirectory) {
                    this.path = newStatusFile;
                }
            } catch (IOException e) {
                LOG.warning("Unable to rename file from " + this.statusFile + " to " + newStatusFile, e);
            }
        }

        private Path calculateNewStatusFilePath(boolean isActive) {
            boolean isFileDisabled = DISABLED_EXT.equals(FileUtils.getExtension(this.statusFile));
            if (isActive && isFileDisabled) {
                return this.statusFile.getParent().resolve(FileUtils.getNameWithoutExtension(this.statusFile));
            } else if (!isActive && !isFileDisabled) {
                return this.statusFile.getParent().resolve(FileUtils.getName(this.statusFile) + "." + DISABLED_EXT);
            }
            return this.statusFile;
        }

        public String getId() {
            return id;
        }

        public LocalModFile.Description getDescription() {
            return description;
        }

        public Datapack getParentDatapack() {
            return parentDatapack;
        }

        public BooleanProperty activeProperty() {
            return activeProperty;
        }

        public boolean isActive() {
            return activeProperty.get();
        }

        public void setActive(boolean active) {
            this.activeProperty.set(active);
        }

        public Path getPath() {
            return path;
        }

        public boolean isDirectory() {
            return isDirectory;
        }
    }

}
