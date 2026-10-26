// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Provides mappings for the Shell API functions used by HMCL.
///
/// @author Glavo
@NotNullByDefault
public interface Shell32 extends StdCallLibrary {

    /// The loaded Shell32 library, or `null` when JNA is unavailable or the current platform is not Windows.
    @Nullable Shell32 INSTANCE = NativeUtils.USE_JNA && com.sun.jna.Platform.isWindows()
            ? NativeUtils.load("shell32", Shell32.class)
            : null;

    /// Specifies a unique application-defined Application User Model ID for the current process.
    ///
    /// @param appID the AppUserModelID to assign to the current process
    /// @return `S_OK` if the operation succeeds; otherwise a failure `HRESULT`
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/shobjidl_core/nf-shobjidl_core-setcurrentprocessexplicitappusermodelid">SetCurrentProcessExplicitAppUserModelID function</a>
    int SetCurrentProcessExplicitAppUserModelID(WString appID);

    /// Retrieves an [`IPropertyStore`] interface for the given window.
    ///
    /// @param hwnd the window whose property store is requested
    /// @param riid the interface identifier; typically [`WinTypes.GUID#IID_IPropertyStore`]
    /// @param ppv receives the requested interface pointer on success
    /// @return `S_OK` if the operation succeeds; otherwise a failure `HRESULT`
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/shobjidl_core/nf-shobjidl_core-shgetpropertystoreforwindow">SHGetPropertyStoreForWindow function</a>
    int SHGetPropertyStoreForWindow(WinTypes.HANDLE hwnd, WinTypes.GUID riid, PointerByReference ppv);
}
