/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.task.SilentException;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

public class ModpackUpdateTask extends Task {

    private final DefaultGameRepository repository;
    private final String id;
    private final Task updateTask;
    private final Path backupFolder;

    public ModpackUpdateTask(DefaultGameRepository repository, String id, Task updateTask) {
        this.repository = repository;
        this.id = id;
        this.updateTask = updateTask;

        Path backup = repository.getBaseDirectory().toPath().resolve("backup");
        while (true) {
            int num = (int)(Math.random() * 10000000);
            if (!Files.exists(backup.resolve(id + "-" + num))) {
                backupFolder = backup.resolve(id + "-" + num);
                break;
            }
        }
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    @Override
    public Collection<? extends Task> getDependencies() {
        return Collections.singleton(updateTask);
    }

    @Override
    public void execute() throws Exception {
        FileUtils.copyDirectory(repository.getVersionRoot(id).toPath(), backupFolder);
    }

    @Override
    public void postExecute() throws Exception {
        if (isDependenciesSucceeded()) {
            // Keep backup game version for further repair.
        } else {
            // Restore backup
            repository.removeVersionFromDisk(id);

            FileUtils.copyDirectory(backupFolder, repository.getVersionRoot(id).toPath());

            throw new SilentException();
        }
    }
}
