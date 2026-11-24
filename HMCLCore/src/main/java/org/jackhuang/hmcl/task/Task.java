// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.task.TaskExecution.Binding;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.Result;
import org.jackhuang.hmcl.util.function.ExceptionalBiConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalBiFunction;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/// A unit of work with a shared result, lifecycle notifications, and optional progress.
/// [#start()], [#run()], and [#test()] start a new task or observe its existing execution,
/// including when another task submitted it as a child. Each body runs at most once.
/// Create a new instance to repeat an operation.
/// A completed task can supply its published outcome to any subsequent child execution.
/// Registering a listener or requesting cancellation before submission reserves the task as a root.
/// Its listeners and cancellation request apply to the whole execution tree.
///
/// An omitted or `null` executor leaves a task body on its parent's executor, or on the
/// task scheduler for a root. Stage, hint and counter decorators are dispatched together
/// with their source on its effective executor. A new decorator around a completed source
/// inherits its parent's executor. The `then` methods create unsubmitted
/// continuations. The single-argument `then*Async` methods run their callbacks on the
/// default task scheduler. The corresponding methods without the `Async` suffix and
/// overloads passed a `null` executor use the continuation's thread. A callback targeting
/// the current executor may run inline.
///
/// Cancellation is cooperative. A running body may check [TaskExecutionContext#isCancelled()] and release its
/// resources before returning. The executor also checks for cancellation before invoking
/// a body and before publishing its result. The `then` continuations check for cancellation
/// on the callback's executor before invoking user code.
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
            EXECUTION_BINDING = lookup.findVarHandle(Task.class, "executionBinding", Binding.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// Prefix identifying framework frames excluded from generated task names.
    private static final String PACKAGE_PREFIX = Task.class.getPackageName() + ".";

    /// Captures the call site when a task factory assigns a name.
    private static final StackWalker WALKER = StackWalker.getInstance();

    /// Optional name used for logging and display.
    private @Nullable TaskName name;

    /// Controls execution logging and visibility in the task list.
    private TaskSignificance significance = TaskSignificance.MAJOR;

    /// Identifies the owning execution until a completed child releases it.
    ///
    /// @see #EXECUTION_BINDING
    private volatile @Nullable Binding executionBinding;

    /// Most recent execution state, initially [State#NEW].
    private volatile State state = State.NEW;

    /// Holds the return value in [State#SUCCEEDED] or the exception in [State#FAILED] or [State#CANCELLED].
    /// The volatile transition to the terminal state publishes this field.
    private @Nullable Object outcome;

    /// Creates an unsubmitted task.
    public Task() {
    }

    /// Returns the display name, or `null` if this task has no name.
    public @Nullable TaskName getName() {
        return name;
    }

    /// Sets the display name and returns this task. Passing `null` clears the name.
    @Contract("_ -> this")
    public Task<T> setName(@Nullable TaskName name) {
        this.name = name;
        return this;
    }

    /// Sets the display name and returns this task. Passing `null` clears the name.
    @Contract("_ -> this")
    public Task<T> setName(@Nullable String name) {
        this.name = name != null ? new TaskName.Simple(name) : null;
        return this;
    }

    /// Returns the class name followed by the display name in brackets, or the
    /// [Object#toString()] representation if this task has no name.
    @Override
    public String toString() {
        @Nullable TaskName name = getName();
        if (name == null) {
            return super.toString();
        }
        String className = getClass().getName();
        return className + "[" + name.text() + "]";
    }

    /// Returns the task name for logs and diagnostics, falling back to the task's string representation.
    final String displayName() {
        @Nullable TaskName name = getName();
        return name == null ? toString() : name.text();
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

    /// Starts this task if necessary and returns the shared result or rethrows its failure.
    /// If interrupted while waiting on another worker,
    /// the caller waits for the submitted work to finish before the interruption is reported.
    /// A caller running directly on a custom executor must leave a worker available if the task
    /// requires that executor. Use [TaskExecutionContext#run(Task)] from another task.
    ///
    /// @throws Exception if execution fails, is cancelled, or the wait is interrupted
    /// @throws IllegalStateException if the caller would wait on itself or an active ancestor
    public final T run() throws Exception {
        @Nullable TaskExecution execution = tryRootSubmission();
        if (execution != null)
            return execution.run(this);
        if (awaitSubmitted())
            throw new InterruptedException();
        return completedOutcome();
    }

    /// Starts a new root asynchronously. Returns this task immediately if it was already submitted.
    public final Task<T> start() {
        @Nullable TaskExecution execution = tryRootSubmission();
        if (execution != null)
            execution.start(this);
        return this;
    }

    /// Starts this task if necessary and returns whether it succeeded. A thread interrupted while waiting for
    /// another worker's result waits for completion and retains its interrupt status.
    ///
    /// @throws IllegalStateException if the caller would wait on itself or an active ancestor
    public final boolean test() {
        @Nullable TaskExecution execution = tryRootSubmission();
        if (execution != null)
            return execution.test(this);
        awaitSubmitted();
        return state == State.SUCCEEDED;
    }

    /// Requests cooperative cancellation of this root and its children, including before submission.
    ///
    /// @throws IllegalStateException if this task was submitted as a child
    public final void cancel() {
        reserveRootIfUnbound().requireRootExecution().cancel();
    }

    /// Returns true as soon as [#cancel()] requests cancellation on this root, even before submission.
    /// An unreserved task returns false.
    ///
    /// @throws IllegalStateException if this task was submitted as a child
    /// @see #isCancelled()
    public final boolean isCancellationRequested() {
        @Nullable Binding binding = executionBinding;
        return binding != null && binding.requireRootExecution().isCancellationRequested();
    }

    /// Registers a listener for subsequent events in this root's execution tree and returns this task.
    /// Register before submitting the root to observe the entire execution.
    ///
    /// @throws IllegalStateException if this task was submitted as a child
    public final Task<T> addTaskListener(TaskListener listener) {
        Objects.requireNonNull(listener);
        reserveRootIfUnbound().requireRootExecution().addTaskListener(listener);
        return this;
    }

    /// Removes the first matching listener registration from this root's execution.
    ///
    /// @throws IllegalStateException if this task was submitted as a child
    public final void removeTaskListener(TaskListener listener) {
        @Nullable Binding binding = executionBinding;
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
        if (!currentState.hasFailure())
            throw new IllegalStateException("Task has not failed: " + currentState);
        return (Throwable) Objects.requireNonNull(outcome);
    }

    /// Returns the recorded exception in [State#FAILED] or [State#CANCELLED], or `null` in any other state.
    public final @Nullable Throwable exceptionNowOrNull() {
        return state.hasFailure() ? (Throwable) outcome : null;
    }

    /// Returns a task that applies `fn` to the successful result on the continuation's thread.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenApply(ExceptionalFunction<T, U, E> fn) {
        return thenApplyAsync(null, fn);
    }

    /// Returns a task that applies `fn` to the successful result on the default task scheduler.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenApplyAsync(ExceptionalFunction<T, U, E> fn) {
        return thenApplyAsync(Schedulers.tasks(), fn);
    }

    /// Returns a task that applies `fn` to the successful result on `executor`,
    /// or on the continuation's thread if `executor` is null.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenApplyAsync(@Nullable Executor executor, ExceptionalFunction<T, U, E> fn) {
        return new Task<U>() {
            /// {@inheritDoc}
            @Override
            public U execute(TaskExecutionContext context) throws Exception {
                T value = context.run(Task.this);
                checkCancellation(context);
                if (executor == null)
                    return fn.apply(value);
                return context.call(executor, () -> {
                    checkCancellation(context);
                    return fn.apply(value);
                });
            }
        }.setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns a task that passes the successful result to `action` on the continuation's thread.
    public <E extends Exception> Task<@Nullable Void> thenAccept(ExceptionalConsumer<T, E> action) {
        return thenAcceptAsync(null, action);
    }

    /// Returns a task that passes the successful result to `action` on the default task scheduler.
    public <E extends Exception> Task<@Nullable Void> thenAcceptAsync(ExceptionalConsumer<T, E> action) {
        return thenAcceptAsync(Schedulers.tasks(), action);
    }

    /// Returns a task that passes the successful result to `action` on `executor`,
    /// or on the continuation's thread if `executor` is null.
    public <E extends Exception> Task<@Nullable Void> thenAcceptAsync(@Nullable Executor executor, ExceptionalConsumer<T, E> action) {
        return thenApplyAsync(executor, result -> {
            action.accept(result);
            return null;
        });
    }

    /// Returns a task that runs `action` on the continuation's thread after this task succeeds.
    public <E extends Exception> Task<@Nullable Void> thenRun(ExceptionalRunnable<E> action) {
        return thenRunAsync(null, action);
    }

    /// Returns a task that runs `action` on the default task scheduler after this task succeeds.
    public <E extends Exception> Task<@Nullable Void> thenRunAsync(ExceptionalRunnable<E> action) {
        return thenRunAsync(Schedulers.tasks(), action);
    }

    /// Returns a task that runs `action` after this task succeeds, on `executor`
    /// or on the continuation's thread if `executor` is null.
    public <E extends Exception> Task<@Nullable Void> thenRunAsync(@Nullable Executor executor, ExceptionalRunnable<E> action) {
        return thenAcceptAsync(executor, ignored -> action.run());
    }

    /// Returns a task that calls `fn` on the continuation's executor after this task succeeds.
    public final <U extends @Nullable Object> Task<U> thenSupply(Callable<U> fn) {
        return thenCompose(() -> Task.supplyAsync(fn));
    }

    /// Returns a task that calls `fn` on the default task scheduler after this task succeeds.
    public final <U extends @Nullable Object> Task<U> thenSupplyAsync(Callable<U> fn) {
        return thenCompose(() -> Task.supplyAsync(Schedulers.tasks(), fn));
    }

    /// Returns a task that runs `other` after this task succeeds and returns its result.
    ///
    /// @throws NullPointerException if `other` is null
    public final <U extends @Nullable Object> Task<U> thenCompose(Task<U> other) {
        Objects.requireNonNull(other);
        return thenCompose(() -> other);
    }

    /// Equivalent to [#thenComposeAsync(ExceptionalSupplier)] with a supplier that returns `other`.
    ///
    /// @throws NullPointerException if `other` is null
    public final <U extends @Nullable Object> Task<U> thenComposeAsync(Task<U> other) {
        Objects.requireNonNull(other);
        return thenComposeAsync(() -> other);
    }

    /// Returns a task that calls `fn` on the continuation's thread after success, then runs the supplied task.
    public final <U extends @Nullable Object> Task<U> thenCompose(ExceptionalSupplier<Task<U>, ?> fn) {
        return thenComposeAsync(null, fn);
    }

    /// Returns a task that calls `fn` on the default task scheduler after success,
    /// then runs the supplied task with its own or the continuation's executor.
    public final <U extends @Nullable Object> Task<U> thenComposeAsync(ExceptionalSupplier<Task<U>, ?> fn) {
        return thenComposeAsync(Schedulers.tasks(), fn);
    }

    /// Returns a task that calls `fn` after success, on `executor` or on the continuation's
    /// thread if `executor` is null, then runs the supplied task.
    /// The child inherits the continuation's executor unless it requests its own.
    public final <U extends @Nullable Object> Task<U> thenComposeAsync(@Nullable Executor executor, ExceptionalSupplier<Task<U>, ?> fn) {
        return thenComposeAsync(executor, ignored -> fn.get());
    }

    /// Returns a task that maps the successful result to a child on the continuation's thread and runs that child.
    /// Use [#thenComposeOptional(ExceptionalFunction)] if `fn` can return a null child.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenCompose(ExceptionalFunction<T, Task<U>, E> fn) {
        return thenComposeAsync(null, fn);
    }

    /// Returns a task that maps the successful result to a child on the default task scheduler,
    /// then runs that child with its own or the continuation's executor.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenComposeAsync(ExceptionalFunction<T, Task<U>, E> fn) {
        return thenComposeAsync(Schedulers.tasks(), fn);
    }

    /// Returns a task that maps the successful result to a child on `executor`,
    /// or on the continuation's thread if `executor` is null, then runs that child.
    /// The child inherits the continuation's executor unless it requests its own.
    public <U extends @Nullable Object, E extends Exception> Task<U> thenComposeAsync(@Nullable Executor executor, ExceptionalFunction<T, Task<U>, E> fn) {
        return new Task<U>() {
            /// {@inheritDoc}
            @Override
            public U execute(TaskExecutionContext context) throws Exception {
                T value = context.run(Task.this);
                checkCancellation(context);
                if (executor == null)
                    return context.run(fn.apply(value));
                Task<U> next = context.call(executor, () -> {
                    checkCancellation(context);
                    return fn.apply(value);
                });
                return context.run(next);
            }
        }.setName(fn.toString()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Maps the successful result to an optional child on the continuation's thread.
    /// If `fn` returns `null`, the continuation succeeds with `null` without dispatching a child.
    public <U extends @Nullable Object> Task<@Nullable U> thenComposeOptional(ExceptionalFunction<T, @Nullable Task<U>, ?> fn) {
        return thenComposeOptionalAsync(null, fn);
    }

    /// Maps the successful result to an optional child on the default task scheduler.
    /// A null child completes the continuation successfully with null.
    public <U extends @Nullable Object> Task<@Nullable U> thenComposeOptionalAsync(ExceptionalFunction<T, @Nullable Task<U>, ?> fn) {
        return thenComposeOptionalAsync(Schedulers.tasks(), fn);
    }

    /// Maps the successful result to an optional child on `executor`, or on the continuation's
    /// thread if `executor` is null. The child uses its own or the continuation's executor.
    /// A null child completes the continuation successfully with null.
    public <U extends @Nullable Object> Task<@Nullable U> thenComposeOptionalAsync(@Nullable Executor executor, ExceptionalFunction<T, @Nullable Task<U>, ?> fn) {
        return new Task<@Nullable U>() {
            /// {@inheritDoc}
            @Override
            public @Nullable U execute(TaskExecutionContext context) throws Exception {
                T value = context.run(Task.this);
                checkCancellation(context);
                @Nullable Task<U> next;
                if (executor == null) {
                    next = fn.apply(value);
                } else {
                    next = context.call(executor, () -> {
                        checkCancellation(context);
                        return fn.apply(value);
                    });
                }
                return next == null ? null : context.run(next);
            }
        }.setName(fn.toString()).setSignificance(TaskSignificance.MODERATE);
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
        return new Task<U>() {
            /// {@inheritDoc}
            @Override
            public U execute(TaskExecutionContext context) throws Exception {
                try {
                    context.run(Task.this);
                } catch (CancellationException e) {
                    throw e;
                } catch (Exception ignored) {
                    // This continuation also runs after an ordinary failure.
                }
                checkCancellation(context);
                return context.run(fn.get());
            }
        }.setName(fn.toString()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns a task that runs `action` on the continuation's executor after success or ordinary failure.
    /// Cancellation and errors propagate.
    public <E extends Exception> Task<@Nullable Void> withRunAsync(ExceptionalRunnable<E> action) {
        return withRunAsync(null, action);
    }

    /// Returns a task that runs `action` on `executor` after success or ordinary failure.
    /// Cancellation and errors propagate.
    public <E extends Exception> Task<@Nullable Void> withRunAsync(@Nullable Executor executor, ExceptionalRunnable<E> action) {
        TaskName name = getCaller();
        return withComposeAsync(() -> Task.<@Nullable Void>callableTask(executor, action.toCallable()).setName(name));
    }

    /// Reports this task's result or failure to `action` on the continuation's thread.
    /// See [#whenCompleteAsync(Executor, ExceptionalBiConsumer)] for the returned task's outcome.
    public final Task<T> whenComplete(ExceptionalBiConsumer<? super @Nullable T, ? super @Nullable Throwable, ?> action) {
        return whenCompleteAsync(null, action);
    }

    /// Reports this task's result or failure to `action` on the default task scheduler.
    /// See [#whenCompleteAsync(Executor, ExceptionalBiConsumer)] for the returned task's outcome.
    public final Task<T> whenCompleteAsync(ExceptionalBiConsumer<? super @Nullable T, ? super @Nullable Throwable, ?> action) {
        return whenCompleteAsync(Schedulers.tasks(), action);
    }

    /// Returns a continuation that invokes `action` with this task's result or failure.
    /// The callback receives a null failure on success and a null result on failure.
    /// Failures include errors and cancellation.
    ///
    /// If both the source and the callback succeed, the continuation returns the source result,
    /// which may be null. If only one fails, its failure propagates.
    /// If both fail, the source failure propagates with the distinct callback failure suppressed.
    /// Cancellation before the returned task starts skips the callback.
    ///
    /// @param executor the executor for the callback, or `null` to run it on the continuation's thread
    /// @param action the observer of the source result or failure
    /// @throws NullPointerException if `action` is null
    public Task<T> whenCompleteAsync(@Nullable Executor executor, ExceptionalBiConsumer<? super @Nullable T, ? super @Nullable Throwable, ?> action) {
        Objects.requireNonNull(action);
        return new Task<T>() {
            /// {@inheritDoc}
            @Override
            public T execute(TaskExecutionContext context) throws Exception {
                T value;
                try {
                    value = context.run(Task.this);
                } catch (Throwable failure) {
                    try {
                        context.call(executor, () -> {
                            action.accept(null, failure);
                            return null;
                        });
                    } catch (Throwable callbackFailure) {
                        if (callbackFailure != failure)
                            failure.addSuppressed(callbackFailure);
                    }
                    throw failure;
                }
                return context.call(executor, () -> {
                    action.accept(value, null);
                    return value;
                });
            }
        }.setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns a continuation that maps this task's result or failure on the continuation's thread.
    /// See [#handleAsync(Executor, ExceptionalBiFunction)] for the handler arguments and outcome.
    public <U extends @Nullable Object> Task<U> handle(ExceptionalBiFunction<? super @Nullable T, ? super @Nullable Throwable, ? extends U, ?> fn) {
        return handleAsync(null, fn);
    }

    /// Returns a continuation that maps this task's result or failure on the default task scheduler.
    /// See [#handleAsync(Executor, ExceptionalBiFunction)] for the handler arguments and outcome.
    public <U extends @Nullable Object> Task<U> handleAsync(ExceptionalBiFunction<? super @Nullable T, ? super @Nullable Throwable, ? extends U, ?> fn) {
        return handleAsync(Schedulers.tasks(), fn);
    }

    /// Returns a continuation that runs this task and maps its result or failure to a new value.
    /// On success, `fn` receives the result (which may be null) and a null failure.
    /// On failure, it receives a null result and the cause, including an [Error] or a
    /// [CancellationException]. If `fn` throws, its failure propagates even if this task failed.
    ///
    /// Cancellation before the continuation starts skips the handler. A cancellation request
    /// for the task tree is still checked before the continuation publishes the handler's result.
    ///
    /// @param <U> the result type of the continuation, which may be nullable
    /// @param executor the executor for the handler, or `null` to run it on the continuation's thread
    /// @param fn the function that computes the continuation's result
    /// @throws NullPointerException if `fn` is null
    public <U extends @Nullable Object> Task<U> handleAsync(@Nullable Executor executor, ExceptionalBiFunction<? super @Nullable T, ? super @Nullable Throwable, ? extends U, ?> fn) {
        Objects.requireNonNull(fn);
        return new Task<U>() {
            /// {@inheritDoc}
            @Override
            public U execute(TaskExecutionContext context) throws Exception {
                T value;
                try {
                    value = context.run(Task.this);
                } catch (Throwable failure) {
                    return context.call(executor, () -> fn.apply(null, failure));
                }
                return context.call(executor, () -> fn.apply(value, null));
            }
        }.setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Represents the result or any source failure as a [Result], including errors and child cancellation.
    /// Cancelling the root still prevents publication of the result.
    public Task<Result<T>> wrapResult() {
        //noinspection NullableProblems
        return handle((value, failure) -> failure == null ? Result.success(value) : Result.failure(failure));
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

    /// Returns a wrapper that declares `stage` around this task. Stages opened by
    /// this task during execution are nested inside `stage`.
    public Task<T> withStage(TaskStage stage) {
        Objects.requireNonNull(stage);
        return new Decorator<>(this, TaskSignificance.MODERATE) {
            /// Returns the stage enclosing the wrapped task.
            @Override
            public TaskStage getStage() {
                return stage;
            }
        };
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

    /// Returns a wrapper that declares the supplied rows in order.
    /// The wrapper takes an immutable snapshot of the hints.
    public Task<T> withStageHints(List<TaskStage.Hint> hints) {
        @Unmodifiable List<TaskStage.Hint> rows = List.copyOf(hints);
        return new Decorator<>(this, TaskSignificance.MODERATE) {
            /// Returns the stage rows supplied to the wrapper.
            @Override
            public @Unmodifiable List<TaskStage.Hint> getStageHints() {
                return rows;
            }
        };
    }

    /// Returns a wrapper that reports one completed item in its inherited stage
    /// after obtaining this task's successful result.
    public Task<T> withCounter() {
        return new Decorator<>(this, TaskSignificance.MINOR) {
            /// Reports the item after the source publishes its result.
            @Override
            public T execute(TaskExecutionContext context) throws Exception {
                T value = super.execute(context);
                context.completeStageItem();
                return value;
            }
        };
    }

    /// Returns a named task that runs this task and then reports estimated progress while waiting for `done`.
    /// The wrapper returns the original result once `done` is true.
    ///
    /// @param k positive finite scale controlling the rate at which the estimate approaches its upper bound
    public Task<T> withFakeProgress(TaskName name, BooleanSupplier done, double k) {
        return new Task<T>() {
            /// Upper bound approached by the progress estimate before the condition is satisfied.
            private static final double MAX_ESTIMATED_PROGRESS = 0.98D;

            /// {@inheritDoc}
            @Override
            public T execute(TaskExecutionContext context) throws Exception {
                T value = context.run(Task.this);
                TaskProgress progress = context.progress();
                if (!done.getAsBoolean()) {
                    progress.update(0.0D);
                    long start = System.nanoTime();
                    while (!done.getAsBoolean()) {
                        if (context.isCancelled())
                            throw new CancellationException();
                        double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
                        double scaledTime = elapsedSeconds * MAX_ESTIMATED_PROGRESS;
                        progress.update(MAX_ESTIMATED_PROGRESS * scaledTime / (k + scaledTime));
                        Thread.sleep(1000);
                    }
                }
                progress.update(1.0D);
                return value;
            }
        }.setName(name);
    }

    /// Creates a task that returns `value` when run.
    public static <V extends @Nullable Object> Task<V> completed(V value) {
        return new Task<>() {
            /// {@inheritDoc}
            @Override
            public V execute(TaskExecutionContext context) {
                return value;
            }
        };
    }

    /// Creates a task that throws `failure` when its body runs.
    ///
    /// @throws NullPointerException if `failure` is null
    public static <V extends @Nullable Object> Task<V> failed(Throwable failure) {
        Objects.requireNonNull(failure);
        return new Task<>() {
            /// {@inheritDoc}
            @Override
            public V execute(TaskExecutionContext context) {
                throw rethrow(failure);
            }
        };
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
        return callableTask(executor, callable).setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Adapts a callable for execution as a task on its requested executor.
    private static <V extends @Nullable Object> Task<V> callableTask(@Nullable Executor executor, Callable<V> callable) {
        return new Task<>() {
            /// {@inheritDoc}
            @Override
            protected @Nullable Executor getExecutor() {
                return executor;
            }

            /// {@inheritDoc}
            @Override
            public V execute(TaskExecutionContext context) throws Exception {
                return callable.call();
            }
        };
    }

    /// Creates a task that calls `fn` on its own thread and returns the supplied child's result.
    /// Use [#composeOptionalAsync(ExceptionalSupplier)] if `fn` can return a null child.
    public static <T extends @Nullable Object> Task<T> composeAsync(ExceptionalSupplier<Task<T>, ?> fn) {
        return composeAsync(null, fn);
    }

    /// Calls `fn` on `executor`, or on the composed task's thread if `null`, and runs the supplied child.
    public static <T extends @Nullable Object> Task<T> composeAsync(@Nullable Executor executor, ExceptionalSupplier<Task<T>, ?> fn) {
        return new Task<T>() {
            /// {@inheritDoc}
            @Override
            public T execute(TaskExecutionContext context) throws Exception {
                checkCancellation(context);
                if (executor == null)
                    return context.run(fn.get());
                Task<T> child = context.call(executor, () -> {
                    checkCancellation(context);
                    return fn.get();
                });
                return context.run(child);
            }
        }.setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Calls `fn` on the composed task's thread and returns the supplied child's result.
    /// If `fn` returns `null`, the task succeeds with `null` without dispatching a child.
    public static <T extends @Nullable Object> Task<@Nullable T> composeOptionalAsync(ExceptionalSupplier<@Nullable Task<T>, ?> fn) {
        return new Task<@Nullable T>() {
            /// {@inheritDoc}
            @Override
            public @Nullable T execute(TaskExecutionContext context) throws Exception {
                @Nullable Task<T> next = fn.get();
                return next == null ? null : context.run(next);
            }
        }.setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /// Creates a task that waits for `future` and returns its result. Cancelling the root
    /// does not cancel the future or interrupt a running wait. Future cancellation propagates
    /// as a [CancellationException].
    public static <T extends @Nullable Object> Task<T> fromCompletableFuture(CompletableFuture<T> future) {
        return new Task<>() {
            /// {@inheritDoc}
            @Override
            public T execute(TaskExecutionContext context) throws Exception {
                try {
                    return future.get();
                } catch (ExecutionException e) {
                    throw rethrow(Objects.requireNonNullElse(Lang.resolveException(e), e));
                }
            }
        };
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
        return new Task<List<T>>() {
            /// {@inheritDoc}
            @Override
            public List<T> execute(TaskExecutionContext context) throws Exception {
                return context.runAll(tasks);
            }
        }.setSignificance(TaskSignificance.MINOR);
    }

    /// Returns a batch task that admits at most `maxConcurrency` independent branches at a time.
    /// The collection is snapshotted when execution starts. See [TaskExecutionContext#runAll(Collection, int)].
    ///
    /// @throws IllegalArgumentException if `maxConcurrency` is not positive
    public static <T extends @Nullable Object> Task<List<T>> allOf(Collection<? extends Task<? extends T>> tasks, int maxConcurrency) {
        if (maxConcurrency <= 0)
            throw new IllegalArgumentException("maxConcurrency must be positive");
        //noinspection NullableProblems
        return new Task<List<T>>() {
            /// {@inheritDoc}
            @Override
            public List<T> execute(TaskExecutionContext context) throws Exception {
                return context.runAll(tasks, maxConcurrency);
            }
        }.setSignificance(TaskSignificance.MINOR);
    }

    /// Returns a task that waits for both children and pairs their results in argument order.
    /// The children may run concurrently. Failure of either child fails the combined task.
    public static <T extends @Nullable Object, U extends @Nullable Object> Task<Pair<T, U>> combine(Task<? extends T> task1, Task<? extends U> task2) {
        return new Task<Pair<T, U>>() {
            /// {@inheritDoc}
            @Override
            public Pair<T, U> execute(TaskExecutionContext context) throws Exception {
                //noinspection NullableProblems
                context.runAll(List.of(task1, task2));
                return Pair.pair(task1.resultNow(), task2.resultNow());
            }
        }.setSignificance(TaskSignificance.MINOR);
    }

    /// Reserves an unbound task as a root or returns the existing binding.
    private Binding reserveRootIfUnbound() {
        @Nullable Binding current = executionBinding;
        if (current != null)
            return current;
        Binding root = Binding.root();
        current = (@Nullable Binding) EXECUTION_BINDING.compareAndExchange(this, null, root);
        return current == null ? root : current;
    }

    /// Returns the execution to its first submitter, or null if another caller owns submission.
    private @Nullable TaskExecution tryRootSubmission() {
        if (state != State.NEW)
            return null;
        Binding binding = reserveRootIfUnbound();
        return binding.isRoot() && STATE_HANDLE.compareAndSet(this, State.NEW, State.READY)
                ? binding.owner() : null;
    }

    /// Observes an existing submission and reports any interruption received while waiting.
    private boolean awaitSubmitted() {
        Binding binding = binding();
        return !isDone() && binding.owner().awaitShared(this, binding);
    }

    /// Returns the execution binding of a reserved or submitted task.
    final Binding binding() {
        return Objects.requireNonNull(executionBinding);
    }

    /// Returns whether the execution has published its outcome.
    final boolean isDone() {
        State current = state;
        return current == State.SUCCEEDED || current.hasFailure();
    }

    /// Reserves a new child, or returns false if its published outcome can be reused.
    ///
    /// @throws IllegalStateException if the task is reserved or already executing
    final boolean tryMarkReadyAsChild(Binding parent) {
        if (executionBinding == null && EXECUTION_BINDING.compareAndSet(this, null, parent.child())) {
            state = State.READY;
            return true;
        }
        if (isDone())
            return false;
        throw new IllegalStateException("Task is reserved as a root or has already been submitted: " + displayName());
    }

    /// Returns the published result of a completed task or rethrows its recorded failure.
    final T completedOutcome() throws Exception {
        if (state == State.SUCCEEDED)
            return successfulResult();
        throw rethrow(exceptionNow());
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

    /// Publishes the outcome before the terminal state and wakes waiting observers.
    /// A null failure denotes success, including when the result is null.
    final void finish(@Nullable T result, @Nullable Throwable failure) {
        Binding binding = binding();
        if (failure == null) {
            outcome = result;
            state = State.SUCCEEDED;
        } else {
            outcome = failure;
            state = failure instanceof CancellationException ? State.CANCELLED : State.FAILED;
        }
        binding.setWork(null);
        if (!binding.isRoot())
            executionBinding = Binding.FINISHED_CHILD;
    }

    /// Stops a callback when its task tree is cancelled or its thread is interrupted.
    private static void checkCancellation(TaskExecutionContext context) {
        if (context.isCancelled())
            throw new CancellationException("Cancelled by user");
    }

    /// Rethrows the original failure across task APIs that declare checked exceptions as [Exception].
    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException rethrow(Throwable failure) throws E {
        throw (E) failure;
    }

    /// Captures the first caller outside the task package for use as a task name.
    private static TaskName getCaller() {
        return WALKER.walk(frames -> frames.filter(frame -> !frame.getClassName().startsWith(PACKAGE_PREFIX))
                .findFirst()
                .<TaskName>map(frame -> new TaskName.CallSite(frame.toStackTraceElement()))
                .orElseGet(() -> new TaskName.Simple("Unknown")));
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
        CANCELLED;

        /// Returns whether a failure has been published for this state.
        private boolean hasFailure() {
            return this == FAILED || this == CANCELLED;
        }
    }

    /// A stage decorator that shares its source's executor when both run together.
    abstract static class Decorator<T extends @Nullable Object> extends Task<T> {
        /// The child whose executor and result this decorator shares.
        final Task<T> source;

        /// Wraps the source with the given logging and display policy.
        private Decorator(Task<T> source, TaskSignificance significance) {
            this.source = source;
            setSignificance(significance);
        }

        /// Runs the source as a child in this decorator's stage.
        @Override
        public T execute(TaskExecutionContext context) throws Exception {
            return context.run(source);
        }
    }
}
