// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// One occurrence of a [TaskStage] in an execution. Each declaring task creates a distinct
/// instance. A child that declares no stage inherits its parent's instance.
@NotNullByDefault
public final class TaskStageInstance {
    /// The task whose outcome determines the outcome of this stage.
    private final Task<?> task;

    /// The stage declared by the task.
    private final TaskStage stage;

    /// The enclosing stage, or `null` when there is none.
    private final @Nullable TaskStageInstance parent;

    /// Creates the instance for the declaring task within its enclosing stage.
    TaskStageInstance(Task<?> task, TaskStage stage, @Nullable TaskStageInstance parent) {
        this.task = task;
        this.stage = stage;
        this.parent = parent;
    }

    /// Returns the task that declared this stage.
    public Task<?> task() {
        return task;
    }

    /// Returns the stage declared by [#task()].
    public TaskStage stage() {
        return stage;
    }

    /// Returns the enclosing stage, or `null` if the task was dispatched outside any stage.
    public @Nullable TaskStageInstance parent() {
        return parent;
    }
}
