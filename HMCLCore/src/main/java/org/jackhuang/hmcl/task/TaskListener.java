// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.EventListener;
import java.util.List;

/// Observes execution of a root task and the children it dispatches.
/// Notifications for concurrently executing tasks may arrive on different threads.
///
/// @see TaskExecutor#addTaskListener(TaskListener)
/// @author huangyuhui
public abstract class TaskListener implements EventListener {

    /// Called on the thread that starts the executor, before it starts the root task.
    public void onStart() {
    }

    /// Announces ordered stage presentation hints when their declaring task is dispatched,
    /// before its stage starts.
    public void onStagesDeclared(@Unmodifiable List<Task.StagesHint> hints) {
    }

    /// Called when the task is [ready][Task.TaskState#READY].
    ///
    /// @param task the task about to run
    public void onReady(Task<?> task) {
    }

    /// Called after the task enters [Task.TaskState#RUNNING], before its body is invoked.
    ///
    /// @param task the task about to execute its body
    public void onRunning(Task<?> task) {
    }

    /// Called after the task enters [Task.TaskState#RUNNING], before its body is invoked.
    ///
    /// @implSpec The default implementation calls [#onRunning(Task)].
    /// @param task the task about to execute its body
    /// @param stage the task's declared or inherited stage, or `null` if neither applies
    public void onRunning(Task<?> task, @Nullable TaskStage stage) {
        onRunning(task);
    }

    /// Called after the executor publishes a successful result and marks the task
    /// [Task.TaskState#SUCCEEDED]. The default [#onFailed(Task, Throwable)] implementation
    /// also invokes this method for failed tasks.
    ///
    /// @param task the completed task
    public void onFinished(Task<?> task) {
    }

    /// Called after the executor marks the task [Task.TaskState#FAILED]. Cancellation or
    /// rejection can cause this notification before the task's body starts.
    ///
    /// @implSpec The default implementation calls [#onFinished(Task)] with the same task.
    /// @param task the failed task
    /// @param throwable the cause of failure, including cancellation
    public void onFailed(Task<?> task, Throwable throwable) {
        onFinished(task);
    }

    /// Called when execution of the root task has ended.
    ///
    /// @param success whether the root task succeeded, including when it recovered from a child failure
    /// @param executor the executor that ran the task tree
    public void onStop(boolean success, TaskExecutor executor) {
    }

    /// Called when the task declaring a stage is dispatched, before its body starts.
    public void onStageStarted(TaskStage stage) {
    }

    /// Called after the declaring task's terminal state and result or failure are published.
    ///
    /// @param stage the same stage supplied to [#onStageStarted(TaskStage)]
    /// @param failure the cause of the task's failure, including cancellation, or `null` on success
    public void onStageFinished(TaskStage stage, @Nullable Throwable failure) {
    }

    /// Called when a task explicitly reports a change to its [properties][Task#getProperties()].
    ///
    /// @param task the task reporting the change
    public void onPropertiesUpdate(Task<?> task) {
    }

    /// Called when a task reports a property change through its execution context.
    ///
    /// @implSpec The default implementation calls [#onPropertiesUpdate(Task)].
    /// @param task the task reporting the change
    /// @param stage the task's declared or inherited stage, or `null` if neither applies
    public void onPropertiesUpdate(Task<?> task, @Nullable TaskStage stage) {
        onPropertiesUpdate(task);
    }
}
