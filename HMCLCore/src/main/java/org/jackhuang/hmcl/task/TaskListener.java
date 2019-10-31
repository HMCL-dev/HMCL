/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
}
