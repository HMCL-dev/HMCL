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

import org.jackhuang.hmcl.util.ReflectionHelper;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import java.util.Collection;
import java.util.Collections;

/**
 * A task that has a result.
 *
 * @author huangyuhui
 */
public abstract class TaskResult<V> extends Task {

    private V result;

    public V getResult() {
        return result;
    }

    public void setResult(V result) {
        this.result = result;
    }

    public abstract String getId();

    public <R, E extends Exception> TaskResult<R> thenResult(ExceptionalFunction<V, R, E> task) {
        return thenResult(Schedulers.defaultScheduler(), task);
    }

    public <R, E extends Exception> TaskResult<R> thenResult(Scheduler scheduler, ExceptionalFunction<V, R, E> task) {
        return thenResult(ReflectionHelper.getCaller().toString(), scheduler, task);
    }

    public <R, E extends Exception> TaskResult<R> thenResult(String id, Scheduler scheduler, ExceptionalFunction<V, R, E> task) {
        return new Subtask<>(id, scheduler, task);
    }

    public <T extends Exception, K extends Exception> Task finalizedResult(Scheduler scheduler, ExceptionalConsumer<V, T> success, ExceptionalConsumer<Exception, K> failure) {
        return finalized(scheduler, variables -> success.accept(getResult()), failure);
    }

    public Task finalizedResult(Scheduler scheduler, FinalizedCallback<V> callback) {
        return new FinalizedTask(this, scheduler,
                (variables, isDependentsSucceeded) -> callback.execute(getResult(), isDependentsSucceeded),
                ReflectionHelper.getCaller().toString());
    }

    private class Subtask<R> extends TaskResult<R> {
        private final String id;
        private final Scheduler scheduler;
        private final ExceptionalFunction<V, R, ?> callable;

        public Subtask(String id, Scheduler scheduler, ExceptionalFunction<V, R, ?> callable) {
            this.id = id;
            this.scheduler = scheduler;
            this.callable = callable;
        }

        @Override
        public Collection<? extends Task> getDependents() {
            return Collections.singleton(TaskResult.this);
        }

        @Override
        public String getId() {
            return id;
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
        void execute(V result, boolean isDependentsSucceeded) throws Exception;
    }
}
