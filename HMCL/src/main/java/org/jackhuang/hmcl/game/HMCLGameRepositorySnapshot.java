// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Collection;

/// HMCL repository snapshot, parallel to [HMCLGameInstance] in the instance hierarchy.
@NotNullByDefault
public class HMCLGameRepositorySnapshot extends DefaultGameRepositorySnapshot {
    /// Creates an empty unsealed HMCL snapshot.
    ///
    /// @param repository the owning repository
    /// @param layout     the HMCL layout for this snapshot
    public HMCLGameRepositorySnapshot(HMCLGameRepository repository, HMCLGameRepositoryLayout layout) {
        super(repository, layout);
    }

    @Override
    public HMCLGameRepository getRepository() {
        return (HMCLGameRepository) super.getRepository();
    }

    @Override
    public HMCLGameRepositoryLayout getLayout() {
        return (HMCLGameRepositoryLayout) super.getLayout();
    }

    @Override
    protected HMCLGameRepositorySnapshot newEmpty() {
        return new HMCLGameRepositorySnapshot(getRepository(), getLayout());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<HMCLGameInstance> getInstances() {
        return (Collection<HMCLGameInstance>) super.getInstances();
    }
}
