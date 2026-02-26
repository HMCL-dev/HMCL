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
package org.jackhuang.hmcl.game;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.util.io.*;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;
import tech.minediamond.micanbt.NBT.NBT;
import tech.minediamond.micanbt.NBT.NBTCompressType;
import tech.minediamond.micanbt.tag.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class World {

    private final Path file;
    private String fileName;
    private Image icon;

    private CompoundTag levelData;
    private Path levelDataPath;
    private CompoundTag worldGenSettingsData;
    private Path worldGenSettingsDataPath;
    private CompoundTag playerData;
    private Path playerDataPath;

    public World(Path file) throws IOException {
        this.file = file;

        if (Files.isDirectory(file))
            loadFromDirectory();
        else if (Files.isRegularFile(file))
            loadFromZip();
        else
            throw new IOException("Path " + file + " cannot be recognized as a Minecraft world");
    }

    public Path getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }

    public String getWorldName() {
        return ((StringTag) levelData.at("Data.LevelName")).getClonedValue();
    }

    public void setWorldName(String worldName) throws IOException {
        if (levelData.at("Data.LevelName") instanceof StringTag levelNameTag) {
            levelNameTag.setValue(worldName);
            writeLevelData();
        }
    }

    public Path getSessionLockFile() {
        return file.resolve("session.lock");
    }

    public CompoundTag getLevelData() {
        return levelData;
    }

    public @Nullable CompoundTag getWorldGenSettingsData() {
        return worldGenSettingsData;
    }

    public @Nullable CompoundTag getPlayerData() {
        return playerData;
    }

    public long getLastPlayed() {
        return ((LongTag) levelData.at("Data.LastPlayed")).getClonedValue();
    }

    public @Nullable GameVersionNumber getGameVersion() {
        if (levelData.at("Data.Version.Name") instanceof StringTag nameTag) {
            return GameVersionNumber.asGameVersion(nameTag.getClonedValue());
        }
        return null;
    }

    public @Nullable Long getSeed() {
        // Valid between 1.16(20w20a) and 26.1-snapshot-6
        if (levelData.at("Data.WorldGenSettings.seed") instanceof LongTag seedTag) {
            return seedTag.getClonedValue();
        }
        // Valid before 1.16(20w20a)
        else if (levelData.at("Data.RandomSeed") instanceof LongTag seedTag) {
            return seedTag.getClonedValue();
        }
        // Valid after 26.1-snapshot-6
        else if (worldGenSettingsData != null && worldGenSettingsData.at("data.seed") instanceof LongTag seedTag) {
            return seedTag.getClonedValue();
        }
        return null;
    }

    public boolean isLargeBiomes() {
        // Valid before 1.16(20w20a)
        if (levelData.at("Data.generatorName") instanceof StringTag generatorNameTag) {
            return "largeBiomes".equals(generatorNameTag.getClonedValue());
        }
        // Valid after 26.1-snapshot-6
        else if (worldGenSettingsData != null) {
            if (worldGenSettingsData.at("data.dimensions.minecraft:overworld.generator.settings") instanceof StringTag settingsTag) {
                return "minecraft:large_biomes".equals(settingsTag.getClonedValue());
            }
        }
        // Valid between 1.16(20w20a) and 1.18(21w37a)
        else if (levelData.at("Data.WorldGenSettings.dimensions.minecraft:overworld.generator.biome_source.large_biomes") instanceof ByteTag largeBiomesTag) {
            return largeBiomesTag.getClonedValue() == (byte) 1;
        }
        // Valid between 1.18(21w37a) and 26.1-snapshot-6
        // Note: In versions 1.16(20w20a) and 1.18(21w37a), the settings tag exists but does not indicate large biomes information
        else if (levelData.at("Data.WorldGenSettings.dimensions.minecraft:overworld.generator.settings") instanceof StringTag settingsTag) {
            return "minecraft:large_biomes".equals(settingsTag.getClonedValue());
        }
        return false;
    }

    public Image getIcon() {
        return icon;
    }

    public boolean isLocked() {
        return isLocked(getSessionLockFile());
    }

    public boolean supportDatapacks() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.13", "17w43a");
    }

    public boolean supportQuickPlay() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.20", "23w14a");
    }

    public static boolean supportQuickPlay(GameVersionNumber gameVersionNumber) {
        return gameVersionNumber != null && gameVersionNumber.isAtLeast("1.20", "23w14a");
    }

    private void loadFromDirectory() throws IOException {
        fileName = FileUtils.getName(file);
        Path levelDat = file.resolve("level.dat");
        if (!Files.exists(levelDat)) { // version 20w14infinite
            levelDat = file.resolve("special_level.dat");
        }
        if (!Files.exists(levelDat)) {
            throw new IOException("Not a valid world directory since level.dat or special_level.dat cannot be found.");
        }
        this.levelDataPath = levelDat;
        loadAndCheckWorldData();

        Path iconFile = file.resolve("icon.png");
        if (Files.isRegularFile(iconFile)) {
            try (InputStream inputStream = Files.newInputStream(iconFile)) {
                icon = new Image(inputStream, 64, 64, true, false);
                if (icon.isError())
                    throw icon.getException();
            } catch (Exception e) {
                LOG.warning("Failed to load world icon", e);
            }
        }
    }

    private void loadFromZipImpl(Path root) throws IOException {
        Path levelDat = root.resolve("level.dat");
        if (!Files.exists(levelDat)) { //version 20w14infinite
            levelDat = root.resolve("special_level.dat");
        }
        if (!Files.exists(levelDat)) {
            throw new IOException("Not a valid world zip file since level.dat or special_level.dat cannot be found.");
        }
        loadAndCheckLevelData(levelDat);

        Path iconFile = root.resolve("icon.png");
        if (Files.isRegularFile(iconFile)) {
            try (InputStream inputStream = Files.newInputStream(iconFile)) {
                icon = new Image(inputStream, 64, 64, true, false);
                if (icon.isError())
                    throw icon.getException();
            } catch (Exception e) {
                LOG.warning("Failed to load world icon", e);
            }
        }
    }

    private void loadFromZip() throws IOException {
        try (FileSystem fs = CompressingUtils.readonly(file).setAutoDetectEncoding(true).build()) {
            Path levelDatPath = fs.getPath("/level.dat");
            if (Files.isRegularFile(levelDatPath)) {
                fileName = FileUtils.getName(file);
                loadFromZipImpl(fs.getPath("/"));
                return;
            }

            try (Stream<Path> stream = Files.list(fs.getPath("/"))) {
                Path root = stream.filter(Files::isDirectory).findAny()
                        .orElseThrow(() -> new IOException("Not a valid world zip file"));
                fileName = FileUtils.getName(root);
                loadFromZipImpl(root);
            }
        }
    }

    private void loadAndCheckWorldData() throws IOException {
        loadAndCheckLevelData(levelDataPath);
        loadOtherData();
    }

    private void loadAndCheckLevelData(Path levelDat) throws IOException {
        this.levelData = NBT.read(levelDat);
        CompoundTag data = (CompoundTag) levelData.get("Data");
        if (data == null)
            throw new IOException("level.dat missing Data");

        if (!(data.get("LevelName") instanceof StringTag))
            throw new IOException("level.dat missing LevelName");

        if (!(data.get("LastPlayed") instanceof LongTag))
            throw new IOException("level.dat missing LastPlayed");
    }

    private void loadOtherData() throws IOException {
        Path worldGenSettingsDatPath = file.resolve("data/minecraft/world_gen_settings.dat");
        if (Files.exists(worldGenSettingsDatPath)) {
            this.worldGenSettingsDataPath = worldGenSettingsDatPath;
            this.worldGenSettingsData = NBT.read(worldGenSettingsDatPath);
        } else {
            this.worldGenSettingsDataPath = null;
            this.worldGenSettingsData = null;
        }

        if (levelData.at("Data.Player") instanceof CompoundTag playerTag) {
            this.playerData = playerTag;
            this.playerDataPath = null;
        } else if (levelData.at("Data.singleplayer_uuid") instanceof IntArrayTag uuidTag) {
            int[] uuidValue = uuidTag.getClonedValue();
            if (uuidValue.length == 4) {
                long mostSigBits = ((long) uuidValue[0] << 32) | (uuidValue[1] & 0xFFFFFFFFL);
                long leastSigBits = ((long) uuidValue[2] << 32) | (uuidValue[3] & 0xFFFFFFFFL);
                String playerUUID = new UUID(mostSigBits, leastSigBits).toString();
                Path playerDatPath = file.resolve("players/data/" + playerUUID + ".dat");
                if (Files.exists(playerDatPath)) {
                    this.playerData = NBT.read(playerDatPath);
                    this.playerDataPath = playerDatPath;
                }
            }
        } else {
            this.playerData = null;
            this.playerDataPath = null;
        }
    }

    public void reloadWorldData() throws IOException {
        loadAndCheckWorldData();
    }

    // The rename method is used to rename temporary world object during installation and copying,
    // so there is no need to modify the `file` field.
    public void rename(String newName) throws IOException {
        if (!Files.isDirectory(file))
            throw new IOException("Not a valid world directory");

        // Change the name recorded in level.dat
        CompoundTag data = (CompoundTag) levelData.get("Data");
        data.put(new StringTag("LevelName", newName));
        writeLevelData();

        // then change the folder's name
        Files.move(file, file.resolveSibling(newName));
    }

    public void install(Path savesDir, String name) throws IOException {
        Path worldDir;
        try {
            worldDir = savesDir.resolve(name);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }

        if (Files.isDirectory(worldDir)) {
            throw new FileAlreadyExistsException("World already exists");
        }

        if (Files.isRegularFile(file)) {
            try (FileSystem fs = CompressingUtils.readonly(file).setAutoDetectEncoding(true).build()) {
                Path levelDatPath = fs.getPath("/level.dat");
                if (Files.isRegularFile(levelDatPath)) {
                    fileName = FileUtils.getName(file);

                    new Unzipper(file, worldDir).unzip();
                } else {
                    try (Stream<Path> stream = Files.list(fs.getPath("/"))) {
                        List<Path> subDirs = stream.toList();
                        if (subDirs.size() != 1) {
                            throw new IOException("World zip malformed");
                        }
                        String subDirectoryName = FileUtils.getName(subDirs.get(0));
                        new Unzipper(file, worldDir)
                                .setSubDirectory("/" + subDirectoryName + "/")
                                .unzip();
                    }
                }

            }
            new World(worldDir).rename(name);
        } else if (Files.isDirectory(file)) {
            FileUtils.copyDirectory(file, worldDir);
        }
    }

    public void export(Path zip, String worldName) throws IOException {
        if (!Files.isDirectory(file))
            throw new IOException();

        try (Zipper zipper = new Zipper(zip)) {
            zipper.putDirectory(file, worldName);
        }
    }

    public void delete() throws IOException {
        if (isLocked()) {
            throw new WorldLockedException("The world " + getFile() + " has been locked");
        }
        FileUtils.forceDelete(file);
    }

    public void copy(String newName) throws IOException {
        if (!Files.isDirectory(file)) {
            throw new IOException("Not a valid world directory");
        }

        if (isLocked()) {
            throw new WorldLockedException("The world " + getFile() + " has been locked");
        }

        Path newPath = file.resolveSibling(newName);
        FileUtils.copyDirectory(file, newPath, path -> !path.contains("session.lock"));
        World newWorld = new World(newPath);
        newWorld.rename(newName);
    }

    public FileChannel lock() throws WorldLockedException {
        Path lockFile = getSessionLockFile();
        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            channel.write(ByteBuffer.wrap("\u2603".getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
            FileLock fileLock = channel.tryLock();
            if (fileLock != null) {
                return channel;
            } else {
                IOUtils.closeQuietly(channel);
                throw new WorldLockedException("The world " + getFile() + " has been locked");
            }
        } catch (IOException e) {
            IOUtils.closeQuietly(channel);
            throw new WorldLockedException(e);
        }
    }

    public void writeWorldData() throws IOException {
        if (!Files.isDirectory(file))
            throw new IOException("Not a valid world directory");

        writeLevelData();

        if (worldGenSettingsDataPath != null) {
            writeTag(worldGenSettingsData, worldGenSettingsDataPath);
        }

        if (playerDataPath != null) {
            writeTag(playerData, playerDataPath);
        }
    }

    public void writeLevelData() throws IOException {
        writeTag(levelData, levelDataPath);
    }

    public void writeTag(CompoundTag nbt, Path path) throws IOException {
        if (!Files.isDirectory(file))
            throw new IOException("Not a valid world directory");

        FileUtils.saveSafely(path, os -> NBT.toStream(nbt, os).compressType(NBTCompressType.GZIP).write());
    }

    private static boolean isLocked(Path sessionLockFile) {
        try (FileChannel fileChannel = FileChannel.open(sessionLockFile, StandardOpenOption.WRITE)) {
            return fileChannel.tryLock() == null;
        } catch (AccessDeniedException | OverlappingFileLockException accessDeniedException) {
            return true;
        } catch (NoSuchFileException noSuchFileException) {
            return false;
        } catch (IOException e) {
            LOG.warning("Failed to open the lock file " + sessionLockFile, e);
            return false;
        }
    }

    public static List<World> getWorlds(Path savesDir) {
        if (Files.exists(savesDir)) {
            try (Stream<Path> stream = Files.list(savesDir)) {
                return stream.flatMap(world -> {
                    try {
                        return Stream.of(new World(world.toAbsolutePath().normalize()));
                    } catch (IOException e) {
                        LOG.warning("Failed to read world " + world, e);
                        return Stream.empty();
                    }
                }).toList();
            } catch (IOException e) {
                LOG.warning("Failed to read saves", e);
            }
        }
        return List.of();
    }
}
