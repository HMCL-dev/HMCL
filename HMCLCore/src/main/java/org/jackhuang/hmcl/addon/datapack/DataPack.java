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
package org.jackhuang.hmcl.addon.datapack;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.meta.PackMcMeta;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class DataPack {
    private static final String DISABLED_EXT = "disabled";

    private final Path path;
    private final ObservableList<Pack> packs = FXCollections.observableArrayList();

    public DataPack(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public ObservableList<Pack> getPacks() {
        return packs;
    }

    public static void installPack(Path sourceDataPackPath, Path targetDataPackDirectory, GameVersionNumber gameVersionNumber) throws IOException {
        boolean containsMultiplePacks;
        Set<String> packs = new HashSet<>();
        try (FileSystem fs = CompressingUtils.readonly(sourceDataPackPath).build()) {
            Path dataPacks = fs.getPath("datapacks");
            Path mcmeta = fs.getPath("pack.mcmeta");

            if (Files.exists(dataPacks)) {
                containsMultiplePacks = true;
            } else if (Files.exists(mcmeta)) {
                containsMultiplePacks = false;
            } else {
                throw new IOException("Malformed datapack zip");
            }

            if (containsMultiplePacks) {
                try (Stream<Path> s = Files.list(dataPacks)) {
                    packs = s.map(FileUtils::getNameWithoutExtension).collect(Collectors.toSet());
                }
            } else {
                packs.add(FileUtils.getNameWithoutExtension(sourceDataPackPath));
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDataPackDirectory)) {
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
            FileUtils.copyFile(sourceDataPackPath, targetDataPackDirectory.resolve(FileUtils.getName(sourceDataPackPath)));
        } else {
            new Unzipper(sourceDataPackPath, targetDataPackDirectory)
                    .setReplaceExistentFile(true)
                    .setSubDirectory("/datapacks/")
                    .unzip();

            Path targetResourceZipPath;
            // When the version cannot be obtained, the old version logic is used by default.
            boolean useNewResourcePath = gameVersionNumber != null
                    && gameVersionNumber.compareTo("26.1-snapshot-6") >= 0;

            if (useNewResourcePath) {
                Files.createDirectories(targetDataPackDirectory.getParent().resolve("resourcepacks"));
                targetResourceZipPath = targetDataPackDirectory.getParent().resolve("resourcepacks/resources.zip");
            } else {
                targetResourceZipPath = targetDataPackDirectory.getParent().resolve("resources.zip");
            }

            try (FileSystem outputResourcesZipFS = CompressingUtils.createWritableZipFileSystem(targetResourceZipPath);
                 FileSystem inputPackZipFS = CompressingUtils.createReadOnlyZipFileSystem(sourceDataPackPath)) {
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

    public void installPack(Path sourcePackPath, GameVersionNumber gameVersionNumber) throws IOException {
        installPack(sourcePackPath, path, gameVersionNumber);
        loadFromDir();
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
                    .parallel()
                    .map(packPath -> {
                        try {
                            return new Pack(packPath, this);
                        } catch (IOException e) {
                            LOG.warning("Failed to load pack " + packPath, e);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
        }
        Platform.runLater(() -> this.packs.setAll(discoveredPacks));
    }

    public static class Pack extends LocalAddonFile implements Comparable<Pack> {
        private Path path;
        private final boolean isDirectory;
        private Path statusFile;
        private final BooleanProperty activeProperty;
        private final String id;
        private final PackMcMeta.PackInfo info;
        private final DataPack manager;

        public Pack(Path path, DataPack manager) throws IOException {
            this.path = path;
            this.manager = manager;
            isDirectory = Files.isDirectory(path);

            String idd = FileUtils.getNameWithoutExtension(path);
            if (!isDirectory && FileUtils.getExtension(path).equals(DISABLED_EXT))
                idd = FileUtils.getNameWithoutExtension(idd);
            id = idd;

            Path mcmetaPath;
            if (isDirectory) {
                Path mcmeta1 = path.resolve("pack.mcmeta");
                mcmetaPath = Files.exists(mcmeta1) ? mcmeta1 : path.resolve("pack.mcmeta.disabled");
            } else
                try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(path)) {
                    mcmetaPath = fs.getPath("pack.mcmeta");
                }

            info = JsonUtils.fromNonNullJson(Files.readString(mcmetaPath), PackMcMeta.class).pack();
            statusFile = isDirectory ? mcmetaPath : path;

            this.activeProperty = initializeActiveProperty();
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

        public PackMcMeta.PackInfo getInfo() {
            return info;
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

        @Override
        public Path getFile() {
            return path;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public String getFileName() {
            return id;
        }

        @Override
        public void markDisabled() {
        }

        @Override
        public void setOld(boolean old) {
        }

        @Override
        public boolean keepOldFiles() {
            return false;
        }

        @Override
        public void delete() throws IOException {
            if (isDirectory)
                FileUtils.deleteDirectory(path);
            else
                Files.delete(path);

            Platform.runLater(() -> manager.packs.removeIf(p -> p.getId().equals(id)));
        }

        @Override
        public int compareTo(@NotNull DataPack.Pack o) {
            return String.CASE_INSENSITIVE_ORDER.compare(id, o.id);
        }
    }

}
