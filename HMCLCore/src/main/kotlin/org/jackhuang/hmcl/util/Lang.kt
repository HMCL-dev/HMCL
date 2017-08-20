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

import javafx.beans.property.Property
import javafx.beans.value.ObservableValue
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.HashMap
import sun.text.normalizer.UTF16.append
import java.lang.reflect.Array.getLength

inline fun ignoreException(func: () -> Unit) {
    try {
        func()
    } catch(ignore: Exception) {}
}

inline fun ignoreThrowable(func: () -> Unit) {
    try {
        func()
    } catch (ignore: Throwable) {}
}

fun <K, V> unmodifiableMap(map: Map<K, V>?): Map<K, V>? =
        if (map == null) null
        else Collections.unmodifiableMap(map)

fun <K, V> copyMap(map: Map<K, V>?): MutableMap<K, V>? =
        if (map == null) null
        else HashMap(map)

fun <T> unmodifiableList(list: List<T>?): List<T>? =
        if (list == null) null
        else Collections.unmodifiableList(list)

fun <T> copyList(list: List<T>?): MutableList<T>? =
        if (list == null) null
        else LinkedList(list)

fun <T> merge(vararg c: Collection<T>): List<T> = LinkedList<T>().apply {
    for (a in c)
        addAll(a)
}

fun isBlank(str: String?) = str?.isBlank() ?: true
fun isNotBlank(str: String?) = !isBlank(str)

fun String.tokenize(delim: String = " \t\n\r"): List<String> {
    val list = mutableListOf<String>()
    val tokenizer = StringTokenizer(this, delim)
    while (tokenizer.hasMoreTokens())
        list.add(tokenizer.nextToken())
    return list
}

fun String.asVersion(): String? {
    if (count { it != '.' && (it < '0' || it > '9') } > 0 || isBlank())
        return null
    val s = split(".")
    for (i in s) if (i.isBlank()) return null
    val builder = StringBuilder()
    var last = s.size - 1
    for (i in s.size - 1 downTo 0)
        if (s[i].toInt() == 0)
            last = i
    for (i in 0 .. last)
        builder.append(s[i]).append('.')
    return builder.deleteCharAt(builder.length - 1).toString()
}


fun parseParams(addBefore: String, objects: Collection<*>, addAfter: String): String {
    return parseParams(addBefore, objects.toTypedArray(), addAfter)
}

fun parseParams(addBefore: String, objects: Array<*>, addAfter: String): String {
    return parseParams({ addBefore }, objects, { addAfter })
}

fun parseParams(beforeFunc: (Any?) -> String, params: Array<*>?, afterFunc: (Any?) -> String): String {
    if (params == null)
        return ""
    val sb = StringBuilder()
    for (i in params.indices) {
        val param = params[i]
        val addBefore = beforeFunc(param)
        val addAfter = afterFunc(param)
        if (i > 0)
            sb.append(addAfter).append(addBefore)
        if (param == null)
            sb.append("null")
        else if (param.javaClass.isArray) {
            sb.append("[")
            if (param is Array<*>) {
                sb.append(parseParams(beforeFunc, param, afterFunc))
            } else
                for (j in 0..java.lang.reflect.Array.getLength(param) - 1) {
                    if (j > 0)
                        sb.append(addAfter)
                    sb.append(addBefore).append(java.lang.reflect.Array.get(param, j))
                }
            sb.append("]")
        } else
            sb.append(addBefore).append(params[i])
    }
    return sb.toString()
}

fun Collection<String>.containsOne(vararg matcher: String): Boolean {
    for (a in this)
        for (b in matcher)
            if (a.toLowerCase().contains(b.toLowerCase()))
                return true
    return false
}

fun <T> Property<in T>.updateAsync(newValue: T, update: AtomicReference<T>) {
    if (update.getAndSet(newValue) == null) {
        UI_THREAD_SCHEDULER {
            val current = update.getAndSet(null)
            this.value = current
        }
    }
}