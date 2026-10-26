// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;

/// Preferred source selection for download providers.
@NotNullByDefault
public enum DownloadSource {
    /// Automatically chooses the best source for the current environment.
    DEFAULT,

    /// Prefers official download sources.
    OFFICIAL,

    /// Prefers mirror download sources.
    MIRROR,
}
