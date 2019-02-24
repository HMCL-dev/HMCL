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

import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * A task that has a result.
 *
 * @author huangyuhui
 */
public abstract class TaskResult<T> extends Task {

    private T result;
    private Consumer<T> resultConsumer;

    @Override
    public TaskResult<T> setName(String name) {
        super.setName(name);
        return this;
    }

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
     * @return this TaskResult
     */
    public TaskResult<T> storeTo(Consumer<T> action) {
        this.resultConsumer = action;
        return this;
    }

    /**
     * Returns a new TaskResult that, when this task completes
     * normally, is executed with result of this task as the argument
     * to the supplied function.
     *
     * @param fn the function returning a new TaskResult
     * @param <U> the type of the returned TaskResult's result
     * @return the TaskResult
     */
    public <U, E extends Exception> TaskResult<U> thenCompose(ExceptionalFunction<T, TaskResult<U>, E> fn) {
        return new TaskResult<U>() {
            TaskResult<U> then;

            @Override
            public Collection<? extends Task> getDependents() {
                return Collections.singleton(TaskResult.this);
            }

            @Override
            public void execute() throws Exception {
                then = fn.apply(TaskResult.this.getResult()).storeTo(this::setResult);
            }

            @Override
            public Collection<? extends Task> getDependencies() {
                return then == null ? Collections.emptyList() : Collections.singleton(then);
            }
        };
    }

    /**
     * Returns a new Task that, when this task completes
     * normally, is executed with this task as the argument
     * to the supplied function.
     *
     * @param fn the function returning a new Task
     * @return the Task
     */
    public <E extends Exception> Task then(ExceptionalFunction<T, Task, E> fn) {
        return new CoupleTask(this, () -> fn.apply(getResult()), true);
    }

    /**
     * Returns a new TaskResult that, when this task completes
     * normally, is executed using the default Scheduler, with this
     * task's result as the argument to the supplied function.
     *
     * @param fn the function to use to compute the value of the returned TaskResult
     * @param <U> the function's return type
     * @return the new TaskResult
     */
    public <U, E extends Exception> TaskResult<U> thenApply(ExceptionalFunction<T, U, E> fn) {
        return thenApply(Schedulers.defaultScheduler(), fn);
    }

    /**
     * Returns a new TaskResult that, when this task completes
     * normally, is executed using the supplied Scheduler, with this
     * task's result as the argument to the supplied function.
     *
     * @param scheduler the executor to use for asynchronous execution
     * @param fn the function to use to compute the value of the returned TaskResult
     * @param <U> the function's return type
     * @return the new TaskResult
     */
    public <U, E extends Exception> TaskResult<U> thenApply(Scheduler scheduler, ExceptionalFunction<T, U, E> fn) {
        return thenApply(getCaller(), scheduler, fn);
    }

    /**
     * Returns a new TaskResult that, when this task completes
     * normally, is executed using the supplied Scheduler, with this
     * task's result as the argument to the supplied function.
     *
     * @param name the name of this new TaskResult for displaying
     * @param scheduler the executor to use for asynchronous execution
     * @param fn the function to use to compute the value of the returned TaskResult
     * @param <U> the function's return type
     * @return the new TaskResult
     */
    public <U, E extends Exception> TaskResult<U> thenApply(String name, Scheduler scheduler, ExceptionalFunction<T, U, E> fn) {
        return new Subtask<>(name, scheduler, fn);
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
    public <E extends Exception> Task thenAccept(ExceptionalConsumer<T, E> action) {
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
    public <E extends Exception> Task thenAccept(Scheduler scheduler, ExceptionalConsumer<T, E> action) {
        return new CoupleTask(this, () -> Task.of(scheduler, () -> action.accept(getResult())), true);
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
    public <E1 extends Exception, E2 extends Exception> Task whenComplete(Scheduler scheduler, ExceptionalConsumer<T, E1> success, ExceptionalConsumer<Exception, E2> failure) {
        return whenComplete(scheduler, () -> success.accept(getResult()), failure);
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
    public Task whenComplete(Scheduler scheduler, FinalizedCallback<T> action) {
        return whenComplete(scheduler, ((isDependentSucceeded, exception) -> action.execute(getResult(), isDependentSucceeded, exception)));
    }

    private class Subtask<R> extends TaskResult<R> {
        private final Scheduler scheduler;
        private final ExceptionalFunction<T, R, ?> callable;

        public Subtask(String name, Scheduler scheduler, ExceptionalFunction<T, R, ?> callable) {
            this.scheduler = scheduler;
            this.callable = callable;

            setName(name);
        }

        @Override
        public Collection<? extends Task> getDependents() {
            return Collections.singleton(TaskResult.this);
        }

        @Override
        public Scheduler getScheduler() {
            return scheduler;
        }

        @Override
        public void execute() throws Exception {
            setResult(callable.apply(TaskResult.this.getResult()));
        }
    }

    public interface FinalizedCallback<V> {
        void execute(V result, boolean isDependentSucceeded, Exception exception) throws Exception;
    }
}
