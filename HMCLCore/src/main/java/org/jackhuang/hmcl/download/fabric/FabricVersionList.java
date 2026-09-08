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
package org.jackhuang.hmcl.download.fabric;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.fabriclike.FabricLikeVersionList;

import java.util.List;

public final class FabricVersionList extends FabricLikeVersionList<FabricRemoteVersion> {
    public FabricVersionList(DownloadProvider downloadProvider) {
        super(downloadProvider);
    }

    @Override
    protected String getLoaderMetaURL() {
        return "https://meta.fabricmc.net/v2/versions/loader";
    }

    @Override
    protected String getGameMetaURL() {
        return "https://meta.fabricmc.net/v2/versions/game";
    }

    @Override
    protected String getLaunchMetaUrl(String gameVersion, String loaderVersion) {
        return String.format("https://meta.fabricmc.net/v2/versions/loader/%s/%s", gameVersion, loaderVersion);
    }

    @Override
    protected FabricRemoteVersion createRemoteVersion(String gameVersion, String loaderVersion, List<String> urls) {
        return new FabricRemoteVersion(gameVersion, loaderVersion, urls);
    }
}
