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
package org.jackhuang.hmcl.download.modrinth;

import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.Task;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class ModrinthComponentRemoteVersion extends ComponentRemoteVersion {
    private final String fullVersion;
    private final RemoteAddon.Version version;

    /**
     * Constructor.
     *
     * @param gameVersion the Minecraft version that this remote version suits.
     * @param selfVersion the version string of the remote version.
     * @param urls        the installer or universal jar original URL.
     */
    ModrinthComponentRemoteVersion(GameComponentType type, String gameVersion, String selfVersion, String fullVersion, Instant datePublished, RemoteAddon.Version version, List<String> urls) {
        super(type, gameVersion, selfVersion, datePublished, urls);

        this.fullVersion = fullVersion;
        this.version = version;
    }

    @Override
    public String getFullVersion() {
        return fullVersion;
    }

    public RemoteAddon.Version getVersion() {
        return version;
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest baseManifest,
            Path modsDirectory) {
        return new ModrinthComponentInstallTask(this, modsDirectory);
    }

    @Override
    public int compareTo(ComponentRemoteVersion o) {
        if (!(o instanceof ModrinthComponentRemoteVersion)) return 0;
        return -this.getReleaseDate().compareTo(o.getReleaseDate());
    }
}
