// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Builds game instances and applies HMCL-specific post-installation settings.
@NotNullByDefault
public class HMCLGameBuilder extends DefaultGameBuilder {
    /// Creates a builder around an HMCL target already reserved by its dependency manager.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param instanceId        the id of the reserved instance
    /// @param updateTarget      the exact update target retained by the draft, or `null` for install
    /// @param draft             the open draft containing `initialManifest`
    /// @param initialManifest   the empty target manifest retained by `draft`
    HMCLGameBuilder(
            HMCLDependencyManager dependencyManager,
            GameInstanceID instanceId,
            @Nullable HMCLGameInstance updateTarget,
            DefaultGameRepositoryDraft draft,
            GameInstanceManifest initialManifest) {
        super(dependencyManager, instanceId, updateTarget, draft, initialManifest);
    }

    /// {@inheritDoc}
    ///
    /// When isolation was enabled, persists the corresponding HMCL instance setting so subsequent
    /// launches use the instance root. A read-only instance retains its existing setting.
    @Override
    protected void onInstanceCommitted(DefaultGameInstance instance) {
        if (isolationEnabled) {
            ((HMCLGameInstance) instance).enableIsolation();
        }
    }
}
