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

import com.google.gson.JsonParseException;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.meta.PackMcMeta;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class DataPack extends LocalAddonManager<DataPack.Pack> {
    private final World world;
    private final Path path;

    public DataPack(World world) {
        super(world.getGameInstance());
        this.world = world;
        this.path = world.getFile().resolve("datapacks");
    }

    public void installPack(Path sourcePackPath) throws IOException {
        boolean containsMultiplePacks;
        Set<String> packs = new HashSet<>();
        try (FileSystem fs = CompressingUtils.readonly(sourcePackPath).build()) {
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
                packs.add(FileUtils.getNameWithoutExtension(sourcePackPath));
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path dir : stream) {
                    String packName = FileUtils.getNameWithoutExtension(StringUtils.removeSuffix(FileUtils.getName(dir), DISABLED_EXTENSION));
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
            FileUtils.copyFile(sourcePackPath, path.resolve(FileUtils.getName(sourcePackPath)));
        } else {
            new Unzipper(sourcePackPath, path)
                    .setReplaceExistentFile(true)
                    .setSubDirectory("/datapacks/")
                    .unzip();

            Path targetResourceZipPath;
            // When the version cannot be obtained, the old version logic is used by default.
            boolean useNewResourcePath = world.getGameVersion() != null
                    && world.getGameVersion().compareTo("26.1-snapshot-6") >= 0;

            if (useNewResourcePath) {
                Files.createDirectories(path.resolveSibling("resourcepacks"));
                targetResourceZipPath = path.resolveSibling("resourcepacks/resources.zip");
            } else {
                targetResourceZipPath = path.resolveSibling("resources.zip");
            }

            try (FileSystem outputResourcesZipFS = CompressingUtils.createWritableZipFileSystem(targetResourceZipPath);
                 FileSystem inputPackZipFS = CompressingUtils.createReadOnlyZipFileSystem(sourcePackPath)) {
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
        refresh();
    }

    public boolean removePacks(List<Pack> datapacks) throws IOException {
        lock.lock();
        try {
            boolean modified = false;
            for (Pack pack : datapacks) {
                if (pack != null && pack.manager == this) {
                    pack.delete();
                    localFiles.remove(pack);
                    modified = true;
                }
            }
            return modified;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Path getDirectory() {
        return path;
    }

    @Override
    public void refresh() throws IOException {
        lock.lock();
        try {
            localFiles.clear();
            try (Stream<Path> stream = Files.list(path)) {
                stream.forEach(packPath -> {
                    try {
                        localFiles.add(new Pack(packPath, this));
                    } catch (IOException | JsonParseException e) {
                        LOG.warning("Failed to load pack " + packPath, e);
                    }
                });
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Comparator<Pack> getComparator() {
        return Pack::compareTo;
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

            String name = FileUtils.getName(path);
            id = isDirectory ? name : FileUtils.getNameWithoutExtension(StringUtils.removeSuffix(name, DISABLED_EXTENSION));

            if (isDirectory) {
                Path mcmeta1 = path.resolve("pack.mcmeta");
                Path mcmetaPath = Files.exists(mcmeta1) ? mcmeta1 : path.resolve("pack.mcmeta.disabled");
                info = JsonUtils.fromNonNullJson(Files.readString(mcmetaPath), PackMcMeta.class).pack();
                statusFile = mcmetaPath;
            } else {
                try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(path)) {
                    info = JsonUtils.fromNonNullJson(Files.readString(fs.getPath("pack.mcmeta")), PackMcMeta.class).pack();
                }
                statusFile = path;
            }

            BooleanProperty property = new SimpleBooleanProperty(this, "active", !FileUtils.getName(statusFile).endsWith(DISABLED_EXTENSION));
            property.addListener((obs, wasActive, isNowActive) -> {
                if (isNowActive == !FileUtils.getName(statusFile).endsWith(DISABLED_EXTENSION))
                    return;

                Path newStatusFile = isNowActive
                        ? statusFile.resolveSibling(FileUtils.getNameWithoutExtension(statusFile))
                        : statusFile.resolveSibling(FileUtils.getName(statusFile) + DISABLED_EXTENSION);

                try {
                    Files.move(statusFile, newStatusFile);
                    statusFile = newStatusFile;
                    if (!isDirectory)
                        this.path = newStatusFile;
                } catch (IOException e) {
                    LOG.warning("Unable to rename file from " + this.statusFile + " to " + newStatusFile, e);
                }
            });
            this.activeProperty = property;
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
        }

        @Override
        public int compareTo(@NotNull DataPack.Pack o) {
            return String.CASE_INSENSITIVE_ORDER.compare(id, o.id);
        }
    }

}
