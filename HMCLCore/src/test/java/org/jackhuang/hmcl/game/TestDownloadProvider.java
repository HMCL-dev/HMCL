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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DefaultCacheRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNullByDefault;

/// Supplies an isolated cache for game repository and dependency-manager tests.
@NotNullByDefault
final class TestDownloadProvider extends DownloadProvider {

    /// Cache supplied by the test that created this provider.
    private final DefaultCacheRepository cacheRepository;

    /// Creates a provider backed by the supplied cache.
    ///
    /// @param cacheRepository the isolated download cache
    TestDownloadProvider(DefaultCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    /// Returns the cache supplied when this provider was created.
    @Override
    public DefaultCacheRepository getCacheRepository() {
        return cacheRepository;
    }
}
