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

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import org.jackhuang.hmcl.util.function.ExceptionalFunction;

import javafx.beans.binding.ObjectBinding;

/**
 * @author yushijinhun
 */
public class ObservableOptionalCache<K, V, E extends Exception> {

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
