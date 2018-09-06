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

import org.jackhuang.hmcl.download.game.LibraryDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;

public final class HMCLLibraryDownloadTask extends LibraryDownloadTask {

    private boolean cached = false;

    public HMCLLibraryDownloadTask(HMCLDependencyManager dependencyManager, File file, Library library) {
        super(dependencyManager, file, library);
    }

    @Override
    public void preExecute() throws Exception {
        Optional<Path> libPath = HMCLLocalRepository.REPOSITORY.getLibrary(library);
        if (libPath.isPresent()) {
            try {
                FileUtils.copyFile(libPath.get().toFile(), jar);
                cached = true;
                return;
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to copy file from cache", e);
                // We cannot copy cached file to current location
                // so we try to download a new one.
            }
        }

        super.preExecute();
    }

    @Override
    public Collection<? extends Task> getDependents() {
        if (cached) return Collections.emptyList();
        else return super.getDependents();
    }

    @Override
    public void execute() throws Exception {
        if (cached) return;
        super.execute();
    }

    @Override
    public void postExecute() throws Exception {
        super.postExecute();

        if (!cached)
            HMCLLocalRepository.REPOSITORY.cacheLibrary(library, jar.toPath(), xz);
    }
}
