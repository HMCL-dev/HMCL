// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Runs a modpack update with an instance-directory backup and rollback on failure.
@NotNullByDefault
public class ModpackUpdateTask extends Task<@Nullable Void> {

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

    /// Backs up the instance and restores it if applying the update fails.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        FileUtils.copyDirectory(instance.getInstanceRoot(), backupFolder);
        try {
            context.run(updateTask);
        } catch (Exception failure) {
            if (!instance.getRepository().removeInstanceFromDisk(instance.getId())) {
                throw new IOException("Failed to remove instance before restoring backup: " + instance.getId(), failure);
            }
            FileUtils.copyDirectory(backupFolder, instance.getInstanceRoot());
            instance.getRepository().refresh();
            throw failure;
        }
        return null;
    }
}
