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
package org.jackhuang.hmcl.download;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class MultipleSourceVersionList extends VersionList<RemoteVersion> {

    private final VersionList<?>[] backends;

    MultipleSourceVersionList(VersionList<?>[] backends) {
        this.backends = backends;

        assert (backends.length >= 1);
    }

    @Override
    public boolean hasType() {
        boolean hasType = backends[0].hasType();
        assert (Arrays.stream(backends).allMatch(versionList -> versionList.hasType() == hasType));
        return hasType;
    }

    @Override
    public CompletableFuture<?> loadAsync() {
        throw new UnsupportedOperationException("MultipleSourceVersionList does not support loading the entire remote version list.");
    }

    @Override
    public CompletableFuture<?> refreshAsync() {
        throw new UnsupportedOperationException("MultipleSourceVersionList does not support loading the entire remote version list.");
    }

    private CompletableFuture<?> refreshAsync(String gameVersion, int sourceIndex) {
        VersionList<?> versionList = backends[sourceIndex];
        CompletableFuture<Void> future = versionList.refreshAsync(gameVersion)
                .thenRunAsync(() -> {
                    lock.writeLock().lock();

                    try {
                        versions.putAll(gameVersion, versionList.getVersions(gameVersion));
                    } finally {
                        lock.writeLock().unlock();
                    }
                });

        if (sourceIndex == backends.length - 1) {
            return future;
        } else {
            return future.<CompletableFuture<?>>handle((ignore, e) -> {
                if (e == null) {
                    return future;
                }

                LOG.log(Level.WARNING, "Failed to fetch versions list and try to fetch from other source", e);
                return refreshAsync(gameVersion, sourceIndex + 1);
            }).thenCompose(it -> it);
        }
    }

    @Override
    public CompletableFuture<?> refreshAsync(String gameVersion) {
        versions.clear(gameVersion);
        return refreshAsync(gameVersion, 0);
    }
}
