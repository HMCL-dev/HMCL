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
        Path tempPath = FileUtils.tmpSaveFile(worldPath);

        // Check if the world format is correct
        new World(backupZipPath);
        try {
            new Unzipper(backupZipPath, tempPath).setSubDirectory(world.getFileName()).unzip();
        } catch (IOException e) {
            FileUtils.deleteDirectoryQuietly(tempPath);
            throw e;
        }
        world.getWorldLock().releaseLock();
        FileUtils.deleteDirectory(worldPath);
        Files.move(tempPath, worldPath, StandardCopyOption.ATOMIC_MOVE);

        setResult(worldPath);
    }
}
