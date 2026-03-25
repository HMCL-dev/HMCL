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
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class World {

    private final Path file;
    private String fileName;
    private Image icon;

    private CompoundTag levelData;
    private CompoundTag dataTag;
    private Path levelDataPath;

    private CompoundTag worldGenSettingsDataBackingTag; // Use for writing back to the file
    private CompoundTag normalizedWorldGenSettingsData; // Use for reading/modification
    private Path worldGenSettingsDataPath;

    private CompoundTag playerData; // Use for both reading/modification and writing back to the file
    private Path playerDataPath;

    private final WorldLock lock;

    public World(Path file) throws IOException {
        this.file = file;
        this.lock = new WorldLock();

        if (Files.isDirectory(file)) {
            fileName = FileUtils.getName(this.file);
            Path levelDatPath = this.file.resolve("level.dat");
            if (!Files.exists(levelDatPath)) { // version 20w14infinite
                levelDatPath = this.file.resolve("special_level.dat");
            }
            if (!Files.exists(levelDatPath)) {
                throw new IOException("Not a valid world directory since level.dat or special_level.dat cannot be found.");
            }
            this.levelDataPath = levelDatPath;
            loadAndCheckWorldData();

            Path iconFile = this.file.resolve("icon.png");
            if (Files.isRegularFile(iconFile)) {
                try (InputStream inputStream = Files.newInputStream(iconFile)) {
                    icon = new Image(inputStream, 64, 64, true, false);
                    if (icon.isError())
                        throw icon.getException();
                } catch (Exception e) {
                    LOG.warning("Failed to load world icon", e);
                }
            }
        } else {
            throw new IOException("Path " + file + " cannot be recognized as a Minecraft world");
        }
    }

    public WorldLock getWorldLock() {
        return lock;
    }

    public Path getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }

    public String getWorldName() {
        if (levelData.get("Data") instanceof CompoundTag data
                && data.get("LevelName") instanceof StringTag levelNameTag)
            return levelNameTag.get();
        else
            return "";
    }

    public void setWorldName(String worldName) throws IOException {
        if (levelData.get("Data") instanceof CompoundTag data && data.get("LevelName") instanceof StringTag levelNameTag) {
            levelNameTag.setValue(worldName);
            writeLevelData();
        }
    }

    public CompoundTag getLevelData() {
        return levelData;
    }

    public CompoundTag getDataTag() {
        return dataTag;
    }

    public @Nullable CompoundTag getNormalizedWorldGenSettingsData() {
        return normalizedWorldGenSettingsData;
    }

    public @Nullable CompoundTag getPlayerData() {
        return playerData;
    }

    public long getLastPlayed() {
        if (dataTag.get("LastPlayed") instanceof LongTag lastPlayedTag) {
            return lastPlayedTag.get();
        } else {
            return 0L;
        }
    }

    public @Nullable GameVersionNumber getGameVersion() {
        if (levelData.get("Data") instanceof CompoundTag data &&
                data.get("Version") instanceof CompoundTag versionTag &&
                versionTag.get("Name") instanceof StringTag nameTag) {
            return GameVersionNumber.asGameVersion(nameTag.getValue());
        }
        return null;
    }

    public @Nullable Long getSeed() {
        // Valid after 1.16(20w20a)
        if (normalizedWorldGenSettingsData != null
                && normalizedWorldGenSettingsData.get("seed") instanceof LongTag seedTag) {
            return seedTag.getValue();
        }
        // Valid before 1.16(20w20a)
        if (dataTag.get("RandomSeed") instanceof LongTag seedTag) {
            return seedTag.getValue();
        }
        return null;
    }

    public boolean isLargeBiomes() {
        // Valid before 1.16(20w20a)
        if (dataTag.get("generatorName") instanceof StringTag generatorNameTag) {
            return "largeBiomes".equals(generatorNameTag.getValue());
        }
        // Unified handling of logic after version 1.16
        else if (normalizedWorldGenSettingsData != null
                && normalizedWorldGenSettingsData.get("dimensions") instanceof CompoundTag dimensionsTag) {
            if (dimensionsTag.get("minecraft:overworld") instanceof CompoundTag overworldTag
                    && overworldTag.get("generator") instanceof CompoundTag generatorTag) {
                // Valid between 1.16(20w20a) and 1.18(21w37a)
                if (generatorTag.get("biome_source") instanceof CompoundTag biomeSourceTag
                        && biomeSourceTag.get("large_biomes") instanceof ByteTag largeBiomesTag) {
                    return largeBiomesTag.get() == (byte) 1;
                }
                // Valid after 1.18(21w37a)
                else if (generatorTag.get("settings") instanceof StringTag settingsTag) {
                    return "minecraft:large_biomes".equals(settingsTag.get());
                }
            }
        }
        return false;
    }

    public Image getIcon() {
        return icon;
    }

    public boolean supportsDatapacks() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.13", "17w43a");
    }

    public boolean supportsQuickPlay() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.20", "23w14a");
    }

    public static boolean supportsQuickPlay(GameVersionNumber gameVersionNumber) {
        return gameVersionNumber != null && gameVersionNumber.isAtLeast("1.20", "23w14a");
    }

    private void loadAndCheckWorldData() throws IOException {
        loadAndCheckLevelData(levelDataPath);
        loadOtherData();
    }

    private void loadAndCheckLevelData(Path levelDat) throws IOException {
        this.levelData = NBTCodec.of().readTag(levelDat, TagType.COMPOUND);
        if (!(levelData.get("Data") instanceof CompoundTag data))
            throw new IOException("level.dat missing Data");

        if (!(data.get("LevelName") instanceof StringTag))
            throw new IOException("level.dat missing LevelName");

        if (!(data.get("LastPlayed") instanceof LongTag))
            throw new IOException("level.dat missing LastPlayed");
        this.dataTag = data;
    }

    private void loadOtherData() throws IOException {
        if (!(levelData.get("Data") instanceof CompoundTag data)) return;

        Path worldGenSettingsDatPath = file.resolve("data/minecraft/world_gen_settings.dat");
        if (data.get("WorldGenSettings") instanceof CompoundTag worldGenSettingsTag) {
            setWorldGenSettingsData(null, worldGenSettingsTag, worldGenSettingsTag);
        } else if (Files.isRegularFile(worldGenSettingsDatPath)) {
            CompoundTag raw = NBTCodec.of().readTag(worldGenSettingsDatPath, TagType.COMPOUND);
            if (raw.get("data") instanceof CompoundTag compoundTag) {
                setWorldGenSettingsData(worldGenSettingsDatPath, raw, compoundTag);
            } else {
                setWorldGenSettingsData(null, null, null);
            }
        } else {
            setWorldGenSettingsData(null, null, null);
        }

        if (data.get("Player") instanceof CompoundTag playerTag) {
            setPlayerData(null, playerTag);
        } else if (data.get("singleplayer_uuid") instanceof IntArrayTag uuidTag && uuidTag.isUUID()) {
            String playerUUID = uuidTag.getUUID().toString();
            Path playerDatPath = file.resolve("players/data/" + playerUUID + ".dat");
            if (Files.exists(playerDatPath)) {
                setPlayerData(playerDatPath, NBTCodec.of().readTag(playerDatPath, TagType.COMPOUND));
            } else {
                setPlayerData(null, null);
            }
        } else {
            setPlayerData(null, null);
        }
    }

    private void setWorldGenSettingsData(Path worldGenSettingsDataPath, CompoundTag worldGenSettingsDataBackingTag, CompoundTag unifiedWorldGenSettingsData) {
        this.worldGenSettingsDataPath = worldGenSettingsDataPath;
        this.worldGenSettingsDataBackingTag = worldGenSettingsDataBackingTag;
        this.normalizedWorldGenSettingsData = unifiedWorldGenSettingsData;
    }

    private void setPlayerData(Path playerDataPath, CompoundTag playerData) {
        this.playerDataPath = playerDataPath;
        this.playerData = playerData;
    }

    public void reloadWorldData() throws IOException {
        loadAndCheckWorldData();
    }

    // The renameWorld method do not modify the `file` field.
    // A new World object needs to be created to obtain the renamed world.
    public Path rename(String newName) throws IOException {
        if (getWorldLock().getLockState() == WorldLock.LockState.LOCKED_BY_OTHER) {
            throw new IOException("The world " + getFile() + " has been locked");
        }

        // Change the name recorded in level.dat
        dataTag.setString("LevelName", newName);
        writeLevelData();

        // Then change the folder's name
        String safeName = FileUtils.getSafeWorldFolderName(newName);
        Path newPath;
        for (int count = 0; count < 256; count++) {
            newPath = file.resolveSibling(count == 0 ? safeName : safeName + " (" + count + ")");
            if (!Files.exists(newPath)) {
                try (WorldLock.Suspension ignored = getWorldLock().suspend()) {
                    Files.move(file, newPath);
                    return newPath;
                }
            }
        }
        throw new IOException("Too many attempts");
    }

    public void export(Path zip, String worldName) throws IOException {
        if (getWorldLock().getLockState() == WorldLock.LockState.LOCKED_BY_OTHER) {
            throw new WorldLockedException("The world " + getFile() + " has been locked");
        }

        try (WorldLock.Suspension ignored = getWorldLock().suspend()) {
            try (Zipper zipper = new Zipper(zip)) {
                zipper.putDirectory(file, worldName);
            }
        }
    }

    public void delete() throws IOException {
        switch (getWorldLock().getLockState()) {
            case LOCKED_BY_OTHER -> throw new WorldLockedException("The world " + getFile() + " has been locked");
            case LOCKED_BY_SELF -> getWorldLock().releaseLock();
        }
        FileUtils.deleteDirectory(file);
    }

    public void copy(String newName) throws IOException {
        if (getWorldLock().getLockState() == WorldLock.LockState.LOCKED_BY_OTHER) {
            throw new WorldLockedException("The world " + getFile() + " has been locked");
        }

        String safeName = FileUtils.getSafeWorldFolderName(newName);
        Path newPath;
        for (int count = 0; count < 256; count++) {
            newPath = file.resolveSibling(count == 0 ? safeName : safeName + " (" + count + ")");
            if (!Files.exists(newPath)) {
                FileUtils.copyDirectory(file, newPath, path -> !path.contains("session.lock"));
                new World(newPath).setWorldName(newName);
                return;
            }
        }
        throw new IOException("Too many attempts");
    }

    public void writeWorldData() throws IOException {
        if (!Files.isDirectory(file)) throw new IOException("Not a valid world directory");

        writeLevelData();

        if (worldGenSettingsDataPath != null && worldGenSettingsDataBackingTag != null) {
            writeTag(worldGenSettingsDataBackingTag, worldGenSettingsDataPath);
        }

        if (playerDataPath != null && playerData != null) {
            writeTag(playerData, playerDataPath);
        }
    }

    public void writeLevelData() throws IOException {
        writeTag(levelData, levelDataPath);
    }

    private void writeTag(CompoundTag nbt, Path path) throws IOException {
        if (!Files.isDirectory(file)) throw new IOException("Not a valid world directory");
        FileUtils.saveSafely(path, os -> {
            try (OutputStream gos = new GZIPOutputStream(os)) {
                NBTCodec.of().writeTag(gos, nbt);
            }
        });
    }

    public static List<World> getWorlds(Path savesDir) {
        if (Files.exists(savesDir)) {
            try (Stream<Path> stream = Files.list(savesDir)) {
                return stream
                        .filter(Files::isDirectory)
                        .flatMap(world -> {
                            try {
                                return Stream.of(new World(world.toAbsolutePath().normalize()));
                            } catch (IOException e) {
                                LOG.warning("Failed to read world " + world, e);
                                return Stream.empty();
                            }
                        })
                        .toList();
            } catch (IOException e) {
                LOG.warning("Failed to read saves", e);
            }
        }
        return List.of();
    }

    public class WorldLock implements AutoCloseable {
        private FileChannel sessionLockChannel;
        private final Path sessionLockFile;

        public enum LockState {
            LOCKED_BY_OTHER,
            LOCKED_BY_SELF,
            UNLOCKED;
        }

        public WorldLock() {
            this.sessionLockFile = file.resolve("session.lock");
            this.sessionLockChannel = null;
        }

        public synchronized LockState getLockState() {
            if (sessionLockChannel != null && sessionLockChannel.isOpen()) {
                return LockState.LOCKED_BY_SELF;
            } else if (isLockedExternally()) {
                return LockState.LOCKED_BY_OTHER;
            } else {
                return LockState.UNLOCKED;
            }
        }

        public synchronized boolean lock() {
            LockState lockState = getLockState();
            return switch (lockState) {
                case LOCKED_BY_OTHER -> false;
                case LOCKED_BY_SELF -> true;
                case UNLOCKED -> {
                    try {
                        acquireLock();
                        yield true;
                    } catch (WorldLockedException e) {
                        LOG.warning("Failed to acquire world lock for " + file, e);
                        yield false;
                    }
                }
            };
        }

        public void lockStrict() throws WorldLockedException {
            if (!lock()) {
                throw new WorldLockedException("Failed to lock world " + World.this.getFile());
            }
        }

        public void acquireLock() throws WorldLockedException {
            FileChannel channel = null;
            try {
                channel = FileChannel.open(sessionLockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                //noinspection ResultOfMethodCallIgnored
                channel.write(ByteBuffer.wrap("\u2603".getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
                FileLock fileLock = channel.tryLock();
                if (fileLock != null) {
                    this.sessionLockChannel = channel;
                } else {
                    IOUtils.closeQuietly(channel);
                    throw new WorldLockedException("The world " + getFile() + " has been locked");
                }
            } catch (IOException e) {
                IOUtils.closeQuietly(channel);
                throw new WorldLockedException(e);
            }
        }

        private boolean isLockedExternally() {
            try (FileChannel fileChannel = FileChannel.open(sessionLockFile, StandardOpenOption.WRITE)) {
                return fileChannel.tryLock() == null;
            } catch (AccessDeniedException accessDeniedException) {
                return true;
            } catch (OverlappingFileLockException | NoSuchFileException overlappingFileLockException) {
                return false;
            } catch (IOException e) {
                LOG.warning("Failed to open the lock file " + sessionLockFile, e);
                return false;
            }
        }

        public synchronized void releaseLock() throws IOException {
            if (sessionLockChannel != null) {
                sessionLockChannel.close();
                sessionLockChannel = null;
            }
        }

        @Override
        public void close() throws IOException {
            releaseLock();
        }

        public Guard guard() throws WorldLockedException {
            return new Guard();
        }

        public Suspension suspend() throws IOException {
            return new Suspension();
        }

        public final class Guard implements AutoCloseable {
            private final boolean wasAlreadyLocked;

            private Guard() throws WorldLockedException {
                synchronized (WorldLock.this) {
                    this.wasAlreadyLocked = (getLockState() == LockState.LOCKED_BY_SELF);
                    if (!wasAlreadyLocked) {
                        lockStrict();
                    }
                }
            }

            @Override
            public void close() {
                synchronized (WorldLock.this) {
                    if (!wasAlreadyLocked) {
                        try {
                            releaseLock();
                        } catch (IOException e) {
                            LOG.warning("Failed to release temporary lock", e);
                        }
                    }
                }
            }
        }

        public final class Suspension implements AutoCloseable {
            private final boolean hadLock;

            private Suspension() throws IOException {
                synchronized (WorldLock.this) {
                    this.hadLock = (getLockState() == LockState.LOCKED_BY_SELF);
                    if (hadLock) {
                        releaseLock();
                    }
                }
            }

            @Override
            public void close() {
                synchronized (WorldLock.this) {
                    if (hadLock) {
                        try {
                            lock();
                        } catch (Exception e) {
                            LOG.warning("Failed to resume lock after suspension", e);
                        }
                    }
                }
            }
        }
    }
}
