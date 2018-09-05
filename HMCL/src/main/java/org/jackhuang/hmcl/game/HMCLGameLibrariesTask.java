/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class HMCLGameLibrariesTask extends Task {

    private final HMCLDependencyManager dependencyManager;
    private final Version version;
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public HMCLGameLibrariesTask(HMCLDependencyManager dependencyManager, Version version) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public List<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() {
        version.getLibraries().stream().filter(Library::appliesToCurrentEnvironment).forEach(library -> {
            File file = dependencyManager.getGameRepository().getLibraryFile(version, library);
            if (!file.exists())
                dependencies.add(new HMCLLibraryDownloadTask(dependencyManager, file, library));
            else
                HMCLLocalRepository.REPOSITORY.tryCacheLibrary(library, file.toPath());
        });
    }

}
