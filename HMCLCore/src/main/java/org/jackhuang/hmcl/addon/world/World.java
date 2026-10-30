// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.world;

import javafx.scene.image.Image;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.game.WorldLockedException;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
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
import java.util.zip.GZIPOutputStream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class World extends LocalAddonFile<World> {
    final Path file;
    private final String fileName;
    private final @Nullable Image icon;
    private LevelData levelData;
    private WorldGenSettingsData worldGenSettingsData;
    private PlayerData playerData;

    public World(Path file) throws IOException {
        this.file = file;
        icon = loadIcon(file);
        fileName = FileUtils.getName(file);
        reloadWorldData();
    }

    static LevelData resolveLevelData(Path root, String missingLevelDataMessage) throws IOException {
        Path levelDataPath = root.resolve("level.dat");
        if (!Files.exists(levelDataPath)) { // version 20w14infinite
            levelDataPath = root.resolve("special_level.dat");
        }
        if (!Files.exists(levelDataPath)) {
            throw new IOException(missingLevelDataMessage);
        }

        CompoundTag levelData = NBTCodec.of().readTag(levelDataPath, TagType.COMPOUND);
        if (!(levelData.get("Data") instanceof CompoundTag data))
            throw new IOException("level.dat missing Data");

        if (!(data.get("LevelName") instanceof StringTag levelName))
            throw new IOException("level.dat missing LevelName");

        if (!(data.get("LastPlayed") instanceof LongTag lastPlayed))
            throw new IOException("level.dat missing LastPlayed");
        return new LevelData(data, levelName, lastPlayed, levelData, levelDataPath);
    }

    static @Nullable Image loadIcon(Path root) {
        Path iconFile = root.resolve("icon.png");
        @Nullable Image icon = null;
        if (Files.isRegularFile(iconFile)) {
            try (InputStream inputStream = Files.newInputStream(iconFile)) {
                icon = new Image(inputStream, 64, 64, true, false);
                if (icon.isError())
                    throw icon.getException();
            } catch (Exception e) {
                LOG.warning("Failed to load world icon", e);
            }
        }
        return icon;
    }

    @Override
    public Path getFile() {
        return file;
    }

    public boolean supportDataPacks() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.13", "17w43a");
    }

    public String getWorldName() {
        return levelData.levelName.get();
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    public @Nullable Image getIcon() {
        return icon;
    }

    public LevelData getLevelData() {
        return levelData;
    }

    public @Nullable GameVersionNumber getGameVersion() {
        return levelData.getGameVersion();
    }

    public long getLastPlayed() {
        return levelData.lastPlayed.get();
    }

    public @Nullable CompoundTag getNormalizedWorldGenSettingsData() {
        return worldGenSettingsData.data;
    }

    public @Nullable CompoundTag getPlayerData() {
        return playerData.data;
    }

    public @Nullable Long getSeed() {
        // Valid after 1.16(20w20a)
        if (worldGenSettingsData.data != null
                && worldGenSettingsData.data.get("seed") instanceof LongTag seedTag) {
            return seedTag.getValue();
        }
        // Valid before 1.16(20w20a)
        if (levelData.data().get("RandomSeed") instanceof LongTag seedTag) {
            return seedTag.getValue();
        }
        return null;
    }

    public boolean isLargeBiomes() {
        // Valid before 1.16(20w20a)
        if (levelData.data().get("generatorName") instanceof StringTag generatorNameTag) {
            return "largeBiomes".equals(generatorNameTag.getValue());
        }
        // Unified handling of logic after version 1.16
        else if (worldGenSettingsData.data() != null
                && worldGenSettingsData.data().get("dimensions") instanceof CompoundTag dimensionsTag) {
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

    public void reloadWorldData() throws IOException {
        levelData = resolveLevelData(file, "Not a valid world directory since level.dat or special_level.dat cannot be found.");

        Path worldGenSettingsDatPath = file.resolve("data/minecraft/world_gen_settings.dat");
        if (levelData.data().get("WorldGenSettings") instanceof CompoundTag worldGenSettingsTag) {
            worldGenSettingsData = new WorldGenSettingsData(null, worldGenSettingsTag, worldGenSettingsTag);
        } else if (Files.isRegularFile(worldGenSettingsDatPath)) {
            CompoundTag raw = NBTCodec.of().readTag(worldGenSettingsDatPath, TagType.COMPOUND);
            if (raw.get("data") instanceof CompoundTag compoundTag) {
                worldGenSettingsData = new WorldGenSettingsData(worldGenSettingsDatPath, raw, compoundTag);
            } else {
                worldGenSettingsData = new WorldGenSettingsData(null, null, null);
            }
        } else {
            worldGenSettingsData = new WorldGenSettingsData(null, null, null);
        }

        if (levelData.data().get("Player") instanceof CompoundTag playerTag) {
            playerData = new PlayerData(null, playerTag);
        } else if (levelData.data().get("singleplayer_uuid") instanceof IntArrayTag uuidTag && uuidTag.isUUID()) {
            String playerUUID = uuidTag.getUUID().toString();
            Path playerDatPath = file.resolve("players/data/" + playerUUID + ".dat");
            if (Files.exists(playerDatPath)) {
                playerData = new PlayerData(playerDatPath, NBTCodec.of().readTag(playerDatPath, TagType.COMPOUND));
            } else {
                playerData = new PlayerData(null, null);
            }
        } else {
            playerData = new PlayerData(null, null);
        }
    }

    public void writeWorldData() throws IOException {
        writeTag(levelData.rootTag(), levelData.file());
        writeTag(worldGenSettingsData.back, worldGenSettingsData.file);
        writeTag(playerData.data, playerData.file);
    }

    public void setWorldName(String worldName) throws IOException {
        levelData.levelName().setValue(worldName);
        writeTag(levelData.rootTag(), levelData.file());
    }

    private void writeTag(@Nullable CompoundTag nbt, @Nullable Path path) throws IOException {
        if (nbt == null || path == null)
            return;
        FileUtils.saveSafely(path, os -> {
            try (OutputStream gos = new GZIPOutputStream(os)) {
                NBTCodec.of().writeTag(gos, nbt);
            }
        });
    }

    public Path getLockFile() {
        return file.resolve("session.lock");
    }

    public FileChannel lock() throws WorldLockedException {
        Path lockFile = getLockFile();
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

    private void checkLock() throws WorldLockedException {
        if (isLocked())
            throw new WorldLockedException("The world " + getFile() + " has been locked");
    }

    public boolean isLocked() {
        Path lockFile = getLockFile();
        try (FileChannel fileChannel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
            return fileChannel.tryLock() == null;
        } catch (AccessDeniedException | OverlappingFileLockException accessDeniedException) {
            return true;
        } catch (NoSuchFileException noSuchFileException) {
            return false;
        } catch (IOException e) {
            LOG.warning("Failed to open the lock file " + lockFile, e);
            return false;
        }
    }

    @Override
    public void delete() throws IOException {
        checkLock();
        FileUtils.deleteDirectory(file);
    }

    public void copy(String newName) throws IOException {
        checkLock();
        Path newPath = file.resolveSibling(newName);
        FileUtils.copyDirectory(file, newPath);
        new World(newPath).setWorldName(newName);
    }

    public void export(Path zip, String worldName) throws IOException {
        try (Zipper zipper = new Zipper(zip)) {
            zipper.putDirectory(file, worldName);
        }
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
    public int compareTo(World o) {
        return getFile().compareTo(o.getFile());
    }

    private record WorldGenSettingsData(@Nullable Path file, @Nullable CompoundTag back, @Nullable CompoundTag data) {
    }

    private record PlayerData(@Nullable Path file, @Nullable CompoundTag data) {
    }

    public record LevelData(CompoundTag data, StringTag levelName, LongTag lastPlayed, CompoundTag rootTag, Path file) {
        public @Nullable GameVersionNumber getGameVersion() {
            if (data.get("Version") instanceof CompoundTag versionTag &&
                    versionTag.get("Name") instanceof StringTag nameTag) {
                return GameVersionNumber.asGameVersion(nameTag.getValue());
            }
            return null;
        }
    }
}
