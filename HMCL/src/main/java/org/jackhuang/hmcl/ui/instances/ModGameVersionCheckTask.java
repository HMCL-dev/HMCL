/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModGameVersionCheck;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Checks a batch of local mod files against the game version of a target instance.
///
/// Structured like [AddonCheckUpdatesTask]: one parallel subtask per mod, progress reported through
/// `withCounter`, and a failing subtask does not affect the others because
/// [#isRelyingOnDependents()] returns `false`. Each mod is checked against every
/// [RemoteAddon.Source]; a source that throws is only logged, so the remaining source can still
/// produce a conclusion.
///
/// The result contains only entries whose [ModGameVersionCheck#needsAction()] is `true`. Mods that
/// already target the game version and mods the repositories cannot identify are dropped.
///
/// Note the cost: every mod requires a full-file hash - the CurseForge fingerprint reads the file
/// twice - plus up to two network requests. This is only suitable for an explicit user action and
/// must not be attached to a page loading path.
@NotNullByDefault
public final class ModGameVersionCheckTask extends Task<List<ModGameVersionCheck>> {

    /// One check subtask per mod; a result of `null` means the mod needs no action or cannot be judged.
    private final List<Task<@Nullable ModGameVersionCheck>> dependents;

    /// Progress stage identifier, also used as the i18n key of the progress dialog.
    static final String STAGE = "mods.check_game_version.checking";

    /// Creates the check task.
    ///
    /// @param downloadProvider  the download provider used when fetching remote build lists
    /// @param targetGameVersion the game version of the target instance; callers must ensure it is known
    /// @param mods              the local mod files to check
    public ModGameVersionCheckTask(DownloadProvider downloadProvider, String targetGameVersion,
                                   Collection<LocalModFile> mods) {
        Objects.requireNonNull(downloadProvider, "downloadProvider");
        Objects.requireNonNull(targetGameVersion, "targetGameVersion");

        this.dependents = mods.stream().map(mod ->
                Task.<@Nullable ModGameVersionCheck>supplyAsync(Schedulers.io(), () -> {
                    RemoteAddon.Source[] sources = RemoteAddon.Source.values();
                    List<@Nullable ModGameVersionCheck> results = new ArrayList<>(sources.length);

                    for (RemoteAddon.Source source : sources) {
                        try {
                            results.add(ModGameVersionCheck.check(mod, downloadProvider, targetGameVersion, source));
                        } catch (IOException e) {
                            LOG.warning(String.format(
                                    "Cannot check game version compatibility for mod %s on %s.",
                                    mod.getFileName(), source), e);
                        }
                    }

                    return ModGameVersionCheck.merge(results);
                }).setName(mod.getFileName()).setSignificance(TaskSignificance.MAJOR).withCounter(STAGE)
        ).toList();

        setStage(STAGE);
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
        setResult(dependents.stream()
                .map(Task::getResult)
                .filter(Objects::nonNull)
                .filter(ModGameVersionCheck::needsAction)
                .toList());
    }
}
