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
package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.mod.LocalMod;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.task.Task;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ModUpdateTask extends Task<List<LocalMod.ModUpdate>> {

    private final String gameVersion;
    private final Collection<LocalMod> mods;
    private final Collection<Task<LocalMod.ModUpdate>> dependents;

    public ModUpdateTask(String gameVersion, Collection<LocalMod> mods) {
        this.gameVersion = gameVersion;
        this.mods = mods;

        dependents = mods.stream()
                .map(mod -> Task.supplyAsync(() -> {
                    return mod.checkUpdates(gameVersion, CurseForgeRemoteModRepository.MODS);
                }).setSignificance(TaskSignificance.MAJOR).withCounter("mods.check_updates"))
                .collect(Collectors.toList());

        setStage("mods.check_updates");
        getProperties().put("total", dependents.size());
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
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
                .filter(task -> task.getResult() != null)
                .map(Task::getResult)
                .collect(Collectors.toList()));
    }
}
