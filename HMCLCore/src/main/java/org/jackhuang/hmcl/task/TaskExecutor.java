// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class TaskExecutor {
    protected final Task<?> firstTask;
    protected final List<TaskListener> taskListeners = new ArrayList<>(0);
    protected volatile boolean cancelled = false;
    protected Exception exception;
    private final List<Task.StagesHint> hints;

    public TaskExecutor(Task<?> task) {
        this.firstTask = task;
        this.hints = task instanceof Task<?>.StagesHintTask hintTask
                ? hintTask.getHints()
                : List.of();
    }

    public void addTaskListener(TaskListener taskListener) {
        taskListeners.add(taskListener);
    }

    /**
     * Reason why the task execution failed.
     * If cancelled, null is returned.
     */
    @Nullable
    public Exception getException() {
        return exception;
    }

    public abstract TaskExecutor start();

    public abstract boolean test();

    /**
     * Cancel the subscription ant interrupt all tasks.
     */
    public abstract void cancel();

    public boolean isCancelled() {
        return cancelled;
    }

    public List<Task.StagesHint> getHints() {
        return hints;
    }
}
