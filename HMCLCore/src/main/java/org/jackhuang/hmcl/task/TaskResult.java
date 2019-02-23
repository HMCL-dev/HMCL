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
public abstract class TaskResult<V> extends Task {

    private V result;
    private Consumer<V> resultConsumer;

    @Override
    public TaskResult<V> setName(String name) {
        super.setName(name);
        return this;
    }

    public V getResult() {
        return result;
    }

    public void setResult(V result) {
        this.result = result;
        if (resultConsumer != null)
            resultConsumer.accept(result);
    }

    public TaskResult<V> storeTo(Consumer<V> resultConsumer) {
        this.resultConsumer = resultConsumer;
        return this;
    }

    public <R, E extends Exception> TaskResult<R> thenTaskResult(ExceptionalFunction<V, TaskResult<R>, E> taskSupplier) {
        return new TaskResult<R>() {
            TaskResult<R> then;

            @Override
            public Collection<? extends Task> getDependents() {
                return Collections.singleton(TaskResult.this);
            }

            @Override
            public void execute() throws Exception {
                then = taskSupplier.apply(TaskResult.this.getResult()).storeTo(this::setResult);
            }

            @Override
            public Collection<? extends Task> getDependencies() {
                return then == null ? Collections.emptyList() : Collections.singleton(then);
            }
        };
    }

    public <R, E extends Exception> Task then(ExceptionalFunction<V, Task, E> taskSupplier) {
        return new CoupleTask(this, () -> taskSupplier.apply(getResult()), true);
    }

    public <R, E extends Exception> TaskResult<R> thenResult(ExceptionalFunction<V, R, E> task) {
        return thenResult(Schedulers.defaultScheduler(), task);
    }

    public <R, E extends Exception> TaskResult<R> thenResult(Scheduler scheduler, ExceptionalFunction<V, R, E> task) {
        return thenResult(getCaller(), scheduler, task);
    }

    public <R, E extends Exception> TaskResult<R> thenResult(String name, Scheduler scheduler, ExceptionalFunction<V, R, E> task) {
        return new Subtask<>(name, scheduler, task);
    }

    // stupid javac stop us from renaming thenVoid to thenResult
    public <E extends Exception> Task thenVoid(ExceptionalConsumer<V, E> task) {
        return thenVoid(Schedulers.defaultScheduler(), task);
    }

    public <E extends Exception> Task thenVoid(Scheduler scheduler, ExceptionalConsumer<V, E> task) {
        return new CoupleTask(this, () -> Task.of(scheduler, () -> task.accept(getResult())), true);
    }

    public <T extends Exception, K extends Exception> Task finalized(Scheduler scheduler, ExceptionalConsumer<V, T> success, ExceptionalConsumer<Exception, K> failure) {
        return finalized(scheduler, () -> success.accept(getResult()), failure);
    }

    public Task finalized(Scheduler scheduler, FinalizedCallback<V> callback) {
        return finalized(scheduler, ((isDependentsSucceeded, exception) -> callback.execute(getResult(), isDependentsSucceeded, exception)));
    }

    private class Subtask<R> extends TaskResult<R> {
        private final Scheduler scheduler;
        private final ExceptionalFunction<V, R, ?> callable;

        public Subtask(String name, Scheduler scheduler, ExceptionalFunction<V, R, ?> callable) {
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
        void execute(V result, boolean isDependentsSucceeded, Exception exception) throws Exception;
    }
}
