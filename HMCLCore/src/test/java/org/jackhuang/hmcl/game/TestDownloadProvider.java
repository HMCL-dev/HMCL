// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
