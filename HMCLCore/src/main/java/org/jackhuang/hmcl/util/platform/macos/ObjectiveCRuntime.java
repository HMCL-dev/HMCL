// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
