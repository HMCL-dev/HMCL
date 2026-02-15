/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.task;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.Result;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Disposable task.
 *
 * @author huangyuhui
 */
public abstract class Task<T> {

    /**
     * True if not logging when executing this task.
     */
    private TaskSignificance significance = TaskSignificance.MAJOR;

    public final TaskSignificance getSignificance() {
        return significance;
    }

    public final Task<T> setSignificance(TaskSignificance significance) {
        this.significance = significance;
        return this;
    }

    // cancel
    private BooleanSupplier cancelled;

    final void setCancelled(BooleanSupplier cancelled) {
        this.cancelled = cancelled;
    }

    protected final boolean isCancelled() {
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            return true;
        }

        return cancelled != null && cancelled.getAsBoolean();
    }

    // stage
    private String stage = null;

    /**
     * Stage of task implies the goal of this task, for grouping tasks.
     * Stage will inherit from the parent task.
     */
    public String getStage() {
        return stage;
    }

    /**
     * You must initialize stage in constructor.
     *
     * @param stage the stage
     */
    protected final void setStage(String stage) {
        this.stage = stage;
    }

    private String inheritedStage = null;

    public String getInheritedStage() {
        return inheritedStage;
    }

    void setInheritedStage(String inheritedStage) {
        this.inheritedStage = inheritedStage;
    }

    // properties
    Map<String, Object> properties;

    public Map<String, Object> getProperties() {
        if (properties == null) properties = new HashMap<>();
        return properties;
    }

    private Runnable notifyPropertiesChanged;

    void setNotifyPropertiesChanged(Runnable runnable) {
        this.notifyPropertiesChanged = runnable;
    }

    protected void notifyPropertiesChanged() {
        if (notifyPropertiesChanged != null) {
            notifyPropertiesChanged.run();
        }
    }

    // state
    private TaskState state = TaskState.READY;

    public final TaskState getState() {
        return state;
    }

    final void setState(TaskState state) {
        this.state = state;
    }

    // last exception
    private Exception exception;

    /**
     * When task has been cancelled, task.exception will be null.
     *
     * @return the exception thrown during execution, possibly from dependents or dependencies.
     */
    @Nullable
    public final Exception getException() {
        return exception;
    }

    final void setException(Exception e) {
        exception = e;
    }

    private Executor executor = Schedulers.defaultScheduler();

    /**
     * The executor that decides how this task runs.
     */
    public final Executor getExecutor() {
        return executor;
    }

    public final Task<T> setExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    // dependents succeeded
    private boolean dependentsSucceeded = false;

    public boolean isDependentsSucceeded() {
        return dependentsSucceeded;
    }

    void setDependentsSucceeded() {
        dependentsSucceeded = true;
    }

    // dependencies succeeded
    private boolean dependenciesSucceeded = false;

    public boolean isDependenciesSucceeded() {
        return dependenciesSucceeded;
    }

    void setDependenciesSucceeded() {
        dependenciesSucceeded = true;
    }

    /**
     * True if requires all {@link #getDependents} finishing successfully.
     * <p>
     * **Note** if this field is set false, you are not supposed to invoke [run]
     */
    public boolean isRelyingOnDependents() {
        return true;
    }

    /**
     * True if requires all {@link #getDependencies} finishing successfully.
     * <p>
     * **Note** if this field is set false, you are not supposed to invoke [run]
     */
    public boolean isRelyingOnDependencies() {
        return true;
    }

    // name
    private String name;

    public String getName() {
        return name != null ? name : getClass().getName();
    }

    public Task<T> setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String toString() {
        if (getClass().getName().equals(getName()))
            return getName();
        else
            return getClass().getName() + "[" + getName() + "]";
    }

    // result
    private T result;
    private Consumer<T> resultConsumer;

    /**
     * Returns the result of this task.
     * <p>
     * The result will be generated only if the execution is completed.
     */
    public T getResult() {
        return result;
    }

    protected void setResult(T result) {
        this.result = result;
        if (resultConsumer != null)
            resultConsumer.accept(result);
    }

    /**
     * Sync the result of this task by given action.
     *
     * @param action the action to perform when result of this task changed
     * @return this Task
     */
    public Task<T> storeTo(Consumer<T> action) {
        this.resultConsumer = action;
        action.accept(getResult());
        return this;
    }

    // execution
    public boolean doPreExecute() {
        return false;
    }

    /**
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted()
     */
    public void preExecute() throws Exception {
    }

    /**
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted()
     */
    public abstract void execute() throws Exception;

    public boolean doPostExecute() {
        return false;
    }

    /**
     * This method will be called after dependency tasks terminated all together.
     * <p>
     * You can check whether dependencies succeed in this method by calling
     * {@link Task#isDependenciesSucceeded()} no matter when
     * {@link Task#isRelyingOnDependencies()} returns true or false.
     *
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted()
     * @see Task#isDependenciesSucceeded()
     */
    public void postExecute() throws Exception {
    }

    /**
     * The collection of sub-tasks that should execute **before** this task running.
     */
    public Collection<? extends Task<?>> getDependents() {
        return Collections.emptySet();
    }

    /**
     * The collection of sub-tasks that should execute **after** this task running.
     * Will not be executed if execution fails.
     */
    public Collection<? extends Task<?>> getDependencies() {
        return Collections.emptySet();
    }

    private volatile EventManager<TaskEvent> onDone;

    public EventManager<TaskEvent> onDone() {
        EventManager<TaskEvent> onDone = this.onDone;
        if (onDone == null) {
            synchronized (this) {
                onDone = this.onDone;
                if (onDone == null) {
                    this.onDone = onDone = new EventManager<>();
                }
            }
        }

        return onDone;
    }

    void fireDoneEvent(Object source, boolean failed) {
        EventManager<TaskEvent> onDone = this.onDone;
        if (onDone != null)
            onDone.fireEvent(new TaskEvent(source, this, failed));
    }

    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", -1);

    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    private long lastUpdateProgressTime = 0L;

    protected void updateProgress(long count, long total) {
        if (count < 0 || total < 0)
            throw new IllegalArgumentException("Invalid count or total: count=" + count + ", total=" + total);

        updateProgress(count < total ? (double) count / total : 1.0);
    }

    protected void updateProgress(double progress) {
        if (progress < 0 || progress > 1.0 || Double.isNaN(progress))
            throw new IllegalArgumentException("Invalid progress: " + progress);

        long now = System.currentTimeMillis();
        if (progress == 1.0 || now - lastUpdateProgressTime >= 1000L) {
            updateProgressImmediately(progress);
            lastUpdateProgressTime = now;
        }
    }

    //region Helpers for updateProgressImmediately

    @SuppressWarnings("FieldMayBeFinal")
    private volatile double pendingProgress = -1.0;

    /// @see Task#pendingProgress
    private static final VarHandle PENDING_PROGRESS_HANDLE;

    static {
        try {
            PENDING_PROGRESS_HANDLE = MethodHandles.lookup()
                    .findVarHandle(Task.class, "pendingProgress", double.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    //endregion updateProgressImmediately

    protected void updateProgressImmediately(double progress) {
        // assert progress >= 0 && progress <= 1.0;
        if ((double) PENDING_PROGRESS_HANDLE.getAndSet(this, progress) == -1.0) {
            Platform.runLater(() -> this.progress.set((double) PENDING_PROGRESS_HANDLE.getAndSet(this, -1.0)));
        }
    }

    public final T run() throws Exception {
        if (getSignificance().shouldLog())
            LOG.trace("Executing task: " + getName());

        for (Task<?> task : getDependents())
            doSubTask(task);
        execute();
        for (Task<?> task : getDependencies())
            doSubTask(task);
        fireDoneEvent(this, false);

        return getResult();
    }

    private void doSubTask(Task<?> task) throws Exception {
        progress.bind(task.progress);
        task.run();
        progress.unbind();
    }

    public final TaskExecutor executor() {
        return new AsyncTaskExecutor(this);
    }

    public final TaskExecutor executor(boolean start) {
        TaskExecutor executor = new AsyncTaskExecutor(this);
        if (start)
            executor.start();
        return executor;
    }

    public final TaskExecutor executor(TaskListener taskListener) {
        TaskExecutor executor = new AsyncTaskExecutor(this);
        executor.addTaskListener(taskListener);
        return executor;
    }

    public final void start() {
        executor().start();
    }

    public final boolean test() {
        return executor().test();
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the default Executor, with this
     * task's result as the argument to the supplied function.
     *
     * @param fn  the function to use to compute the value of the returned Task
     * @param <U> the function's return type
     * @return the new Task
     */
    public <U, E extends Exception> Task<U> thenApplyAsync(ExceptionalFunction<T, U, E> fn) {
        return thenApplyAsync(Schedulers.defaultScheduler(), fn);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the supplied Executor, with this
     * task's result as the argument to the supplied function.
     *
     * @param executor the executor to use for asynchronous execution
     * @param fn       the function to use to compute the value of the returned Task
     * @param <U>      the function's return type
     * @return the new Task
     */
    public <U, E extends Exception> Task<U> thenApplyAsync(Executor executor, ExceptionalFunction<T, U, E> fn) {
        return thenApplyAsync(getCaller(), executor, fn).setSignificance(TaskSignificance.MODERATE);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the supplied Executor, with this
     * task's result as the argument to the supplied function.
     *
     * @param name     the name of this new Task for displaying
     * @param executor the executor to use for asynchronous execution
     * @param fn       the function to use to compute the value of the returned Task
     * @param <U>      the function's return type
     * @return the new Task
     */
    public <U, E extends Exception> Task<U> thenApplyAsync(String name, Executor executor, ExceptionalFunction<T, U, E> fn) {
        return new UniApply<>(fn).setExecutor(executor).setName(name);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the default Executor, with this
     * task's result as the argument to the supplied action.
     *
     * @param action the action to perform before completing the
     *               returned Task
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenAcceptAsync(ExceptionalConsumer<T, E> action) {
        return thenAcceptAsync(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the supplied Executor, with this
     * task's result as the argument to the supplied action.
     *
     * @param action   the action to perform before completing the returned Task
     * @param executor the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenAcceptAsync(Executor executor, ExceptionalConsumer<T, E> action) {
        return thenAcceptAsync(getCaller(), executor, action).setSignificance(TaskSignificance.MODERATE);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the supplied Executor, with this
     * task's result as the argument to the supplied action.
     *
     * @param name     the name of this new Task for displaying
     * @param action   the action to perform before completing the returned Task
     * @param executor the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenAcceptAsync(String name, Executor executor, ExceptionalConsumer<T, E> action) {
        return thenApplyAsync(name, executor, result -> {
            action.accept(result);
            return null;
        });
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the default Executor.
     *
     * @param action the action to perform before completing the
     *               returned Task
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenRunAsync(ExceptionalRunnable<E> action) {
        return thenRunAsync(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Executor.
     *
     * @param action   the action to perform before completing the
     *                 returned Task
     * @param executor the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenRunAsync(Executor executor, ExceptionalRunnable<E> action) {
        return thenRunAsync(getCaller(), executor, action).setSignificance(TaskSignificance.MODERATE);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Executor.
     *
     * @param name     the name of this new Task for displaying
     * @param action   the action to perform before completing the
     *                 returned Task
     * @param executor the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenRunAsync(String name, Executor executor, ExceptionalRunnable<E> action) {
        return thenApplyAsync(name, executor, ignore -> {
            action.run();
            return null;
        });
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the default Executor.
     *
     * @param fn  the function to use to compute the value of the returned Task
     * @param <U> the function's return type
     * @return the new Task
     */
    public final <U> Task<U> thenSupplyAsync(Callable<U> fn) {
        return thenComposeAsync(() -> Task.supplyAsync(fn));
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the default Executor.
     *
     * @param name the name of this new Task for displaying
     * @param fn   the function to use to compute the value of the returned Task
     * @param <U>  the function's return type
     * @return the new Task
     */
    public final <U> Task<U> thenSupplyAsync(String name, Callable<U> fn) {
        return thenComposeAsync(() -> Task.supplyAsync(name, fn));
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed.
     *
     * @param other the another Task
     * @param <U>   the type of the returned Task's result
     * @return the Task
     */
    public final <U> Task<U> thenComposeAsync(Task<U> other) {
        return thenComposeAsync(() -> other);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed.
     *
     * @param fn  the function returning a new Task
     * @param <U> the type of the returned Task's result
     * @return the Task
     */
    public final <U> Task<U> thenComposeAsync(ExceptionalSupplier<Task<U>, ?> fn) {
        return thenComposeAsync(Schedulers.defaultScheduler(), fn);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed.
     *
     * @param fn       the function returning a new Task
     * @param executor the executor to use for asynchronous execution
     * @param <U>      the type of the returned Task's result
     * @return the Task
     */
    public final <U> Task<U> thenComposeAsync(Executor executor, ExceptionalSupplier<Task<U>, ?> fn) {
        return new UniCompose<>(fn, true).setExecutor(executor);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed with result of this task as the argument
     * to the supplied function.
     *
     * @param fn  the function returning a new Task
     * @param <U> the type of the returned Task's result
     * @return the Task
     */
    public <U, E extends Exception> Task<U> thenComposeAsync(ExceptionalFunction<T, Task<U>, E> fn) {
        return thenComposeAsync(Schedulers.defaultScheduler(), fn);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed with result of this task as the argument
     * to the supplied function.
     *
     * @param fn       the function returning a new Task
     * @param executor the executor to use for asynchronous execution
     * @param <U>      the type of the returned Task's result
     * @return the Task
     */
    public <U, E extends Exception> Task<U> thenComposeAsync(Executor executor, ExceptionalFunction<T, Task<U>, E> fn) {
        return new UniCompose<>(fn, true).setExecutor(executor);
    }

    public final <U> Task<U> withComposeAsync(Task<U> other) {
        return withComposeAsync(() -> other);
    }

    public final <U, E extends Exception> Task<U> withComposeAsync(ExceptionalSupplier<Task<U>, E> fn) {
        return new UniCompose<>(fn, false);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the default Executor.
     *
     * @param action the action to perform before completing the
     *               returned Task
     * @return the new Task
     */
    public <E extends Exception> Task<Void> withRunAsync(ExceptionalRunnable<E> action) {
        return withRunAsync(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Executor.
     *
     * @param action   the action to perform before completing the
     *                 returned Task
     * @param executor the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> withRunAsync(Executor executor, ExceptionalRunnable<E> action) {
        return withRunAsync(getCaller(), executor, action).setSignificance(TaskSignificance.MODERATE);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Executor.
     *
     * @param name     the name of this new Task for displaying
     * @param action   the action to perform before completing the
     *                 returned Task
     * @param executor the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> withRunAsync(String name, Executor executor, ExceptionalRunnable<E> action) {
        return new UniCompose<>(() -> Task.runAsync(name, executor, action), false);
    }

    /**
     * Returns a new Task with the same exception as this task, that executes
     * the given action when this task completes.
     *
     * <p>When this task is complete, the given action is invoked, a boolean
     * value represents the execution status of this task, and the exception
     * (or {@code null} if none) of this task as arguments.  The returned task
     * is completed when the action returns.  If the supplied action itself
     * encounters an exception, then the returned task exceptionally completes
     * with this exception unless this task also completed exceptionally.
     *
     * @param action the action to perform
     * @return the new Task
     */
    public final Task<Void> whenComplete(FinalizedCallback action) {
        return whenComplete(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task with the same exception as this task, that executes
     * the given action when this task completes.
     *
     * <p>When this task is complete, the given action is invoked, a boolean
     * value represents the execution status of this task, and the exception
     * (or {@code null} if none, which means when isDependentSucceeded is false,
     * exception may be null) of this task as arguments.  The returned task
     * is completed when the action returns.  If the supplied action itself
     * encounters an exception, then the returned task exceptionally completes
     * with this exception unless this task also completed exceptionally.
     *
     * @param action   the action to perform
     * @param executor the executor to use for asynchronous execution
     * @return the new Task
     */
    public final Task<Void> whenComplete(Executor executor, FinalizedCallback action) {
        return new Task<Void>() {
            {
                setSignificance(TaskSignificance.MODERATE);
            }

            @Override
            public void execute() throws Exception {
                if (isDependentsSucceeded() != (Task.this.getException() == null))
                    throw new AssertionError("When whenComplete succeeded, Task.exception must be null.", Task.this.getException());

                action.execute(Task.this.getException());

                if (!isDependentsSucceeded()) {
                    setSignificance(TaskSignificance.MINOR);
                    if (Task.this.getException() == null)
                        throw new AssertionError("When failed, exception cannot be null");
                    else
                        throw Task.this.getException();
                }
            }

            @Override
            public Collection<Task<?>> getDependents() {
                return Collections.singleton(Task.this);
            }

            @Override
            public boolean isRelyingOnDependents() {
                return false;
            }
        }.setExecutor(executor).setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /**
     * Returns a new Task with the same exception as this task, that executes
     * the given action when this task completes.
     *
     * <p>When this task is complete, the given action is invoked with the
     * result (or {@code null} if none), a boolean value represents the
     * execution status of this task, and the exception (or {@code null}
     * if none) of this task as arguments.  The returned task is completed
     * when the action returns.  If the supplied action itself encounters an
     * exception, then the returned task exceptionally completes with this
     * exception unless this task also completed exceptionally.
     *
     * @param action the action to perform
     * @return the new Task
     */
    public Task<Void> whenComplete(Executor executor, FinalizedCallbackWithResult<T> action) {
        return whenComplete(executor, (exception -> action.execute(getResult(), exception)));
    }

    public Task<Result<T>> wrapResult() {
        return new Task<Result<T>>() {
            {
                setSignificance(TaskSignificance.MODERATE);
            }

            @Override
            public void execute() throws Exception {
                if (isDependentsSucceeded() != (Task.this.getException() == null))
                    throw new AssertionError("When whenComplete succeeded, Task.exception must be null.", Task.this.getException());

                if (isDependentsSucceeded()) {
                    setResult(Result.success(Task.this.getResult()));
                } else {
                    setSignificance(TaskSignificance.MINOR);
                    setResult(Result.failure(Task.this.getException()));
                }
            }

            @Override
            public Collection<Task<?>> getDependents() {
                return Collections.singleton(Task.this);
            }

            @Override
            public boolean isRelyingOnDependents() {
                return false;
            }
        }.setExecutor(executor).setName(getCaller()).setSignificance(TaskSignificance.MODERATE);
    }

    /**
     * Returns a new Task with the same exception as this task, that executes
     * the given actions when this task completes.
     *
     * <p>When this task is complete, the given success action is invoked, the
     * given failure action is invoked with the exception of this task.  The
     * returned task is completed when the action returns.  If the supplied
     * action itself encounters an exception, then the returned task exceptionally
     * completes with this exception unless this task also
     * completed exceptionally.
     *
     * @param success the action to perform when this task successfully completed
     * @param failure the action to perform when this task exceptionally returned
     * @return the new Task
     */
    public final <E1 extends Exception, E2 extends Exception> Task<Void> whenComplete(Executor executor, ExceptionalRunnable<E1> success, ExceptionalConsumer<Exception, E2> failure) {
        return whenComplete(executor, exception -> {
            if (exception == null) {
                if (success != null)
                    try {
                        success.run();
                    } catch (Exception e) {
                        LOG.warning("Failed to execute " + success, e);
                        if (failure != null)
                            failure.accept(e);
                    }
            } else {
                if (failure != null)
                    failure.accept(exception);
            }
        });
    }

    /**
     * Returns a new Task with the same exception as this task, that executes
     * the given actions when this task completes.
     *
     * <p>When this task is complete, the given success action is invoked with
     * the result, the given failure action is invoked with the exception of
     * this task.  The returned task is completed when the action returns.  If
     * the supplied action itself encounters an exception, then the returned
     * task exceptionally completes with this exception unless this task also
     * completed exceptionally.
     *
     * @param success the action to perform when this task successfully completed
     * @param failure the action to perform when this task exceptionally returned
     * @return the new Task
     */
    public <E1 extends Exception, E2 extends Exception> Task<Void> whenComplete(Executor executor, ExceptionalConsumer<T, E1> success, ExceptionalConsumer<Exception, E2> failure) {
        return whenComplete(executor, () -> success.accept(getResult()), failure);
    }

    public Task<T> withStage(String stage) {
        return new StageTask(stage);
    }

    public Task<T> withFakeProgress(String name, BooleanSupplier done, double k) {
        return new FakeProgressTask(done, k).setExecutor(Schedulers.defaultScheduler()).setName(name).setSignificance(TaskSignificance.MAJOR);
    }

    public Task<T> withStagesHint(List<String> stages) {
        return new StagesHintTask(stages);
    }

    public class StagesHintTask extends Task<T> {
        private final List<String> stages;

        public StagesHintTask(List<String> stages) {
            this.stages = stages;
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.singleton(Task.this);
        }

        @Override
        public void execute() {
            setResult(Task.this.getResult());
        }

        public List<String> getStages() {
            return stages;
        }
    }

    public Task<T> withCounter(String countStage) {
        return new CountTask(countStage);
    }

    public static Task<Void> runAsync(ExceptionalRunnable<?> closure) {
        return runAsync(Schedulers.defaultScheduler(), closure);
    }

    public static Task<Void> runAsync(String name, ExceptionalRunnable<?> closure) {
        return runAsync(name, Schedulers.defaultScheduler(), closure);
    }

    public static Task<Void> runAsync(Executor executor, ExceptionalRunnable<?> closure) {
        return runAsync(getCaller(), executor, closure).setSignificance(TaskSignificance.MODERATE);
    }

    public static Task<Void> runAsync(String name, Executor executor, ExceptionalRunnable<?> closure) {
        return new SimpleTask<>(closure.toCallable()).setExecutor(executor).setName(name);
    }

    public static <T> Task<T> composeAsync(ExceptionalSupplier<Task<T>, ?> fn) {
        return composeAsync(getCaller(), fn).setSignificance(TaskSignificance.MODERATE);
    }

    public static <T> Task<T> composeAsync(String name, ExceptionalSupplier<Task<T>, ?> fn) {
        return new Task<T>() {
            Task<T> then;

            @Override
            public void execute() throws Exception {
                then = fn.get();
                if (then != null)
                    then.storeTo(this::setResult);
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return then == null ? Collections.emptySet() : Collections.singleton(then);
            }
        }.setName(name);
    }

    public static <T> Task<T> composeAsync(Executor executor, ExceptionalSupplier<Task<T>, ?> fn) {
        return composeAsync(fn).setExecutor(executor);
    }

    public static <V> Task<V> supplyAsync(Callable<V> callable) {
        return supplyAsync(getCaller(), callable).setSignificance(TaskSignificance.MODERATE);
    }

    public static <V> Task<V> supplyAsync(Executor executor, Callable<V> callable) {
        return supplyAsync(getCaller(), executor, callable).setSignificance(TaskSignificance.MODERATE);
    }

    public static <V> Task<V> supplyAsync(String name, Callable<V> callable) {
        return supplyAsync(name, Schedulers.defaultScheduler(), callable);
    }

    public static <V> Task<V> supplyAsync(String name, Executor executor, Callable<V> callable) {
        return new SimpleTask<>(callable).setExecutor(executor).setName(name);
    }

    public static <V> Task<V> completed(V value) {
        return fromCompletableFuture(CompletableFuture.completedFuture(value));
    }

    /**
     * Returns a new Task that is completed when all of the given Tasks
     * complete.  If any of the given Tasks complete exceptionally,
     * then the returned Task also does so.  Otherwise, the results, if
     * any, of the given Tasks are not reflected in the returned Task,
     * but may be obtained by inspecting them individually. If no Tasks
     * are provided, returns a Task completed with the value {@code null}.
     *
     * @param tasks the Tasks
     * @return a new Task that is completed when all of the given Tasks complete
     */
    @SafeVarargs
    public static <T> Task<List<T>> allOf(Task<? extends T>... tasks) {
        return allOf(Arrays.asList(tasks));
    }

    /**
     * Returns a new Task that is completed when all of the given Tasks
     * complete.  If any of the given Tasks complete exceptionally,
     * then the returned Task also does so.  Otherwise, the results, if
     * any, of the given Tasks are not reflected in the returned Task,
     * but may be obtained by inspecting them individually. If no Tasks
     * are provided, returns a Task completed with the value {@code null}.
     *
     * @param tasks the Tasks
     * @return a new Task that is completed when all of the given Tasks complete
     */
    public static <T> Task<List<T>> allOf(Collection<? extends Task<? extends T>> tasks) {
        return new Task<>() {
            {
                setSignificance(TaskSignificance.MINOR);
            }

            @Override
            public void execute() {
                setResult(tasks.stream().map(Task::getResult).collect(Collectors.toList()));
            }

            @Override
            public Collection<? extends Task<?>> getDependents() {
                return tasks;
            }
        };
    }

    /**
     * Returns a new task that runs the given tasks sequentially
     * and returns the result of the last task.
     *
     * @param tasks tasks to run sequentially
     * @return the combination of these tasks
     */
    public static Task<?> runSequentially(Task<?>... tasks) {
        if (tasks.length == 0) {
            return new SimpleTask<>(() -> null);
        }

        Task<?> task = tasks[0];
        for (int i = 1; i < tasks.length; i++) {
            task = task.thenComposeAsync(tasks[i]);
        }
        return task;
    }

    public static <T> Task<T> fromCompletableFuture(CompletableFuture<T> future) {
        return new CompletableFutureTask<T>() {
            @Override
            public CompletableFuture<T> getFuture(TaskCompletableFuture executor) {
                return future;
            }
        };
    }

    public enum TaskSignificance {
        MAJOR,
        MODERATE,
        MINOR;

        public boolean shouldLog() {
            return this != MINOR;
        }

        public boolean shouldShow() {
            return this == MAJOR;
        }
    }

    public enum TaskState {
        READY,
        RUNNING,
        EXECUTED,
        SUCCEEDED,
        FAILED
    }

    @FunctionalInterface
    public interface FinalizedCallback {
        void execute(Exception exception) throws Exception;
    }

    @FunctionalInterface
    public interface FinalizedCallbackWithResult<T> {
        void execute(T result, Exception exception) throws Exception;
    }

    private static final String PACKAGE_PREFIX = Task.class.getPackageName() + ".";
    private static final Predicate<StackWalker.StackFrame> PREDICATE = stackFrame -> !stackFrame.getClassName().startsWith(PACKAGE_PREFIX);
    private static final Function<Stream<StackWalker.StackFrame>, Optional<StackWalker.StackFrame>> FUNCTION = stream -> stream.filter(PREDICATE).findFirst();
    private static final Function<StackWalker.StackFrame, String> FRAME_MAPPING = frame -> {
        String fileName = frame.getFileName();
        if (fileName != null)
            return frame.getClassName() + '.' + frame.getMethodName() + '(' + fileName + ':' + frame.getLineNumber() + ')';
        else
            return frame.getClassName() + '.' + frame.getMethodName();
    };

    private static String getCaller() {
        return StackWalker.getInstance().walk(FUNCTION).map(FRAME_MAPPING).orElse("Unknown");
    }

    private static final class SimpleTask<T> extends Task<T> {

        private final Callable<T> callable;

        SimpleTask(Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public void execute() throws Exception {
            setResult(callable.call());
        }
    }

    private class UniApply<R> extends Task<R> {
        private final ExceptionalFunction<T, R, ?> callable;

        UniApply(ExceptionalFunction<T, R, ?> callable) {
            this.callable = callable;
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.singleton(Task.this);
        }

        @Override
        public void execute() throws Exception {
            setResult(callable.apply(Task.this.getResult()));
        }
    }

    /**
     * A task that combines two tasks and make sure [pred] runs before succ.
     *
     * @author huangyuhui
     */
    private final class UniCompose<U> extends Task<U> {

        private final boolean relyingOnDependents;
        private Task<U> succ;
        private final ExceptionalFunction<T, Task<U>, ?> fn;

        /**
         * A task that combines two tasks and make sure pred runs before succ.
         *
         * @param fn                  a callback that returns the task runs after pred, succ will be executed asynchronously. You can do something that relies on the result of pred.
         * @param relyingOnDependents true if this task chain will be broken when task pred fails.
         */
        UniCompose(ExceptionalSupplier<Task<U>, ?> fn, boolean relyingOnDependents) {
            this(result -> fn.get(), relyingOnDependents);
        }

        /**
         * A task that combines two tasks and make sure pred runs before succ.
         *
         * @param fn                  a callback that returns the task runs after pred, succ will be executed asynchronously. You can do something that relies on the result of pred.
         * @param relyingOnDependents true if this task chain will be broken when task pred fails.
         */
        UniCompose(ExceptionalFunction<T, Task<U>, ?> fn, boolean relyingOnDependents) {
            this.fn = fn;
            this.relyingOnDependents = relyingOnDependents;

            setSignificance(TaskSignificance.MODERATE);
            setName(fn.toString());
        }

        @Override
        public void execute() throws Exception {
            setName(fn.toString());
            succ = fn.apply(Task.this.getResult());
            if (succ != null)
                succ.storeTo(this::setResult);
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.singleton(Task.this);
        }

        @Override
        public Collection<Task<?>> getDependencies() {
            return succ == null ? Collections.emptySet() : Collections.singleton(succ);
        }

        @Override
        public boolean isRelyingOnDependents() {
            return relyingOnDependents;
        }
    }

    private final class StageTask extends Task<T> {
        private StageTask(String stage) {
            this.setStage(stage);
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.singleton(Task.this);
        }

        @Override
        public void execute() {
            setResult(Task.this.getResult());
        }
    }

    private final class FakeProgressTask extends Task<T> {
        private static final double MAX_VALUE = 0.98D;

        private final BooleanSupplier done;

        private final double k;

        private FakeProgressTask(BooleanSupplier done, double k) {
            this.done = done;
            this.k = k;
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.singleton(Task.this);
        }

        @Override
        public void execute() throws InterruptedException {
            if (!done.getAsBoolean()) {
                updateProgress(0.0D);

                final long start = System.currentTimeMillis();
                final double k2 = k / MAX_VALUE;
                while (!done.getAsBoolean()) {
                    updateProgressImmediately(-k / ((System.currentTimeMillis() - start) / 1000D + k2) + MAX_VALUE);

                    Thread.sleep(1000);
                }
            }

            updateProgress(1.0D);
            setResult(Task.this.getResult());
        }
    }

    public final class CountTask extends Task<T> {
        private final String countStage;

        private CountTask(String countStage) {
            this.countStage = countStage;
            setSignificance(TaskSignificance.MINOR);
        }

        public String getCountStage() {
            return countStage;
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.singleton(Task.this);
        }

        @Override
        public void execute() throws Exception {
            setResult(Task.this.getResult());
        }

        @Override
        public boolean doPostExecute() {
            return true;
        }

        @Override
        public void postExecute() throws Exception {
            notifyPropertiesChanged();
        }
    }
}
