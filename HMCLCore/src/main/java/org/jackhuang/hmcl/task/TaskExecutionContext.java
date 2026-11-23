// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Executes children within the current task's cancellation and listener scope.
/// Stage, hint and counter decorators share their source's executor. A child without an
/// executor preference inherits the executor running the current task's body.
/// Children whose [Task#getStage()] returns `null` inherit the current task's stage.
/// A task must use its context only on the thread executing its body and before the body returns.
/// While waiting, a worker may execute submissions from the awaited child's subtree on its
/// own executor. Use the same executor instance for work requiring the same worker.
/// Tasks using constrained executors must avoid wait cycles between independent
/// branches and must not hold resources that their children need to complete.
@NotNullByDefault
public interface TaskExecutionContext {
    /// Returns true if cancellation has been requested for this task tree or the calling thread
    /// is interrupted. This method preserves the thread's interrupted status.
    boolean isCancelled();

    /// Returns the current task's progress reporter. Obtain it on the task's thread
    /// before sharing it with workers contributing to this task.
    TaskProgress progress();

    /// Adds `count` items to the total for the task's declared or inherited stage.
    /// Has no effect outside a stage.
    void addStageItems(int count);

    /// Reports one completed item in the task's declared or inherited stage.
    /// Has no effect outside a stage.
    void completeStageItem();

    /// Runs a child to completion and returns its published result.
    ///
    /// @throws Exception if the child fails or execution is cancelled or interrupted
    /// @throws IllegalStateException if the child was already submitted
    <T extends @Nullable Object> T run(Task<T> task) throws Exception;

    /// Runs one child to completion. A reported failure, including an error or independent
    /// child cancellation, is logged.
    ///
    /// @return true if the child succeeded
    /// @throws CancellationException if this task tree is cancelled or the calling thread is interrupted
    default boolean tryRun(Task<?> task) {
        Objects.requireNonNull(task);
        try {
            run(task);
        } catch (Throwable failure) {
            return childExecutionSucceeded(task, failure);
        }
        return childExecutionSucceeded(task, null);
    }

    /// Schedules the children on their respective executors and waits for all of them.
    /// Siblings on executors with available workers may run concurrently. A failure in
    /// one child does not cancel the others. An empty collection schedules no work.
    /// Submitting an instance twice is a failure of the batch. Other children are still awaited.
    ///
    /// @return a mutable list of successful results in the input's iteration order
    /// @throws Exception if a child fails or execution is cancelled or interrupted
    <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks) throws Exception;

    /// Runs the children as a batch and waits for all of them, even if one fails.
    /// A reported failure is logged as in [#tryRun(Task)].
    ///
    /// @return true if all children succeeded or none were supplied, false if execution reported a failure
    /// @throws CancellationException if this task tree is cancelled or the calling thread is interrupted
    default boolean tryRunAll(List<? extends Task<?>> tasks) {
        @Unmodifiable List<? extends Task<?>> children = List.copyOf(tasks);
        try {
            runAll(children);
        } catch (Throwable failure) {
            return childExecutionSucceeded(children, failure);
        }
        return childExecutionSucceeded(children, null);
    }

    /// Reports a child or batch failure and propagates cancellation before the caller continues.
    private boolean childExecutionSucceeded(Object children, @Nullable Throwable failure) {
        if (failure != null) {
            if (failure instanceof CancellationException cancelled && isCancelled())
                throw cancelled;
            LOG.warning("Child task failed: " + children, failure);
        }
        if (isCancelled())
            throw new CancellationException("Cancelled while waiting for child tasks");
        return failure == null;
    }

    /// Executes an action on the specified executor and returns its result without creating
    /// a task or sending lifecycle notifications. A `null` executor runs the action directly
    /// on the calling thread. An action targeting the current executor may also run inline.
    /// An already running task may call this method after cancellation to perform cleanup.
    ///
    /// @throws Exception if the action fails or the wait is interrupted
    <T extends @Nullable Object> T call(@Nullable Executor executor, Callable<T> action) throws Exception;
}
