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

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import javafx.beans.property.Property
import javafx.event.Event.fireEvent
import org.jackhuang.hmcl.event.Event
import org.jackhuang.hmcl.event.EventBus
import org.jackhuang.hmcl.event.EventManager
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Schedulers
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.TaskResult
import org.jackhuang.hmcl.util.Constants.UI_THREAD_SCHEDULER
import java.io.InputStream
import java.lang.reflect.Type
import java.net.URL
import java.rmi.activation.Activatable.unregister
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

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

fun Any?.toStringOrEmpty() = this?.toString().orEmpty()

fun String.toURL() = URL(this)

fun Collection<String>.containsOne(vararg matcher: String): Boolean {
    for (a in this)
        for (b in matcher)
            if (a.toLowerCase().contains(b.toLowerCase()))
                return true
    return false
}

fun <T> Property<in T>.updateAsync(newValue: T, update: AtomicReference<T>) {
    if (update.getAndSet(newValue) == null) {
        UI_THREAD_SCHEDULER.accept(Runnable {
            val current = update.getAndSet(null)
            this.value = current
        })
    }
}

inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type

inline fun <reified T> Gson.fromJson(json: String): T? = fromJson<T>(json, T::class.java)

inline fun <reified T> Gson.fromJsonQuietly(json: String): T? {
    try {
        return fromJson<T>(json)
    } catch (json: JsonParseException) {
        return null
    }
}

fun task(scheduler: Scheduler = Schedulers.defaultScheduler(), closure: (AutoTypingMap<String>) -> Unit): Task = Task.of(closure, scheduler)
fun <V> taskResult(id: String, callable: Callable<V>): TaskResult<V> = Task.ofResult(id, callable)
fun <V> taskResult(id: String, callable: (AutoTypingMap<String>) -> V): TaskResult<V> = Task.ofResult(id, callable)

fun InputStream.readFullyAsString() = IOUtils.readFullyAsString(this)
inline fun <reified T : Event> EventBus.channel() = channel(T::class.java)

operator fun <T : Event> EventManager<T>.plusAssign(func: (T) -> Unit) = register(func)
operator fun <T : Event> EventManager<T>.plusAssign(func: () -> Unit) = register(func)
operator fun <T : Event> EventManager<T>.minusAssign(func: (T) -> Unit) = unregister(func)
operator fun <T : Event> EventManager<T>.minusAssign(func: () -> Unit) = unregister(func)
operator fun <T : Event> EventManager<T>.invoke(event: T) = fireEvent(event)