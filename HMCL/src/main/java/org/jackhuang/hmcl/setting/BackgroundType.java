// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;

/// Selects the source used to render the launcher background.
@NotNullByDefault
public enum BackgroundType {
    /// Uses the selected theme background when available, otherwise a built-in fallback wallpaper.
    DEFAULT,

    /// Uses one built-in wallpaper selected by launcher settings.
    BUILTIN,

    /// Uses a user-selected local image file or a random image from a selected directory.
    CUSTOM,

    /// Uses an image loaded from a user-provided URL.
    NETWORK,

    /// Uses the configured paint value as a flat background.
    PAINT,

    /// Uses the current theme color scheme surface container as a flat background.
    THEME_COLOR;
}
