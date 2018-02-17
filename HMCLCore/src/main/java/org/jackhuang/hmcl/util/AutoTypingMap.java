/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A map that support auto casting.
 *
 * @author huangyuhui
 */
public final class AutoTypingMap<K> {

    private final Map<K, Object> impl;

    public AutoTypingMap(Map<K, Object> impl) {
        this.impl = impl;
    }

    public synchronized <V> V get(K key) {
        return (V) impl.get(key);
    }

    public synchronized  <V> Optional<V> getOptional(K key) {
        return Optional.ofNullable(get(key));
    }

    public synchronized void set(K key, Object value) {
        if (value != null)
            impl.put(key, value);
    }

    public Collection<Object> values() {
        return impl.values();
    }

    public Set<K> keys() {
        return impl.keySet();
    }

    public boolean containsKey(K key) {
        return impl.containsKey(key);
    }

    public Object remove(K key) {
        return impl.remove(key);
    }
}
