// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import java.util.EventListener;

/**
 *
 * @author huangyuhui
 */
public abstract class TaskListener implements EventListener {

    /**
     * Executed when a Task execution chain starts.
     */
    public void onStart() {
    }

    /**
     * Executed before the task's pre-execution and dependents execution.
     *
     * TaskState of this task is READY.
     *
     * @param task the task that gets ready.
     */
    public void onReady(Task<?> task) {
    }

    /**
     * Executed when the task's execution starts.
     *
     * TaskState of this task is RUNNING.
     *
     * @param task the task which is being run.
     */
    public void onRunning(Task<?> task) {
    }

    /**
     * Executed after the task's dependencies and post-execution finished.
     *
     * TaskState of the task is EXECUTED.
     *
     * @param task the task which finishes its work.
     */
    public void onFinished(Task<?> task) {
    }

    /**
     * Executed when an exception occurred during the task's execution.
     *
     * @param task the task which finishes its work.
     */
    public void onFailed(Task<?> task, Throwable throwable) {
        onFinished(task);
    }

    /**
     * Executed when the task execution chain stopped.
     *
     * @param success true if no error occurred during task execution.
     * @param executor the task executor with responsibility to the task execution.
     */
    public void onStop(boolean success, TaskExecutor executor) {
    }

    public void onPropertiesUpdate(Task<?> task) {
    }
}
