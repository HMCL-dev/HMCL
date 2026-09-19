/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.legacyfabric;

import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.fabriclike.FabricLikeInstallTask;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;

public final class LegacyFabricInstallTask extends FabricLikeInstallTask {

    public LegacyFabricInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, ComponentRemoteVersion remoteVersion) {
        super(dependencyManager, manifest, remoteVersion);
    }

    @Override
    protected String getMavenRepositoryByGroup(String maven) {
        Artifact artifact = Artifact.fromDescriptor(maven);
        return switch (artifact.getGroup()) {
            case "net.fabricmc" -> "https://maven.fabricmc.net/";
            case "net.legacyfabric" -> "https://maven.legacyfabric.net/";
            default -> "https://maven.fabricmc.net/";
        };
    }

    protected GameComponentType getComponentType() {
        return GameComponentType.LEGACY_FABRIC;
    }
}
