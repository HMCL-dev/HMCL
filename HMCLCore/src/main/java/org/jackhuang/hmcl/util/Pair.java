/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.util.Map;
import java.util.Objects;

/**
 *
 * @author huangyuhui
 */
public class Pair<K, V> implements Map.Entry<K, V> {

    public static <K, V> Pair<K, V> pair(K key, V value) {
        return new Pair<>(key, value);
    }

    private K key;
    private V value;

    @Deprecated
    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey() {
        return key;
    }

    public void setKey(K key) {
        this.key = key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V setValue(V value) {
        V original = this.value;
        this.value = value;
        return original;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.key);
        hash = 23 * hash + Objects.hashCode(this.value);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Pair<?, ?> other = (Pair<?, ?>) obj;
        return Objects.equals(this.key, other.key) && Objects.equals(this.value, other.value);
    }

    @Override
    public String toString() {
        return "(" + key + ", " + value + ")";
    }
}
