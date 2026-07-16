/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class TaskExecutor {
    protected final Task<?> firstTask;
    // CopyOnWriteArrayList: listeners may be added from the FX thread (e.g. TaskCenter / TaskListPane)
    // while the executor iterates them from a worker thread on completion. A plain ArrayList would
    // race (ConcurrentModificationException / lost updates).
    protected final List<TaskListener> taskListeners = new CopyOnWriteArrayList<>();
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

    /// Removes a previously added listener so short-lived views (e.g. a re-attachable task dialog)
    /// don't keep receiving events after they are closed.
    public void removeTaskListener(TaskListener taskListener) {
        taskListeners.remove(taskListener);
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
