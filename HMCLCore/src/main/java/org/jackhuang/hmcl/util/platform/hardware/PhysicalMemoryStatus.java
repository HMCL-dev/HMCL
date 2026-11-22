// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jetbrains.annotations.NotNullByDefault;

/// A snapshot of physical memory sizes, in bytes.
///
/// @param total the total physical memory size
/// @param available the available physical memory size, or a negative value if unknown
@NotNullByDefault
public record PhysicalMemoryStatus(long total, long available) {

    /// Returns total minus available memory, in bytes, or zero if available memory is unknown.
    public long getUsed() {
        return hasAvailable() ? total - available : 0;
    }

    /// Returns whether the available memory size is non-negative.
    public boolean hasAvailable() {
        return available >= 0;
    }

    /// An unknown memory status with zero total memory and an available memory size of `-1`.
    public static final PhysicalMemoryStatus INVALID = new PhysicalMemoryStatus(0, -1);
}
