// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import org.jetbrains.annotations.NotNullByDefault;

/// Controls how the launcher window is shown while the selected background is loading.
@NotNullByDefault
public enum BackgroundLoadPolicy {
    /// Loads the selected background before the launcher background property is first exposed to the UI.
    WAIT_FOR_BACKGROUND,

    /// Shows the fallback background immediately, then replaces it when the selected background loads.
    SHOW_FALLBACK_WHILE_LOADING
}
