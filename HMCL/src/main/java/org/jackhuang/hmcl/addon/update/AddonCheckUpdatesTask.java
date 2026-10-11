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
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.ui.instances.AddonUpdatesPage;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonCheckUpdatesTask extends Task<AddonUpdates> {

    public static TaskExecutorDialogPane openAddonsCheckUpdateDialog(HMCLGameInstance instance, Collection<? extends LocalAddonFile> addons, Path addonsDirectory) {
        return Controllers.taskDialog(
                Task.composeAsync(() -> {
                    GameVersionNumber version = instance.getVersion();
                    if (version != GameVersionNumber.unknown()) {
                        return new AddonCheckUpdatesTask(DownloadProviders.getDownloadProvider(), version.toString(), addons);
                    } else {
                        LOG.warning("Failed to check for updates, due to unable to get instance game version");
                        return null;
                    }
                }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception != null || result == null) {
                        Controllers.dialog(I18n.i18n("addon.check_update.failed_check"), I18n.i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                    } else if (result.commonUpdates().isEmpty()) {
                        Controllers.dialog(I18n.i18n("addon.check_update.empty"));
                    } else {
                        Controllers.navigateForward(new AddonUpdatesPage(DownloadProviders.getDownloadProvider(), addonsDirectory, result));
                    }
                }).withStagesHints("update.checking"),
                I18n.i18n("addon.check_update"), TaskCancellationAction.NORMAL);
    }

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
        setResult(new AddonUpdates(commonUpdates, releaseUpdates));
    }
}
