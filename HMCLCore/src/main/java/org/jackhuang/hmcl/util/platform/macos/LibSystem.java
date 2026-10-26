// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.macos;

import com.sun.jna.Library;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
public interface LibSystem extends Library {
    /// The Objective-C runtime library instance.
    @Nullable LibSystem INSTANCE = NativeUtils.USE_JNA && com.sun.jna.Platform.isMac()
            ? NativeUtils.load("System", LibSystem.class)
            : null;

    int geteuid();
}
