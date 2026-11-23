// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.Result;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.*;
import java.util.stream.Stream;

/// A single-use unit of work with a result, lifecycle notifications, and optional progress.
/// Submit the root using [#start()], [#run()], or [#test()]. Each task can be submitted only
/// once, either as a root or as a child. Create a new instance to repeat an operation.
/// Registering a listener or requesting cancellation before submission reserves the task as a root.
/// Its listeners and cancellation request apply to the whole execution tree.
///
/// An omitted or `null` executor leaves a task body on its parent's executor, or on the
/// task scheduler for a root. A continuation with an omitted or `null` callback executor
/// invokes the callback on the thread running the continuation.
///
/// Cancellation is cooperative. A running body may check [TaskExecutionContext#isCancelled()] and release its
/// resources before returning. The executor also checks for cancellation before invoking
/// a body and before publishing its result.
///
/// @param <T> the result type, which may be nullable
@NotNullByDefault
public abstract class Task<T extends @Nullable Object> {

    /// Atomically transitions a root from [State#NEW] to [State#READY].
    ///
    /// @see #state
    private static final VarHandle STATE_HANDLE;

    /// Atomically installs the task's execution binding.
    ///
    /// @see #executionBinding
    private static final VarHandle EXECUTION_BINDING;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATE_HANDLE = lookup.findVarHandle(Task.class, "state", State.class);
            EXECUTION_BINDING = lookup.findVarHandle(Task.class, "executionBinding", ExecutionBinding.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// Prefix identifying framework frames excluded from generated task names.
    private static final String PACKAGE_PREFIX = Task.class.getPackageName() + ".";

    /// Selects caller frames outside the task package.
    private static final Predicate<StackWalker.StackFrame> PREDICATE = stackFrame -> !stackFrame.getClassName().startsWith(PACKAGE_PREFIX);

    /// Finds the first external frame for the generated task name.
    private static final Function<Stream<StackWalker.StackFrame>, Optional<StackWalker.StackFrame>> FUNCTION = stream -> stream.filter(PREDICATE).findFirst();

    /// Formats a caller frame, including the source location when available.
    private static final Function<StackWalker.StackFrame, String> FRAME_MAPPING = frame -> {
        @Nullable String fileName = frame.getFileName();
        if (fileName != null)
            return frame.getClassName() + '.' + frame.getMethodName() + '(' + fileName + ':' + frame.getLineNumber() + ')';
        else
            return frame.getClassName() + '.' + frame.getMethodName();
    };

    /// Optional name used for logging and display.
    private @Nullable String name;

    /// Controls execution logging and visibility in the task list.
    private TaskSignificance significance = TaskSignificance.MAJOR;

    /// Null until root reservation or first submission, then permanently bound.
    ///
    /// @see #EXECUTION_BINDING
    @SuppressWarnings("unused")
    private volatile @Nullable ExecutionBinding executionBinding;

    /// Most recent execution state, initially [State#NEW].
    private volatile State state = State.NEW;

    /// Holds the return value in [State#SUCCEEDED] or the exception in [State#FAILED] or [State#CANCELLED].
    /// The volatile transition to the terminal state publishes this field.
    private @Nullable Object outcome;

    /// Creates an unsubmitted task.
    public Task() {
    }

    /// Returns the configured display name, or the class name if none was supplied.
    public String getName() {
        return name != null ? name : getClass().getName();
    }

    /// Sets the display name and returns this task. Passing `null` restores the class name.
    @Contract("_ -> this")
    public Task<T> setName(@Nullable String name) {
        this.name = name;
        return this;
    }

    /// Returns the class name and, when different, the display name.
    @Override
    public String toString() {
        if (getClass().getName().equals(getName()))
            return getName();
        else
            return getClass().getName() + "[" + getName() + "]";
    }

    /// Returns the logging and display policy for this task.
    public final TaskSignificance getSignificance() {
        return significance;
    }

    /// Sets the logging and display policy and returns this task.
    @Contract("_ -> this")
    public final Task<T> setSignificance(TaskSignificance significance) {
        this.significance = significance;
        return this;
    }

    /// Returns the executor requested for [#execute(TaskExecutionContext)], or `null` to
    /// inherit the executor running the parent task's body. A root task returning `null`
    /// runs on the task scheduler. The scheduler queries this method once per dispatch.
    ///
    /// @implSpec The default implementation returns `null`.
    @Nullable
    protected Executor getExecutor() {
        return null;
    }

    /// Runs the task body using the supplied context to execute children and obtain their results.
    /// Call [#run()] or [TaskExecutionContext#run(Task)] to submit a task through its lifecycle.
    ///
    /// @implSpec Implementations that need child work must use the context within this invocation
    /// and on its calling thread. The executor publishes the returned value after its final
    /// cancellation check and records an exception if execution fails.
    /// @param context the execution scope for this invocation
    /// @return the value to publish on success
    /// @throws Exception if execution cannot complete
    public abstract T execute(TaskExecutionContext context) throws Exception;

    /// Runs this task and waits for its result. If interrupted while waiting on another worker,
    /// the caller waits for the submitted work to finish before the interruption is reported.
    /// A caller running directly on a custom executor must leave a worker available if the task
    /// requires that executor. Use [TaskExecutionContext#run(Task)] from another task.
    ///
    /// @throws Exception if execution fails, is cancelled, or the wait is interrupted
    /// @throws IllegalStateException if this task was already submitted
    public final T run() throws Exception {
        return markReadyAsRoot().run(this);
    }

    /// Starts this root asynchronously and returns it.
    ///
    /// @throws IllegalStateException if this task was already submitted
    public final Task<T> start() {
        markReadyAsRoot().start(this);
        return this;
    }

    /// Runs this root and returns whether it succeeded. A thread interrupted while waiting for
    /// another worker's result waits for completion and retains its interrupt status.
    ///
    /// @throws IllegalStateException if this task was already submitted
    public final boolean test() {
        return markReadyAsRoot().test(this);
    }

    /// Requests cooperative cancellation of this root and its children, including before submission.
    ///
    /// @throws IllegalStateException if this task was submitted as a child
    public final void cancel() {
        rootExecution().cancel();
    }

    /// Returns true as soon as [#cancel()] requests cancellation on this root, even before submission.
    /// An unreserved task returns false.
    ///
    /// @throws IllegalStateException if this task was submitted as a child
    /// @see #isCancelled()
    public final boolean isCancellationRequested() {
        @Nullable ExecutionBinding binding = executionBinding;
        return binding != null && binding.requireRootExecution().isCancellationRequested();
    }

    /// Registers a listener for subsequent events in this root's execution tree and returns this task.
    /// Register before submitting the root to observe the entire execution.
    ///
    /// @throws IllegalStateException if this task was submitted as a child
    public final Task<T> addTaskListener(TaskListener listener) {
        Objects.requireNonNull(listener);
        rootExecution().addTaskListener(listener);
        return this;
    }

    /// Removes the first matching listener registration from this root's execution.
    ///
    /// @throws IllegalStateException if this task was submitted as a child
    public final void removeTaskListener(TaskListener listener) {
        @Nullable ExecutionBinding binding = executionBinding;
        if (binding != null)
            binding.requireRootExecution().removeTaskListener(listener);
    }

    /// Sets the handler for unexpected runtime exceptions reported by [#start()] and [#test()].
    /// Pass null to clear the handler.
    public static void setUncaughtExceptionHandler(Thread.@Nullable UncaughtExceptionHandler handler) {
        TaskExecution.setUncaughtExceptionHandler(handler);
    }

    /// Returns the current execution state.
    public final State getState() {
        return state;
    }

    /// Returns true once this task, whether root or child, has completed with a [CancellationException].
    public final boolean isCancelled() {
        return state == State.CANCELLED;
    }

    /// Returns the successful result without waiting. A task with a nullable result type may return `null`.
    ///
    /// @throws IllegalStateException if the task is not in [State#SUCCEEDED]
    public final T resultNow() {
        State currentState = state;
        if (currentState != State.SUCCEEDED)
            throw new IllegalStateException("Task has not completed successfully: " + currentState);
        return successfulResult();
    }

    /// Returns the result without waiting, or `null` if the task has not succeeded or returned `null`.
    public final @Nullable T resultNowOrNull() {
        return state == State.SUCCEEDED ? successfulResult() : null;
    }

    /// Returns the recorded failure without waiting. Cancellation is recorded as a [CancellationException].
    ///
    /// @throws IllegalStateException unless the task is in [State#FAILED] or [State#CANCELLED]
    public final Throwable exceptionNow() {
        State currentState = state;
        if (currentState != State.FAILED && currentState != State.CANCELLED)
            throw new IllegalStateException("Task has not failed: " + currentState);
        return (Throwable) Objects.requireNonNull(outcome);
    }

    /// Returns the recorded exception in [State#FAILED] or [State#CANCELLED], or `null` in any other state.
    public final @Nullable Throwable exceptionNowOrNull() {
        State currentState = state;
        return currentState == State.FAILED || currentState == State.CANCELLED ? (Throwable) outcome : null;
    }

    /// Returns a task that applies `fn` to the successful result on the continuation's thread.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenApplyAsync(ExceptionalFunction<T, U, E> fn) {
        return thenApplyAsync(null, fn);
    }

    /// Returns a task that applies `fn` to the successful result on `executor`.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenApplyAsync(@Nullable Executor executor, ExceptionalFunction<T, U, E> fn) {
        return Task.of(context -> {
            T value = context.run(this);
            return context.call(executor, () -> fn.apply(value));
        }).setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns a task that passes the successful result to `action` on the continuation's thread.
    public <E extends Exception> Task<@Nullable Void> thenAcceptAsync(ExceptionalConsumer<T, E> action) {
        return thenAcceptAsync(null, action);
    }

    /// Returns a task that passes the successful result to `action` on `executor`.
    public <E extends Exception> Task<@Nullable Void> thenAcceptAsync(@Nullable Executor executor, ExceptionalConsumer<T, E> action) {
        return thenApplyAsync(executor, result -> {
            action.accept(result);
            return null;
        });
    }

    /// Returns a task that runs `action` on the continuation's thread after this task succeeds.
    public <E extends Exception> Task<@Nullable Void> thenRunAsync(ExceptionalRunnable<E> action) {
        return thenRunAsync(null, action);
    }

    /// Returns a task that runs `action` on `executor` after this task succeeds.
    public <E extends Exception> Task<@Nullable Void> thenRunAsync(@Nullable Executor executor, ExceptionalRunnable<E> action) {
        return thenAcceptAsync(executor, ignored -> action.run());
    }

    /// Returns a task that calls `fn` on the continuation's executor after this task succeeds.
    public final <U extends @Nullable Object> Task<U> thenSupplyAsync(Callable<U> fn) {
        return thenComposeAsync(() -> Task.supplyAsync(fn));
    }

    /// Returns a task that runs `other` after this task succeeds and returns its result.
    ///
    /// @throws NullPointerException if `other` is null
    public final <U extends @Nullable Object> Task<U> thenComposeAsync(Task<U> other) {
        Objects.requireNonNull(other);
        return thenComposeAsync(() -> other);
    }

    /// Returns a task that calls `fn` on the continuation's thread after success, then runs the supplied task.
    public final <U extends @Nullable Object> Task<U> thenComposeAsync(ExceptionalSupplier<Task<U>, ?> fn) {
        return thenComposeAsync(null, fn);
    }

    /// Returns a task that calls `fn` on `executor` after success, then runs the supplied task.
    public final <U extends @Nullable Object> Task<U> thenComposeAsync(@Nullable Executor executor, ExceptionalSupplier<Task<U>, ?> fn) {
        return thenComposeAsync(executor, ignored -> fn.get());
    }

    /// Returns a task that maps the successful result to a child on the continuation's thread and runs that child.
    /// Use [#thenComposeOptionalAsync(ExceptionalFunction)] if `fn` can return a null child.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenComposeAsync(ExceptionalFunction<T, Task<U>, E> fn) {
        return thenComposeAsync(null, fn);
    }

    /// Returns a task that maps the successful result to a child on `executor` and runs that child.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenComposeAsync(@Nullable Executor executor, ExceptionalFunction<T, Task<U>, E> fn) {
        return Task.of(context -> {
            T value = context.run(this);
            Task<U> next = context.call(executor, () -> fn.apply(value));
            return context.run(next);
        }).setName(fn.toString()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Maps the successful result to an optional child on the continuation's thread.
    /// If `fn` returns `null`, the continuation succeeds with `null` without dispatching a child.
    public <U extends @Nullable Object> Task<@Nullable U> thenComposeOptionalAsync(ExceptionalFunction<T, @Nullable Task<U>, ?> fn) {
        return Task.<@Nullable U>of(context -> {
            T value = context.run(this);
            @Nullable Task<U> next = fn.apply(value);
            return next == null ? null : context.run(next);
        }).setName(fn.toString()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns a task that runs `other` after success or ordinary failure, yielding its result.
    /// Cancellation and errors propagate.
    ///
    /// @throws NullPointerException if `other` is null
    public final <U extends @Nullable Object> Task<U> withComposeAsync(Task<U> other) {
        Objects.requireNonNull(other);
        return withComposeAsync(() -> other);
    }

    /// Returns a task that calls `fn` on the continuation's thread after success or ordinary failure
    /// and runs the supplied task.
    /// Cancellation and errors propagate.
    public final <U extends @Nullable Object> Task<U> withComposeAsync(ExceptionalSupplier<Task<U>, ?> fn) {
        return Task.of(context -> {
            try {
                context.run(this);
            } catch (CancellationException e) {
                throw e;
            } catch (Exception ignored) {
                // This continuation also runs after an ordinary failure.
            }
            return context.run(fn.get());
        }).setName(fn.toString()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns a task that runs `action` on the continuation's executor after success or ordinary failure.
    /// Cancellation and errors propagate.
    public <E extends Exception> Task<@Nullable Void> withRunAsync(ExceptionalRunnable<E> action) {
        return withRunAsync(null, action);
    }

    /// Returns a task that runs `action` on `executor` after success or ordinary failure.
    /// Cancellation and errors propagate.
    public <E extends Exception> Task<@Nullable Void> withRunAsync(@Nullable Executor executor, ExceptionalRunnable<E> action) {
        String name = getCaller();
        return withComposeAsync(() -> Task.of(executor, action.toCallable()).setName(name));
    }

    /// Returns a task that reports success or failure to `action` on the continuation's thread.
    /// See [#whenComplete(Executor, FinalizedCallbackWithResult)] for failure propagation.
    public final Task<@Nullable Void> whenComplete(FinalizedCallback action) {
        return whenComplete(null, action);
    }

    /// Returns a task that reports success or failure to `action` on `executor`.
    /// See [#whenComplete(Executor, FinalizedCallbackWithResult)] for failure propagation.
    public final Task<@Nullable Void> whenComplete(@Nullable Executor executor, FinalizedCallback action) {
        return whenComplete(executor, (value, failure) -> action.execute(failure));
    }

    /// Runs this task and invokes `action` on `executor` with its result or failure.
    /// The callback receives a null failure on success and a null result on failure.
    /// Failures include errors and cancellation.
    ///
    /// The returned task succeeds with null if both the source and the callback succeed.
    /// If both fail, the source failure propagates with the distinct callback failure suppressed.
    /// Cancellation before the returned task starts skips the callback.
    public Task<@Nullable Void> whenComplete(@Nullable Executor executor, FinalizedCallbackWithResult<T> action) {
        return Task.<@Nullable Void>of(context -> {
            T value;
            try {
                value = context.run(this);
            } catch (Throwable failure) {
                throw notifyAndRethrow(failure, () -> context.call(executor, () -> {
                    action.execute(null, failure);
                    return null;
                }));
            }
            return context.call(executor, () -> {
                action.execute(value, null);
                return null;
            });
        }).setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Invokes the corresponding optional callback on `executor`. If `success` throws, its failure
    /// is also reported to `failure`. The first failure propagates, with any distinct later failure suppressed.
    public final <E1 extends Exception, E2 extends Exception> Task<@Nullable Void> whenComplete(@Nullable Executor executor, @Nullable ExceptionalRunnable<E1> success, @Nullable ExceptionalConsumer<? super Throwable, E2> failure) {
        return whenComplete(executor, exception -> {
            if (exception != null) {
                if (failure != null)
                    failure.accept(exception);
            } else if (success != null) {
                try {
                    success.run();
                } catch (Throwable callbackFailure) {
                    if (failure != null)
                        throw notifyAndRethrow(callbackFailure, () -> failure.accept(callbackFailure));
                    throw callbackFailure;
                }
            }
        });
    }

    /// Passes the successful result to `success` or the failure to `failure` on `executor`.
    /// A failure thrown by `success` is reported to the optional failure callback,
    /// as in [#whenComplete(Executor, ExceptionalRunnable, ExceptionalConsumer)].
    public <E1 extends Exception, E2 extends Exception> Task<@Nullable Void> whenComplete(@Nullable Executor executor, ExceptionalConsumer<T, E1> success, @Nullable ExceptionalConsumer<? super Throwable, E2> failure) {
        return whenComplete(executor, () -> success.accept(resultNow()), failure);
    }

    /// Represents the result or any source failure as a [Result], including errors and child cancellation.
    /// Cancelling the root still prevents publication of the result.
    public Task<Result<T>> wrapResult() {
        return Task.<Result<T>>of(context -> {
            T value;
            try {
                value = context.run(this);
            } catch (Throwable failure) {
                return Result.failure(failure);
            }
            return Result.success(value);
        }).setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the stage this task declares when dispatched, or `null` to
    /// inherit its parent's stage. A root task returning `null` runs outside any stage.
    /// The stage covers this task and is inherited by children until they declare their own.
    /// The stage succeeds if this task succeeds, even if it recovers from a child failure.
    ///
    /// @implSpec The default implementation returns `null`.
    public @Nullable TaskStage getStage() {
        return null;
    }

    /// Returns a task that declares `stage` and runs this task as its child.
    /// If this task declares its own stage, that stage is nested inside `stage`.
    public Task<T> withStage(TaskStage stage) {
        return new StageTask(stage);
    }

    /// Returns the stage rows this task declares for display, in presentation order.
    /// A hint can declare a row before any task enters the corresponding stage.
    ///
    /// @implSpec The default implementation returns an empty list.
    public @Unmodifiable List<TaskStage.Hint> getStageHints() {
        return List.of();
    }

    /// Returns a wrapper with one hint per stage, in argument order.
    public Task<T> withStageHints(TaskStage... stages) {
        return withStageHints(Arrays.stream(stages).map(TaskStage.Hint::new).toList());
    }

    /// Returns a wrapper with an immutable snapshot of the supplied hints.
    public Task<T> withStageHints(TaskStage.Hint... hints) {
        return withStageHints(List.of(hints));
    }

    /// Returns a wrapper that declares the supplied rows in order before dispatching this task.
    /// The wrapper takes an immutable snapshot of the hints.
    public Task<T> withStageHints(List<TaskStage.Hint> hints) {
        @Unmodifiable List<TaskStage.Hint> rows = List.copyOf(hints);
        return new Task<>() {
            {
                setSignificance(TaskSignificance.MODERATE);
            }

            /// Returns the stage rows supplied to the wrapper.
            @Override
            public @Unmodifiable List<TaskStage.Hint> getStageHints() {
                return rows;
            }

            /// Runs the wrapped task and returns its result.
            @Override
            public T execute(TaskExecutionContext context) throws Exception {
                return context.run(Task.this);
            }
        };
    }

    /// Returns a wrapper that reports one completed item in its inherited stage after this task succeeds.
    public Task<T> withCounter() {
        return Task.of(context -> {
            T value = context.run(this);
            context.completeStageItem();
            return value;
        }).setSignificance(TaskSignificance.MINOR);
    }

    /// Returns a named task that runs this task and then reports estimated progress while waiting for `done`.
    /// The wrapper returns the original result once `done` is true.
    ///
    /// @param k positive finite scale controlling the rate at which the estimate approaches its upper bound
    public Task<T> withFakeProgress(String name, BooleanSupplier done, double k) {
        return new FakeProgressTask(done, k).setName(name).setSignificance(TaskSignificance.MAJOR);
    }

    /// Creates a task that returns `value` when run.
    public static <V extends @Nullable Object> Task<V> completed(V value) {
        return of(context -> value);
    }

    /// Creates a task that throws `failure` when its body runs.
    ///
    /// @throws NullPointerException if `failure` is null
    public static <V extends @Nullable Object> Task<V> failed(Throwable failure) {
        Objects.requireNonNull(failure);
        return of(context -> {
            throw rethrow(failure);
        });
    }

    /// Creates a task with no executor preference that runs `closure` and returns `null`.
    public static Task<@Nullable Void> runAsync(ExceptionalRunnable<?> closure) {
        return runAsync(null, closure);
    }

    /// Creates a task that runs `closure` and returns `null`, with an optional executor preference.
    public static Task<@Nullable Void> runAsync(@Nullable Executor executor, ExceptionalRunnable<?> closure) {
        return supplyAsync(executor, closure.toCallable());
    }

    /// Creates a task with no executor preference that returns the value of `callable`.
    public static <V extends @Nullable Object> Task<V> supplyAsync(Callable<V> callable) {
        return supplyAsync(null, callable);
    }

    /// Creates a task that returns the value of `callable`, with an optional executor preference.
    public static <V extends @Nullable Object> Task<V> supplyAsync(@Nullable Executor executor, Callable<V> callable) {
        return of(executor, callable).setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Creates a task that calls `fn` on its own thread and returns the supplied child's result.
    /// Use [#composeOptionalAsync(ExceptionalSupplier)] if `fn` can return a null child.
    public static <T extends @Nullable Object> Task<T> composeAsync(ExceptionalSupplier<Task<T>, ?> fn) {
        return composeAsync(null, fn);
    }

    /// Calls `fn` on `executor`, or on the composed task's thread if `null`, and runs the supplied child.
    public static <T extends @Nullable Object> Task<T> composeAsync(@Nullable Executor executor, ExceptionalSupplier<Task<T>, ?> fn) {
        return Task.of(context -> context.run(context.call(executor, fn::get)))
                .setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Calls `fn` on the composed task's thread and returns the supplied child's result.
    /// If `fn` returns `null`, the task succeeds with `null` without dispatching a child.
    public static <T extends @Nullable Object> Task<@Nullable T> composeOptionalAsync(ExceptionalSupplier<@Nullable Task<T>, ?> fn) {
        return Task.<@Nullable T>of(context -> {
            @Nullable Task<T> next = fn.get();
            return next == null ? null : context.run(next);
        }).setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Creates a task that waits for `future` and returns its result. Cancelling the root
    /// does not cancel the future or interrupt a running wait. Future cancellation propagates
    /// as a [CancellationException].
    public static <T extends @Nullable Object> Task<T> fromCompletableFuture(CompletableFuture<T> future) {
        return of(context -> {
            try {
                return future.get();
            } catch (ExecutionException e) {
                throw rethrow(Objects.requireNonNullElse(Lang.resolveException(e), e));
            }
        });
    }

    /// Returns a task that runs all supplied children and collects successful results in array order.
    /// See [#allOf(Collection)]. The array is retained until execution.
    @SafeVarargs
    public static <T extends @Nullable Object> Task<List<T>> allOf(Task<? extends T>... tasks) {
        //noinspection NullableProblems
        return allOf(Arrays.asList(tasks));
    }

    /// Returns a task that runs the supplied children, waits for them all, and collects their results
    /// in iteration order. The returned list is mutable and is empty when there are no children.
    /// A child failure prevents publication of the result list and leaves successful siblings' results
    /// available through [#resultNow()]. Children may execute concurrently.
    ///
    /// The returned task retains the collection and snapshots its elements when execution starts.
    public static <T extends @Nullable Object> Task<List<T>> allOf(Collection<? extends Task<? extends T>> tasks) {
        //noinspection NullableProblems
        return Task.of(context -> context.runAll(tasks)).setSignificance(TaskSignificance.MINOR);
    }

    /// Returns a task that waits for both children and pairs their results in argument order.
    /// The children may run concurrently. Failure of either child fails the combined task.
    public static <T extends @Nullable Object, U extends @Nullable Object> Task<Pair<T, U>> combine(Task<? extends T> task1, Task<? extends U> task2) {
        //noinspection NullableProblems
        return Task.of(context -> {
            //noinspection NullableProblems
            context.runAll(List.of(task1, task2));
            return Pair.pair(task1.resultNow(), task2.resultNow());
        }).setSignificance(TaskSignificance.MINOR);
    }

    /// Returns a task that runs the children in array order, stopping at the first failure.
    /// The result is the last child's result, or `null` for an empty array. The array is retained.
    public static Task<?> runSequentially(Task<?>... tasks) {
        return of(context -> {
            @Nullable Object last = null;
            for (Task<?> task : tasks) {
                last = context.run(task);
            }
            return last;
        });
    }

    /// Reserves the root's execution for configuration or submission.
    private TaskExecution rootExecution() {
        @Nullable ExecutionBinding binding = executionBinding;
        if (binding == null) {
            TaskExecution execution = new TaskExecution();
            binding = (@Nullable ExecutionBinding) EXECUTION_BINDING.compareAndExchange(
                    this, null, new ExecutionBinding(execution));
            if (binding == null)
                return execution;
        }
        return binding.requireRootExecution();
    }

    /// Marks a new root as ready and returns its execution.
    private TaskExecution markReadyAsRoot() {
        TaskExecution execution = rootExecution();
        if (!STATE_HANDLE.compareAndSet(this, State.NEW, State.READY))
            throw new IllegalStateException("Task has already been submitted: " + (name != null ? name : getClass().getName()));
        return execution;
    }

    /// Marks an unreserved task as ready for execution as a child.
    final void markReadyAsChild() {
        if (!EXECUTION_BINDING.compareAndSet(this, null, ExecutionBinding.CHILD))
            throw new IllegalStateException("Task is reserved as a root or has already been submitted: " + (name != null ? name : getClass().getName()));
        state = State.READY;
    }

    /// Marks the task as running before start listeners are notified.
    final void markRunning() {
        state = State.RUNNING;
    }

    /// Reads the return value after the caller has observed [State#SUCCEEDED].
    @SuppressWarnings("unchecked")
    private T successfulResult() {
        //noinspection DataFlowIssue
        return (T) outcome;
    }

    /// Publishes the value returned by the body and marks the task successful.
    final void publishResult(T value) {
        outcome = value;
        state = State.SUCCEEDED;
    }

    /// Publishes the exception and marks the task as failed or cancelled.
    final void publishException(Throwable failure) {
        outcome = failure;
        state = failure instanceof CancellationException ? State.CANCELLED : State.FAILED;
    }

    /// Notifies the observer and propagates the primary failure even if notification also fails.
    private static RuntimeException notifyAndRethrow(Throwable failure, ExceptionalRunnable<?> observer) {
        try {
            observer.run();
        } catch (Throwable secondary) {
            if (secondary != failure)
                failure.addSuppressed(secondary);
        }
        return rethrow(failure);
    }

    /// Rethrows the original failure across task APIs that declare checked exceptions as [Exception].
    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException rethrow(Throwable failure) throws E {
        throw (E) failure;
    }

    /// Creates a task whose body invokes `callable` with the given executor preference.
    private static <V extends @Nullable Object> Task<V> of(@Nullable Executor executor, Callable<V> callable) {
        return new Task<>() {
            /// Returns the executor supplied to the factory.
            @Override
            @Nullable
            protected Executor getExecutor() {
                return executor;
            }

            /// Returns the result of the supplied callable.
            @Override
            public V execute(TaskExecutionContext context) throws Exception {
                return callable.call();
            }
        };
    }

    /// Creates a task that invokes `body` with its execution context.
    private static <T extends @Nullable Object> Task<T> of(ExceptionalFunction<TaskExecutionContext, T, ?> body) {
        return new Task<>() {
            /// {@inheritDoc}
            @Override
            public T execute(TaskExecutionContext context) throws Exception {
                return body.apply(context);
            }
        };
    }

    /// Returns a display name derived from the first caller outside the task package.
    private static String getCaller() {
        return StackWalker.getInstance().walk(FUNCTION).map(FRAME_MAPPING).orElse("Unknown");
    }

    /// Execution state published by the task executor.
    public enum State {
        /// The task has not been submitted.
        NEW,
        /// The task has been submitted and its body has not started.
        READY,
        /// Execution has started and the final outcome has yet to be published.
        RUNNING,
        /// The executor has published the successful result.
        SUCCEEDED,
        /// The executor has recorded a failure other than cancellation.
        FAILED,
        /// The executor has recorded a [CancellationException].
        CANCELLED
    }

    /// Receives the failure outcome of a task.
    @FunctionalInterface
    public interface FinalizedCallback {
        /// Receives null on success or the cause on failure.
        void execute(@Nullable Throwable failure) throws Exception;
    }

    /// Receives the result and failure outcome of a task.
    ///
    /// @param <T> the task result type
    @FunctionalInterface
    public interface FinalizedCallbackWithResult<T extends @Nullable Object> {
        /// Receives the result and a null failure on success, or a null result and the cause on failure.
        void execute(@Nullable T result, @Nullable Throwable failure) throws Exception;
    }

    /// Binds a task as a root or a child.
    ///
    /// @param rootExecution the execution controlled by the root, or `null` for a child
    private record ExecutionBinding(@Nullable TaskExecution rootExecution) {
        /// Binding shared by all submitted children.
        private static final ExecutionBinding CHILD = new ExecutionBinding(null);

        /// Returns the root's execution or throws if this task was submitted as a child.
        private TaskExecution requireRootExecution() {
            if (rootExecution == null)
                throw new IllegalStateException("Task has been submitted as a child");
            return rootExecution;
        }
    }

    /// Provides an enclosing stage for the wrapped task.
    private final class StageTask extends Task<T> {
        /// The stage declared by the wrapper.
        private final TaskStage stage;

        /// Creates a stage wrapper for the enclosing task.
        private StageTask(TaskStage stage) {
            this.stage = Objects.requireNonNull(stage);
            setSignificance(TaskSignificance.MODERATE);
        }

        /// Returns the stage enclosing the wrapped task.
        @Override
        public TaskStage getStage() {
            return stage;
        }

        /// {@inheritDoc}
        @Override
        public T execute(TaskExecutionContext context) throws Exception {
            return context.run(Task.this);
        }
    }

    /// Waits for an external condition after the wrapped task has completed.
    private final class FakeProgressTask extends Task<T> {
        /// Upper bound approached by the progress estimate before the condition is satisfied.
        private static final double MAX_VALUE = 0.98D;

        /// Condition indicating that the wrapper may complete.
        private final BooleanSupplier done;

        /// Time scale used to compute the progress estimate.
        private final double k;

        /// Creates a wrapper that reports estimated progress until the supplied condition is true.
        private FakeProgressTask(BooleanSupplier done, double k) {
            this.done = done;
            this.k = k;
        }

        /// {@inheritDoc}
        @Override
        public T execute(TaskExecutionContext context) throws Exception {
            T value = context.run(Task.this);
            TaskProgress progress = context.progress();
            if (!done.getAsBoolean()) {
                progress.update(0.0D);

                final long start = System.nanoTime();
                while (!done.getAsBoolean()) {
                    if (context.isCancelled()) throw new CancellationException();
                    double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
                    double scaledTime = elapsedSeconds * MAX_VALUE;
                    progress.update(MAX_VALUE * scaledTime / (k + scaledTime));

                    Thread.sleep(1000);
                }
            }

            progress.update(1.0D);
            return value;
        }
    }
}
