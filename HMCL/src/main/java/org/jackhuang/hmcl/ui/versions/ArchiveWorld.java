/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import javafx.scene.image.Image;
import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.CompoundTag;
import org.glavo.nbt.tag.LongTag;
import org.glavo.nbt.tag.StringTag;
import org.glavo.nbt.tag.TagType;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author mineDiamond
public final class ArchiveWorld {
    private final Path file;
    private final String fileName;
    private final boolean hasSubDir;
    private String worldName;
    private @Nullable GameVersionNumber gameVersion;
    private @Nullable Image icon;

    public ArchiveWorld(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            this.file = file;

            try (FileSystem fs = CompressingUtils.readonly(this.file).setAutoDetectEncoding(true).build()) {
                Path root;
                if (Files.isRegularFile(fs.getPath("/level.dat"))) {
                    root = fs.getPath("/");
                    hasSubDir = false;
                    fileName = FileUtils.getName(this.file);
                } else {
                    List<Path> files = Files.list(fs.getPath("/")).toList();
                    if (files.size() != 1 || !Files.isDirectory(files.get(0))) {
                        throw new IOException("Not a valid world zip file");
                    }

                    root = files.get(0);
                    hasSubDir = true;
                    fileName = FileUtils.getName(root);
                }

                Path levelDat = root.resolve("level.dat");
                if (!Files.exists(levelDat)) { //version 20w14infinite
                    levelDat = root.resolve("special_level.dat");
                }
                if (!Files.exists(levelDat)) {
                    throw new IOException("Not a valid world zip file since level.dat or special_level.dat cannot be found.");
                }
                checkAndLoadLevelData(levelDat);

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
        } else {
            throw new IOException("Path " + file + " cannot be recognized as a archive Minecraft world");
        }
    }

    private void checkAndLoadLevelData(Path levelDatPath) throws IOException {
        CompoundTag levelData = NBTCodec.of().readTag(levelDatPath, TagType.COMPOUND);
        if (!(levelData.get("Data") instanceof CompoundTag data))
            throw new IOException("level.dat missing Data");

        if (data.get("LevelName") instanceof StringTag levelNameTag) {
            this.worldName = levelNameTag.getValue();
        } else {
            throw new IOException("level.dat missing LevelName");
        }

        if (data.get("Version") instanceof CompoundTag versionTag &&
                versionTag.get("Name") instanceof StringTag nameTag) {
            this.gameVersion = GameVersionNumber.asGameVersion(nameTag.getValue());
        }

        if (!(data.get("LastPlayed") instanceof LongTag))
            throw new IOException("level.dat missing LastPlayed");
    }

    public Path getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean hasSubDir() {
        return hasSubDir;
    }

    public String getWorldName() {
        return worldName;
    }

    public @Nullable GameVersionNumber getGameVersion() {
        return gameVersion;
    }

    public @Nullable Image getIcon() {
        return icon;
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

        if (hasSubDir) {
            new Unzipper(file, worldDir).setSubDirectory("/" + fileName + "/").unzip();
        } else {
            new Unzipper(file, worldDir).unzip();
        }
        new World(worldDir).rename(name);
    }
}
