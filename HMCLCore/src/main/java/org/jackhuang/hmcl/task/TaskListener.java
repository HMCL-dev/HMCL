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
    /// @param scope the task's declared or inherited stage scope, or `null` if neither applies
    public void onTaskStarted(Task<?> task, TaskStage.@Nullable Scope scope) {
    }

    /// Called once when the task succeeds or fails. A failure before the body starts can
    /// produce this notification without a preceding [#onTaskStarted(Task, TaskStage.Scope)].
    ///
    /// @param scope the task's declared or inherited stage scope, or `null` if neither applies
    /// @param failure the failure cause, including cancellation or an [Error], or `null`
    ///                on success, when [Task#resultNow()] is available
    public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
    }

    /// Announces ordered stage presentation hints when their declaring task is dispatched,
    /// before its stage starts.
    public void onStagesDeclared(@Unmodifiable List<TaskStage.Hint> hints) {
    }

    /// Called when the task declaring a stage is dispatched, before its body starts.
    public void onStageStarted(TaskStage.Scope scope) {
    }

    /// Called after the declaring task's terminal state and result or failure are published.
    ///
    /// @param scope the same scope supplied to [#onStageStarted(TaskStage.Scope)]
    /// @param failure the cause of the task's failure, including cancellation, or `null` on success
    public void onStageFinished(TaskStage.Scope scope, @Nullable Throwable failure) {
    }

    /// Called when a task adds to the completed or total item count of its effective stage.
    /// Both deltas are relative to the counts already reported.
    public void onStageProgress(TaskStage.Scope scope, int completedDelta, int totalDelta) {
    }
}
