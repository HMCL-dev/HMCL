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
        boolean containsMultiplePacks = false;
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
            Set<String> packs;
            if (containsMultiplePacks) {
                try(Stream<Path> s = Files.list(datapacks)) {
                    packs = s.map(FileUtils::getNameWithoutExtension).collect(Collectors.toSet());
                }
            } else {
                packs = new HashSet<>();
                packs.add(FileUtils.getNameWithoutExtension(sourceDatapackPath));
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDatapackDirectory)) {
                for (Path dir : stream) {
                    String packName = FileUtils.getName(dir);
                    if (FileUtils.getExtension(dir).equals(DISABLED_EXT)) {
                        packName = StringUtils.removeSuffix(packName,"." + DISABLED_EXT);
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

        if (containsMultiplePacks) {
            new Unzipper(sourceDatapackPath, targetDatapackDirectory)
                    .setReplaceExistentFile(true)
                    .setSubDirectory("/datapacks/")
                    .unzip();

            try (FileSystem outputResourcesZipFS = CompressingUtils.createWritableZipFileSystem(targetDatapackDirectory.getParent().resolve("resources.zip"));
                 FileSystem inputPackZipFS = CompressingUtils.createReadOnlyZipFileSystem(sourceDatapackPath)) {
                Path resourcesZip = inputPackZipFS.getPath("resources.zip");
                if (Files.isRegularFile(resourcesZip)) {
                    Path tempResourcesFile = Files.createTempFile("hmcl", ".zip");
                    Files.copy(resourcesZip, tempResourcesFile, StandardCopyOption.REPLACE_EXISTING);
                    try (FileSystem resources = CompressingUtils.createReadOnlyZipFileSystem(tempResourcesFile)) {
                        FileUtils.copyDirectory(resources.getPath("/"), outputResourcesZipFS.getPath("/"));
                    }
                }
                Path packMcMeta = outputResourcesZipFS.getPath("pack.mcmeta");
                Files.write(packMcMeta, Arrays.asList("{",
                        "\t\"pack\": {",
                        "\t\t\"pack_format\": 4,",
                        "\t\t\"description\": \"Modified by HMCL.\"",
                        "\t}",
                        "}"), StandardOpenOption.CREATE);

                Path packPng = outputResourcesZipFS.getPath("pack.png");
                if (Files.isRegularFile(packPng))
                    Files.delete(packPng);
            }
        } else {
            FileUtils.copyFile(sourceDatapackPath, targetDatapackDirectory.resolve(FileUtils.getName(sourceDatapackPath)));
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
        List<Pack> discoveredPacks = new ArrayList<>();

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
                            discoveredPacks.add(new Pack(subDir, true, FileUtils.getName(subDir), packMcMeta.getPackInfo().getDescription(), this));
                        } catch (IOException | JsonParseException e) {
                            LOG.warning("Failed to read datapack " + subDir, e);
                        }
                    } else if (Files.isRegularFile(subDir)) {
                        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(subDir)) {
                            Path mcmeta = fs.getPath("pack.mcmeta");

                            if (!Files.exists(mcmeta)) {
                                continue;
                            }

                            String packName = FileUtils.getName(subDir);
                            if (FileUtils.getExtension(subDir).equals(DISABLED_EXT)) {
                                packName = FileUtils.getNameWithoutExtension(packName);
                            }
                            if (!FileUtils.getExtension(packName).equals(ZIP_EXT)) {
                                continue;
                            }
                            packName = FileUtils.getNameWithoutExtension(packName);

                            PackMcMeta packMcMeta = JsonUtils.fromNonNullJson(Files.readString(mcmeta), PackMcMeta.class);
                            discoveredPacks.add(new Pack(subDir, false, packName, packMcMeta.getPackInfo().getDescription(), this));
                        } catch (IOException | JsonParseException e) {
                            LOG.warning("Failed to read datapack " + subDir, e);
                        }
                    }
                }
            }
        }

        Platform.runLater(() -> this.packs.setAll(discoveredPacks));
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

            if (isDirectory) {
                statusFile = Files.exists(path.resolve("pack.mcmeta")) ? path.resolve("pack.mcmeta") : path.resolve("pack.mcmeta.disabled");
            } else {
                statusFile = path;
            }

            activeProperty = new SimpleBooleanProperty(this, "active", !(FileUtils.getExtension(statusFile).equals(DISABLED_EXT))) {
                @Override
                protected void invalidated() {
                    Path newStatusFile = statusFile, newPath = path;
                    if (DISABLED_EXT.equals(FileUtils.getExtension(statusFile)) && this.get()) {
                        newStatusFile = statusFile.getParent().resolve(FileUtils.getNameWithoutExtension(statusFile));
                        if (!isDirectory) {
                            newPath = newStatusFile;
                        }
                    } else if (!DISABLED_EXT.equals(FileUtils.getExtension(statusFile)) && !this.get()) {
                        newStatusFile = statusFile.getParent().resolve(FileUtils.getName(statusFile) + "." + DISABLED_EXT);
                        if (!isDirectory) {
                            newPath = newStatusFile;
                        }
                    }
                    try {
                        Files.move(statusFile, newStatusFile);
                        Pack.this.statusFile = newStatusFile;
                        Pack.this.path = newPath;
                    } catch (IOException e) {
                        // Mod statusFile is occupied.
                        LOG.warning("Unable to rename statusFile " + statusFile + " to " + newStatusFile);
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
