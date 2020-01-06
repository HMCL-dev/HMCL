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
public final class SimpleMultimap<K, V> {

    private final Map<K, Collection<V>> map;
    private final Supplier<Collection<V>> valuer;

    public SimpleMultimap(Supplier<Map<K, Collection<V>>> mapper, Supplier<Collection<V>> valuer) {
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
        for (Map.Entry<K, Collection<V>> entry : map.entrySet())
            res.addAll(entry.getValue());
        return res;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsKey(K key) {
        return map.containsKey(key) && !map.get(key).isEmpty();
    }

    public Collection<V> get(K key) {
        return map.computeIfAbsent(key, any -> valuer.get());
    }

    public void put(K key, V value) {
        Collection<V> set = get(key);
        set.add(value);
    }

    public Collection<V> removeKey(K key) {
        return map.remove(key);
    }

    public boolean removeValue(V value) {
        boolean flag = false;
        for (Collection<V> c : map.values())
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
