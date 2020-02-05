package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TaskExecutor {
    protected final Task<?> firstTask;
    protected final List<TaskListener> taskListeners = new LinkedList<>();
    protected final AtomicInteger totTask = new AtomicInteger(0);
    protected final AtomicBoolean cancelled = new AtomicBoolean(false);
    protected Exception exception;

    public TaskExecutor(Task<?> task) {
        this.firstTask = task;
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
        return cancelled.get();
    }

    public int getRunningTasks() {
        return totTask.get();
    }
}
