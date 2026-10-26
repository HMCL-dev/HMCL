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
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.addon.LocalAddonManager.DISABLED_EXTENSION;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public class DataPackManager {
    private final World world;
    private final Path directory;
    protected final Set<DataPackFile> localFiles = new LinkedHashSet<>();
    private final ReentrantLock lock = new ReentrantLock();

    public DataPackManager(World world) {
        this.world = world;
        directory = world.getFile().resolve("datapacks");
    }

    public void importDataPack(Path sourcePackPath) throws IOException {
        lock.lock();
        try {
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

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
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
                FileUtils.copyFile(sourcePackPath, directory.resolve(FileUtils.getName(sourcePackPath)));
            } else {
                new Unzipper(sourcePackPath, directory)
                        .setReplaceExistentFile(true)
                        .setSubDirectory("/datapacks/")
                        .unzip();

                Path targetResourceZipPath;
                // When the version cannot be obtained, the old version logic is used by default.
                boolean useNewResourcePath = world.getGameVersion() != null
                        && world.getGameVersion().compareTo("26.1-snapshot-6") >= 0;

                if (useNewResourcePath) {
                    Files.createDirectories(directory.resolveSibling("resourcepacks"));
                    targetResourceZipPath = directory.resolveSibling("resourcepacks/resources.zip");
                } else {
                    targetResourceZipPath = directory.resolveSibling("resources.zip");
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
        } finally {
            lock.unlock();
        }
    }

    public void removePack(DataPackFile dataPack) throws IOException {
        lock.lock();
        try {
            if (dataPack.manager == this) {
                dataPack.delete();
                localFiles.remove(dataPack);
            }
        } finally {
            lock.unlock();
        }
    }

    public Path getDirectory() {
        return directory;
    }

    public @Unmodifiable List<DataPackFile> getLocalFiles() {
        lock.lock();
        try {
            return localFiles.stream().sorted(getComparator()).toList();
        } finally {
            lock.unlock();
        }
    }

    public void refresh() throws IOException {
        lock.lock();
        try {
            localFiles.clear();
            try (Stream<Path> stream = Files.list(directory)) {
                stream.forEach(packPath -> {
                    try {
                        localFiles.add(new DataPackFile(this, packPath));
                    } catch (IOException | JsonParseException e) {
                        LOG.warning("Failed to load pack " + packPath, e);
                    }
                });
            }
        } finally {
            lock.unlock();
        }
    }

    public Comparator<DataPackFile> getComparator() {
        return DataPackFile::compareTo;
    }
}
