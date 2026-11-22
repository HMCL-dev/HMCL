// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/// Executes a root task and exposes cancellation and lifecycle notifications for its children.
public abstract class TaskExecutor {
    /// The task submitted by the caller.
    protected final Task<?> firstTask;

    /// Listeners registered for execution notifications.
    protected final List<TaskListener> taskListeners = new CopyOnWriteArrayList<>();

    /// Whether cancellation has been requested.
    protected volatile boolean cancelled;

    /// The recorded task failure, if any.
    protected volatile @Nullable Exception exception;

    /// Stage descriptions supplied with the root task.
    private final List<Task.StagesHint> hints;

    /// Creates an executor for the task and its children.
    public TaskExecutor(Task<?> task) {
        this.firstTask = task;
        this.hints = task instanceof Task<?>.StagesHintTask hintTask
                ? hintTask.getHints()
                : List.of();
    }

    /// Registers a listener for subsequent notifications. Register before starting to observe the entire execution.
    public void addTaskListener(TaskListener taskListener) {
        taskListeners.add(taskListener);
    }

    /// Returns the recorded failure, or null if none has been recorded.
    public @Nullable Exception getException() {
        return exception;
    }

    /// Starts execution asynchronously and returns this executor.
    public abstract TaskExecutor start();

    /// Starts execution and waits for the outcome.
    ///
    /// @return true if the root task succeeded
    public abstract boolean test();

    /// Requests cancellation of this executor's task tree.
    public abstract void cancel();

    /// Returns whether cancellation has been requested.
    public boolean isCancelled() {
        return cancelled;
    }

    /// Returns the stage hints supplied with the root task, or an empty list if absent.
    public List<Task.StagesHint> getHints() {
        return hints;
    }
}
