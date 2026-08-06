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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Installs a modpack bundled next to the launcher when the selected repository is empty.
///
/// Looks for `modpack.zip` or `modpack.mrpack` under [Metadata#CURRENT_DIRECTORY]. This is a
/// startup product feature (portable / first-run bundle), not UI page logic.
@NotNullByDefault
public final class BundledModpackBootstrap {

    private static final AtomicBoolean attempted = new AtomicBoolean();

    private BundledModpackBootstrap() {
    }

    /// Returns the bundled modpack file under the process working directory, if present.
    ///
    /// Prefers `modpack.zip` over `modpack.mrpack` when both exist.
    ///
    /// @return the modpack path, or `null` when neither file exists
    public static @Nullable Path findBundledModpackFile() {
        Path zipModpack = Metadata.CURRENT_DIRECTORY.resolve("modpack.zip");
        if (Files.isRegularFile(zipModpack)) {
            return zipModpack;
        }
        Path mrpackModpack = Metadata.CURRENT_DIRECTORY.resolve("modpack.mrpack");
        if (Files.isRegularFile(mrpackModpack)) {
            return mrpackModpack;
        }
        return null;
    }

    /// Schedules a one-shot attempt after the selected repository finishes a full refresh.
    ///
    /// When the repository has no instances and a bundled modpack file exists, builds an install
    /// [TaskExecutor] and passes it to `presentAndStart` on the JavaFX thread. The consumer should
    /// show progress UI (if any) and call [TaskExecutor#start].
    ///
    /// @param presentAndStart presents and starts the install executor; must not be null
    public static void scheduleAfterSelectedRepositoryLoaded(Consumer<TaskExecutor> presentAndStart) {
        GameDirectoryManager.registerVersionsListener(repository ->
                tryInstall(repository, presentAndStart));
    }

    /// Attempts a one-shot bundled modpack install for the given repository.
    private static void tryInstall(HMCLGameRepository repository, Consumer<TaskExecutor> presentAndStart) {
        if (!attempted.compareAndSet(false, true)) {
            return;
        }
        if (repository.getInstanceCount() != 0) {
            return;
        }

        @Nullable Path modpackFile = findBundledModpackFile();
        if (modpackFile == null) {
            return;
        }

        LOG.info("Found bundled modpack at " + modpackFile + "; starting automatic install");

        Task.supplyAsync(() -> CompressingUtils.findSuitableEncoding(modpackFile))
                .thenApplyAsync(encoding -> ModpackHelper.readModpackManifest(modpackFile, encoding))
                .thenApplyAsync(modpack -> ModpackHelper
                        .getInstallTask(repository, modpackFile, new GameInstanceID(modpack.getName()), modpack, null)
                        .executor())
                .thenAcceptAsync(Schedulers.javafx(), presentAndStart::accept)
                .start();
    }
}
