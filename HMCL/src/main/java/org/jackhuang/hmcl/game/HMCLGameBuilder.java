// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Builds game instances and applies HMCL-specific post-installation settings.
@NotNullByDefault
public class HMCLGameBuilder extends DefaultGameBuilder {
    /// Uses the instance reservation opened by the HMCL dependency manager.
    HMCLGameBuilder(
            HMCLDependencyManager dependencyManager,
            GameInstanceID instanceId,
            @Nullable HMCLGameInstance updateTarget,
            DefaultGameRepositoryDraft draft,
            GameInstanceManifest initialManifest) {
        super(dependencyManager, instanceId, updateTarget, draft, initialManifest);
    }

    /// Persists requested isolation for writable instances.
    @Override
    protected void onInstanceCommitted(DefaultGameInstance instance) {
        if (isolationEnabled) {
            ((HMCLGameInstance) instance).enableIsolation();
        }
    }
}
