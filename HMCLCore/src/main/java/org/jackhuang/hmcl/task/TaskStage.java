// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A stage declared by one task in an execution. Tasks declaring the same identifier
/// create distinct stages. A child without a stage declaration inherits its parent's stage.
@NotNullByDefault
public final class TaskStage {
    /// The task whose outcome determines the outcome of this stage.
    private final Task<?> task;

    /// The identifier supplied by the declaring task.
    private final String id;

    /// The enclosing stage, or `null` when there is none.
    private final @Nullable TaskStage parent;

    /// Identifies a stage declared by `task` within its parent's execution.
    TaskStage(Task<?> task, String id, @Nullable TaskStage parent) {
        this.task = task;
        this.id = id;
        this.parent = parent;
    }

    /// Returns the task that declared this stage.
    public Task<?> task() {
        return task;
    }

    /// Returns the identifier supplied by the declaring task.
    public String id() {
        return id;
    }

    /// Returns the enclosing stage, or `null` if the task was dispatched outside any stage.
    public @Nullable TaskStage parent() {
        return parent;
    }
}
