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

import org.jackhuang.hmcl.mod.LocalAddonFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.task.Task;

import java.util.*;
import java.util.stream.Collectors;

public class CheckUpdatesTask<T extends LocalAddonFile> extends Task<List<LocalAddonFile.ModUpdate>> {
    private final Collection<Collection<Task<LocalAddonFile.ModUpdate>>> dependents;

    public CheckUpdatesTask(String gameVersion, Collection<T> mods, RemoteModRepository.Type repoType) {
        Map<String, RemoteModRepository> repos = new LinkedHashMap<>(2);
        for (RemoteMod.Type modType : RemoteMod.Type.values()) {
            RemoteModRepository repo = modType.getRepoForType(repoType);
            if (repo != null) {
                repos.put(modType.name(), repo);
            }
        }
        dependents = mods.stream()
                .map(mod ->
                        repos.entrySet().stream()
                                .map(entry ->
                                        Task.supplyAsync(() -> mod.checkUpdates(gameVersion, entry.getValue()))
                                                .setSignificance(TaskSignificance.MAJOR)
                                                .setName(String.format("%s (%s)", mod.getFileName(), entry.getKey())).withCounter("update.checking")
                                ).collect(Collectors.toList())
                )
                .collect(Collectors.toList());

        setStage("update.checking");
        getProperties().put("total", dependents.size() * repos.size());
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
        return dependents.stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        setResult(dependents.stream()
                .map(tasks -> tasks.stream()
                        .filter(task -> task.getResult() != null)
                        .map(Task::getResult)
                        .filter(modUpdate -> !modUpdate.candidates().isEmpty())
                        .max(Comparator.comparing((LocalAddonFile.ModUpdate modUpdate) -> modUpdate.candidates().get(0).getDatePublished()))
                        .orElse(null)
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }
}
