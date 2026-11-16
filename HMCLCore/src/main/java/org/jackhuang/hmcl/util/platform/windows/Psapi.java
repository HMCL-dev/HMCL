// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Windows process status APIs.
///
/// @see <a href="https://learn.microsoft.com/windows/win32/psapi/process-status-helper">Process Status API</a>
@NotNullByDefault
public interface Psapi extends StdCallLibrary {

    /// The native binding, or null when unavailable or disabled.
    @Nullable Psapi INSTANCE = NativeUtils.USE_JNA && Platform.isWindows()
            ? NativeUtils.load("psapi", Psapi.class) : null;

    /// Enumerates active page files; returns false on failure.
    ///
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/psapi/nf-psapi-enumpagefilesw">EnumPageFilesW function</a>
    boolean EnumPageFilesW(PageFileCallback callback, @Nullable Pointer context);

    /// Receives each page file's current allocation and usage in pages.
    ///
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/psapi/nc-psapi-penum_page_file_callbackw">PENUM_PAGE_FILE_CALLBACKW callback function</a>
    @NotNullByDefault
    interface PageFileCallback extends StdCallCallback {

        /// Returns true to continue enumeration, or false to stop.
        ///
        /// @see <a href="https://learn.microsoft.com/windows/win32/api/psapi/nc-psapi-penum_page_file_callbackw">PENUM_PAGE_FILE_CALLBACKW callback function</a>
        boolean invoke(@Nullable Pointer context, WinTypes.ENUM_PAGE_FILE_INFORMATION information, WString fileName);
    }
}
