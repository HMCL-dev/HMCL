// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import com.google.gson.JsonParseException;
import javafx.application.Platform;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Runs a task tree with shared cancellation, stage inheritance, and lifecycle notifications.
@NotNullByDefault
public final class DefaultTaskHandle extends TaskHandle {
    /// The framework submission currently using this thread.
    private static final ThreadLocal<@Nullable Work<?>> CURRENT_WORK = new ThreadLocal<>();

    /// Handler for unexpected runtime exceptions from a root task.
    private static volatile Thread.@Nullable UncaughtExceptionHandler uncaughtExceptionHandler;

    /// Guards the waiting threads and the work they can execute without losing thread affinity.
    private final ReentrantLock lock = new ReentrantLock();

    /// Creates a handle for a root task.
    public DefaultTaskHandle(Task<?> task) {
        super(task);
    }

    @Override
    public TaskHandle start() {
        beginExecution();
        new Work<>(Schedulers.tasks(), this::runToCompletion).submit();
        return this;
    }

    @Override
    public boolean test() {
        beginExecution();
        return runToCompletion();
    }

    /// Reserves the root task before a listener or another caller can start it again.
    private void beginExecution() {
        firstTask.claimExecution();
        notifyListeners(listener -> listener.onExecutionStarted(this));
    }

    /// Runs one root task synchronously, propagating its failure to the caller.
    static <T extends @Nullable Object> T run(Task<T> task) throws Exception {
        task.claimExecution();
        return new DefaultTaskHandle(task).runClaimed(null, task);
    }

    /// Runs the root through its final notification and reports uncaught failures.
    private boolean runToCompletion() {
        boolean success = false;
        try {
            runClaimed(null, firstTask);
            exception = null;
            success = true;
        } catch (Exception e) {
            exception = e;
            LOG.warning("An exception occurred in task execution", e);
            Thread.@Nullable UncaughtExceptionHandler handler = uncaughtExceptionHandler;
            if (handler != null && e instanceof RuntimeException && !(e instanceof CancellationException)
                    && !(e instanceof JsonParseException) && !(e instanceof RejectedExecutionException)) {
                handler.uncaughtException(Thread.currentThread(), e);
            }
        } catch (Error e) {
            Lang.handleUncaughtException(e);
        } finally {
            boolean completed = success;
            notifyListeners(listener -> listener.onExecutionFinished(this, completed));
        }
        return success;
    }

    /// Requests cancellation. Already running bodies may finish before they observe the request.
    /// Starting a previously cancelled handle still delivers its completion notification.
    @Override
    public void cancel() {
        cancelled = true;
    }

    /// Returns the executor whose work the caller can execute inline.
    private static @Nullable Executor currentExecutor() {
        @Nullable Work<?> current = CURRENT_WORK.get();
        if (current != null)
            return current.executor;
        return Platform.isFxApplicationThread() ? Schedulers.javafx() : null;
    }

    /// Announces the task's presentation hints and stage, then resolves its executor.
    private <T extends @Nullable Object> Context<T> prepare(@Nullable Context<?> parent, Task<T> task) {
        @Nullable TaskStage stage = parent == null ? null : parent.stage;
        try {
            @Unmodifiable List<Task.StageHint> hints = task.getStageHints();
            if (!hints.isEmpty())
                notifyListeners(listener -> listener.onStagesDeclared(hints));
            @Nullable String id = task.getStage();
            if (id != null) {
                TaskStage declared = new TaskStage(task, id, stage);
                stage = declared;
                notifyListeners(listener -> listener.onStageStarted(declared));
            }
            @Nullable Executor executor = task.getExecutor();
            if (executor == null)
                executor = parent == null ? Schedulers.tasks() : parent.executor;
            return new Context<>(task, executor, stage);
        } catch (RuntimeException | Error failure) {
            fail(task, stage, failure);
            throw failure;
        }
    }

    /// Dispatches a claimed task, running it inline on a worker of its effective executor.
    private <T extends @Nullable Object> T runClaimed(@Nullable Context<?> parent, Task<T> child) throws Exception {
        Context<T> context = prepare(parent, child);
        if (context.executor == currentExecutor())
            return execute(context);
        return await(submitChild(context));
    }

    /// Schedules a child and records task failure if the executor rejects the submission.
    private <T extends @Nullable Object> Work<T> submitChild(Context<T> context) {
        Work<T> work = new Work<>(context.executor, () -> execute(context));
        try {
            checkCancellation();
            work.submit();
        } catch (RuntimeException | Error failure) {
            fail(context.task, context.stage, failure);
            work.reject(failure);
        }
        return work;
    }

    /// Executes the body and publishes its result before notifying listeners of success.
    private <T extends @Nullable Object> T execute(Context<T> context) throws Exception {
        Task<T> task = context.task;
        T result;
        try {
            checkCancellation();
            if (task.getSignificance().shouldLog()) {
                LOG.trace("Executing task: " + task.getName());
                checkCancellation();
            }
            task.setState(Task.TaskState.RUNNING);
            notifyListeners(listener -> listener.onTaskStarted(task, context.stage));
            try {
                result = task.execute(context);
            } finally {
                task.setState(Task.TaskState.EXECUTED);
            }
            checkCancellation();
        } catch (Throwable failure) {
            Throwable cause = Objects.requireNonNullElse(Lang.resolveException(failure), failure);
            if (cause instanceof InterruptedException)
                cause = new CancellationException(cause.getMessage());
            fail(task, context.stage, cause);
            throw asException(cause);
        }

        task.publishResult(result);
        notifyTaskFinished(task, context.stage, null);
        if (task.getSignificance().shouldLog())
            LOG.trace("Task finished: " + task.getName());
        return result;
    }

    /// Records the checked failure exposed by a task and delivers its terminal notifications.
    private void fail(Task<?> task, @Nullable TaskStage stage, Throwable cause) {
        Exception recorded = cause instanceof Exception e ? e : new Exception(cause);
        exception = recorded;
        task.publishException(recorded);
        notifyTaskFinished(task, stage, cause);
    }

    /// Reports the task's published outcome, then closes its stage if it declared one.
    private void notifyTaskFinished(Task<?> task, @Nullable TaskStage stage, @Nullable Throwable failure) {
        notifyListeners(listener -> listener.onTaskFinished(task, stage, failure));
        if (stage != null && stage.task() == task)
            notifyListeners(listener -> listener.onStageFinished(stage, failure));
    }

    /// Delivers a notification to each listener even if another listener fails.
    private void notifyListeners(Consumer<TaskListener> notification) {
        for (TaskListener listener : taskListeners)
            notifySafely(() -> notification.accept(listener));
    }

    /// Reports a listener failure independently of the observed task's outcome.
    private static void notifySafely(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException e) {
            LOG.warning("Task listener failed", e);
        } catch (Error e) {
            Lang.handleUncaughtException(e);
        }
    }

    /// Returns an exception for propagation, preserving errors as errors.
    private static Exception asException(Throwable failure) {
        if (failure instanceof Error e)
            throw e;
        return failure instanceof Exception e ? e : new Exception(failure);
    }

    /// Retains the first batch failure, giving errors precedence over ordinary exceptions.
    private static Throwable batchFailure(@Nullable Throwable previous, Throwable next) {
        return previous == null || next instanceof Error && !(previous instanceof Error) ? next : previous;
    }

    /// Returns whether this task tree was cancelled or the calling thread is interrupted.
    private boolean isCancelledOrInterrupted() {
        return isCancelled() || Thread.currentThread().isInterrupted();
    }

    /// Throws when cancellation has been requested or the current worker is interrupted.
    private void checkCancellation() {
        if (isCancelledOrInterrupted())
            throw new CancellationException("Cancelled by user");
    }

    /// Executes children under a single parent and shares the handle's listeners and cancellation.
    @NotNullByDefault
    private final class Context<R extends @Nullable Object> implements TaskExecutionContext {
        /// The task executed in this context.
        private final Task<R> task;

        /// Executor inherited by children that have no executor preference.
        private final Executor executor;

        /// Stage declared by the running task or inherited from its parent.
        private final @Nullable TaskStage stage;

        /// Binds the task to its effective executor and stage for this execution.
        Context(Task<R> task, Executor executor, @Nullable TaskStage stage) {
            this.task = task;
            this.executor = executor;
            this.stage = stage;
        }

        @Override
        public boolean isCancelled() {
            return isCancelledOrInterrupted();
        }

        /// {@inheritDoc}
        @Override
        public void addStageItems(int count) {
            if (stage != null)
                notifyListeners(listener -> listener.onStageProgress(stage, 0, count));
        }

        /// {@inheritDoc}
        @Override
        public void completeStageItem() {
            if (stage != null)
                notifyListeners(listener -> listener.onStageProgress(stage, 1, 0));
        }

        @Override
        public <T extends @Nullable Object> T run(Task<T> task) throws Exception {
            task.claimExecution();
            return runClaimed(this, task);
        }

        @Override
        public <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks) throws Exception {
            checkCancellation();
            List<Work<? extends T>> children = new ArrayList<>(tasks.size());
            @Nullable Throwable failure = null;
            for (Task<? extends T> child : List.copyOf(tasks)) {
                try {
                    child.claimExecution();
                    children.add(submitChild(prepare(this, child)));
                } catch (RuntimeException | Error e) {
                    failure = batchFailure(failure, e);
                }
            }

            List<T> results = new ArrayList<>(children.size());
            for (Work<? extends T> child : children) {
                try {
                    results.add(await(child));
                } catch (Throwable e) {
                    // Drain every sibling, including after failure. Errors must reach the global handler.
                    failure = batchFailure(failure, e);
                }
            }
            if (failure != null)
                throw asException(failure);
            checkCancellation();
            return results;
        }

        @Override
        public <T extends @Nullable Object> T call(Executor executor, Callable<T> action) throws Exception {
            if (executor == currentExecutor())
                return action.call();
            Work<T> work = new Work<>(executor, action);
            work.submit();
            return await(work);
        }
    }

    /// Waits for completion while allowing a constrained worker to run other work for its executor.
    /// An interruption is reported after the submitted work has stopped.
    private <T extends @Nullable Object> T await(Work<T> work) throws Exception {
        @Nullable Executor current = currentExecutor();
        boolean interrupted = false;
        while (!work.isDone()) {
            @Nullable Work<?> next = null;
            lock.lock();
            try {
                if (work.isDone())
                    break;
                next = work.availableFor(current);
                if (next == null)
                    work.changed.await();
            } catch (InterruptedException e) {
                interrupted = true;
            } finally {
                lock.unlock();
            }
            if (next != null)
                next.run();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException();
        }
        try {
            return work.get();
        } catch (ExecutionException e) {
            throw asException(e.getCause());
        }
    }

    /// A submission that can be claimed once, either by its executor or by a waiting worker of that executor.
    @NotNullByDefault
    private final class Work<T extends @Nullable Object> extends FutureTask<T> {
        /// Executor whose workers may run this submission.
        private final Executor executor;

        /// Signals changes in the set of runnable descendants or the completion of this work.
        private final Condition changed = lock.newCondition();

        /// Submission in this task tree that created this work.
        private final @Nullable Work<?> parent;

        /// Accepted, unclaimed descendants on constrained executors, guarded by the enclosing lock.
        private @Nullable Map<Executor, Set<Work<?>>> pending;

        /// Whether this work has been made available to waiting workers.
        private boolean queued;

        /// Indicates that a worker has claimed this submission, guarded by the enclosing lock.
        private boolean started;

        /// Creates a submission for the given executor.
        Work(Executor executor, Callable<T> action) {
            super(action);
            this.executor = executor;
            @Nullable Work<?> current = CURRENT_WORK.get();
            this.parent = current != null && current.owner() == DefaultTaskHandle.this ? current : null;
        }

        /// Returns the task tree owning this submission.
        private DefaultTaskHandle owner() {
            return DefaultTaskHandle.this;
        }

        /// Finds waiting work in this branch that the given worker can run.
        private @Nullable Work<?> availableFor(@Nullable Executor worker) {
            if (queued && executor == worker)
                return this;
            @Nullable Set<Work<?>> available = pending == null ? null : pending.get(worker);
            return available != null ? available.iterator().next() : null;
        }

        /// Offers this submission to its executor before making it available to waiting workers.
        void submit() {
            executor.execute(this);
            // These executors provide a worker per active task. Waiting parents need only help
            // constrained executors, where they could otherwise occupy every available worker.
            if (executor == Schedulers.io() || executor == Schedulers.tasks() && Runtime.version().feature() >= 21)
                return;
            lock.lock();
            try {
                if (!started) {
                    queued = true;
                    changed.signal();
                    // Index only this work's ancestors. A waiting worker can then claim a
                    // descendant without scanning or recursively entering unrelated siblings.
                    for (@Nullable Work<?> node = parent; node != null; node = node.parent) {
                        if (node.pending == null)
                            node.pending = new IdentityHashMap<>();
                        node.pending.computeIfAbsent(executor, ignored -> new LinkedHashSet<>()).add(this);
                        node.changed.signal();
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        /// Publishes a submission failure to waiters.
        void reject(Throwable failure) {
            setException(failure);
        }

        @Override
        public void run() {
            lock.lock();
            try {
                if (started)
                    return;
                started = true;
                if (queued) {
                    queued = false;
                    for (@Nullable Work<?> node = parent; node != null; node = node.parent) {
                        Map<Executor, Set<Work<?>>> index = Objects.requireNonNull(node.pending);
                        Set<Work<?>> available = index.get(executor);
                        available.remove(this);
                        if (available.isEmpty())
                            index.remove(executor);
                    }
                }
            } finally {
                lock.unlock();
            }

            @Nullable Work<?> previous = CURRENT_WORK.get();
            CURRENT_WORK.set(this);
            try {
                super.run();
            } finally {
                if (previous == null)
                    CURRENT_WORK.remove();
                else
                    CURRENT_WORK.set(previous);
            }
        }

        @Override
        protected void done() {
            lock.lock();
            try {
                changed.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    /// Sets the handler for unexpected runtime exceptions from asynchronous task execution.
    public static void setUncaughtExceptionHandler(Thread.@Nullable UncaughtExceptionHandler handler) {
        uncaughtExceptionHandler = handler;
    }
}
