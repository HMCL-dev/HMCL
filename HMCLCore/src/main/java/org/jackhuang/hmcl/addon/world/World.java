// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.world;

import javafx.scene.image.Image;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public abstract sealed class World extends LocalAddonFile<World> permits WorldFolder, WorldZipFile {
    final Path file;

    public World(Path file) throws IOException {
        this.file = file;
    }

    public static World of(Path file) throws IOException {
        return Files.isDirectory(file) ? new WorldFolder(file) : new WorldZipFile(file);
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

    public abstract @Nullable Image getIcon();

    public abstract LevelData getLevelData();

    public boolean supportDataPacks() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.13", "17w43a");
    }

    public String getWorldName() {
        return getLevelData().levelName.get();
    }

    public long getLastPlayed() {
        return getLevelData().lastPlayed.get();
    }

    public @Nullable GameVersionNumber getGameVersion() {
        if (getLevelData().data.get("Version") instanceof CompoundTag versionTag &&
                versionTag.get("Name") instanceof StringTag nameTag) {
            return GameVersionNumber.asGameVersion(nameTag.getValue());
        }
        return null;
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

    public record LevelData(CompoundTag data, StringTag levelName, LongTag lastPlayed, CompoundTag rootTag, Path file) {
    }
}
