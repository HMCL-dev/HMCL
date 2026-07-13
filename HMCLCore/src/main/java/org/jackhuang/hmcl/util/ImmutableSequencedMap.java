/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.BiConsumer;

/// An unmodifiable [Map] that preserves insertion order.
///
/// This class wrap a [LinkedHashMap] and exposes only read operations;
/// any attempt to modify the map through the standard [Map] mutator methods
/// throws [UnsupportedOperationException].
/// The iteration order of keys, values, and entries is always the order in which
/// they were originally inserted.
///
/// Use the static factory methods [`of()`](#of()) and [`copyOf(Map)`](#copyOf(java.util.Map))
/// to obtain instances.
///
/// @param <K> the type of keys
/// @param <V> the type of values
@NotNullByDefault
public final class ImmutableSequencedMap<K extends @UnknownNullability Object, V extends @UnknownNullability Object>
        implements Map<K, V> {

    /// Shared empty instance.
    private static final ImmutableSequencedMap<?, ?> EMPTY = new ImmutableSequencedMap<>(new LinkedHashMap<>());

    /// Returns the empty immutable sequenced map.
    ///
    /// @param <K> the type of keys
    /// @param <V> the type of values
    /// @return an empty [ImmutableSequencedMap]
    @SuppressWarnings("unchecked")
    public static <K extends @UnknownNullability Object, V extends @UnknownNullability Object> ImmutableSequencedMap<K, V> of() {
        return (ImmutableSequencedMap<K, V>) EMPTY;
    }

    /// Returns an immutable sequenced map containing the entries of the given map
    /// in their original iteration order.
    ///
    /// The entries are copied into an internal [LinkedHashMap],
    /// so subsequent changes to the source map do not affect the returned map.
    ///
    /// @param <K> the type of keys
    /// @param <V> the type of values
    /// @param map the source map whose entries are copied
    /// @return an [ImmutableSequencedMap] with the same entries and order as {@code map}
    @SuppressWarnings("unchecked")
    public static <K extends @UnknownNullability Object, V extends @UnknownNullability Object> ImmutableSequencedMap<K, V> copyOf(Map<? extends K, ? extends V> map) {
        if (map instanceof ImmutableSequencedMap<?, ?>) {
            return (ImmutableSequencedMap<K, V>) map;
        }

        if (map.isEmpty()) {
            return of();
        }

        return new ImmutableSequencedMap<>(new LinkedHashMap<>(map));
    }

    /// Wraps the given [LinkedHashMap] directly without copying.
    ///
    /// The caller **must not** modify the map after passing it to this method;
    /// the resulting [ImmutableSequencedMap] does not
    /// make a defensive copy.
    ///
    /// @param <K> the type of keys
    /// @param <V> the type of values
    /// @param map the linked hash map to wrap
    /// @return an [ImmutableSequencedMap] backed by {@code map}
    public static <K extends @UnknownNullability Object, V extends @UnknownNullability Object> ImmutableSequencedMap<K, V> wrap(@Unmodifiable LinkedHashMap<K, V> map) {
        return new ImmutableSequencedMap<>(map);
    }

    /// The backing map that stores the entries in insertion order.
    private final LinkedHashMap<K, V> map;

    private ImmutableSequencedMap(LinkedHashMap<K, V> map) {
        this.map = map;
    }

    /// {@inheritDoc}
    @Override
    public int size() {
        return map.size();
    }

    /// {@inheritDoc}
    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /// {@inheritDoc}
    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    /// {@inheritDoc}
    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    /// {@inheritDoc}
    @Override
    public V get(Object key) {
        return map.get(key);
    }

    /// Always throws [UnsupportedOperationException] because this map is unmodifiable.
    ///
    /// @throws UnsupportedOperationException always
    @Override
    public @Nullable V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    /// Always throws [UnsupportedOperationException] because this map is unmodifiable.
    ///
    /// @throws UnsupportedOperationException always
    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    /// Always throws [UnsupportedOperationException] because this map is unmodifiable.
    ///
    /// @throws UnsupportedOperationException always
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    /// Always throws [UnsupportedOperationException] because this map is unmodifiable.
    ///
    /// @throws UnsupportedOperationException always
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    /// {@inheritDoc}
    @Override
    public @Unmodifiable Set<K> keySet() {
        return map.keySet();
    }

    /// {@inheritDoc}
    @Override
    public @Unmodifiable Collection<V> values() {
        return map.values();
    }

    /// {@inheritDoc}
    @Override
    public @Unmodifiable Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    /// {@inheritDoc}
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        map.forEach(action);
    }

    /// {@inheritDoc}
    @Override
    public boolean equals(@Nullable Object obj) {
        return obj == this || map.equals(obj);
    }

    /// {@inheritDoc}
    @Override
    public int hashCode() {
        return map.hashCode();
    }

    /// {@inheritDoc}
    @Override
    public String toString() {
        return map.toString();
    }
}
