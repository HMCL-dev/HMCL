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
package org.jackhuang.hmcl.util.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import org.jackhuang.hmcl.util.platform.NativeUtils;

/// @see <a href="https://developer.apple.com/documentation/objectivec/objective-c-runtime">Objective-C Runtime</a>
public interface ObjectiveCRuntime extends Library {

    /// The Objective-C runtime library instance.
    ObjectiveCRuntime INSTANCE = NativeUtils.USE_JNA && com.sun.jna.Platform.isMac()
            ? NativeUtils.load("objc", ObjectiveCRuntime.class)
            : null;

    /// @see <a href="https://developer.apple.com/documentation/objectivec/objc_getclass(_:)">objc_getClass function</a>
    Pointer objc_getClass(String name);

    /// @see <a href="https://developer.apple.com/documentation/objectivec/sel_registername(_:)">sel_registerName function</a>
    Pointer sel_registerName(String name);

    /// @see <a href="https://developer.apple.com/documentation/ObjectiveC/objc_msgSend">objc_msgSend function</a>
    Pointer objc_msgSend(Pointer receiver, Pointer selector);

    /// @see <a href="https://developer.apple.com/documentation/ObjectiveC/objc_msgSend">objc_msgSend function</a>
    Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer arg);

    /// @see <a href="https://developer.apple.com/documentation/ObjectiveC/objc_msgSend">objc_msgSend function</a>
    Pointer objc_msgSend(Pointer receiver, Pointer selector, String arg);
}
