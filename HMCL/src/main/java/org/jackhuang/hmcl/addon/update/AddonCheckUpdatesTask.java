// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonCheckUpdatesTask extends Task<AddonUpdates> {
    /// The stage shared by add-on update checks and their progress dialogs.
    public static final TaskStage STAGE = new TaskStage("update.checking");

    /// Provider used for remote version queries.
    private final DownloadProvider downloadProvider;
    /// Game version selected for the checks.
    private final String gameVersion;
    /// Add-ons selected when the task was created, in input order.
    private final @Unmodifiable List<LocalAddonFile<?>> addons;

    /// Captures the add-ons to check for the given game version.
    public AddonCheckUpdatesTask(DownloadProvider downloadProvider, String gameVersion, Collection<? extends LocalAddonFile<?>> addons) {
        this.downloadProvider = downloadProvider;
        this.gameVersion = gameVersion;
        this.addons = List.copyOf(addons);
    }

    /// Returns the stage for addon update checks.
    @Override
    public TaskStage getStage() {
        return STAGE;
    }

    /// Collects available updates from all checks that completed successfully.
    @Override
    public AddonUpdates execute(TaskExecutionContext context) throws Exception {
        List<Task<@Nullable Pair<AddonUpdate, @Nullable AddonUpdate>>> checks = new ArrayList<>(addons.size());
        for (LocalAddonFile<?> addon : addons) {
            context.checkCancellation();
            checks.add(Task.supplyAsync(Schedulers.io(), () -> checkUpdates(addon))
                    .setName(addon.getFileName()).setSignificance(TaskSignificance.MAJOR).withCounter());
        }
        context.addStageItems(checks.size());
        try {
            context.runAll(checks, FetchTask.getDownloadExecutorConcurrency());
        } catch (Exception failure) {
            if (context.isCancelled() || failure instanceof InterruptedException || failure instanceof CancellationException)
                throw failure;
            // Other add-ons can still contribute updates after a failed check.
        }

        List<AddonUpdate> commonUpdates = new ArrayList<>(), releaseUpdates = new ArrayList<>();
        checks.stream()
                .map(Task::resultNowOrNull)
                .filter(Objects::nonNull)
                .forEachOrdered(pair -> {
                    commonUpdates.add(pair.key());
                    if (pair.value() != null) releaseUpdates.add(pair.value());
                });
        return new AddonUpdates(commonUpdates, releaseUpdates);
    }

    /// Finds the newest available update and the newest release across the add-on sources.
    private @Nullable Pair<AddonUpdate, @Nullable AddonUpdate> checkUpdates(LocalAddonFile<?> addon) {
        @Nullable AddonUpdate candidate = null;
        @Nullable AddonUpdate candidateRelease = null;
        for (RemoteAddon.Source source : RemoteAddon.Source.values()) {
            @Nullable Pair<AddonUpdate, @Nullable AddonUpdate> versions;
            try {
                versions = addon.checkUpdates(downloadProvider, gameVersion, source);
            } catch (IOException e) {
                LOG.warning(String.format("Cannot check update for addon %s.", addon.getFileName()), e);
                continue;
            }
            if (versions == null || versions.key() == null) continue;
            AddonUpdate update = versions.key();
            if (candidate == null || candidate.targetVersion().datePublished().isBefore(update.targetVersion().datePublished()))
                candidate = update;
            @Nullable AddonUpdate release = versions.value();
            if (release != null && (candidateRelease == null || candidateRelease.targetVersion().datePublished().isBefore(release.targetVersion().datePublished())))
                candidateRelease = release;
        }
        return candidate == null ? null : Pair.pair(candidate, candidateRelease);
    }
}
