// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.update;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

@NotNullByDefault
public record AddonUpdates(@Unmodifiable List<AddonUpdate> commonUpdates, @Unmodifiable List<AddonUpdate> releaseUpdates) {

    public AddonUpdates {
        commonUpdates = List.copyOf(commonUpdates);
        releaseUpdates = List.copyOf(releaseUpdates);
    }
}
