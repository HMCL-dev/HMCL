// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.PointerType;
import com.sun.jna.win32.StdCallLibrary;
import org.jackhuang.hmcl.util.platform.NativeUtils;

/// @author Glavo
public interface Dwmapi extends StdCallLibrary {
    Dwmapi INSTANCE = NativeUtils.USE_JNA && com.sun.jna.Platform.isWindows()
            ? NativeUtils.load("dwmapi", Dwmapi.class)
            : null;

    /// @see <a href="https://learn.microsoft.com/windows/win32/api/dwmapi/nf-dwmapi-dwmsetwindowattribute">DwmSetWindowAttribute function</a>
    int DwmSetWindowAttribute(WinTypes.HANDLE hwnd, int dwAttribute, PointerType pvAttribute, int cbAttribute);
}
