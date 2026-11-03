// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.jetbrains.annotations.NotNullByDefault;

/// Device identity with a default display name, optionally extended by platform-specific metadata.
/// Implementations must be immutable because system information caches and shares them.
@NotNullByDefault
public interface DeviceInfo {

    /// Returns the default hardware model name for display and logging, independent of the UI locale.
    String name();

    /// Device identity containing only a default hardware model name.
    ///
    /// @param name the default display name
    @NotNullByDefault
    record Simple(String name) implements DeviceInfo {
    }
}
