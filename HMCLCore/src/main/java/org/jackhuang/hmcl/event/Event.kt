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

open class Event(source: Any) : EventObject(source) {
    /**
     * true if this event is canceled.
     *
     * @throws UnsupportedOperationException if trying to cancel a non-cancelable event.
     */
    var isCanceled = false
        set(value) {
            if (!isCancelable)
                throw UnsupportedOperationException("Attempted to cancel a non-cancelable event: ${this.javaClass}")
            field = value
        }

    /**
     * Retutns the value set as the result of this event
     */
    var result = Result.DEFAULT
        set(value) {
            if (!hasResult)
                throw UnsupportedOperationException("Attempted to set result on a no result event: ${this.javaClass} of type.")
            field = value
        }

    /**
     * true if this Event this cancelable.
     */
    open val isCancelable = false

    /**
     * true if this event has a significant result.
     */
    open val hasResult = false

    enum class Result {
        DENY,
        DEFAULT,
        ALLOW
    }
}