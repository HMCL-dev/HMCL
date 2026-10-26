// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import javafx.scene.layout.Background;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A launcher background and the opacity applied by its rendering node.
///
/// @param background the JavaFX background rendered by the launcher
/// @param opacity     the node opacity in the range `[0, 1]`
@NotNullByDefault
public record LauncherBackground(Background background, double opacity) {
    /// Creates a launcher background value.
    public LauncherBackground {
        Objects.requireNonNull(background);
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("Launcher background opacity must be between 0 and 1: " + opacity);
        }
    }

}
