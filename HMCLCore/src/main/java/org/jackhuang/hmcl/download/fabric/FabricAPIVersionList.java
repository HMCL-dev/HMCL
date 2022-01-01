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

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.util.Lang;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.Lang.wrap;

public class FabricAPIVersionList extends VersionList<FabricAPIRemoteVersion> {

    private final DownloadProvider downloadProvider;

    public FabricAPIVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public CompletableFuture<?> refreshAsync() {
        return CompletableFuture.runAsync(wrap(() -> {
            for (RemoteMod.Version modVersion : Lang.toIterable(ModrinthRemoteModRepository.INSTANCE.getRemoteVersionsById("P7dR8mSH"))) {
                for (String gameVersion : modVersion.getGameVersions()) {
                    versions.put(gameVersion, new FabricAPIRemoteVersion(gameVersion, modVersion.getVersion(), modVersion.getName(), modVersion.getDatePublished(), modVersion,
                            Collections.singletonList(modVersion.getFile().getUrl())));
                }
            }
        }));
    }
}
