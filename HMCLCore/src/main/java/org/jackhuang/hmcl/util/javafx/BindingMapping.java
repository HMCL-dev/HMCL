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
package org.jackhuang.hmcl.util.javafx;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.value.ObservableValue;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.handleUncaughtException;

/**
 * @author yushijinhun
 */
public abstract class BindingMapping<T, U> extends ObjectBinding<U> {

    public static <T> BindingMapping<?, T> of(ObservableValue<T> property) {
        if (property instanceof BindingMapping) {
            return (BindingMapping<?, T>) property;
        }
        return new SimpleBinding<>(property);
    }

    public static <S extends Observable, T> BindingMapping<?, T> of(S watched, Function<S, T> mapper) {
        return of(Bindings.createObjectBinding(() -> mapper.apply(watched), watched));
    }

    protected final ObservableValue<? extends T> predecessor;

    public BindingMapping(ObservableValue<? extends T> predecessor) {
        this.predecessor = requireNonNull(predecessor);
        bind(predecessor);
    }

    public <V> BindingMapping<?, V> map(Function<? super U, ? extends V> mapper) {
        return new MappedBinding<>(this, mapper);
    }

    public <V> BindingMapping<?, V> flatMap(Function<? super U, ? extends ObservableValue<? extends V>> mapper) {
        return flatMap(mapper, null);
    }

    public <V> BindingMapping<?, V> flatMap(Function<? super U, ? extends ObservableValue<? extends V>> mapper, Supplier<? extends V> nullAlternative) {
        return new FlatMappedBinding<>(map(mapper), nullAlternative);
    }

    public <V> BindingMapping<?, V> asyncMap(Function<U, CompletableFuture<V>> mapper, V initial) {
        return new AsyncMappedBinding<>(this, mapper, initial);
    }

    private static class SimpleBinding<T> extends BindingMapping<T, T> {

        public SimpleBinding(ObservableValue<T> predecessor) {
            super(predecessor);
        }

        @Override
        protected T computeValue() {
            return predecessor.getValue();
        }

        @Override
        public <V> BindingMapping<?, V> map(Function<? super T, ? extends V> mapper) {
            return new MappedBinding<>(predecessor, mapper);
        }

        @Override
        public <V> BindingMapping<?, V> asyncMap(Function<T, CompletableFuture<V>> mapper, V initial) {
            return new AsyncMappedBinding<>(predecessor, mapper, initial);
        }
    }

    private static class MappedBinding<T, U> extends BindingMapping<T, U> {

        private final Function<? super T, ? extends U> mapper;

        public MappedBinding(ObservableValue<? extends T> predecessor, Function<? super T, ? extends U> mapper) {
            super(predecessor);
            this.mapper = mapper;
        }

        @Override
        protected U computeValue() {
            return mapper.apply(predecessor.getValue());
        }
    }

    private static class FlatMappedBinding<T extends ObservableValue<? extends U>, U> extends BindingMapping<T, U> {

        private final Supplier<? extends U> nullAlternative;
        private T lastObservable = null;

        public FlatMappedBinding(ObservableValue<? extends T> predecessor, Supplier<? extends U> nullAlternative) {
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

    private static class AsyncMappedBinding<T, U> extends BindingMapping<T, U> {

        private boolean initialized = false;
        private T prev;
        private U value;

        private final Function<? super T, ? extends CompletableFuture<? extends U>> mapper;
        private T computingPrev;
        private boolean computing = false;

        public AsyncMappedBinding(ObservableValue<? extends T> predecessor, Function<? super T, ? extends CompletableFuture<? extends U>> mapper, U initial) {
            super(predecessor);
            this.value = initial;
            this.mapper = mapper;
        }

        private void tryUpdateValue(T currentPrev) {
            synchronized (this) {
                if ((initialized && Objects.equals(prev, currentPrev))
                        || isComputing(currentPrev)) {
                    return;
                }
                computing = true;
                computingPrev = currentPrev;
            }

            CompletableFuture<? extends U> task;
            try {
                task = requireNonNull(mapper.apply(currentPrev));
            } catch (Throwable e) {
                valueUpdateFailed(currentPrev);
                throw e;
            }

            task.handle((result, e) -> {
                if (e == null) {
                    valueUpdate(currentPrev, result);
                    Platform.runLater(this::invalidate);
                } else {
                    handleUncaughtException(e);
                    valueUpdateFailed(currentPrev);
                }
                return null;
            });
        }

        private void valueUpdate(T currentPrev, U computed) {
            synchronized (this) {
                if (isComputing(currentPrev)) {
                    computing = false;
                    computingPrev = null;
                    prev = currentPrev;
                    value = computed;
                    initialized = true;
                }
            }
        }

        private void valueUpdateFailed(T currentPrev) {
            synchronized (this) {
                if (isComputing(currentPrev)) {
                    computing = false;
                    computingPrev = null;
                }
            }
        }

        private boolean isComputing(T prev) {
            return computing && Objects.equals(prev, computingPrev);
        }

        @Override
        protected U computeValue() {
            tryUpdateValue(predecessor.getValue());
            return value;
        }
    }
}
