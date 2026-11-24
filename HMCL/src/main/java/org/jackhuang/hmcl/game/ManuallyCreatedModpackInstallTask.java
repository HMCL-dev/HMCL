// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Extracts a manually created modpack and registers its new external game directory.
@NotNullByDefault
final class ManuallyCreatedModpackInstallTask extends Task<@Nullable Void> {

    /// Archive containing the game directory.
    private final Path zipFile;
    /// Installation path reserved exclusively for this extraction.
    private final Path destination;
    /// Display name of the registered directory.
    private final String gameDirectoryName;

    /// Installs the archive into a new directory under the given display name.
    ManuallyCreatedModpackInstallTask(Path zipFile, Path destination, String gameDirectoryName) {
        this.zipFile = zipFile;
        this.destination = destination;
        this.gameDirectoryName = gameDirectoryName;

        setName(i18n("modpack.installing"));
    }

    /// Removes an incomplete installation if extraction or registration fails or is cancelled.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        String subdirectory = ModpackHelper.findMinecraftDirectoryInManuallyCreatedModpack(zipFile.toString(), zipFile);

        context.checkCancellation();
        AtomicBoolean registered = new AtomicBoolean();
        @Nullable Path parent = destination.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        Files.createDirectory(destination);
        try {
            new Unzipper(zipFile, destination)
                    .setSubDirectory(subdirectory)
                    .setTerminateIfSubDirectoryNotExists()
                    .setFilter((entry, file, relativePath) -> {
                        context.checkCancellation();
                        return true;
                    })
                    .unzip();
            context.checkCancellation();
            return context.call(Schedulers.javafx(), () -> {
                context.checkCancellation();
                GameDirectory gameDirectory = new GameDirectory(
                        GameDirectoryManager.newGameDirectoryId(),
                        LocalizedText.plain(gameDirectoryName),
                        PortablePath.fromPath(destination));
                GameDirectoryManager.addLocalGameDirectory(gameDirectory);
                // An interrupted caller may still observe failure after registration succeeds.
                registered.set(true);
                GameDirectoryManager.setSelectedGameDirectory(gameDirectory);
                return null;
            });
        } catch (Throwable failure) {
            if (!registered.get()) {
                try {
                    FileUtils.deleteDirectory(destination);
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }
}
