// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DefaultCacheRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNullByDefault;

/// Provides HMCL-specific game builders for an HMCL game repository.
@NotNullByDefault
public class HMCLDependencyManager extends DefaultDependencyManager {
    /// Creates a dependency manager for a repository and download context.
    ///
    /// @param repository       the associated game repository
    /// @param downloadProvider the remote download provider
    /// @param cacheRepository  the artifact cache
    public HMCLDependencyManager(
            HMCLGameRepository repository,
            DownloadProvider downloadProvider,
            DefaultCacheRepository cacheRepository) {
        super(repository, downloadProvider, cacheRepository);
    }

    /// {@inheritDoc}
    @Override
    public HMCLGameRepository getGameRepository() {
        return (HMCLGameRepository) super.getGameRepository();
    }

    /// {@inheritDoc}
    @Override
    public HMCLGameBuilder newGameBuilder(GameInstanceID instanceId) {
        GameInstanceManifest initialManifest = new GameInstanceManifest(instanceId);
        DefaultGameRepositoryDraft draft = openGameBuilderDraft(initialManifest, null);
        return new HMCLGameBuilder(this, instanceId, null, draft, initialManifest);
    }

    /// {@inheritDoc}
    @Override
    public HMCLGameBuilder newGameBuilder(GameInstance instance) {
        validateGameInstance(instance);
        HMCLGameInstance updateTarget = (HMCLGameInstance) instance;

        GameInstanceManifest initialManifest = new GameInstanceManifest(updateTarget.getId());
        DefaultGameRepositoryDraft draft = openGameBuilderDraft(initialManifest, updateTarget);
        return new HMCLGameBuilder(
                this, updateTarget.getId(), updateTarget, draft, initialManifest);
    }
}
