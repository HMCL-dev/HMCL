/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

    /// Retrieves the application-defined AppUserModelID for the current process.
    ///
    /// The caller must free the returned string with [`Ole32#CoTaskMemFree(com.sun.jna.Pointer)`]
    /// when it is no longer needed.
    ///
    /// @param ppszAppID receives a pointer to the process AppUserModelID string
    /// @return `S_OK` if the operation succeeds; otherwise a failure `HRESULT`
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/shobjidl_core/nf-shobjidl_core-getcurrentprocessexplicitappusermodelid">GetCurrentProcessExplicitAppUserModelID function</a>
    int GetCurrentProcessExplicitAppUserModelID(PointerByReference ppszAppID);

    /// Retrieves an [`IPropertyStore`] interface for the given window.
    ///
    /// @param hwnd the window whose property store is requested
    /// @param riid the interface identifier; typically [`WinTypes.GUID#IID_IPropertyStore`]
    /// @param ppv receives the requested interface pointer on success
    /// @return `S_OK` if the operation succeeds; otherwise a failure `HRESULT`
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/shobjidl_core/nf-shobjidl_core-shgetpropertystoreforwindow">SHGetPropertyStoreForWindow function</a>
    int SHGetPropertyStoreForWindow(WinTypes.HANDLE hwnd, WinTypes.GUID riid, PointerByReference ppv);
}
