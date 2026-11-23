// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import com.google.gson.JsonParseException;
import javafx.application.Platform;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Runs a task tree with shared cancellation, stage inheritance, and lifecycle notifications.
@NotNullByDefault
final class TaskExecution {
    /// The framework submission currently using this thread.
    private static final ThreadLocal<@Nullable Work<?>> CURRENT_WORK = new ThreadLocal<>();

    /// Handler for unexpected runtime exceptions from a root task.
    private static volatile Thread.@Nullable UncaughtExceptionHandler uncaughtExceptionHandler;

    /// Guards the waiting threads and the work they can execute without losing thread affinity.
    private final ReentrantLock lock = new ReentrantLock();

    /// Shared snapshot for executions with no registered listeners.
    private static final TaskListener @Unmodifiable [] NO_LISTENERS = {};

    /// Terminal snapshot that blocks further listener registration.
    private static final TaskListener @Unmodifiable [] FINISHED = {};

    /// Current listeners in registration order. Each notification reads one snapshot.
    private volatile TaskListener @Unmodifiable [] taskListeners = NO_LISTENERS;

    /// Atomically replaces the listener snapshot when registrations change.
    private static final VarHandle TASK_LISTENERS_HANDLE;

    static {
        try {
            TASK_LISTENERS_HANDLE = MethodHandles.lookup()
                    .findVarHandle(TaskExecution.class, "taskListeners", TaskListener[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// Whether cancellation has been requested.
    private volatile boolean cancellationRequested;

    /// Creates an execution for one root and its descendants.
    TaskExecution() {
    }

    /// Registers a listener for subsequent notifications. Register before starting to observe the entire execution.
    void addTaskListener(TaskListener taskListener) {
        while (true) {
            TaskListener @Unmodifiable [] current = taskListeners;
            if (current == FINISHED)
                return;
            TaskListener[] updated = Arrays.copyOf(current, current.length + 1);
            updated[current.length] = taskListener;

            if (TASK_LISTENERS_HANDLE.compareAndSet(this, current, updated)) {
                return;
            }
        }
    }

    /// Removes the first matching registration from subsequent notifications.
    void removeTaskListener(TaskListener taskListener) {
        while (true) {
            TaskListener @Unmodifiable [] current = taskListeners;

            int index = 0;
            while (index < current.length && current[index] != taskListener) {
                index++;
            }

            if (index == current.length) {
                return;
            }

            TaskListener[] updated;

            if (current.length == 1) {
                updated = NO_LISTENERS;
            } else {
                updated = new TaskListener[current.length - 1];
                System.arraycopy(current, 0, updated, 0, index);
                System.arraycopy(current, index + 1, updated, index, updated.length - index);
            }

            if (TASK_LISTENERS_HANDLE.compareAndSet(this, current, updated)) {
                return;
            }
        }
    }

    /// Notifies a snapshot in registration order and reports each listener's failure independently.
    private void notifyListeners(Consumer<TaskListener> notification) {
        for (TaskListener listener : taskListeners) {
            try {
                notification.accept(listener);
            } catch (Throwable e) {
                LOG.warning("Task listener failed", e);
            }
        }
    }

    /// Starts a ready root asynchronously and reports submission failures to the caller.
    void start(Task<?> root) {
        notifyListeners(listener -> listener.onExecutionStarted(root));
        try {
            Executor scheduler = Schedulers.tasks();
            scheduler.execute(new Work<>(scheduler, () -> runToCompletion(root)));
        } catch (Throwable failure) {
            fail(root, null, failure);
            notifyExecutionFinished(root);
            throw failure;
        }
    }

    /// Runs a ready root and returns its outcome, preserving the waiting thread's interrupt status.
    boolean test(Task<?> root) {
        notifyListeners(listener -> listener.onExecutionStarted(root));
        return runToCompletion(root);
    }

    /// Runs a ready root synchronously and propagates its failure to the caller.
    <T extends @Nullable Object> T run(Task<T> root) throws Exception {
        notifyListeners(listener -> listener.onExecutionStarted(root));
        try {
            return runRoot(root, true);
        } finally {
            notifyExecutionFinished(root);
        }
    }

    /// Reports uncaught failures and returns whether the root succeeded.
    private boolean runToCompletion(Task<?> root) {
        try {
            runRoot(root, false);
        } catch (Exception e) {
            LOG.warning("An exception occurred in task execution", e);
            Thread.@Nullable UncaughtExceptionHandler handler = uncaughtExceptionHandler;
            if (handler != null && e instanceof RuntimeException && !(e instanceof CancellationException)
                    && !(e instanceof JsonParseException) && !(e instanceof RejectedExecutionException)) {
                handler.uncaughtException(Thread.currentThread(), e);
            }
        } catch (Throwable failure) {
            Lang.handleUncaughtException(failure);
        } finally {
            notifyExecutionFinished(root);
        }
        return root.getState() == Task.TaskState.SUCCEEDED;
    }

    /// Publishes a root failure even if its executor could not be reached.
    private <T extends @Nullable Object> T runRoot(Task<T> root, boolean propagateWaitInterruption) throws Exception {
        try {
            return runReadyTask(null, root, propagateWaitInterruption);
        } catch (Throwable failure) {
            if (root.getState() == Task.TaskState.READY)
                fail(root, null, failure);
            throw failure;
        }
    }

    /// Reports the root's published outcome and releases references to its listeners.
    private void notifyExecutionFinished(Task<?> root) {
        boolean success = root.getState() == Task.TaskState.SUCCEEDED;
        try {
            notifyListeners(listener -> listener.onExecutionFinished(root, success));
        } finally {
            taskListeners = FINISHED;
        }
    }

    /// Requests cancellation of this execution.
    void cancel() {
        cancellationRequested = true;
    }

    /// Returns whether cancellation was requested for this execution.
    boolean isCancellationRequested() {
        return cancellationRequested;
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
        TaskStage.@Nullable Scope scope = parent == null ? null : parent.stageScope;
        try {
            @Unmodifiable List<TaskStage.Hint> hints = task.getStageHints();
            if (!hints.isEmpty())
                notifyListeners(listener -> listener.onStagesDeclared(hints));
            @Nullable TaskStage stage = task.getStage();
            if (stage != null) {
                TaskStage.Scope declared = new TaskStage.Scope(stage, task, scope);
                scope = declared;
                notifyListeners(listener -> listener.onStageStarted(declared));
            }
            @Nullable Executor executor = task.getExecutor();
            if (executor == null)
                executor = parent == null ? Schedulers.tasks() : parent.executor;
            return new Context<>(task, executor, scope);
        } catch (Throwable failure) {
            fail(task, scope, failure);
            throw failure;
        }
    }

    /// Dispatches a ready task, running it inline on a worker of its effective executor.
    private <T extends @Nullable Object> T runReadyTask(@Nullable Context<?> parent, Task<T> task, boolean propagateWaitInterruption) throws Exception {
        Context<T> context = prepare(parent, task);
        if (context.executor == currentExecutor())
            return execute(context);
        return await(submitChild(context), propagateWaitInterruption);
    }

    /// Schedules a child and records task failure if the executor rejects the submission.
    private <T extends @Nullable Object> Work<T> submitChild(Context<T> context) {
        Work<T> work = new Work<>(context.executor, () -> execute(context));
        try {
            checkCancellation();
            work.submit();
        } catch (Throwable failure) {
            fail(context.task, context.stageScope, failure);
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
            notifyListeners(listener -> listener.onTaskStarted(task, context.stageScope));
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
            fail(task, context.stageScope, cause);
            throw Task.rethrow(cause);
        }

        task.publishResult(result);
        notifyTaskFinished(task, context.stageScope, null);
        if (task.getSignificance().shouldLog())
            LOG.trace("Task finished: " + task.getName());
        return result;
    }

    /// Records the failure before delivering terminal notifications.
    private void fail(Task<?> task, TaskStage.@Nullable Scope scope, Throwable cause) {
        task.publishException(cause);
        notifyTaskFinished(task, scope, cause);
    }

    /// Reports the task's published outcome, then closes its stage if it declared one.
    private void notifyTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
        notifyListeners(listener -> listener.onTaskFinished(task, scope, failure));
        if (scope != null && scope.task() == task)
            notifyListeners(listener -> listener.onStageFinished(scope, failure));
    }

    /// Retains the first batch failure, giving errors precedence over ordinary exceptions.
    private static Throwable batchFailure(@Nullable Throwable previous, Throwable next) {
        return previous == null || next instanceof Error && !(previous instanceof Error) ? next : previous;
    }

    /// Returns whether this task tree was cancelled or the calling thread is interrupted.
    private boolean isCancelledOrInterrupted() {
        return isCancellationRequested() || Thread.currentThread().isInterrupted();
    }

    /// Throws when cancellation has been requested or the current worker is interrupted.
    private void checkCancellation() {
        if (isCancelledOrInterrupted())
            throw new CancellationException("Cancelled by user");
    }

    /// Executes children under a single parent and shares the root's listeners and cancellation.
    @NotNullByDefault
    private final class Context<R extends @Nullable Object> implements TaskExecutionContext, TaskProgress {
        /// The task executed in this context.
        private final Task<R> task;

        /// Executor inherited by children that have no executor preference.
        private final Executor executor;

        /// Stage scope opened by the running task or inherited from its parent.
        private final TaskStage.@Nullable Scope stageScope;

        /// Binds the task to its effective executor and stage for this execution.
        Context(Task<R> task, Executor executor, TaskStage.@Nullable Scope stageScope) {
            this.task = task;
            this.executor = executor;
            this.stageScope = stageScope;
        }

        /// {@inheritDoc}
        @Override
        public boolean isCancelled() {
            return isCancelledOrInterrupted();
        }

        /// {@inheritDoc}
        @Override
        public TaskProgress progress() {
            return this;
        }

        /// {@inheritDoc}
        @Override
        public void update(double fraction) {
            if (!(fraction >= 0 && fraction <= 1))
                throw new IllegalArgumentException("Invalid progress: " + fraction);
            notifyListeners(listener -> listener.onTaskProgress(task, fraction));
        }

        /// {@inheritDoc}
        @Override
        public void addStageItems(int count) {
            if (stageScope != null)
                notifyListeners(listener -> listener.onStageProgress(stageScope, 0, count));
        }

        /// {@inheritDoc}
        @Override
        public void completeStageItem() {
            if (stageScope != null)
                notifyListeners(listener -> listener.onStageProgress(stageScope, 1, 0));
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> T run(Task<T> task) throws Exception {
            task.markReadyAsChild();
            return runReadyTask(this, task, true);
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks) throws Exception {
            checkCancellation();
            List<Work<? extends T>> children = new ArrayList<>(tasks.size());
            @Nullable Throwable failure = null;
            for (Task<? extends T> child : List.copyOf(tasks)) {
                try {
                    child.markReadyAsChild();
                    children.add(submitChild(prepare(this, child)));
                } catch (Throwable e) {
                    failure = batchFailure(failure, e);
                }
            }

            List<T> results = new ArrayList<>(children.size());
            for (Work<? extends T> child : children) {
                try {
                    results.add(await(child, true));
                } catch (Throwable e) {
                    // Drain every sibling before propagating the batch failure.
                    failure = batchFailure(failure, e);
                }
            }
            if (failure != null)
                throw Task.rethrow(failure);
            checkCancellation();
            return results;
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> T call(@Nullable Executor executor, Callable<T> action) throws Exception {
            if (executor == null || executor == currentExecutor())
                return action.call();
            Work<T> work = new Work<>(executor, action);
            work.submit();
            return await(work, true);
        }
    }

    /// Waits for completion while allowing a constrained worker to run other work for its executor.
    /// Restores any interruption after the work stops. A caller reporting the root's outcome
    /// observes that outcome even if the waiting thread was interrupted.
    private <T extends @Nullable Object> T await(Work<T> work, boolean propagateWaitInterruption) throws Exception {
        @Nullable Executor current = currentExecutor();
        boolean interrupted = false;
        while (!work.isDone()) {
            @Nullable Work<?> next = null;
            lock.lock();
            try {
                if (work.isDone())
                    break;
                next = work.availableFor(current);
                if (next == null) {
                    if (work.changed == null)
                        work.changed = lock.newCondition();
                    work.changed.await();
                }
            } catch (InterruptedException e) {
                interrupted = true;
            } finally {
                lock.unlock();
            }
            if (next != null)
                next.run();
        }
        try {
            if (interrupted && propagateWaitInterruption)
                throw new InterruptedException();
            return work.get();
        } catch (ExecutionException e) {
            throw Task.rethrow(Objects.requireNonNull(e.getCause()));
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

    /// A submission that can be claimed once, either by its executor or by a waiting worker of that executor.
    @NotNullByDefault
    private final class Work<T extends @Nullable Object> extends FutureTask<T> {
        /// Executor whose workers may run this submission.
        private final Executor executor;

        /// Created under the execution lock on the first wait for completion or runnable descendants.
        private @Nullable Condition changed;

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
            this.parent = current != null && current.owner() == TaskExecution.this ? current : null;
        }

        /// Returns the task tree owning this submission.
        private TaskExecution owner() {
            return TaskExecution.this;
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
                    // Index only this work's ancestors. A waiting worker can then claim a
                    // descendant without scanning or recursively entering unrelated siblings.
                    for (@Nullable Work<?> node = parent; node != null; node = node.parent) {
                        if (node.pending == null)
                            node.pending = new IdentityHashMap<>();
                        node.pending.computeIfAbsent(executor, ignored -> new LinkedHashSet<>()).add(this);
                        if (node.changed != null)
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

        /// Claims this work and executes it once in its assigned executor context.
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

        /// Wakes the parent waiting for this submission.
        @Override
        protected void done() {
            lock.lock();
            try {
                if (changed != null)
                    changed.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    /// Sets the handler for unexpected runtime exceptions reported by start or test.
    static void setUncaughtExceptionHandler(Thread.@Nullable UncaughtExceptionHandler handler) {
        uncaughtExceptionHandler = handler;
    }
}
