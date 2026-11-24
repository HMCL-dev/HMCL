// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.logging;

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;

/// Finds the caller of a logging method.
///
/// @author Glavo
final class CallerFinder {

    /// The stack walker used to find the caller of a logging method.
    ///
    /// Compared to `StackWalker.getInstance()`, this walker has a smaller estimated depth, so it incurs lower overhead.
    ///
    /// If the call stack depth in the implementation of `Logger` ever exceeds 3 frames, the configuration of this walker needs to be changed.
    private static final StackWalker WALKER = StackWalker.getInstance(Set.of(), 3);

    /// The package prefix of the logging utility classes.
    ///
    /// Used to skip frames in the call stack that belong to the logging utility classes.
    private static final String PACKAGE_PREFIX = CallerFinder.class.getPackageName() + ".";


    /// Returns the first stack frame that does not belong to the logging utility classes.
    ///
    /// The returned value is formatted as `className.methodName`. Frames whose class name starts
    /// with [#PACKAGE_PREFIX] are skipped, because they are internal to the logging implementation.
    ///
    /// @return the formatted caller location, or `null` if no non-logging frame is found
    ///         within the examined stack frames
    @Nullable
    static String getCaller() {
        return WALKER.walk(stream -> {
            Iterator<StackWalker.StackFrame> it = stream.iterator();
            while (it.hasNext()) {
                StackWalker.StackFrame frame = it.next();
                if (!frame.getClassName().startsWith(PACKAGE_PREFIX)) {
                    return frame.getClassName() + "." + frame.getMethodName();
                }
            }
            return null;
        });
    }

    private CallerFinder() {
    }
}
