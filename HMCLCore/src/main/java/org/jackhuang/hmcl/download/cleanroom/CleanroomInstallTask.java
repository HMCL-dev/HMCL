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
package org.jackhuang.hmcl.download.cleanroom;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

public final class CleanroomInstallTask extends Task<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private Path installer;
    private final CleanroomRemoteVersion remote;
    private FileDownloadTask dependent;
    private Task<Version> task;

    public CleanroomInstallTask(DefaultDependencyManager dependencyManager, Version version, CleanroomRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remote = remoteVersion;
        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        installer = Files.createTempFile("cleanroom-installer", ".jar");

        dependent = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLsWithCandidates(remote.getUrls()),
                installer, null);
        dependent.setCacheRepository(dependencyManager.getCacheRepository());
        dependent.setCaching(true);
        dependent.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        Files.deleteIfExists(installer);
        setResult(task.getResult());
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(dependent);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return Collections.singleton(task);
    }

    @Override
    public void execute() throws IOException, VersionMismatchException, UnsupportedInstallationException {
        task = new ForgeNewInstallTask(dependencyManager, version, remote.getSelfVersion(), installer).thenApplyAsync((version) -> version.setId(LibraryAnalyzer.LibraryType.CLEANROOM.getPatchId()));
    }
}
