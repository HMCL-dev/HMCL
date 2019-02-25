/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.ReflectionHelper;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.logging.Level;

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

    public void setSignificance(TaskSignificance significance) {
        this.significance = significance;
    }

    // state
    private TaskState state = TaskState.READY;

    public TaskState getState() {
        return state;
    }

    void setState(TaskState state) {
        this.state = state;
    }

    // last exception
    private Exception exception;

    public Exception getException() {
        return exception;
    }

    void setException(Exception e) {
        exception = e;
    }

    /**
     * The scheduler that decides how this task runs.
     */
    public Scheduler getScheduler() {
        return Schedulers.defaultScheduler();
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
        return this;
    }

    // execution
    public boolean doPreExecute() {
        return false;
    }

    /**
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted
     */
    public void preExecute() throws Exception {}

    /**
     * @throws InterruptedException if current thread is interrupted
     * @see Thread#isInterrupted
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
     * @see Thread#isInterrupted
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

    protected void updateProgress(int progress, int total) {
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
        return new TaskExecutor(this);
    }

    public final TaskExecutor executor(boolean start) {
        TaskExecutor executor = new TaskExecutor(this);
        if (start)
            executor.start();
        return executor;
    }

    public final TaskExecutor executor(TaskListener taskListener) {
        TaskExecutor executor = new TaskExecutor(this);
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
     * normally, is executed using the default Scheduler, with this
     * task's result as the argument to the supplied function.
     *
     * @param fn the function to use to compute the value of the returned Task
     * @param <U> the function's return type
     * @return the new Task
     */
    public <U, E extends Exception> Task<U> thenApply(ExceptionalFunction<T, U, E> fn) {
        return thenApply(Schedulers.defaultScheduler(), fn);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the supplied Scheduler, with this
     * task's result as the argument to the supplied function.
     *
     * @param scheduler the executor to use for asynchronous execution
     * @param fn the function to use to compute the value of the returned Task
     * @param <U> the function's return type
     * @return the new Task
     */
    public <U, E extends Exception> Task<U> thenApply(Scheduler scheduler, ExceptionalFunction<T, U, E> fn) {
        return thenApply(getCaller(), scheduler, fn);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the supplied Scheduler, with this
     * task's result as the argument to the supplied function.
     *
     * @param name the name of this new Task for displaying
     * @param scheduler the executor to use for asynchronous execution
     * @param fn the function to use to compute the value of the returned Task
     * @param <U> the function's return type
     * @return the new Task
     */
    public <U, E extends Exception> Task<U> thenApply(String name, Scheduler scheduler, ExceptionalFunction<T, U, E> fn) {
        return new UniApply<>(name, scheduler, fn);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the default Scheduler, with this
     * task's result as the argument to the supplied action.
     *
     * @param action the action to perform before completing the
     * returned Task
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenAccept(ExceptionalConsumer<T, E> action) {
        return thenAccept(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the supplied Scheduler, with this
     * task's result as the argument to the supplied action.
     *
     * @param action the action to perform before completing the returned Task
     * @param scheduler the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenAccept(Scheduler scheduler, ExceptionalConsumer<T, E> action) {
        return thenAccept(getCaller(), scheduler, action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the supplied Scheduler, with this
     * task's result as the argument to the supplied action.
     *
     * @param name the name of this new Task for displaying
     * @param action the action to perform before completing the returned Task
     * @param scheduler the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenAccept(String name, Scheduler scheduler, ExceptionalConsumer<T, E> action) {
        return thenApply(name, scheduler, result -> {
            action.accept(result);
            return null;
        });
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the default Scheduler.
     *
     * @param action the action to perform before completing the
     * returned Task
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenRun(ExceptionalRunnable<E> action) {
        return thenRun(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Scheduler.
     *
     * @param action the action to perform before completing the
     * returned Task
     * @param scheduler the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenRun(Scheduler scheduler, ExceptionalRunnable<E> action) {
        return thenRun(getCaller(), scheduler, action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Scheduler.
     *
     * @param name the name of this new Task for displaying
     * @param action the action to perform before completing the
     * returned Task
     * @param scheduler the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> thenRun(String name, Scheduler scheduler, ExceptionalRunnable<E> action) {
        return thenApply(name, scheduler, ignore -> {
            action.run();
            return null;
        });
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the default Scheduler.
     *
     * @param fn the function to use to compute the value of the returned Task
     * @param <U> the function's return type
     * @return the new Task
     */
    public final <U> Task<U> thenSupply(Callable<U> fn) {
        return thenCompose(() -> Task.supplyAsync(fn));
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed using the default Scheduler.
     *
     * @param name the name of this new Task for displaying
     * @param fn the function to use to compute the value of the returned Task
     * @param <U> the function's return type
     * @return the new Task
     */
    public final <U> Task<U> thenSupply(String name, Callable<U> fn) {
        return thenCompose(() -> Task.supplyAsync(name, fn));
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed.
     *
     * @param other the another Task
     * @param <U> the type of the returned Task's result
     * @return the Task
     */
    public final <U> Task<U> thenCompose(Task<U> other) {
        return thenCompose(() -> other);
    }
    
    /**
     * Returns a new Task that, when this task completes
     * normally, is executed.
     *
     * @param fn the function returning a new Task
     * @param <U> the type of the returned Task's result
     * @return the Task
     */
    public final <U> Task<U> thenCompose(ExceptionalSupplier<Task<U>, ?> fn) {
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
    public <U, E extends Exception> Task<U> thenCompose(ExceptionalFunction<T, Task<U>, E> fn) {
        return new UniCompose<>(fn, true);
    }

    public final <U> Task<U> withCompose(Task<U> other) {
        return withCompose(() -> other);
    }

    public final <U, E extends Exception> Task<U> withCompose(ExceptionalSupplier<Task<U>, E> fn) {
        return new UniCompose<>(fn, false);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the default Scheduler.
     *
     * @param action the action to perform before completing the
     * returned Task
     * @return the new Task
     */
    public <E extends Exception> Task<Void> withRun(ExceptionalRunnable<E> action) {
        return withRun(Schedulers.defaultScheduler(), action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Scheduler.
     *
     * @param action the action to perform before completing the
     * returned Task
     * @param scheduler the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> withRun(Scheduler scheduler, ExceptionalRunnable<E> action) {
        return withRun(getCaller(), scheduler, action);
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, executes the given action using the supplied Scheduler.
     *
     * @param name the name of this new Task for displaying
     * @param action the action to perform before completing the
     * returned Task
     * @param scheduler the executor to use for asynchronous execution
     * @return the new Task
     */
    public <E extends Exception> Task<Void> withRun(String name, Scheduler scheduler, ExceptionalRunnable<E> action) {
        return new UniCompose<>(() -> Task.runAsync(name, scheduler, action), false);
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
     * @param scheduler the executor to use for asynchronous execution
     * @return the new Task
     */
    public final Task<Void> whenComplete(Scheduler scheduler, FinalizedCallback action) {
        return new Task<Void>() {
            {
                setSignificance(TaskSignificance.MODERATE);
            }

            @Override
            public Scheduler getScheduler() {
                return scheduler;
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
        }.setName(getCaller());
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
    public Task<Void> whenComplete(Scheduler scheduler, FinalizedCallbackWithResult<T> action) {
        return whenComplete(scheduler, (exception -> action.execute(getResult(), exception)));
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
    public final <E1 extends Exception, E2 extends Exception> Task<Void> whenComplete(Scheduler scheduler, ExceptionalRunnable<E1> success, ExceptionalConsumer<Exception, E2> failure) {
        return whenComplete(scheduler, exception -> {
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
    public <E1 extends Exception, E2 extends Exception> Task<Void> whenComplete(Scheduler scheduler, ExceptionalConsumer<T, E1> success, ExceptionalConsumer<Exception, E2> failure) {
        return whenComplete(scheduler, () -> success.accept(getResult()), failure);
    }

    public static Task<Void> runAsync(ExceptionalRunnable<?> closure) {
        return runAsync(Schedulers.defaultScheduler(), closure);
    }

    public static Task<Void> runAsync(String name, ExceptionalRunnable<?> closure) {
        return runAsync(name, Schedulers.defaultScheduler(), closure);
    }

    public static Task<Void> runAsync(Scheduler scheduler, ExceptionalRunnable<?> closure) {
        return runAsync(getCaller(), scheduler, closure);
    }

    public static Task<Void> runAsync(String name, Scheduler scheduler, ExceptionalRunnable<?> closure) {
        return new SimpleTask<>(closure.toCallable(), scheduler).setName(name);
    }

    public static <T> Task<T> composeAsync(ExceptionalSupplier<Task<T>, ?> fn) {
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
        };
    }

    public static <V> Task<V> supplyAsync(Callable<V> callable) {
        return supplyAsync(getCaller(), callable);
    }

    public static <V> Task<V> supplyAsync(Scheduler scheduler, Callable<V> callable) {
        return supplyAsync(getCaller(), scheduler, callable);
    }

    public static <V> Task<V> supplyAsync(String name, Callable<V> callable) {
        return supplyAsync(name, Schedulers.defaultScheduler(), callable);
    }

    public static <V> Task<V> supplyAsync(String name, Scheduler scheduler, Callable<V> callable) {
        return new SimpleTask<>(callable, scheduler).setName(name);
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
        private final Scheduler scheduler;

        SimpleTask(Callable<T> callable, Scheduler scheduler) {
            this.callable = callable;
            this.scheduler = scheduler;
        }

        @Override
        public Scheduler getScheduler() {
            return scheduler;
        }

        @Override
        public void execute() throws Exception {
            setResult(callable.call());
        }
    }

    private class UniApply<R> extends Task<R> {
        private final Scheduler scheduler;
        private final ExceptionalFunction<T, R, ?> callable;

        UniApply(String name, Scheduler scheduler, ExceptionalFunction<T, R, ?> callable) {
            this.scheduler = scheduler;
            this.callable = callable;

            setName(name);
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return Collections.singleton(Task.this);
        }

        @Override
        public Scheduler getScheduler() {
            return scheduler;
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
    }
}
