// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jetbrains.annotations.NotNullByDefault;

/// Configured page file policy, which may require a restart to take effect.
///
/// @param maximumSize the sum of configured maximum sizes in bytes, or -1 unless all files are manually sized
/// @param mode the aggregate sizing policy
@NotNullByDefault
public record PageFileConfiguration(long maximumSize, Mode mode) {

    /// Configuration could not be determined completely.
    public static final PageFileConfiguration UNKNOWN = new PageFileConfiguration(-1, Mode.UNKNOWN);

    /// Whether configured page files have explicit or system-managed sizes.
    @NotNullByDefault
    public enum Mode {
        /// Missing, unsupported, or invalid configuration.
        UNKNOWN,
        /// Every page file has an explicit maximum size.
        MANUAL,
        /// Every page file is managed by Windows.
        SYSTEM_MANAGED,
        /// Explicitly sized and system-managed page files coexist.
        MIXED
    }
}
