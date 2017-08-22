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
package org.jackhuang.hmcl.util

import java.util.*

/**
 * A simple implementation of Multimap.
 * Just a combination of map and set.
 */
class SimpleMultimap<K, V>(val maper: () -> MutableMap<K, Collection<V>>, val valuer: () -> MutableCollection<V>) {
    private val map = HashMap<K, MutableCollection<V>>()
    private val valuesImpl: MutableCollection<V> = valuer()

    val size = valuesImpl.size

    val keys: Set<K> get() = map.keys
    val values: Collection<V> = valuesImpl

    val isEmpty: Boolean = size == 0
    val isNotEmpty: Boolean = size != 0

    fun containsKey(key: K): Boolean = map.containsKey(key)
    operator fun get(key: K): Collection<V> = map.getOrPut(key, valuer)

    fun put(key: K, value: V) {
        val set = map.getOrPut(key, valuer)
        set += value
        valuesImpl += value
    }

    fun removeAll(key: K): Collection<V>? {
        val result = map.remove(key)
        if (result != null)
            valuesImpl.removeAll(result)
        return result
    }

    fun remove(value: V) {
        map.values.forEach {
            it.remove(value)
        }
    }

    fun clear() {
        map.clear()
        valuesImpl.clear()
    }

}