// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DefaultCacheRepository;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.function.Consumer;

/// Provides HMCL-specific game builders for an HMCL game repository.
@NotNullByDefault
public class HMCLDependencyManager extends DefaultDependencyManager {
    /// Creates a dependency manager for the given repository, download provider, and cache.
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

    /// Reserves and installs a new instance when the returned task runs.
    public Task<HMCLGameInstance> installGameAsync(GameInstanceID instanceId, Consumer<? super HMCLGameBuilder> configure) {
        return new Task<HMCLGameInstance>() {
            /// {@inheritDoc}
            @Override
            public HMCLGameInstance execute(TaskExecutionContext context) throws Exception {
                try (HMCLGameBuilder builder = newGameBuilder(instanceId)) {
                    configure.accept(builder);
                    return (HMCLGameInstance) builder.build(context);
                }
            }
        }.setName("Install instance " + instanceId).setSignificance(TaskSignificance.MODERATE);
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
