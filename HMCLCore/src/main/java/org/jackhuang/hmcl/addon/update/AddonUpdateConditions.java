// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Predicate;

///
/// @param restrictions conditions that the remote version must satisfy to become a valid target
@NotNullByDefault
public record AddonUpdateConditions(@Unmodifiable List<Predicate<RemoteAddon.Version>> restrictions) {
    public AddonUpdateConditions {
        restrictions = List.copyOf(restrictions);
    }

    @SafeVarargs
    public AddonUpdateConditions(Predicate<RemoteAddon.Version>... restrictions) {
        this(List.of(restrictions));
    }
}
