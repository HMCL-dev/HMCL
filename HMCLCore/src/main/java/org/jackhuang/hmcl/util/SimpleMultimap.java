// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A simple implementation of Multimap.
 * Just a combination of map and set.
 *
 * @author huangyuhui
 */
public final class SimpleMultimap<K, V, M extends Collection<V>> {

    private final Map<K, M> map;
    private final Supplier<M> valuer;

    public SimpleMultimap(Supplier<Map<K, M>> mapper, Supplier<M> valuer) {
        this.map = mapper.get();
        this.valuer = valuer;
    }

    public int size() {
        return values().size();
    }

    public Set<K> keys() {
        return map.keySet();
    }

    public Collection<V> values() {
        Collection<V> res = valuer.get();
        for (Map.Entry<K, M> entry : map.entrySet())
            res.addAll(entry.getValue());
        return res;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsKey(K key) {
        return map.containsKey(key) && !map.get(key).isEmpty();
    }

    public M get(K key) {
        return map.computeIfAbsent(key, any -> valuer.get());
    }

    public void put(K key, V value) {
        M set = get(key);
        set.add(value);
    }

    public void putAll(K key, Collection<? extends V> value) {
        M set = get(key);
        set.addAll(value);
    }

    public M removeKey(K key) {
        return map.remove(key);
    }

    public boolean removeValue(V value) {
        boolean flag = false;
        for (M c : map.values())
            flag |= c.remove(value);
        return flag;
    }

    public boolean removeValue(K key, V value) {
        return get(key).remove(value);
    }

    public void clear() {
        map.clear();
    }

    public void clear(K key) {
        if (map.containsKey(key))
            map.get(key).clear();
        else
            map.put(key, valuer.get());
    }
}
