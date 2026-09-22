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

import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.ComponentVersionList;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

public abstract class FabricLikeVersionList<T extends ComponentRemoteVersion> extends ComponentVersionList<T> {
    private final DownloadProvider downloadProvider;

    public FabricLikeVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public Task<?> refreshAsync() {
        return Task.runAsync(() -> {
            List<String> gameVersions = getGameVersions(getGameMetaURL());
            List<String> loaderVersions = getGameVersions(getLoaderMetaURL());

            lock.writeLock().lock();

            try {
                for (String gameVersion : gameVersions)
                    for (String loaderVersion : loaderVersions) {
                        gameVersion = normalizeVersion(gameVersion);
                        versions.put(gameVersion, createRemoteVersion(gameVersion, loaderVersion,
                                Collections.singletonList(getLaunchMetaUrl(gameVersion, loaderVersion))));
                    }
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    private List<String> getGameVersions(String metaUrl) throws IOException {
        String json = NetworkUtils.doGet(downloadProvider.injectURLWithCandidates(metaUrl));
        return JsonUtils.GSON.fromJson(json, listTypeOf(GameVersion.class))
                .stream().map(GameVersion::version).collect(Collectors.toList());
    }

    protected abstract String getLoaderMetaURL();

    protected abstract String getGameMetaURL();

    protected abstract String getLaunchMetaUrl(String gameVersion, String loaderVersion);

    protected abstract T createRemoteVersion(String gameVersion, String loaderVersion, List<String> urls);

    protected String normalizeVersion(String version) {
        return version;
    }

    @JsonSerializable
    private record GameVersion(String version, String maven, boolean stable) {
    }
}
