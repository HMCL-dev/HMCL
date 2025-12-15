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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;

/**
 * @author yushijinhun
 */
public final class ObservableCache<K, V, E extends Exception> {

    private final ReentrantLock lock = new ReentrantLock();
    private final ExceptionalFunction<K, V, E> source;
    private final BiConsumer<K, Throwable> exceptionHandler;
    private final V fallbackValue;
    private final Executor executor;
    private final ObservableHelper observable = new ObservableHelper();
    private final Map<K, V> cache = new HashMap<>();
    private final Map<K, CompletableFuture<V>> pendings = new HashMap<>();
    private final Map<K, Boolean> invalidated = new HashMap<>();

    public ObservableCache(ExceptionalFunction<K, V, E> source, BiConsumer<K, Throwable> exceptionHandler, V fallbackValue, Executor executor) {
        this.source = source;
        this.exceptionHandler = exceptionHandler;
        this.fallbackValue = fallbackValue;
        this.executor = executor;
    }

    public Optional<V> getImmediately(K key) {
        lock.lock();
        try {
            return Optional.ofNullable(cache.get(key));
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            cache.put(key, value);
            invalidated.remove(key);
        } finally {
            lock.unlock();
        }
        Platform.runLater(observable::invalidate);
    }

    private CompletableFuture<V> query(K key, Executor executor) {
        CompletableFuture<V> future;
        lock.lock();
        try {
            CompletableFuture<V> prev = pendings.get(key);
            if (prev != null) {
                return prev;
            } else {
                future = new CompletableFuture<>();
                pendings.put(key, future);
            }
        } finally {
            lock.unlock();
        }

        executor.execute(() -> {
            V result;
            try {
                result = source.apply(key);
            } catch (Throwable ex) {
                lock.lock();
                try {
                    pendings.remove(key);
                } finally {
                    lock.unlock();
                }
                exceptionHandler.accept(key, ex);
                future.completeExceptionally(ex);
                return;
            }

            lock.lock();
            try {
                cache.put(key, result);
                invalidated.remove(key);
                pendings.remove(key, future);
            } finally {
                lock.unlock();
            }
            future.complete(result);
            Platform.runLater(observable::invalidate);
        });

        return future;
    }

    public V get(K key) {
        V cached;
        lock.lock();
        try {
            cached = cache.get(key);
            if (cached != null && !invalidated.containsKey(key)) {
                return cached;
            }
        } finally {
            lock.unlock();
        }

        try {
            return query(key, Runnable::run).join();
        } catch (CompletionException | CancellationException ignored) {
        }

        if (cached == null) {
            return fallbackValue;
        } else {
            return cached;
        }
    }

    public V getDirectly(K key) throws E {
        V result = source.apply(key);
        put(key, result);
        return result;
    }

    public ObjectBinding<V> binding(K key) {
        return binding(key, false);
    }

    /**
     * @param quiet if true, calling get() on the returned binding won't toggle a query
     */
    public ObjectBinding<V> binding(K key, boolean quiet) {
        // This method is thread-safe because ObservableHelper supports concurrent modification
        return Bindings.createObjectBinding(() -> {
            V result;
            boolean refresh;

            lock.lock();
            try {
                result = cache.get(key);
                if (result == null) {
                    result = fallbackValue;
                    refresh = true;
                } else {
                    refresh = invalidated.containsKey(key);
                }
            } finally {
                lock.unlock();
            }
            if (!quiet && refresh) {
                query(key, executor);
            }
            return result;
        }, observable);
    }

    public void invalidate(K key) {
        lock.lock();
        try {
            if (cache.containsKey(key)) {
                invalidated.put(key, Boolean.TRUE);
            }
        } finally {
            lock.unlock();
        }
        Platform.runLater(observable::invalidate);
    }
}
