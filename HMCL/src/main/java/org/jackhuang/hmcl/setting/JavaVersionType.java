// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;

/// Selects how HMCL resolves the Java runtime used to launch a game.
///
/// Additional payload for these modes is stored in `GameSettings`:
/// - [VERSION] uses `customJavaVersion` as the user-entered Java version selector.
/// - [DETECTED] uses `detectedJava` as the detected runtime reference.
/// - [CUSTOM] uses `customJavaPath` as the executable path selected by the user.
///
/// @author Glavo
@NotNullByDefault
public enum JavaVersionType {
    /// Automatically selects a suitable Java runtime for the target game version.
    AUTO,

    /// Selects an installed Java runtime by major version.
    VERSION,

    /// Selects one runtime from the Java runtimes detected by HMCL.
    DETECTED,

    /// Uses a user-provided Java executable path.
    CUSTOM
}
