// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.function.ExceptionalFunction;
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

/// Runs children in the current task's execution tree. Children inherit the current stage
/// unless they declare one. Use this context on the task body's thread during its invocation.
/// Actions dispatched by [#call(Executor, Callable)] can also check cancellation.
///
/// A waiting worker can run descendant work scheduled on the same executor instance.
/// On constrained executors, avoid wait cycles between independent branches and release
/// resources that children need before waiting for them.
@NotNullByDefault
public interface TaskExecutionContext {
    /// Returns true if cancellation has been requested for this task tree or the calling thread
    /// is interrupted. This method preserves the thread's interrupted status.
    boolean isCancelled();

    /// Throws [CancellationException] if [#isCancelled()] is true.
    default void checkCancellation() {
        if (isCancelled())
            throw new CancellationException("Cancelled by user");
    }

    /// Makes the final cancellation check for a task that commits external changes.
    /// Once it returns, a later cancellation request alone cannot discard this body's successful result.
    default void commit() {
        checkCancellation();
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

    /// Runs a new child or reuses its completed outcome without repeating lifecycle events.
    /// Returns the value to the caller and preserves this task's choice of continuation executor.
    /// Checks cancellation first, leaving an unsubmitted child available for later use.
    ///
    /// @throws Exception if the child fails or execution is cancelled or interrupted
    /// @throws IllegalStateException if the child is already submitted or reserved as a root and has not completed
    <T extends @Nullable Object> T run(Task<T> task) throws Exception;

    /// Runs the child and selects its outcome's executor for this task's continuations,
    /// even if the child throws. Use `return context.delegateTo(child)` when the child
    /// supplies this task's result. A cached outcome, failed dispatch, or interrupted
    /// wait selects this task's body executor.
    ///
    /// @implSpec The default delegates to [#run(Task)] for contexts that do not track result executors.
    default <T extends @Nullable Object> T delegateTo(Task<T> task) throws Exception {
        return run(task);
    }

    /// Runs the callback on `executor` and selects it for this task's continuations.
    /// A null executor follows the most recently delegated outcome, or the task body's
    /// executor if none was delegated. A rejection or interrupted wait selects the body's executor.
    ///
    /// @implSpec The default uses [#call(Executor, Callable)] and runs a null-executor
    /// callback on the calling thread.
    default <T extends @Nullable Object> T delegateTo(@Nullable Executor executor, Callable<T> action) throws Exception {
        return call(executor, action);
    }

    /// Runs `source` as a child, then applies `fn` to its successful result. A null `executor`
    /// follows the source's result executor. The callback supplies this context's result executor.
    /// Checks cancellation before dispatch and again before invoking `fn`.
    default <S extends @Nullable Object, T extends @Nullable Object> T runAfter(
            Task<S> source, @Nullable Executor executor, ExceptionalFunction<? super S, ? extends T, ?> fn) throws Exception {
        S value = delegateTo(source);
        checkCancellation();
        return delegateTo(executor, () -> {
            checkCancellation();
            return fn.apply(value);
        });
    }

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

    /// Runs independent children concurrently where their executors allow, reusing completed
    /// outcomes as in [#run(Task)]. A duplicate task instance or a child failure fails the
    /// batch. Other children can still run. The first observed failure is reported, with errors
    /// taking precedence.
    ///
    /// Cancellation or interruption stops admission. The batch waits for all admitted children
    /// and preserves the caller's interrupt status. Unsubmitted children remain available for reuse.
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
        for (int start = 0; start < batch.size(); ) {
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

    /// Runs independent children concurrently where their executors allow, reusing completed
    /// outcomes as in [#run(Task)]. A duplicate task instance or a child failure fails the
    /// batch. Other children can still run. The first observed failure is reported, with errors
    /// taking precedence.
    ///
    /// Cancellation or interruption stops admission. The batch waits for all admitted children
    /// and preserves the caller's interrupt status. Unsubmitted children remain available for reuse.
    ///
    /// @return a mutable list of successful results in the input's iteration order
    /// @throws InterruptedException if the calling thread is interrupted during admission or while waiting
    /// @throws Exception if a child fails or the task tree is cancelled
    @SuppressWarnings("unchecked")
    default <T extends @Nullable Object> List<T> runAll(Task<? extends T>... tasks) throws Exception {
        //noinspection NullableProblems
        return runAll(List.of(tasks));
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
