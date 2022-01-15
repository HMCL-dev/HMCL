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

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.task.Task;

import java.time.Instant;
import java.util.List;

public class FabricAPIRemoteVersion extends RemoteVersion {
    private final String fullVersion;
    private final RemoteMod.Version version;

    /**
     * Constructor.
     *
     * @param gameVersion the Minecraft version that this remote version suits.
     * @param selfVersion the version string of the remote version.
     * @param urls        the installer or universal jar original URL.
     */
    FabricAPIRemoteVersion(String gameVersion, String selfVersion, String fullVersion, Instant datePublished, RemoteMod.Version version, List<String> urls) {
        super(LibraryAnalyzer.LibraryType.FABRIC_API.getPatchId(), gameVersion, selfVersion, datePublished, urls);

        this.fullVersion = fullVersion;
        this.version = version;
    }

    @Override
    public String getFullVersion() {
        return fullVersion;
    }

    public RemoteMod.Version getVersion() {
        return version;
    }

    @Override
    public Task<Version> getInstallTask(DefaultDependencyManager dependencyManager, Version baseVersion) {
        return new FabricAPIInstallTask(dependencyManager, baseVersion, this);
    }

    @Override
    public int compareTo(RemoteVersion o) {
        if (!(o instanceof FabricAPIRemoteVersion)) return 0;
        return -this.getReleaseDate().compareTo(o.getReleaseDate());
    }
}
