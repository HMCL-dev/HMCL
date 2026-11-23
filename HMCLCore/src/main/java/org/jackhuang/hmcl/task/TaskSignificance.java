// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;

/// Controls execution logging and visibility in the task list.
@NotNullByDefault
public enum TaskSignificance {
    /// Logged and displayed as a task.
    MAJOR,
    /// Logged and hidden from the task list by default.
    MODERATE,
    /// Hidden from normal task logging and display.
    MINOR;

    /// Returns whether the executor should log task execution.
    public boolean shouldLog() {
        return this != MINOR;
    }

    /// Returns whether the task should be displayed as a separate item.
    public boolean shouldShow() {
        return this == MAJOR;
    }
}
