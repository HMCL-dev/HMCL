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

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MultipleSourceVersionList extends VersionList<RemoteVersion> {

    private final List<VersionList<?>> backends;

    MultipleSourceVersionList(List<VersionList<?>> backends) {
        this.backends = backends;

        assert (backends.size() >= 1);
    }

    @Override
    public boolean hasType() {
        boolean hasType = backends.get(0).hasType();
        assert (backends.stream().allMatch(versionList -> versionList.hasType() == hasType));
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

    @Override
    public CompletableFuture<?> refreshAsync(String gameVersion) {
        versions.clear(gameVersion);
        return CompletableFuture.anyOf(backends.stream()
                .map(versionList -> versionList.refreshAsync(gameVersion)
                .thenRunAsync(() -> {
                    lock.writeLock().lock();

                    try {
                        versions.putAll(gameVersion, versionList.getVersions(gameVersion));
                    } finally {
                        lock.writeLock().unlock();
                    }
                }))
                .toArray(CompletableFuture[]::new));
    }
}
