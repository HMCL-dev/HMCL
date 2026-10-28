// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import javafx.scene.image.Image;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
public final class WorldFolder extends World {
    private final String fileName;
    private final @Nullable Image icon;
    private LevelData levelData;
    private WorldGenSettingsData worldGenSettingsData;
    private PlayerData playerData;

    public WorldFolder(Path file) throws IOException {
        super(file);

        icon = loadIcon(file);
        fileName = FileUtils.getName(file);
        reloadWorldData();
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public LevelData getLevelData() {
        return levelData;
    }

    @Override
    public @Nullable Image getIcon() {
        return icon;
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

    private void writeTag(@Nullable CompoundTag nbt, @Nullable Path path) throws IOException {
        if (nbt == null || path == null)
            return;
        FileUtils.saveSafely(path, os -> {
            try (OutputStream gos = new GZIPOutputStream(os)) {
                NBTCodec.of().writeTag(gos, nbt);
            }
        });
    }

    public void setWorldName(String worldName) throws IOException {
        levelData.levelName().setValue(worldName);
        writeTag(levelData.rootTag(), levelData.file());
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

    public void delete() throws IOException {
        checkLock();
        FileUtils.deleteDirectory(file);
    }

    public void copy(String newName) throws IOException {
        checkLock();
        Path newPath = file.resolveSibling(newName);
        FileUtils.copyDirectory(file, newPath);
        new WorldFolder(newPath).setWorldName(newName);
    }

    public void export(Path zip, String worldName) throws IOException {
        try (Zipper zipper = new Zipper(zip)) {
            zipper.putDirectory(file, worldName);
        }
    }

    private record WorldGenSettingsData(@Nullable Path file, @Nullable CompoundTag back, @Nullable CompoundTag data) {
    }

    private record PlayerData(@Nullable Path file, @Nullable CompoundTag data) {
    }
}
