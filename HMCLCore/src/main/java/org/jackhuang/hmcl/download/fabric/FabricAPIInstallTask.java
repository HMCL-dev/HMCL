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
package org.jackhuang.hmcl.download.fabric;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * <b>Note</b>: Fabric should be installed first.
 *
 * @author huangyuhui
 */
public final class FabricAPIInstallTask extends Task<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final FabricAPIRemoteVersion remote;
    private final List<Task<?>> dependencies = new LinkedList<>();

    public FabricAPIInstallTask(DefaultDependencyManager dependencyManager, Version version, FabricAPIRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remote = remoteVersion;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    @Override
    public void execute() {
        List<Library> libraries = new ArrayList<>();
        libraries.add(new Library(new Artifact("net", "fabricmc", "fabric-api"), null,
                new LibrariesDownloadInfo(new LibraryDownloadInfo(
                        "net/fabricmc/fabric-api/" + remote.getFullVersion() + "/fabric-api-" + remote.getFullVersion() + ".jar",
                        remote.getUrls().get(0)))));

        setResult(new Version(LibraryAnalyzer.LibraryType.FABRIC_API.getPatchId(), remote.getSelfVersion(), 31000, new Arguments(), null, libraries));
        dependencies.add(dependencyManager.checkLibraryCompletionAsync(getResult(), true));
    }
}
