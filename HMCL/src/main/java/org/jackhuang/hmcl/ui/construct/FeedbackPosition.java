// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import org.jetbrains.annotations.NotNullByDefault;

/// Specifies the stacking order of interaction feedback relative to wrapped content.
@NotNullByDefault
public enum FeedbackPosition {
    /// Draws feedback above the content and intercepts pointer picking within its bounds.
    FRONT,
    /// Draws feedback below the content and receives pointer input in uncovered areas.
    BACK
}
