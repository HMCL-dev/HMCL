// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Provides mappings for the user interface functions used by HMCL.
///
/// @author Glavo
@NotNullByDefault
public interface User32 extends StdCallLibrary {

    /// The loaded User32 library, or `null` when JNA is unavailable or the current platform is not Windows.
    @Nullable User32 INSTANCE = NativeUtils.USE_JNA && com.sun.jna.Platform.isWindows()
            ? NativeUtils.load("user32", User32.class)
            : null;

    /// Retrieves a device context for a window or for the entire screen.
    ///
    /// @param hWnd the target window, or `null` to retrieve the screen device context
    /// @return the device context, or `null` if the function fails
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-getdc">GetDC function</a>
    @Nullable Pointer GetDC(@Nullable Pointer hWnd);

    /// Releases a device context retrieved by {@link #GetDC(Pointer)}.
    ///
    /// @param hWnd the same window passed to {@link #GetDC(Pointer)}, or `null` for a screen device context
    /// @param hDC the device context to release
    /// @return `1` if the device context was released, or `0` if it was not released
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-releasedc">ReleaseDC function</a>
    int ReleaseDC(@Nullable Pointer hWnd, Pointer hDC);
}
