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
package org.jackhuang.hmcl.download.fabriclike;

import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class FabricAPILikeInstallTask extends Task<GameInstancePatch> {
    private final FabricAPILikeRemoteVersion remoteVersion;
    private final Path modsDirectory;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    public FabricAPILikeInstallTask(
            FabricAPILikeRemoteVersion remoteVersion,
            Path modsDirectory) {
        this.remoteVersion = remoteVersion;
        this.modsDirectory = modsDirectory;
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
    public void execute() throws IOException {
        dependencies.add(new FileDownloadTask(
                remoteVersion.getVersion().file().url(),
                modsDirectory.resolve(remoteVersion.getVersion().file().filename()),
                remoteVersion.getVersion().file().getIntegrityCheck())
        );
    }

    public FabricAPILikeRemoteVersion getRemoteVersion() {
        return remoteVersion;
    }
}
