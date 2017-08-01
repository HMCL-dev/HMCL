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
package org.jackhuang.hmcl.event

import java.util.*

class EventManager<T : EventObject> {
    private val handlers = EnumMap<EventPriority, MutableList<(T) -> Unit>>(EventPriority::class.java).apply {
        for (value in EventPriority.values())
            put(value, LinkedList<(T) -> Unit>())
    }

    fun register(func: (T) -> Unit, priority: EventPriority = EventPriority.NORMAL) {
        if (!handlers[priority]!!.contains(func))
            handlers[priority]!!.add(func)
    }

    fun unregister(func: (T) -> Unit) {
        EventPriority.values().forEach { handlers[it]!!.remove(func) }
    }

    fun fireEvent(event: T) {
        for (priority in EventPriority.values())
            for (handler in handlers[priority]!!)
                handler(event)
    }

    operator fun plusAssign(func: (T) -> Unit) = register(func)
    operator fun minusAssign(func: (T) -> Unit) = unregister(func)
    operator fun invoke(event: T) = fireEvent(event)
}