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

/**
 * A map that support auto casting.
 */
class AutoTypingMap<K>(private val impl: MutableMap<K, Any>) {

    fun clear() = impl.clear()

    @Suppress("UNCHECKED_CAST")
    operator fun <V> get(key: K): V = impl[key] as V
    operator fun set(key: K, value: Any?) {
        if (value != null)
            impl[key] = value
    }
    val values get() = impl.values
    val keys get() = impl.keys

    fun containsKey(key: K) = impl.containsKey(key)
    fun remove(key: K) = impl.remove(key)
}