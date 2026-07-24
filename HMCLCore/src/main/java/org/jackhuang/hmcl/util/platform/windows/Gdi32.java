/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Provides mappings for the GDI functions used by HMCL.
///
/// @author Glavo
@NotNullByDefault
public interface Gdi32 extends StdCallLibrary {

    /// The loaded GDI library, or `null` when JNA is unavailable or the current platform is not Windows.
    @Nullable Gdi32 INSTANCE = NativeUtils.USE_JNA && com.sun.jna.Platform.isWindows()
            ? NativeUtils.load("gdi32", Gdi32.class)
            : null;

    /// The [GetDeviceCaps](https://learn.microsoft.com/windows/win32/api/wingdi/nf-wingdi-getdevicecaps)
    /// index for the current vertical refresh rate, in hertz.
    int VREFRESH = 116;

    /// Retrieves device-specific information for the supplied device context.
    ///
    /// @param hdc the device context to query
    /// @param index the capability index to retrieve
    /// @return the value of the requested capability
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/wingdi/nf-wingdi-getdevicecaps">GetDeviceCaps function</a>
    int GetDeviceCaps(Pointer hdc, int index);
}
