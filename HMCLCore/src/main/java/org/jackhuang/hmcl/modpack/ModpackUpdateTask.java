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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

/// Runs a modpack update with an instance-directory backup and rollback on failure.
@NotNullByDefault
public class ModpackUpdateTask extends Task<@Nullable Void> {

    /// The fixed pre-update instance snapshot.
    private final DefaultGameInstance instance;

    /// The task that applies the modpack update after the backup is created.
    private final Task<?> updateTask;

    /// Creates an update task that backs up and restores a registered instance as one operation.
    ///
    /// @param instance   the registered instance to update
    /// @param updateTask the task that performs the update
    public ModpackUpdateTask(DefaultGameInstance instance, Task<?> updateTask) {
        this.instance = instance;
        this.updateTask = updateTask;
    }

    /// Creates a new directory for this update beneath the repository's backup directory.
    private Path createBackupDirectory() throws IOException {
        Path parent = Files.createDirectories(instance.getLayout().getBaseDirectory().resolve("backup"));
        while (true) {
            int suffix = ThreadLocalRandom.current().nextInt(10_000_000);
            try {
                return Files.createDirectory(parent.resolve(instance.getId() + "-" + suffix));
            } catch (FileAlreadyExistsException ignored) {
                // Leave the existing backup intact and try another name.
            }
        }
    }

    /// Backs up the instance and restores it if applying the update fails.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Path backupFolder = createBackupDirectory();
        FileUtils.copyDirectory(instance.getInstanceRoot(), backupFolder);
        context.checkCancellation();
        if (instance.getRepository().getSnapshot().findInstance(instance.getId()) != instance)
            throw new IllegalStateException("Game instance has changed: " + instance.getId());
        if (updateTask.getState() != State.NEW) {
            context.run(updateTask);
            return null;
        }
        try {
            context.run(updateTask);
        } catch (Exception | Error failure) {
            // The update may have been cancelled before the scheduler admitted it.
            if (updateTask.getState() == State.NEW)
                throw failure;
            try {
                if (!instance.getRepository().removeInstanceFromDisk(instance.getId()))
                    throw new IOException("Failed to remove instance before restoring backup: " + instance.getId());
                FileUtils.copyDirectory(backupFolder, instance.getInstanceRoot());
                instance.getRepository().refresh();
            } catch (Exception | Error rollbackFailure) {
                if (rollbackFailure != failure)
                    failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        return null;
    }
}
