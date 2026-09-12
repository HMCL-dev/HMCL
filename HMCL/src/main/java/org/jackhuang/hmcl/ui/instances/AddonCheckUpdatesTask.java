/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances;

import org.jackhuang.hmcl.addon.AddonUpdate;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonCheckUpdatesTask extends Task<AddonCheckUpdatesTask.Result> {
    private final List<Task<Pair<AddonUpdate, @Nullable AddonUpdate>>> dependents;

    public AddonCheckUpdatesTask(DownloadProvider downloadProvider, String gameVersion, Collection<? extends LocalAddonFile> addons) {
        dependents = addons.stream().map(addon ->
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
                }).setName(addon.getFileName()).setSignificance(TaskSignificance.MAJOR).withCounter("update.checking")
        ).toList();

        setStage("update.checking");
        getProperties().put("total", dependents.size());
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() {
        notifyPropertiesChanged();
    }

    @Override
    public Collection<? extends Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        List<AddonUpdate> commonUpdates = new ArrayList<>(), releaseUpdates = new ArrayList<>();
        dependents.stream().map(Task::getResult).filter(Objects::nonNull)
                .forEachOrdered(pair -> {
                    commonUpdates.add(pair.key());
                    if (pair.value() != null) releaseUpdates.add(pair.value());
                });
        setResult(new Result(List.copyOf(commonUpdates), List.copyOf(releaseUpdates)));
    }

    public record Result(List<AddonUpdate> commonUpdates, List<AddonUpdate> releaseUpdates) {
    }
}
