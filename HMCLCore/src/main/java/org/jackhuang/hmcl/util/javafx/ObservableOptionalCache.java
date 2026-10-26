// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.javafx;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import javafx.beans.binding.ObjectBinding;

/**
 * @author yushijinhun
 */
public final class ObservableOptionalCache<K, V, E extends Exception> {

    private final ObservableCache<K, Optional<V>, E> backed;

    public ObservableOptionalCache(ExceptionalFunction<K, Optional<V>, E> source, BiConsumer<K, Throwable> exceptionHandler, Executor executor) {
        backed = new ObservableCache<>(source, exceptionHandler, Optional.empty(), executor);
    }

    public Optional<V> getImmediately(K key) {
        return backed.getImmediately(key).flatMap(it -> it);
    }

    public void put(K key, V value) {
        backed.put(key, Optional.of(value));
    }

    public Optional<V> get(K key) {
        return backed.get(key);
    }

    public Optional<V> getDirectly(K key) throws E {
        return backed.getDirectly(key);
    }

    public ObjectBinding<Optional<V>> binding(K key) {
        return backed.binding(key);
    }

    public ObjectBinding<Optional<V>> binding(K key, boolean quiet) {
        return backed.binding(key, quiet);
    }

    public void invalidate(K key) {
        backed.invalidate(key);
    }
}
