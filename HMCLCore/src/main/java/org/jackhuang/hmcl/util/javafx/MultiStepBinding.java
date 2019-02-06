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
package org.jackhuang.hmcl.util.javafx;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jackhuang.hmcl.util.InvocationDispatcher;

import javafx.application.Platform;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.value.ObservableValue;

/**
 * @author yushijinhun
 */
public abstract class MultiStepBinding<T, U> extends ObjectBinding<U> {

    public static <T> MultiStepBinding<?, T> of(ObservableValue<T> property) {
        return new SimpleBinding<>(property);
    }

    protected final ObservableValue<T> predecessor;

    public MultiStepBinding(ObservableValue<T> predecessor) {
        this.predecessor = requireNonNull(predecessor);
        bind(predecessor);
    }

    public <V> MultiStepBinding<?, V> map(Function<U, V> mapper) {
        return new MappedBinding<>(this, mapper);
    }

    public <V> MultiStepBinding<?, V> flatMap(Function<U, ? extends ObservableValue<V>> mapper) {
        return flatMap(mapper, null);
    }

    public <V> MultiStepBinding<?, V> flatMap(Function<U, ? extends ObservableValue<V>> mapper, Supplier<V> nullAlternative) {
        return new FlatMappedBinding<>(map(mapper), nullAlternative);
    }

    public <V> MultiStepBinding<?, V> asyncMap(Function<U, V> mapper, V initial, Executor executor) {
        return new AsyncMappedBinding<>(this, mapper, executor, initial);
    }

    private static class SimpleBinding<T> extends MultiStepBinding<T, T> {

        public SimpleBinding(ObservableValue<T> predecessor) {
            super(predecessor);
        }

        @Override
        protected T computeValue() {
            return predecessor.getValue();
        }

        @Override
        public <V> MultiStepBinding<?, V> map(Function<T, V> mapper) {
            return new MappedBinding<>(predecessor, mapper);
        }

        @Override
        public <V> MultiStepBinding<?, V> asyncMap(Function<T, V> mapper, V initial, Executor executor) {
            return new AsyncMappedBinding<>(predecessor, mapper, executor, initial);
        }
    }

    private static class MappedBinding<T, U> extends MultiStepBinding<T, U> {

        private final Function<T, U> mapper;

        public MappedBinding(ObservableValue<T> predecessor, Function<T, U> mapper) {
            super(predecessor);
            this.mapper = mapper;
        }

        @Override
        protected U computeValue() {
            return mapper.apply(predecessor.getValue());
        }
    }

    private static class FlatMappedBinding<T extends ObservableValue<U>, U> extends MultiStepBinding<T, U> {

        private final Supplier<U> nullAlternative;
        private T lastObservable = null;

        public FlatMappedBinding(ObservableValue<T> predecessor, Supplier<U> nullAlternative) {
            super(predecessor);
            this.nullAlternative = nullAlternative;
        }

        @Override
        protected U computeValue() {
            T currentObservable = predecessor.getValue();
            if (currentObservable != lastObservable) {
                if (lastObservable != null) {
                    unbind(lastObservable);
                }
                if (currentObservable != null) {
                    bind(currentObservable);
                }
                lastObservable = currentObservable;
            }

            if (currentObservable == null) {
                if (nullAlternative == null) {
                    throw new NullPointerException();
                } else {
                    return nullAlternative.get();
                }
            } else {
                return currentObservable.getValue();
            }
        }
    }

    private static class AsyncMappedBinding<T, U> extends MultiStepBinding<T, U> {

        private final InvocationDispatcher<T> dispatcher;

        private boolean initialized = false;
        private T prev;
        private U value;

        public AsyncMappedBinding(ObservableValue<T> predecessor, Function<T, U> mapper, Executor executor, U initial) {
            super(predecessor);
            this.value = initial;

            dispatcher = InvocationDispatcher.runOn(executor, arg -> {
                synchronized (this) {
                    if (initialized && Objects.equals(arg, prev)) {
                        return;
                    }
                }
                U newValue = mapper.apply(arg);
                synchronized (this) {
                    prev = arg;
                    value = newValue;
                    initialized = true;
                }
                Platform.runLater(this::invalidate);
            });
        }

        // called on FX thread, this method is serial
        @Override
        protected U computeValue() {
            T currentPrev = predecessor.getValue();
            U value;
            boolean updateNeeded = false;
            synchronized (this) {
                value = this.value;
                if (!initialized || !Objects.equals(currentPrev, prev)) {
                    updateNeeded = true;
                }
            }
            if (updateNeeded) {
                dispatcher.accept(currentPrev);
            }
            return value;
        }

    }
}
