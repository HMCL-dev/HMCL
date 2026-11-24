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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Runs a task tree with shared cancellation, stage inheritance, and lifecycle notifications.
@NotNullByDefault
final class TaskExecution {
    /// The framework submission currently using this thread.
    private static final ThreadLocal<@Nullable Work<?>> CURRENT_WORK = new ThreadLocal<>();

    /// Tasks whose bodies or lifecycle callbacks are on the calling thread's stack.
    private static final ThreadLocal<@Nullable ArrayDeque<Binding>> ACTIVE_TASKS = new ThreadLocal<>();

    /// Handler for unexpected runtime exceptions from a root task.
    private static volatile Thread.@Nullable UncaughtExceptionHandler uncaughtExceptionHandler;

    /// Guards the waiting threads and the work they can execute without losing thread affinity.
    private final ReentrantLock lock = new ReentrantLock();

    /// Shared observers wait here for a published outcome or eligible work.
    private final Condition sharedChanged = lock.newCondition();

    /// Number of shared observers, updated under the lock.
    private volatile int sharedWaiters;

    /// Shared snapshot for executions with no registered listeners.
    private static final TaskListener @Unmodifiable [] NO_LISTENERS = {};

    /// Terminal snapshot that blocks further listener registration.
    private static final TaskListener @Unmodifiable [] FINISHED = {};

    /// Current listeners in registration order. Each notification reads one snapshot.
    private final AtomicReference<TaskListener @Unmodifiable []> taskListeners = new AtomicReference<>(NO_LISTENERS);

    /// Whether cancellation has been requested.
    private volatile boolean cancellationRequested;

    /// The task waiting for this root's synchronous submission, through its completion callbacks.
    private volatile @Nullable Binding synchronousCaller;

    /// The execution and logical parent of an admitted task. A completed child drops both references.
    @NotNullByDefault
    static final class Binding {
        /// Marker retaining the root-only API restrictions after a child completes.
        static final Binding FINISHED_CHILD = new Binding(null, null);

        /// The tree that schedules this task.
        private final @Nullable TaskExecution execution;

        /// Parent in the execution tree, including decorators executed inline.
        private final @Nullable Binding parent;

        /// Submission whose work a shared observer may help, when this task was dispatched separately.
        private volatile @Nullable Work<?> work;

        /// Binds a task to its tree and parent.
        private Binding(@Nullable TaskExecution execution, @Nullable Binding parent) {
            this.execution = execution;
            this.parent = parent;
        }

        /// Reserves a new root execution.
        static Binding root() {
            return new Binding(new TaskExecution(), null);
        }

        /// Binds a child to the same tree.
        Binding child() {
            return new Binding(owner(), this);
        }

        /// Returns the execution controlling this active task or root.
        TaskExecution owner() {
            return Objects.requireNonNull(execution);
        }

        /// Returns whether this binding controls a root execution.
        boolean isRoot() {
            return parent == null && execution != null;
        }

        /// Returns the execution for a root-only operation.
        TaskExecution requireRootExecution() {
            if (!isRoot())
                throw new IllegalStateException("Task has been submitted as a child");
            return owner();
        }

        /// Wakes observers when work becomes available or the task finishes, including inline tasks.
        void setWork(@Nullable Work<?> work) {
            this.work = work;
            owner().signalSharedWaiters();
        }

        /// Finds the enclosing submission for a task running inline.
        private @Nullable Work<?> enclosingWork() {
            for (@Nullable Binding b = this; b != null; b = b.parent) {
                @Nullable Work<?> found = b.work;
                if (found != null)
                    return found;
            }
            return null;
        }

        /// Tracks user code that must return before this task or its ancestors can finish.
        private void enter() {
            @Nullable ArrayDeque<Binding> active = ACTIVE_TASKS.get();
            if (active == null) {
                active = new ArrayDeque<>();
                ACTIVE_TASKS.set(active);
            }
            active.push(this);
        }

        /// Leaves the current task's user code.
        private void leave() {
            ArrayDeque<Binding> active = Objects.requireNonNull(ACTIVE_TASKS.get());
            active.pop();
            if (active.isEmpty())
                ACTIVE_TASKS.remove();
        }

        /// Rejects a synchronous wait for a task that depends on the caller returning.
        private void checkWait() {
            @Nullable ArrayDeque<Binding> active = ACTIVE_TASKS.get();
            if (active == null)
                return;
            for (Binding frame : active) {
                for (@Nullable Binding b = frame; b != null; b = b.parent != null ? b.parent : b.owner().synchronousCaller) {
                    if (b == this)
                        throw new IllegalStateException("Cannot wait for the current task or an ancestor");
                }
            }
            // An inline source cannot start until its enclosing decorator's callback returns.
            if (work == null) {
                for (@Nullable Binding b = parent; b != null; b = b.parent) {
                    if (active.contains(b))
                        throw new IllegalStateException("Cannot wait for an inline task from its enclosing task");
                    if (b.work != null)
                        break;
                }
            }
        }
    }

    /// Registers a listener for subsequent notifications. Register before starting to observe the entire execution.
    void addTaskListener(TaskListener taskListener) {
        taskListeners.updateAndGet(current -> {
            if (current == FINISHED)
                return current;
            TaskListener[] updated = Arrays.copyOf(current, current.length + 1);
            updated[current.length] = taskListener;
            return updated;
        });
    }

    /// Removes the first matching registration from subsequent notifications.
    void removeTaskListener(TaskListener taskListener) {
        taskListeners.updateAndGet(current -> {
            int index = 0;
            while (index < current.length && current[index] != taskListener)
                index++;
            if (index == current.length)
                return current;
            if (current.length == 1)
                return NO_LISTENERS;
            TaskListener[] updated = new TaskListener[current.length - 1];
            System.arraycopy(current, 0, updated, 0, index);
            System.arraycopy(current, index + 1, updated, index, updated.length - index);
            return updated;
        });
    }

    /// Notifies a snapshot in registration order and reports each listener's failure independently.
    private void notifyListeners(Consumer<TaskListener> notification) {
        for (TaskListener listener : taskListeners.get()) {
            try {
                notification.accept(listener);
            } catch (Throwable e) {
                LOG.warning("Task listener failed", e);
            }
        }
    }

    /// Starts a ready root asynchronously and reports submission failures to the caller.
    void start(Task<?> root) {
        notifyExecutionStarted(root);
        try {
            Executor scheduler = Schedulers.tasks();
            Work<Boolean> work = new Work<>(scheduler, () -> runToCompletion(root));
            root.binding().setWork(work);
            work.submit();
        } catch (Throwable failure) {
            fail(root, null, failure);
            notifyExecutionFinished(root);
            throw failure;
        }
    }

    /// Runs a ready root and returns its outcome, preserving the waiting thread's interrupt status.
    boolean test(Task<?> root) {
        synchronousCaller = currentTask();
        notifyExecutionStarted(root);
        return runToCompletion(root);
    }

    /// Runs a ready root synchronously and propagates its failure to the caller.
    <T extends @Nullable Object> T run(Task<T> root) throws Exception {
        synchronousCaller = currentTask();
        notifyExecutionStarted(root);
        try {
            return runRoot(root, true);
        } finally {
            notifyExecutionFinished(root);
        }
    }

    /// Delivers the start event before dispatch, where waiting for the root would block its launch.
    private void notifyExecutionStarted(Task<?> root) {
        Binding binding = root.binding();
        binding.enter();
        try {
            notifyListeners(listener -> listener.onExecutionStarted(root));
        } finally {
            binding.leave();
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
        return root.getState() == Task.State.SUCCEEDED;
    }

    /// Publishes a root failure even if its executor could not be reached.
    private <T extends @Nullable Object> T runRoot(Task<T> root, boolean propagateWaitInterruption) throws Exception {
        try {
            return prepare(root, Schedulers.tasks(), null).runReady(propagateWaitInterruption);
        } catch (Throwable failure) {
            if (root.getState() == Task.State.READY)
                fail(root, null, failure);
            throw failure;
        }
    }

    /// Reports the root's published outcome and releases references to its listeners.
    private void notifyExecutionFinished(Task<?> root) {
        boolean success = root.getState() == Task.State.SUCCEEDED;
        try {
            notifyListeners(listener -> listener.onExecutionFinished(root, success));
        } finally {
            taskListeners.set(FINISHED);
            synchronousCaller = null;
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

    /// Returns the innermost task whose user code is running on this thread.
    private static @Nullable Binding currentTask() {
        @Nullable ArrayDeque<Binding> active = ACTIVE_TASKS.get();
        return active == null ? null : active.peek();
    }

    /// Returns the executor whose work the caller can execute inline.
    private static @Nullable Executor currentExecutor() {
        @Nullable Work<?> current = CURRENT_WORK.get();
        if (current != null)
            return current.executor;
        return Platform.isFxApplicationThread() ? Schedulers.javafx() : null;
    }

    /// Opens stages from the outermost decorator inward and resolves the source executor once.
    private <T extends @Nullable Object> Context<T> prepare(Task<T> task, Executor inheritedExecutor,
                                                             TaskStage.@Nullable Scope scope) {
        Binding binding = task.binding();
        binding.enter();
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
            if (task instanceof Task.Decorator<T> decorator) {
                if (isCancelledOrInterrupted())
                    throw new CancellationException("Cancelled by user");
                if (!decorator.source.tryMarkReadyAsChild(binding))
                    return new Context<>(task, inheritedExecutor, scope, null);
                Context<T> source = prepare(decorator.source, inheritedExecutor, scope);
                return new Context<>(task, source.executor, scope, source);
            }
            @Nullable Executor executor = task.getExecutor();
            return new Context<>(task, executor != null ? executor : inheritedExecutor, scope, null);
        } catch (Throwable failure) {
            fail(task, scope, failure);
            throw failure;
        } finally {
            binding.leave();
        }
    }

    /// Records the failure before delivering terminal notifications.
    private void fail(Task<?> task, TaskStage.@Nullable Scope scope, Throwable cause) {
        task.finish(null, cause);
        notifyTaskFinished(task, scope, cause);
    }

    /// Reports the task's published outcome, then closes its stage if it declared one.
    private void notifyTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
        notifyListeners(listener -> listener.onTaskFinished(task, scope, failure));
        if (scope != null && scope.task() == task)
            notifyListeners(listener -> listener.onStageFinished(scope, failure));
    }

    /// Retains the first batch failure, giving errors precedence over ordinary exceptions.
    static Throwable batchFailure(@Nullable Throwable previous, Throwable next) {
        return previous == null || next instanceof Error && !(previous instanceof Error) ? next : previous;
    }

    /// Reports why batch admission stopped, preserving the caller's interrupt status.
    static Exception batchCancellation() {
        return Thread.currentThread().isInterrupted()
                ? new InterruptedException()
                : new CancellationException("Cancelled by user");
    }

    /// Returns whether this task tree was cancelled or the calling thread is interrupted.
    private boolean isCancelledOrInterrupted() {
        return isCancellationRequested() || Thread.currentThread().isInterrupted();
    }

    /// A prepared task with its executor, stage and child execution context.
    @NotNullByDefault
    private final class Context<R extends @Nullable Object> implements TaskExecutionContext, TaskProgress {
        /// The task executed in this context.
        private final Task<R> task;

        /// Executor inherited by children that have no executor preference.
        private final Executor executor;

        /// Stage scope opened by the running task or inherited from its parent.
        private final TaskStage.@Nullable Scope stageScope;

        /// Source reserved with a stage decorator, ready to run inline when its body dispatches it.
        private final @Nullable Context<R> source;

        /// Binds the task to its effective executor, stage and any source in the same submission.
        Context(Task<R> task, Executor executor, TaskStage.@Nullable Scope stageScope, @Nullable Context<R> source) {
            this.task = task;
            this.executor = executor;
            this.stageScope = stageScope;
            this.source = source;
        }

        /// Runs on the current worker when possible, otherwise submits and waits.
        private R runReady(boolean propagateWaitInterruption) throws Exception {
            if (executor != currentExecutor())
                return submit(null).await(propagateWaitInterruption);
            @Nullable Work<?> current = CURRENT_WORK.get();
            if (current != null && current.owner() == TaskExecution.this)
                return execute();
            // A root entered from another tree or directly on JavaFX needs its own helping index.
            Work<R> inline = new Work<>(executor, this::execute);
            task.binding().setWork(inline);
            inline.run();
            return inline.completedOutcome();
        }

        /// Submits the prepared task and publishes any rejection to its waiters.
        private Work<R> submit(@Nullable BatchWindow window) {
            Work<R> work = new Work<>(executor, this::execute, window);
            Binding binding = task.binding();
            binding.enter();
            try {
                checkCancellation();
                binding.setWork(work);
                work.submit();
            } catch (Throwable failure) {
                fail(failure);
                work.reject(failure);
            } finally {
                binding.leave();
            }
            return work;
        }

        /// Publishes a successful result before delivering completion notifications.
        private R execute() throws Exception {
            Binding binding = task.binding();
            binding.enter();
            try {
                R result = invokeBody();
                task.finish(result, null);
                notifyTaskFinished(task, stageScope, null);
                if (task.getSignificance().shouldLog()) {
                    try {
                        LOG.trace("Task finished: " + task.displayName());
                    } catch (Throwable failure) {
                        LOG.warning("Failed to log task completion", failure);
                    }
                }
                return result;
            } finally {
                binding.leave();
            }
        }

        /// Checks cancellation around user code and records a failure before propagating it.
        private R invokeBody() throws Exception {
            try {
                checkCancellation();
                if (task.getSignificance().shouldLog()) {
                    LOG.trace("Executing task: " + task.displayName());
                    checkCancellation();
                }
                task.markRunning();
                notifyListeners(listener -> listener.onTaskStarted(task, stageScope));
                checkCancellation();
                R result = task.execute(this);
                checkCancellation();
                return result;
            } catch (Throwable failure) {
                Throwable cause = Objects.requireNonNullElse(Lang.resolveException(failure), failure);
                if (cause instanceof InterruptedException)
                    cause = new CancellationException(cause.getMessage()).initCause(cause);
                fail(cause);
                throw Task.rethrow(cause);
            }
        }

        /// Closes any prepared sources whose bodies were skipped before reporting this task's failure.
        private void fail(Throwable cause) {
            if (source != null && source.task.getState() == Task.State.READY)
                source.fail(cause);
            TaskExecution.this.fail(task, stageScope, cause);
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
        public <T extends @Nullable Object> T run(Task<T> child) throws Exception {
            if (source != null && source.task == child) {
                source.execute();
                return child.resultNow();
            }
            checkCancellation();
            if (!child.tryMarkReadyAsChild(task.binding()))
                return child.completedOutcome();
            return prepare(child, executor, stageScope).runReady(true);
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks) throws Exception {
            return runAll(tasks, Integer.MAX_VALUE);
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks, int maxConcurrency) throws Exception {
            if (maxConcurrency <= 0)
                throw new IllegalArgumentException("maxConcurrency must be positive");
            if (isCancelled())
                throw batchCancellation();
            @Unmodifiable List<? extends Task<? extends T>> batch = List.copyOf(tasks);
            @Nullable BatchWindow window = batch.size() > maxConcurrency ? new BatchWindow(maxConcurrency) : null;
            Binding parent = task.binding();
            // Every encountered instance gets one entry. Only newly scheduled children have work to await.
            Map<Task<? extends T>, @Nullable Work<? extends T>> submissions = new IdentityHashMap<>(batch.size());
            List<Task<? extends T>> admitted = new ArrayList<>(batch.size());
            @Nullable Throwable failure = null;
            for (Task<? extends T> child : batch) {
                try {
                    if (window != null)
                        window.awaitSlot();
                    if (isCancelled()) {
                        failure = batchFailure(failure, batchCancellation());
                        break;
                    }
                    if (submissions.containsKey(child))
                        throw new IllegalStateException("Duplicate task in batch");
                    submissions.put(child, null);
                    if (child.tryMarkReadyAsChild(parent))
                        submissions.put(child, prepare(child, executor, stageScope).submit(window));
                    admitted.add(child);
                } catch (Throwable e) {
                    failure = batchFailure(failure, e);
                }
            }

            return awaitBatch(admitted, submissions, failure);
        }

        /// Drains admitted tasks in input order, including after a submission or a wait fails.
        private <T extends @Nullable Object> List<T> awaitBatch(
                List<Task<? extends T>> admitted, Map<Task<? extends T>, @Nullable Work<? extends T>> submissions,
                @Nullable Throwable failure) throws Exception {
            List<T> results = new ArrayList<>(admitted.size());
            // Defer interruption while servicing callbacks needed by admitted children.
            boolean interrupted = Thread.interrupted();
            if (interrupted && failure == null)
                failure = new InterruptedException();
            try {
                for (Task<? extends T> child : admitted) {
                    @Nullable Throwable childFailure = null;
                    try {
                        @Nullable Work<? extends T> work = submissions.get(child);
                        results.add(work == null ? child.completedOutcome() : work.await(false));
                    } catch (Throwable e) {
                        childFailure = e;
                    }
                    if (Thread.interrupted() && !interrupted) {
                        interrupted = true;
                        failure = batchFailure(failure, new InterruptedException());
                    }
                    if (childFailure != null)
                        failure = batchFailure(failure, childFailure);
                }
            } finally {
                if (interrupted)
                    Thread.currentThread().interrupt();
            }
            if (failure != null)
                throw Task.rethrow(failure);
            if (isCancelled())
                throw batchCancellation();
            return results;
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> T call(@Nullable Executor executor, Callable<T> action) throws Exception {
            if (executor == null || executor == currentExecutor())
                return action.call();
            Binding binding = task.binding();
            Work<T> work = new Work<>(executor, () -> {
                binding.enter();
                try {
                    return action.call();
                } finally {
                    binding.leave();
                }
            });
            work.submit();
            return work.await(true);
        }
    }

    /// Limits the number of unfinished branches submitted by one batch.
    private final class BatchWindow {
        /// Maximum number of admitted branches.
        private final int limit;

        /// Accepted branches tracked until completion, in submission order and guarded by the execution lock.
        private final Set<Work<?>> active = new LinkedHashSet<>();

        /// Wakes the submitting worker on branch completion or arrival of runnable descendants.
        private final Condition changed = lock.newCondition();

        /// Creates a window for a batch larger than its limit.
        BatchWindow(int limit) {
            this.limit = limit;
        }

        /// Waits for a slot, cancellation, or interruption, servicing work that needs the submitting worker.
        void awaitSlot() {
            @Nullable Executor current = currentExecutor();
            // The task scheduler supplies workers for queued branches. Its submitting worker
            // can service callbacks without getting stuck inside a slow branch itself.
            boolean helpQueuedBranches = current != Schedulers.tasks();
            while (true) {
                @Nullable Work<?> next = null;
                lock.lock();
                try {
                    if (active.size() < limit || isCancelledOrInterrupted())
                        return;
                    for (Work<?> branch : active) {
                        next = branch.availableFor(current, helpQueuedBranches);
                        if (next != null)
                            break;
                    }
                    if (next == null)
                        changed.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    lock.unlock();
                }
                if (next != null)
                    next.run();
            }
        }
    }

    /// Waits for this task's published outcome, helping its submission and preserving interruption.
    boolean awaitShared(Task<?> task, Binding binding) {
        @Nullable Executor worker = currentExecutor();
        boolean interrupted = false;
        lock.lock();
        sharedWaiters++;
        try {
            if (task.isDone())
                return false;
            binding.checkWait();
            while (!task.isDone()) {
                @Nullable Work<?> anchor = binding.enclosingWork();
                @Nullable Work<?> next = anchor == null ? null : anchor.availableFor(worker, true);
                if (next == null) {
                    try {
                        sharedChanged.await();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                } else {
                    lock.unlock();
                    try {
                        // Keep an interrupt already pending on the observer out of the helped work.
                        if (Thread.interrupted())
                            interrupted = true;
                        next.run();
                    } finally {
                        if (Thread.interrupted())
                            interrupted = true;
                        lock.lock();
                    }
                }
            }
        } finally {
            sharedWaiters--;
            lock.unlock();
            if (interrupted)
                Thread.currentThread().interrupt();
        }
        return interrupted;
    }

    /// Wakes shared observers after an outcome or a submission has been published.
    private void signalSharedWaiters() {
        if (sharedWaiters == 0)
            return;
        lock.lock();
        try {
            sharedChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// A submission that can be claimed once, either by its executor or by a waiting worker of that executor.
    @NotNullByDefault
    private final class Work<T extends @Nullable Object> extends FutureTask<T> {
        /// Executor whose workers may run this submission.
        private final Executor executor;

        /// Whether a waiting worker may take this submission from its executor.
        private final boolean canBeHelped;

        /// The admission window to release on completion, if this submission is a bounded batch item.
        private final @Nullable BatchWindow window;

        /// Shared with the batch window or created on the first wait for this submission.
        private @Nullable Condition changed;

        /// Submission in this task tree that created this work.
        private final @Nullable Work<?> parent;

        /// Accepted, unclaimed descendants on constrained executors, guarded by the enclosing lock.
        private @Nullable Map<Executor, Set<Work<?>>> pending;

        /// Registration state of a helpable submission, guarded by the execution lock.
        private DispatchState dispatchState = DispatchState.UNREGISTERED;

        /// A worker can start a submission before its submitter registers it for helping.
        private enum DispatchState {
            /// The submitter has not added this work to the helping index.
            UNREGISTERED,
            /// A waiting worker may take the work from its executor.
            QUEUED,
            /// Either its executor or a waiting worker has claimed the work.
            STARTED
        }

        /// Creates a submission for the given executor.
        Work(Executor executor, Callable<T> action) {
            this(executor, action, null);
        }

        /// Creates a submission and associates it with its batch window, when present.
        Work(Executor executor, Callable<T> action, @Nullable BatchWindow window) {
            super(action);
            this.executor = executor;
            this.canBeHelped = !(executor instanceof UnboundedExecutorService);
            this.window = window;
            this.changed = window == null ? null : window.changed;
            @Nullable Work<?> current = CURRENT_WORK.get();
            this.parent = current != null && current.owner() == TaskExecution.this ? current : null;
        }

        /// Returns the task tree owning this submission.
        private TaskExecution owner() {
            return TaskExecution.this;
        }

        /// Finds work for the given worker among the descendants or, if allowed, this submission.
        private @Nullable Work<?> availableFor(@Nullable Executor worker, boolean includeSelf) {
            if (worker == null)
                return null;
            if (includeSelf && dispatchState == DispatchState.QUEUED && executor == worker)
                return this;
            @Nullable Set<Work<?>> available = pending == null ? null : pending.get(worker);
            return available != null ? available.iterator().next() : null;
        }

        /// Submits the work, then registers it with its batch window and waiting workers as needed.
        void submit() {
            executor.execute(this);
            if ((!canBeHelped && window == null) || isDone())
                return;
            lock.lock();
            try {
                // Only the submitter admits branches. Completion may race with registration.
                if (window != null && !isDone())
                    window.active.add(this);
                if (!canBeHelped || dispatchState == DispatchState.STARTED)
                    return;
                dispatchState = DispatchState.QUEUED;
                // Index only this work's ancestors. A waiting worker can then claim a
                // descendant without scanning or recursively entering unrelated siblings.
                for (@Nullable Work<?> node = parent; node != null; node = node.parent) {
                    if (node.pending == null)
                        node.pending = new IdentityHashMap<>();
                    node.pending.computeIfAbsent(executor, ignored -> new LinkedHashSet<>()).add(this);
                    if (node.changed != null)
                        node.changed.signal();
                }
                if (sharedWaiters != 0)
                    sharedChanged.signalAll();
            } finally {
                lock.unlock();
            }
        }

        /// Publishes a submission failure to waiters.
        void reject(Throwable failure) {
            setException(failure);
        }

        /// Reads the completed submission and propagates its original failure to the caller.
        private T completedOutcome() throws Exception {
            try {
                return get();
            } catch (ExecutionException e) {
                throw Task.rethrow(Objects.requireNonNull(e.getCause()));
            }
        }

        /// Waits for completion while allowing a constrained worker to run other work for its executor.
        /// Restores any interruption after the work stops. A caller reporting the root's outcome
        /// observes that outcome even if the waiting thread was interrupted.
        T await(boolean propagateWaitInterruption) throws Exception {
            @Nullable Executor current = currentExecutor();
            boolean interrupted = false;
            while (!isDone()) {
                @Nullable Work<?> next = null;
                lock.lock();
                try {
                    if (isDone())
                        break;
                    next = availableFor(current, true);
                    if (next == null) {
                        if (changed == null)
                            changed = lock.newCondition();
                        changed.await();
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
                return completedOutcome();
            } finally {
                if (interrupted)
                    Thread.currentThread().interrupt();
            }
        }

        /// Claims work eligible for helping and removes it from its ancestors' indexes.
        private boolean tryStart() {
            lock.lock();
            try {
                DispatchState previous = dispatchState;
                if (previous == DispatchState.STARTED)
                    return false;
                dispatchState = DispatchState.STARTED;
                if (previous == DispatchState.UNREGISTERED)
                    return true;
                for (@Nullable Work<?> node = parent; node != null; node = node.parent) {
                    Map<Executor, Set<Work<?>>> index = Objects.requireNonNull(node.pending);
                    Set<Work<?>> available = index.get(executor);
                    available.remove(this);
                    if (available.isEmpty())
                        index.remove(executor);
                }
                return true;
            } finally {
                lock.unlock();
            }
        }

        /// Runs this submission once in its assigned executor context.
        @Override
        public void run() {
            if (canBeHelped && !tryStart())
                return;
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
                if (window != null)
                    window.active.remove(this);
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
