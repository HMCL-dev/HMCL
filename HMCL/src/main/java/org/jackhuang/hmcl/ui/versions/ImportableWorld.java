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
public final class ImportableWorld {
    private final Path sourcePath;
    private final String fileName;
    private final boolean isArchive;
    private final boolean hasTopLevelDirectory;
    private String worldName;
    private @Nullable GameVersionNumber gameVersion;
    private @Nullable Image icon;

    public ImportableWorld(Path sourcePath) throws IOException {
        if (Files.isRegularFile(sourcePath)) {
            this.sourcePath = sourcePath;
            this.isArchive = true;

            try (FileSystem fs = CompressingUtils.readonly(this.sourcePath).setAutoDetectEncoding(true).build()) {
                Path root;
                if (Files.isRegularFile(fs.getPath("/level.dat"))) {
                    root = fs.getPath("/");
                    hasTopLevelDirectory = false;
                    fileName = FileUtils.getName(this.sourcePath);
                } else {
                    List<Path> files = Files.list(fs.getPath("/")).toList();
                    if (files.size() != 1 || !Files.isDirectory(files.get(0))) {
                        throw new IOException("Not a valid world zip file");
                    }

                    root = files.get(0);
                    hasTopLevelDirectory = true;
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
        } else if (Files.isDirectory(sourcePath)) {
            this.sourcePath = sourcePath;
            fileName = FileUtils.getName(this.sourcePath);
            this.isArchive = false;
            this.hasTopLevelDirectory = false;

            Path levelDatPath = this.sourcePath.resolve("level.dat");
            if (!Files.exists(levelDatPath)) { // version 20w14infinite
                levelDatPath = this.sourcePath.resolve("special_level.dat");
            }
            if (!Files.exists(levelDatPath)) {
                throw new IOException("Not a valid world directory since level.dat or special_level.dat cannot be found.");
            }
            checkAndLoadLevelData(levelDatPath);
        } else {
            throw new IOException("Path " + sourcePath + " cannot be recognized as a archive Minecraft world");
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

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean hasSubDir() {
        return hasTopLevelDirectory;
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
        String safeName = FileUtils.getSafeWorldFolderName(name);

        Path worldDir;
        for (int count = 0; count < 256; count++) {
            worldDir = savesDir.resolve(count == 0 ? safeName : safeName + " (" + count + ")");
            if (!Files.exists(worldDir)) {
                if (isArchive) {
                    if (hasTopLevelDirectory) {
                        new Unzipper(sourcePath, worldDir).setSubDirectory("/" + fileName + "/").unzip();
                    } else {
                        new Unzipper(sourcePath, worldDir).unzip();
                    }
                } else {
                    FileUtils.copyDirectory(sourcePath, worldDir, path -> !path.contains("session.lock"));
                }
                new World(worldDir).setWorldName(name);
                return;
            }
        }
        throw new IOException("Too many attempts");
    }
}
