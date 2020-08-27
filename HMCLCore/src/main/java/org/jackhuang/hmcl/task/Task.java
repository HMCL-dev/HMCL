/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.InvocationDispatcher;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.ReflectionHelper;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Disposable task.
 *
 * @author huangyuhui
 */
public abstract class Task<T> {

    private final EventManager<TaskEvent> onDone = new EventManager<>();

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
    private Supplier<Boolean> cancelled;

    final void setCancelled(Supplier<Boolean> cancelled) {
        this.cancelled = cancelled;
    }

    protected final boolean isCancelled() {
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            return true;
        }

        return cancelled != null ? cancelled.get() : false;
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
     * @param stage the stage
     */
    final void setStage(String stage) {
        this.stage = stage;
    }

    public List<String> getStages() {
        return getStage() == null ? Collections.emptyList() : Collections.singletonList(getStage());
    }

    Map<String, Object> properties;

    protected Map<String, Object> getProperties() {
        if (properties == null) properties = new HashMap<>();
        return properties;
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
    private String name = getClass().getName();

    public String getName() {
        return name;
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
     *
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
     * @see Thread#interrupted
     */
    public void preExecute() throws Exception {}

    /**
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#interrupted
     */
    public abstract void execute() throws Exception;

    public boolean doPostExecute() {
        return false;
    }

    /**
     * This method will be called after dependency tasks terminated all together.
     *
     * You can check whether dependencies succeed in this method by calling
     * {@link Task#isDependenciesSucceeded()} no matter when
     * {@link Task#isRelyingOnDependencies()} returns true or false.
     *
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#interrupted
     * @see Task#isDependenciesSucceeded()
     */
    public void postExecute() throws Exception {}


    /**
     * The collection of sub-tasks that should execute **before** this task running.
     */
    public Collection<Task<?>> getDependents() {
        return Collections.emptySet();
    }

    /**
     * The collection of sub-tasks that should execute **after** this task running.
     * Will not be executed if execution fails.
     */
    public Collection<Task<?>> getDependencies() {
        return Collections.emptySet();
    }

    public EventManager<TaskEvent> onDone() {
        return onDone;
    }

    protected long getProgressInterval() {
        return 1000L;
    }

    private long lastTime = Long.MIN_VALUE;
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(this, "progress", -1);
    private final InvocationDispatcher<Double> progressUpdate = InvocationDispatcher.runOn(Platform::runLater, progress::set);

    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    protected void updateProgress(long progress, long total) {
        updateProgress(1.0 * progress / total);
    }

    protected void updateProgress(double progress) {
        if (progress < 0 || progress > 1.0)
            throw new IllegalArgumentException("Progress is must between 0 and 1.");
        long now = System.currentTimeMillis();
        if (lastTime == Long.MIN_VALUE || now - lastTime >= getProgressInterval()) {
            updateProgressImmediately(progress);
            lastTime = now;
        }
    }

    protected void updateProgressImmediately(double progress) {
        progressUpdate.accept(progress);
    }

    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", null);
    private final InvocationDispatcher<String> messageUpdate = InvocationDispatcher.runOn(Platform::runLater, message::set);

    public final ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    protected final void updateMessage(String newMessage) {
        messageUpdate.accept(newMessage);
    }

    public final void run() throws Exception {
        if (getSignificance().shouldLog())
            Logging.LOG.log(Level.FINE, "Executing task: " + getName());

        for (Task<?> task : getDependents())
            doSubTask(task);
        execute();
        for (Task<?> task : getDependencies())
            doSubTask(task);
        onDone.fireEvent(new TaskEvent(this, this, false));
    }

    private void doSubTask(Task<?> task) throws Exception {
        message.bind(task.message);
        progress.bind(task.progress);
        task.run();
        message.unbind();
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
     * @param fn the function to use to compute the value of the returned Task
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
     * @param fn the function to use to compute the value of the returned Task
     * @param <U> the function's return type
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
     * @param name the name of this new Task for displaying
     * @param executor the executor to use for asynchronous execution
     * @param fn the function to use to compute the value of the returned Task
     * @param <U> the function's return type
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
     * returned Task
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
     * @param action the action to perform before completing the returned Task
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
     * @param name the name of this new Task for displaying
     * @param action the action to perform before completing the returned Task
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
     * returned Task
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenRunAsync(ExceptionalRunnable<E> action) {
        return thenRunAsync(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Executor.
     *
     * @param action the action to perform before completing the
     * returned Task
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
     * @param name the name of this new Task for displaying
     * @param action the action to perform before completing the
     * returned Task
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
     * @param fn the function to use to compute the value of the returned Task
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
     * @param fn the function to use to compute the value of the returned Task
     * @param <U> the function's return type
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
     * @param <U> the type of the returned Task's result
     * @return the Task
     */
    public final <U> Task<U> thenComposeAsync(Task<U> other) {
        return thenComposeAsync(() -> other);
    }
    
    /**
     * Returns a new Task that, when this task completes
     * normally, is executed.
     *
     * @param fn the function returning a new Task
     * @param <U> the type of the returned Task's result
     * @return the Task
     */
    public final <U> Task<U> thenComposeAsync(ExceptionalSupplier<Task<U>, ?> fn) {
        return new UniCompose<>(fn, true);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed with result of this task as the argument
     * to the supplied function.
     *
     * @param fn the function returning a new Task
     * @param <U> the type of the returned Task's result
     * @return the Task
     */
    public <U, E extends Exception> Task<U> thenComposeAsync(ExceptionalFunction<T, Task<U>, E> fn) {
        return new UniCompose<>(fn, true);
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
     * returned Task
     * @return the new Task
     */
    public <E extends Exception> Task<Void> withRunAsync(ExceptionalRunnable<E> action) {
        return withRunAsync(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Executor.
     *
     * @param action the action to perform before completing the
     * returned Task
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
     * @param name the name of this new Task for displaying
     * @param action the action to perform before completing the
     * returned Task
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
     * @param action the action to perform
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
                    throw new AssertionError("When dependents succeeded, Task.exception must be nonnull.");

                action.execute(Task.this.getException());

                if (!isDependentsSucceeded()) {
                    setSignificance(TaskSignificance.MINOR);
                    if (Task.this.getException() == null)
                        throw new CancellationException();
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

            @Override
            public List<String> getStages() {
                return Lang.merge(Task.this.getStages(), super.getStages());
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
                        Logging.LOG.log(Level.WARNING, "Failed to execute " + success, e);
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
        StageTask task = new StageTask();
        task.setStage(stage);
        return task;
    }

    public Task<T> withStagesHint(List<String> stages) {
        return new Task<T>() {

            @Override
            public Collection<Task<?>> getDependents() {
                return Collections.singleton(Task.this);
            }

            @Override
            public void execute() throws Exception {
                setResult(Task.this.getResult());
            }

            @Override
            public List<String> getStages() {
                return stages;
            }
        };
    }

    public Task<T> withCounter() {
        return new CountTask();
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

            @Override
            public List<String> getStages() {
                return Lang.merge(super.getStages(), then == null ? null : then.getStages());
            }
        }.setName(name);
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
    public static Task<Void> allOf(Task<?>... tasks) {
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
    public static Task<Void> allOf(Collection<Task<?>> tasks) {
        return new Task<Void>() {
            {
                setSignificance(TaskSignificance.MINOR);
            }

            @Override
            public void execute() {
            }

            @Override
            public Collection<Task<?>> getDependents() {
                return tasks;
            }

            @Override
            public List<String> getStages() {
                return tasks.stream().flatMap(task -> task.getStages().stream()).collect(Collectors.toList());
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

    public interface FinalizedCallback {
        void execute(Exception exception) throws Exception;
    }

    public interface FinalizedCallbackWithResult<T> {
        void execute(T result, Exception exception) throws Exception;
    }

    private static String getCaller() {
        return ReflectionHelper.getCaller(packageName -> !"org.jackhuang.hmcl.task".equals(packageName)).toString();
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

        @Override
        public List<String> getStages() {
            return Lang.merge(Task.this.getStages(), super.getStages());
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
         * @param fn a callback that returns the task runs after pred, succ will be executed asynchronously. You can do something that relies on the result of pred.
         * @param relyingOnDependents true if this task chain will be broken when task pred fails.
         */
        UniCompose(ExceptionalSupplier<Task<U>, ?> fn, boolean relyingOnDependents) {
            this(result -> fn.get(), relyingOnDependents);
        }

        /**
         * A task that combines two tasks and make sure pred runs before succ.
         *
         * @param fn a callback that returns the task runs after pred, succ will be executed asynchronously. You can do something that relies on the result of pred.
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

        @Override
        public List<String> getStages() {
            return Stream.of(Task.this.getStages(), super.getStages(), succ == null ? Collections.<String>emptyList() : succ.getStages())
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }
    }

    public class StageTask extends Task<T> {

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.singleton(Task.this);
        }

        @Override
        public void execute() throws Exception {
            setResult(Task.this.getResult());
        }

        @Override
        public List<String> getStages() {
            return Lang.merge(Task.this.getStages(), super.getStages());
        }
    }

    private class CountTask extends Task<T> {
        private final UnaryOperator<Integer> COUNTER = a -> {
            int result = 0;
            if (a != null) result += a;
            return result + 1;
        };

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
        public void postExecute() {
            getProperties().put("count", COUNTER);
        }

        @Override
        public List<String> getStages() {
            return Lang.merge(Task.this.getStages(), super.getStages());
        }
    }
}
