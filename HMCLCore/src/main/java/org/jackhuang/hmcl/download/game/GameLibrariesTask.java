/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * This task is to download game libraries.
 * This task should be executed last(especially after game downloading, Forge, LiteLoader and OptiFine install task).
 *
 * @author huangyuhui
 */
public final class GameLibrariesTask extends Task {

    private final AbstractDependencyManager dependencyManager;
    private final Version version;
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version the <b>resolved</b> version
     */
    public GameLibrariesTask(AbstractDependencyManager dependencyManager, Version version) {
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
                dependencies.add(new LibraryDownloadTask(dependencyManager, file, library));
        });
    }

}
