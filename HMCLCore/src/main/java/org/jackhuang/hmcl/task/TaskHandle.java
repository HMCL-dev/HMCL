// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Controls execution of a root task and exposes cancellation and lifecycle notifications for its task tree.
@NotNullByDefault
public abstract class TaskHandle {
    /// Shared snapshot for handles with no registered listeners.
    private static final TaskListener @Unmodifiable [] NO_LISTENERS = {};

    /// The task submitted by the caller.
    protected final Task<?> firstTask;

    /// Current listeners in registration order. Each notification reads one snapshot.
    private volatile TaskListener @Unmodifiable [] taskListeners = NO_LISTENERS;

    private static final VarHandle TASK_LISTENERS_HANDLE;

    static {
        try {
            TASK_LISTENERS_HANDLE = MethodHandles.lookup()
                    .findVarHandle(TaskHandle.class, "taskListeners", TaskListener[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
        while (true) {
            TaskListener[] current = taskListeners;
            TaskListener[] updated = Arrays.copyOf(current, current.length + 1);
            updated[current.length] = taskListener;
            taskListeners = updated;

            if (TASK_LISTENERS_HANDLE.compareAndSet(this, current, updated)) {
                return;
            }
        }
    }

    /// Removes the first matching registration from subsequent notifications.
    public void removeTaskListener(TaskListener taskListener) {
        while (true) {
            TaskListener[] current = taskListeners;

            int index = 0;
            while (index < current.length && current[index] != taskListener) {
                index++;
            }

            if (index == current.length) {
                return;
            }

            TaskListener[] updated;

            if (current.length == 1) {
                updated = NO_LISTENERS;
            } else {
                updated = new TaskListener[current.length - 1];
                System.arraycopy(current, 0, updated, 0, index);
                System.arraycopy(current, index + 1, updated, index, updated.length - index);
            }

            if (TASK_LISTENERS_HANDLE.compareAndSet(this, current, updated)) {
                return;
            }
        }
    }

    /// Notifies a snapshot in registration order and reports each listener's failure independently.
    protected final void notifyListeners(Consumer<TaskListener> notification) {
        for (TaskListener listener : taskListeners) {
            try {
                notification.accept(listener);
            } catch (RuntimeException e) {
                LOG.warning("Task listener failed", e);
            } catch (Error e) {
                Lang.handleUncaughtException(e);
            }
        }
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
