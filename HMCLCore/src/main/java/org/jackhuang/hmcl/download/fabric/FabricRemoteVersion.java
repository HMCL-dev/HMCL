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

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.List;

@NotNullByDefault
public final class FabricRemoteVersion extends ComponentRemoteVersion {
    public static final WebURL LOADER_META_URL = WebURL.parse("https://meta.fabricmc.net/v2/versions/loader");
    public static final WebURL GAME_META_URL = WebURL.parse("https://meta.fabricmc.net/v2/versions/game");

    /**
     * Constructor.
     *
     * @param gameVersion the Minecraft version that this remote version suits.
     * @param selfVersion the version string of the remote version.
     * @param urls        the installer or universal jar original URL.
     */
    public FabricRemoteVersion(GameVersionNumber gameVersion, String selfVersion, List<String> urls) {
        super(GameComponentType.FABRIC, gameVersion, selfVersion, null, Type.UNCATEGORIZED, urls);
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new FabricInstallTask(dependencyManager, baseManifest, this);
    }
}
