// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/// Controls execution of a root task and exposes cancellation and lifecycle notifications for its task tree.
public abstract class TaskHandle {
    /// The task submitted by the caller.
    protected final Task<?> firstTask;

    /// Listeners registered for execution notifications.
    protected final List<TaskListener> taskListeners = new CopyOnWriteArrayList<>();

    /// Whether cancellation has been requested.
    protected volatile boolean cancelled;

    /// The recorded task failure, if any.
    protected volatile @Nullable Exception exception;

    /// Creates a handle for the task and its children.
    public TaskHandle(Task<?> task) {
        this.firstTask = task;
    }

    /// Registers a listener for subsequent notifications. Register before starting to observe the entire execution.
    public void addTaskListener(TaskListener taskListener) {
        taskListeners.add(taskListener);
    }

    /// Removes a previously registered listener from subsequent notifications.
    public void removeTaskListener(TaskListener taskListener) {
        taskListeners.remove(taskListener);
    }

    /// Returns the recorded failure, or null if none has been recorded.
    public @Nullable Exception getException() {
        return exception;
    }

    /// Starts execution asynchronously and returns this handle.
    ///
    /// @throws IllegalStateException if the root task was already submitted
    public abstract TaskHandle start();

    /// Starts execution and waits for the outcome.
    ///
    /// @return true if the root task succeeded
    /// @throws IllegalStateException if the root task was already submitted
    public abstract boolean test();

    /// Requests cancellation of the task tree.
    public abstract void cancel();

    /// Returns whether cancellation has been requested.
    public boolean isCancelled() {
        return cancelled;
    }
}
