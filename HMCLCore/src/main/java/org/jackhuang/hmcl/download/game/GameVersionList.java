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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 *
 * @author huangyuhui
 */
public final class GameVersionList extends VersionList<GameRemoteVersion> {
    private final DownloadProvider downloadProvider;

    public GameVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    @Override
    protected Collection<GameRemoteVersion> getVersionsImpl(String gameVersion) {
        return versions.values();
    }

    @Override
    public CompletableFuture<?> refreshAsync() {
        CompletableFuture<GameRemoteVersions> primaryFuture = HttpRequest.GET(downloadProvider.getVersionListURL())
                .getJsonAsync(GameRemoteVersions.class);

        CompletableFuture<GameRemoteVersions> uvmcFuture = HttpRequest.GET(downloadProvider.getUvmcListURL())
                .getJsonAsync(GameRemoteVersions.class);

        return CompletableFuture.allOf(primaryFuture, uvmcFuture)
                .thenAcceptAsync(ignored -> {
                    lock.writeLock().lock();
                    try {
                        versions.clear();

                        Stream.of(primaryFuture.join(), uvmcFuture.join())
                                .flatMap(gameRemoteVersions -> gameRemoteVersions.getVersions().stream())
                                .forEach(remoteVersion -> versions.put(
                                        remoteVersion.getGameVersion(),
                                        new GameRemoteVersion(
                                                remoteVersion.getGameVersion(),
                                                remoteVersion.getGameVersion(),
                                                Collections.singletonList(remoteVersion.getUrl()),
                                                remoteVersion.getType(),
                                                remoteVersion.getReleaseTime()
                                        )
                                ));
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }
}
