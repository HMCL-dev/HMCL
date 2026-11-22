// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import org.jetbrains.annotations.NotNullByDefault;

/// Controls whether a remote launcher background image may be stored in the local cache.
@NotNullByDefault
public enum NetworkBackgroundImageCachePolicy {
    /// Allows HMCL to cache the image file locally.
    ENABLED,

    /// Forces HMCL to load the image directly without storing it in the file cache.
    DISABLED
}
