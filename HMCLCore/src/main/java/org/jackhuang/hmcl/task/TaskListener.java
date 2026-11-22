// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.EventListener;
import java.util.List;

/// Observes execution of a root task and the children it dispatches.
/// A task publishes its terminal state and outcome before its finish notification.
/// Different tasks can invoke the same listener concurrently, including progress updates for the same stage.
///
/// @see TaskHandle#addTaskListener(TaskListener)
/// @author huangyuhui
@NotNullByDefault
public abstract class TaskListener implements EventListener {

    /// Called on the thread that starts the handle, before the root task runs.
    public void onExecutionStarted(TaskHandle handle) {
    }

    /// Called after the handle finishes executing its task tree.
    ///
    /// @param success whether execution succeeded, including when the root recovered from a child failure
    public void onExecutionFinished(TaskHandle handle, boolean success) {
    }

    /// Called after the task enters [Task.TaskState#RUNNING], before its body is invoked.
    ///
    /// @param instance the task's declared or inherited stage instance, or `null` if neither applies
    public void onTaskStarted(Task<?> task, @Nullable TaskStageInstance instance) {
    }

    /// Called once when the task succeeds or fails. A failure before the body starts can
    /// produce this notification without a preceding [#onTaskStarted(Task, TaskStageInstance)].
    ///
    /// @param instance the task's declared or inherited stage instance, or `null` if neither applies
    /// @param failure the failure cause, including cancellation or an [Error], or `null`
    ///                on success, when [Task#resultNow()] is available
    public void onTaskFinished(Task<?> task, @Nullable TaskStageInstance instance, @Nullable Throwable failure) {
    }

    /// Announces ordered stage presentation hints when their declaring task is dispatched,
    /// before its stage starts.
    public void onStagesDeclared(@Unmodifiable List<Task.StageHint> hints) {
    }

    /// Called when the task declaring a stage is dispatched, before its body starts.
    public void onStageStarted(TaskStageInstance instance) {
    }

    /// Called after the declaring task's terminal state and result or failure are published.
    ///
    /// @param instance the same instance supplied to [#onStageStarted(TaskStageInstance)]
    /// @param failure the cause of the task's failure, including cancellation, or `null` on success
    public void onStageFinished(TaskStageInstance instance, @Nullable Throwable failure) {
    }

    /// Called when a task adds to the completed or total item count of its effective stage.
    /// Both deltas are relative to the counts already reported.
    public void onStageProgress(TaskStageInstance instance, int completedDelta, int totalDelta) {
    }
}
