// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;

/// Selects the source used to choose the launcher Monet theme color seed.
@NotNullByDefault
public enum ThemeColorType {
    /// Uses the built-in launcher default theme color.
    DEFAULT,

    /// Follows the accent color provided by the operating system.
    SYSTEM,

    /// Uses the custom launcher theme color selected by the user.
    CUSTOM,

    /// Extracts the launcher theme color from the current background when possible.
    BACKGROUND
}
