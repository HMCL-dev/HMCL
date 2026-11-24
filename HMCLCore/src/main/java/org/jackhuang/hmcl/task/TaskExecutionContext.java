// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Executes children within the current task's cancellation and listener scope.
/// Stage, hint and counter decorators share their source's executor while running that source.
/// A decorator consuming a completed source, like a child without an executor preference,
/// inherits the executor running the current task's body.
/// Children whose [Task#getStage()] returns `null` inherit the current task's stage.
/// A task must use its context before its body returns and on the thread executing its body,
/// except that an action dispatched by [#call(Executor, Callable)] may use [#isCancelled()]
/// and [#checkCancellation()].
/// While waiting, a worker may execute submissions from the awaited child's subtree on its
/// own executor. Use the same executor instance for work requiring the same worker.
/// Tasks using constrained executors must avoid wait cycles between independent
/// branches and must not hold resources that their children need to complete.
@NotNullByDefault
public interface TaskExecutionContext {
    /// Returns true if cancellation has been requested for this task tree or the calling thread
    /// is interrupted. This method preserves the thread's interrupted status.
    boolean isCancelled();

    /// Throws [CancellationException] if [#isCancelled()] is true, preserving the
    /// calling thread's interrupted status.
    default void checkCancellation() {
        if (isCancelled())
            throw new CancellationException("Cancelled by user");
    }

    /// Returns the current task's progress reporter. Obtain it on the task's thread
    /// before sharing it with workers contributing to this task.
    TaskProgress progress();

    /// Adds `count` items to the total for the task's declared or inherited stage.
    /// Has no effect outside a stage.
    void addStageItems(int count);

    /// Reports one completed item in the task's declared or inherited stage.
    /// Has no effect outside a stage.
    void completeStageItem();

    /// Runs a new child to completion or reads the outcome of a completed task.
    /// Reusing an outcome preserves the original result or failure and emits no lifecycle events.
    /// Cancellation of the calling context takes precedence over a completed outcome.
    /// A child that has not yet been admitted remains available for later submission
    /// if the caller observes cancellation or interruption before admission.
    ///
    /// @throws Exception if the child fails or execution is cancelled or interrupted
    /// @throws IllegalStateException if the child is already submitted or reserved as a root and has not completed
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

    /// Schedules new children on their respective executors, reuses completed outcomes as in
    /// [#run(Task)], and waits for all admitted children.
    /// Siblings on executors with available workers may run concurrently. A failure in
    /// one child does not cancel the others. An empty collection schedules no work.
    /// Listing the same task instance twice in one batch is a failure, even if it has completed.
    /// Other children are still awaited.
    /// Tree cancellation or interruption of the calling thread stops admission. Children
    /// not yet admitted remain available for later submission. The caller's interrupt status is preserved.
    /// The batch reports its first observed failure, giving errors precedence.
    ///
    /// @return a mutable list of successful results in the input's iteration order
    /// @throws InterruptedException if the calling thread is interrupted during admission or while waiting
    /// @throws Exception if a child fails or the task tree is cancelled
    <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks) throws Exception;

    /// Runs independent children with at most `maxConcurrency` unfinished branches admitted at once.
    /// Children must be able to finish without waiting for a sibling that has yet to be admitted.
    /// Result order and failure handling follow [#runAll(Collection)].
    ///
    /// @implSpec The default implementation uses successive batches. The task scheduler can
    /// refill a slot when any admitted branch finishes.
    /// @throws IllegalArgumentException if `maxConcurrency` is not positive
    default <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks, int maxConcurrency) throws Exception {
        if (maxConcurrency <= 0)
            throw new IllegalArgumentException("maxConcurrency must be positive");
        if (isCancelled())
            throw TaskExecution.batchCancellation();
        @Unmodifiable List<? extends Task<? extends T>> batch = List.copyOf(tasks);
        if (batch.size() <= maxConcurrency)
            return runAll(batch);

        Set<Task<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<T> results = new ArrayList<>(batch.size());
        @Nullable Throwable failure = null;
        for (int start = 0; start < batch.size();) {
            if (isCancelled())
                break;
            int end = start + Math.min(maxConcurrency, batch.size() - start);
            List<Task<? extends T>> children = new ArrayList<>(end - start);
            for (Task<? extends T> child : batch.subList(start, end)) {
                if (seen.add(child))
                    children.add(child);
                else
                    failure = TaskExecution.batchFailure(failure, new IllegalStateException("Duplicate task in batch"));
            }
            try {
                if (!children.isEmpty())
                    results.addAll(runAll(children));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure = TaskExecution.batchFailure(failure, e);
                break;
            } catch (Throwable e) {
                failure = TaskExecution.batchFailure(failure, e);
            }
            start = end;
        }
        if (failure != null)
            throw Task.rethrow(failure);
        if (isCancelled())
            throw TaskExecution.batchCancellation();
        return results;
    }

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
