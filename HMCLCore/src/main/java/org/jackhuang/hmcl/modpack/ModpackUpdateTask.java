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
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

public class ModpackUpdateTask extends Task<Void> {

    private final DefaultGameRepository repository;
    private final GameInstanceID id;
    private final Task<?> updateTask;
    private final Path backupFolder;

    public ModpackUpdateTask(DefaultGameRepository repository, GameInstanceID instanceId, Task<?> updateTask) {
        this.repository = repository;
        this.id = instanceId;
        this.updateTask = updateTask;

        Path backup = repository.getBaseDirectory().resolve("backup");
        while (true) {
            int num = (int)(Math.random() * 10000000);
            if (!Files.exists(backup.resolve(instanceId + "-" + num))) {
                backupFolder = backup.resolve(instanceId + "-" + num);
                break;
            }
        }
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return Collections.singleton(updateTask);
    }

    @Override
    public void execute() throws Exception {
        FileUtils.copyDirectory(repository.getInstanceRoot(id), backupFolder);
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        if (isDependenciesSucceeded()) {
            // Keep backup game version for further repair.
        } else {
            // Restore backup
            repository.removeInstanceFromDisk(id);

            FileUtils.copyDirectory(backupFolder, repository.getInstanceRoot(id));

            repository.refreshAsync().start();
        }
    }
}
