// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonCheckUpdatesTask extends Task<AddonUpdates> {
    /// One update check per installed add-on.
    private final List<Task<@Nullable Pair<AddonUpdate, @Nullable AddonUpdate>>> checks;

    public AddonCheckUpdatesTask(DownloadProvider downloadProvider, String gameVersion, Collection<? extends LocalAddonFile<?>> addons) {
        checks = addons.stream().map(addon ->
                Task.supplyAsync(Schedulers.io(), () -> {
                    AddonUpdate candidate = null;
                    AddonUpdate candidateRelease = null;
                    for (RemoteAddon.Source source : RemoteAddon.Source.values()) {
                        AddonUpdate update = null;
                        AddonUpdate updateRelease = null;
                        try {
                            var u = addon.checkUpdates(downloadProvider, gameVersion, source);
                            if (u != null) {
                                update = u.key();
                                updateRelease = u.value();
                            }
                        } catch (IOException e) {
                            LOG.warning(String.format("Cannot check update for addon %s.", addon.getFileName()), e);
                        }
                        if (update == null) continue;
                        if (candidate == null || candidate.targetVersion().datePublished().isBefore(update.targetVersion().datePublished()))
                            candidate = update;

                        if (updateRelease == null) continue;
                        if (candidateRelease == null || candidateRelease.targetVersion().datePublished().isBefore(updateRelease.targetVersion().datePublished()))
                            candidateRelease = updateRelease;
                    }

                    if (candidate == null) return null; // If there's no candidate for all channels, then no candidate for release channel
                    return Pair.pair(candidate, candidateRelease);
                }).setName(addon.getFileName()).setSignificance(TaskSignificance.MAJOR).withCounter()
        ).toList();

        getProperties().put("total", checks.size());
    }

    /// Returns the stage for addon update checks.
    @Override
    public String getStage() {
        return "update.checking";
    }

    /// Collects available updates from all checks that completed successfully.
    @Override
    public AddonUpdates execute(TaskExecutionContext context) throws Exception {
        context.notifyPropertiesChanged();
        try {
            context.runAll(checks);
        } catch (Exception failure) {
            if (context.isCancelled() || failure instanceof InterruptedException || failure instanceof CancellationException)
                throw failure;
            // Other add-ons can still contribute updates after a failed check.
        }

        List<AddonUpdate> commonUpdates = new ArrayList<>(), releaseUpdates = new ArrayList<>();
        checks.stream().map(Task::getResult).filter(Objects::nonNull)
                .forEachOrdered(pair -> {
                    commonUpdates.add(pair.key());
                    if (pair.value() != null) releaseUpdates.add(pair.value());
                });
        return new AddonUpdates(commonUpdates, releaseUpdates);
    }
}
