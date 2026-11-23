// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// A task name with a stable text representation for logs and display.
@NotNullByDefault
@FunctionalInterface
public interface TaskName {
    /// Returns the text to use in diagnostics or when no specialized display is available.
    String text();

    /// A literal task name.
    ///
    /// @param text the name as it should appear
    @NotNullByDefault
    record Simple(String text) implements TaskName {
        /// Creates a name from the supplied text.
        public Simple {
            Objects.requireNonNull(text);
        }
    }

    /// Names a task by the call site where it was created.
    ///
    /// @param frame the captured creation site
    @NotNullByDefault
    record CallSite(StackTraceElement frame) implements TaskName {
        /// Creates a name for the supplied frame.
        public CallSite {
            Objects.requireNonNull(frame);
        }

        /// Returns the class and method, followed by the source location when a file name is available.
        @Override
        public String text() {
            String method = frame.getClassName() + '.' + frame.getMethodName();
            @Nullable String fileName = frame.getFileName();
            return fileName == null ? method : method + '(' + fileName + ':' + frame.getLineNumber() + ')';
        }
    }
}
