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
        List<Task<?>> updates = new ArrayList<>(addons.size());
        for (SelectedUpdate selected : addons) {
            context.checkCancellation();
            LocalAddonFile<?> local = selected.update().localAddonFile();
            RemoteAddon.Version remote = selected.update().targetVersion();
            String fileName = remote.file().filename();
            String newFileName = selected.disabled()
                    ? StringUtils.addSuffix(fileName, LocalAddonManager.DISABLED_EXTENSION) : fileName;
            Path targetFile = addonDirectory.resolve(newFileName);

            updates.add(Task
                    .supplyAsync(Schedulers.io(), () -> Files.createTempFile("hmcl-addon-update-", ".tmp"))
                    .thenComposeAsync(tempFile ->
                            new FileDownloadTask(downloadProvider.getDownloadCandidates(remote.file().url()), tempFile)
                                    .setName(remote.name())
                                    .thenRunAsync(Schedulers.javafx(), () -> local.setOld(true))
                                    .thenRunAsync(Schedulers.io(), () -> Files.move(tempFile, targetFile))
                                    .whenCompleteAsync(Schedulers.javafx(), (ignored, exception) -> {
                                        try {
                                            Files.deleteIfExists(tempFile);
                                        } catch (Exception e) {
                                            LOG.warning("Failed to delete temp file " + tempFile, e);
                                        }
                                        if (exception != null) {
                                            // restore state if failed
                                            local.setOld(false);
                                            if (selected.disabled())
                                                local.markDisabled();
                                        } else {
                                            local.onUpdated(newFileName);
                                            if (!local.keepOldFiles()) {
                                                try {
                                                    local.delete();
                                                } catch (IOException e) {
                                                    LOG.warning("Failed to delete outdated addon: " + local.getFile(), e);
                                                }
                                            }
                                        }
                                    })
                    ).whenCompleteAsync(Schedulers.javafx(), (ignored, exception) -> {
                        if (exception != null && !(exception instanceof CancellationException)) {
                            LOG.warning("Failed to update addon", exception);
                            failedAddons.add(local);
                        }
                    }).withCounter());
        }
        context.addStageItems(updates.size());
        context.runAll(updates, FetchTask.getDownloadExecutorConcurrency());
        return null;
    }
}
