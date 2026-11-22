// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.event.Event;

/// Reports the completion of a single task to its [Task#onDone()] handlers.
/// The executor emits this event after recording the outcome and before notifying
/// registered [task listeners][TaskListener] of the task's completion.
///
/// @author huang
public class TaskEvent extends Event {

    /// The task whose completion is reported.
    private final Task<?> task;
    /// Whether the reported outcome is failure, including cancellation.
    private final boolean failed;

    /// Creates an event describing the supplied task outcome.
    ///
    /// @param source the event source, normally the executor that ran the task
    /// @param task the task whose completion is reported
    /// @param failed whether the task failed or was cancelled
    /// @throws NullPointerException if the source is null
    public TaskEvent(Object source, Task<?> task, boolean failed) {
        super(source);
        this.task = task;
        this.failed = failed;
    }

    /// Returns the task whose completion this event reports.
    public Task<?> getTask() {
        return task;
    }

    /// Returns true if this event reports failure or cancellation.
    public boolean isFailed() {
        return failed;
    }
}
