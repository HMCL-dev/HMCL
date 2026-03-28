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
    private final String fileName;
    private Image icon;

    private WorldDataSection levelDataTag;
    private WorldDataSection worldGenSettingsTag;
    private WorldDataSection playerTag;

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
            loadAndCheckWorldData(levelDatPath);

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
        if (getDataTag().get("LevelName") instanceof StringTag levelNameTag)
            return levelNameTag.get();
        else
            return "";
    }

    public void setWorldName(String worldName) throws IOException {
        getDataTag().setString("LevelName", worldName);
        levelDataTag.write();
    }

    public CompoundTag getLevelData() {
        return levelDataTag.nbtBackingTag();
    }

    public CompoundTag getDataTag() {
        return levelDataTag.normalizedNbtTag;
    }

    public @Nullable CompoundTag getNormalizedWorldGenSettingsData() {
        return worldGenSettingsTag.normalizedNbtTag;
    }

    public @Nullable CompoundTag getPlayerData() {
        return playerTag.normalizedNbtTag;
    }

    public long getLastPlayed() {
        if (getDataTag().get("LastPlayed") instanceof LongTag lastPlayedTag) {
            return lastPlayedTag.get();
        } else {
            return 0L;
        }
    }

    public @Nullable GameVersionNumber getGameVersion() {
        if (getDataTag().get("Version") instanceof CompoundTag versionTag &&
                versionTag.get("Name") instanceof StringTag nameTag) {
            return GameVersionNumber.asGameVersion(nameTag.getValue());
        }
        return null;
    }

    public @Nullable Long getSeed() {
        // Valid after 1.16(20w20a)
        if (getNormalizedWorldGenSettingsData() != null
                && getNormalizedWorldGenSettingsData().get("seed") instanceof LongTag seedTag) {
            return seedTag.getValue();
        }
        // Valid before 1.16(20w20a)
        if (getDataTag().get("RandomSeed") instanceof LongTag seedTag) {
            return seedTag.getValue();
        }
        return null;
    }

    public boolean isLargeBiomes() {
        // Valid before 1.16(20w20a)
        if (getDataTag().get("generatorName") instanceof StringTag generatorNameTag) {
            return "largeBiomes".equals(generatorNameTag.getValue());
        }
        // Unified handling of logic after version 1.16
        else if (getNormalizedWorldGenSettingsData() != null
                && getNormalizedWorldGenSettingsData().get("dimensions") instanceof CompoundTag dimensionsTag) {
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

    public boolean supportsDataPacks() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.13", "17w43a");
    }

    public boolean supportsQuickPlay() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.20", "23w14a");
    }

    public static boolean supportsQuickPlay(GameVersionNumber gameVersionNumber) {
        return gameVersionNumber != null && gameVersionNumber.isAtLeast("1.20", "23w14a");
    }

    private void loadAndCheckWorldData(Path levelDataPath) throws IOException {
        loadAndCheckLevelData(levelDataPath);
        loadOtherData();
    }

    private void loadAndCheckLevelData(Path levelDatPath) throws IOException {
        CompoundTag levelData = NBTCodec.of().readTag(levelDatPath, TagType.COMPOUND);
        if (!(levelData.get("Data") instanceof CompoundTag data))
            throw new IOException("level.dat missing Data");

        if (!(data.get("LevelName") instanceof StringTag))
            throw new IOException("level.dat missing LevelName");

        if (!(data.get("LastPlayed") instanceof LongTag))
            throw new IOException("level.dat missing LastPlayed");
        this.levelDataTag = new WorldDataSection(levelDatPath, levelData, data);
    }

    private void loadOtherData() throws IOException {

        Path worldGenSettingsDatPath = file.resolve("data/minecraft/world_gen_settings.dat");
        if (getDataTag().get("WorldGenSettings") instanceof CompoundTag worldGenSettingsTag) {
            this.worldGenSettingsTag = new WorldDataSection(null, worldGenSettingsTag, worldGenSettingsTag);
        } else if (Files.isRegularFile(worldGenSettingsDatPath)) {
            CompoundTag raw = NBTCodec.of().readTag(worldGenSettingsDatPath, TagType.COMPOUND);
            if (raw.get("data") instanceof CompoundTag compoundTag) {
                this.worldGenSettingsTag = new WorldDataSection(worldGenSettingsDatPath, raw, compoundTag);
            } else {
                this.worldGenSettingsTag = new WorldDataSection(null, null, null);
            }
        } else {
            this.worldGenSettingsTag = new WorldDataSection(null, null, null);
        }

        if (getDataTag().get("Player") instanceof CompoundTag playerTag) {
            this.playerTag = new WorldDataSection(null, playerTag, playerTag);
        } else if (getDataTag().get("singleplayer_uuid") instanceof IntArrayTag uuidTag && uuidTag.isUUID()) {
            String playerUUID = uuidTag.getUUID().toString();
            Path playerDatPath = file.resolve("players/data/" + playerUUID + ".dat");
            if (Files.exists(playerDatPath)) {
                CompoundTag playerTag = NBTCodec.of().readTag(playerDatPath, TagType.COMPOUND);
                this.playerTag = new WorldDataSection(playerDatPath, playerTag, playerTag);
            } else {
                this.playerTag = new WorldDataSection(null, null, null);
            }
        } else {
            this.playerTag = new WorldDataSection(null, null, null);
        }
    }

    public void reloadWorldData() throws IOException {
        loadAndCheckWorldData(levelDataTag.nbtPath());
    }

    // The renameWorld method do not modify the `file` field.
    // A new World object needs to be created to obtain the renamed world.
    public Path rename(String newName) throws IOException {
        switch (getWorldLock().getLockState()) {
            case LOCKED_BY_OTHER -> throw new WorldLockedException("The world " + getFile() + " has been locked");
            case LOCKED_BY_SELF -> getWorldLock().releaseLock();
        }

        // Change the name recorded in level.dat
        setWorldName(newName);

        // Then change the folder's name
        Path targetPath = FileUtils.getNonConflictingDirectory(file.getParent(), FileUtils.getSafeWorldFolderName(newName));
        Files.move(file, targetPath);
        return targetPath;
    }

    public void export(Path zipPath, String worldName) throws IOException {
        if (getWorldLock().getLockState() == WorldLock.LockState.LOCKED_BY_OTHER) {
            throw new WorldLockedException("The world " + getFile() + " has been locked");
        }

        try (WorldLock.Suspension ignored = getWorldLock().suspend();
             Zipper zipper = new Zipper(zipPath)) {
            zipper.putDirectory(file, worldName);
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

        Path targetPath = FileUtils.getNonConflictingDirectory(file.getParent(), FileUtils.getSafeWorldFolderName(newName));
        FileUtils.copyDirectory(file, targetPath, path -> !path.contains("session.lock"));
        new World(targetPath).setWorldName(newName);
    }

    public void writeWorldData() throws IOException {
        levelDataTag.write();
        worldGenSettingsTag.write();
        playerTag.write();
    }

    public static List<World> getWorlds(Path savesDir) {
        if (Files.isDirectory(savesDir)) {
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

    public class WorldLock {
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
            if (isLockedInternally()) {
                return LockState.LOCKED_BY_SELF;
            } else if (isLockedExternally()) {
                return LockState.LOCKED_BY_OTHER;
            } else {
                return LockState.UNLOCKED;
            }
        }

        public synchronized boolean lock() {
            try {
                lockStrict();
                return true;
            } catch (WorldLockedException e) {
                return false;
            }
        }

        public void lockStrict() throws WorldLockedException {
            switch (getLockState()) {
                case LOCKED_BY_SELF -> {
                }
                case LOCKED_BY_OTHER -> throw new WorldLockedException("World is locked by others");
                case UNLOCKED -> acquireLock();
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

        private boolean isLockedInternally() {
            return sessionLockChannel != null && sessionLockChannel.isOpen();
        }

        private boolean isLockedExternally() {
            try (FileChannel fileChannel = FileChannel.open(sessionLockFile, StandardOpenOption.WRITE)) {
                return fileChannel.tryLock() == null;
            } catch (AccessDeniedException accessDeniedException) {
                return true;
            } catch (OverlappingFileLockException | NoSuchFileException overlappingFileLockException) {
                return false;
            } catch (IOException e) {
                LOG.warning("Unexpected I/O error checking world lock: " + sessionLockFile, e);
                return false;
            }
        }

        public synchronized void releaseLock() throws IOException {
            if (sessionLockChannel != null) {
                sessionLockChannel.close();
                sessionLockChannel = null;
            }
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
                    this.wasAlreadyLocked = isLockedInternally();
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
                    this.hadLock = isLockedInternally();
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
                            lockStrict();
                        } catch (WorldLockedException e) {
                            LOG.warning("Failed to resume lock after suspension", e);
                        }
                    }
                }
            }
        }
    }

    record WorldDataSection(Path nbtPath,
                            CompoundTag nbtBackingTag, // Use for writing back to the file
                            CompoundTag normalizedNbtTag // Use for reading/modification
    ) {
        public void write() throws IOException {
            if (nbtPath != null) {
                FileUtils.saveSafely(nbtPath, os -> {
                    try (OutputStream gos = new GZIPOutputStream(os)) {
                        NBTCodec.of().writeTag(gos, nbtBackingTag);
                    }
                });
            }
        }
    }
}
