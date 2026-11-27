// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/// Runs a modpack update with an instance-directory backup and rollback on failure.
@NotNullByDefault
public class ModpackUpdateTask extends Task<Void> {

    /// The fixed pre-update instance snapshot.
    private final DefaultGameInstance instance;

    /// The task that applies the modpack update after the backup is created.
    private final Task<?> updateTask;

    /// A randomly named backup directory that was unused when this task was created.
    private final Path backupFolder;

    /// Creates an update task that backs up and restores a registered instance as one operation.
    ///
    /// @param instance   the registered instance to update
    /// @param updateTask the task that performs the update
    public ModpackUpdateTask(DefaultGameInstance instance, Task<?> updateTask) {
        this.instance = instance;
        this.updateTask = updateTask;

        Path backup = instance.getLayout().getBaseDirectory().resolve("backup");
        while (true) {
            int num = (int) (Math.random() * 10000000);
            Path candidate = backup.resolve(instance.getId() + "-" + num);
            if (!Files.exists(candidate)) {
                backupFolder = candidate;
                break;
            }
        }
    }

    /// Returns the update task that runs after this task creates the backup.
    ///
    /// @return a singleton containing the update task
    @Override
    public Collection<Task<?>> getDependencies() {
        return Collections.singleton(updateTask);
    }

    /// Copies the instance directory into the backup directory.
    ///
    /// @throws IllegalStateException if the original instance is no longer published
    @Override
    public void execute() throws Exception {
        if (instance.getRepository().getSnapshot().findInstance(instance.getId()) != instance)
            throw new IllegalStateException("Game instance has changed: " + instance.getId());

        FileUtils.copyDirectory(instance.getInstanceRoot(), backupFolder);
    }

    /// Requests post-execution cleanup or rollback after the update task terminates.
    ///
    /// @return `true`
    @Override
    public boolean doPostExecute() {
        return true;
    }

    /// Retains the backup after success. On failure, restores the backup and refreshes the
    /// repository unless the game installation stopped before reserving the target.
    ///
    /// @throws IOException if the repository layout has changed before rollback or the backup
    ///                     cannot be restored
    @Override
    public void postExecute() throws Exception {
        if (isDependenciesSucceeded()) {
            // Keep backup game version for further repair.
            return;
        }
        // The target may have been replaced between the backup and the reservation.
        if (updateTask instanceof ModpackGameInstallTask installation && !installation.hasReservedInstance())
            return;

        // Restore backup
        if (instance.getRepository().getLayout() != instance.getLayout())
            throw new IOException("Game directory has changed, backup retained at " + backupFolder);
        if (!instance.getRepository().removeInstanceFromDisk(instance.getId())) {
            throw new IOException("Failed to remove instance before restoring backup: " + instance.getId());
        }

        FileUtils.copyDirectory(backupFolder, instance.getInstanceRoot());
        instance.getRepository().refresh();
    }
}
