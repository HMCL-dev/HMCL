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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/// @author mineDiamond
record ImportableWorld(Path sourcePath, String fileName, boolean isArchive, boolean hasTopLevelDirectory, String worldName, @Nullable GameVersionNumber gameVersion, @Nullable Image icon) {

    public static ImportableWorld fromPath(Path sourcePath) throws IOException {

        String fileName;
        boolean isArchive;
        boolean hasTopLevelDirectory;
        String worldName;
        GameVersionNumber gameVersion;
        Image icon = null;

        if (Files.isRegularFile(sourcePath)) {
            isArchive = true;
            try (FileSystem fs = CompressingUtils.readonly(sourcePath).setAutoDetectEncoding(true).build()) {
                Path root;
                if (Files.isRegularFile(fs.getPath("/level.dat"))) {
                    root = fs.getPath("/");
                    hasTopLevelDirectory = false;
                    fileName = FileUtils.getName(sourcePath);
                } else {
                    try (Stream<Path> stream = Files.list(fs.getPath("/"))) {
                        List<Path> files = stream.toList();
                        if (files.size() != 1 || !Files.isDirectory(files.get(0))) {
                            throw new IOException("Not a valid world zip file");
                        }

                        root = files.get(0);
                        hasTopLevelDirectory = true;
                        fileName = FileUtils.getName(root);
                    }
                }

                CompoundTag dataTag = loadLevelData(World.findLevelDatPath(root));
                worldName = getLevelName(dataTag);
                gameVersion = getGameVersion(dataTag);
                icon = World.loadIcon(root);
            }
        } else {
            fileName = FileUtils.getName(sourcePath);
            isArchive = false;
            hasTopLevelDirectory = false;
            CompoundTag dataTag = loadLevelData(World.findLevelDatPath(sourcePath));
            worldName = getLevelName(dataTag);
            gameVersion = getGameVersion(dataTag);
        }
        return new ImportableWorld(sourcePath, fileName, isArchive, hasTopLevelDirectory, worldName, gameVersion, icon);
    }

    private static CompoundTag loadLevelData(Path levelDatPath) throws IOException {
        CompoundTag levelData = NBTCodec.of().readTag(levelDatPath, TagType.COMPOUND);
        if (!(levelData.get("Data") instanceof CompoundTag data))
            throw new IOException("level.dat missing Data");

        if (!(data.get("LastPlayed") instanceof LongTag))
            throw new IOException("level.dat missing LastPlayed");

        return data;
    }

    private static String getLevelName(CompoundTag data) throws IOException {
        if (data.get("LevelName") instanceof StringTag levelNameTag) {
            return levelNameTag.getValue();
        }
        throw new IOException("level.dat missing LevelName");
    }

    private static GameVersionNumber getGameVersion(CompoundTag data) throws IOException {
        if (data.get("Version") instanceof CompoundTag versionTag &&
                versionTag.get("Name") instanceof StringTag nameTag) {
            return GameVersionNumber.asGameVersion(nameTag.getValue());
        }
        return null;
    }

    public void install(Path savesDir, String name) throws IOException {
        Path targetPath = FileUtils.getNonConflictingDirectory(savesDir, FileUtils.getSafeWorldFolderName(name));

        if (isArchive) {
            if (hasTopLevelDirectory) {
                new Unzipper(sourcePath, targetPath).setSubDirectory("/" + fileName + "/").unzip();
            } else {
                new Unzipper(sourcePath, targetPath).unzip();
            }
        } else {
            FileUtils.copyDirectory(sourcePath, targetPath, path -> !path.contains("session.lock"));
        }
        new World(targetPath).setWorldName(name);
    }
}
