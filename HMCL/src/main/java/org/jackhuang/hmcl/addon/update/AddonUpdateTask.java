// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonUpdateTask extends Task<@Nullable Void> {
    /// Provider used to download the selected versions.
    private final DownloadProvider downloadProvider;
    /// Destination for updated add-ons.
    private final Path addonDirectory;
    /// Versions and enabled states captured when the user requested the update.
    private final @Unmodifiable List<SelectedUpdate> addons;
    /// Add-ons whose updates failed.
    private final List<LocalAddonFile<?>> failedAddons = new ArrayList<>();

    /// An update retains the original disabled state when replacing or restoring its file.
    private record SelectedUpdate(AddonUpdate update, boolean disabled) {}

    /// Captures the updates selected for the given add-on directory.
    public AddonUpdateTask(DownloadProvider downloadProvider, Path addonDirectory, List<AddonUpdate> addons) {
        this.downloadProvider = downloadProvider;
        this.addonDirectory = addonDirectory;
        this.addons = addons.stream().map(addon -> new SelectedUpdate(addon, addon.localAddonFile().isDisabled())).toList();
    }

    /// Returns the add-ons whose updates failed.
    public List<LocalAddonFile<?>> getFailedAddons() {
        return failedAddons;
    }

    /// Returns the stage for addon updates.
    @Override
    public TaskStage getStage() {
        return new TaskStage("addon.check_update.confirm");
    }

    /// Downloads the selected updates and waits for each addon's cleanup to finish.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        @Unmodifiable List<Task<@Nullable Void>> updates = addons.stream().map(this::update).toList();
        context.addStageItems(updates.size());
        try {
            context.runAll(updates, FetchTask.getDownloadExecutorConcurrency());
        } finally {
            for (int i = 0; i < updates.size(); i++) {
                @Nullable Throwable failure = updates.get(i).exceptionNowOrNull();
                if (failure != null && !(failure instanceof CancellationException)) {
                    LOG.warning("Failed to update addon", failure);
                    failedAddons.add(addons.get(i).update().localAddonFile());
                }
            }
        }
        return null;
    }

    /// Stages one download beside its destination and accepts cancellation until replacement begins.
    private Task<@Nullable Void> update(SelectedUpdate selected) {
        return new Task<@Nullable Void>() {
            /// Creates and moves the staged file on the I/O executor.
            @Override
            protected Executor getExecutor() {
                return Schedulers.io();
            }

            /// Downloads the new file before renaming the installed add-on.
            @Override
            public @Nullable Void execute(TaskExecutionContext context) throws Exception {
                RemoteAddon.Version remote = selected.update().targetVersion();
                Path temporary = Files.createTempFile(addonDirectory, ".hmcl-addon-update-", ".tmp");
                try {
                    context.run(new FileDownloadTask(downloadProvider.getDownloadCandidates(remote.file().url()), temporary)
                            .setName(remote.name()));
                    replace(context, selected, temporary);
                } catch (Exception | Error failure) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
                return null;
            }
        }.setSignificance(TaskSignificance.MINOR).withCounter();
    }

    /// Moves the downloaded file and publishes or restores the add-on state on JavaFX.
    private void replace(TaskExecutionContext context, SelectedUpdate selected, Path temporary) throws Exception {
        LocalAddonFile<?> local = selected.update().localAddonFile();
        String fileName = selected.update().targetVersion().file().filename();
        String newFileName = selected.disabled() ? StringUtils.addSuffix(fileName, LocalAddonManager.DISABLED_EXTENSION) : fileName;
        Path target = addonDirectory.resolve(newFileName);
        AtomicBoolean markedOld = new AtomicBoolean();
        try {
            context.call(Schedulers.javafx(), () -> {
                context.commit();
                local.setOld(true);
                markedOld.set(true);
                return null;
            });
            Files.move(temporary, target);
        } catch (Exception | Error failure) {
            if (markedOld.get()) {
                try {
                    context.call(Schedulers.javafx(), () -> {
                        local.setOld(false);
                        if (selected.disabled())
                            local.markDisabled();
                        return null;
                    });
                } catch (Exception | Error cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
        context.call(Schedulers.javafx(), () -> {
            local.onUpdated(newFileName);
            if (!local.keepOldFiles()) {
                try {
                    local.delete();
                } catch (IOException e) {
                    LOG.warning("Failed to delete outdated addon: " + local.getFile(), e);
                }
            }
            return null;
        });
    }
}
