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
package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class WorldRestoreTask extends Task<Path> {
    private final Path backupZipPath;
    private final World world;

    public WorldRestoreTask(Path backupZipPath, World world) {
        this.backupZipPath = backupZipPath;
        this.world = world;
    }

    @Override
    public void execute() throws Exception {
        Path worldPath = world.getFile();
        Path tempPath = worldPath.toAbsolutePath().resolveSibling("." + worldPath.getFileName().toString() + ".tmp");
        Path tempPath2 = worldPath.toAbsolutePath().resolveSibling("." + worldPath.getFileName().toString() + ".tmp2");

        // Check if the world format is correct
        ImportableWorld importableWorld = ImportableWorld.fromPath(backupZipPath);
        try {
            new Unzipper(backupZipPath, tempPath).setSubDirectory(importableWorld.fileName()).unzip();
        } catch (IOException e) {
            FileUtils.deleteDirectoryQuietly(tempPath);
            throw e;
        }

        try {
            world.getWorldLock().releaseLock();
            Files.move(worldPath, tempPath2, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            FileUtils.deleteDirectoryQuietly(tempPath);
            FileUtils.deleteDirectoryQuietly(tempPath2);
            world.getWorldLock().lock();
            throw e;
        }

        try {
            Files.move(tempPath, worldPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(tempPath2, worldPath, StandardCopyOption.REPLACE_EXISTING);
            FileUtils.deleteDirectoryQuietly(tempPath);
            world.getWorldLock().lock();
            throw e;
        }

        FileUtils.deleteDirectoryQuietly(tempPath2);

        setResult(worldPath);
    }
}
