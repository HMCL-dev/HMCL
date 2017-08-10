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
package org.jackhuang.hmcl.task

internal class CoupleTask<P: Task>(private val pred: P, private val succ: Task.(P) -> Task?, override val reliant: Boolean) : Task() {
    override val hidden: Boolean = true

    override val dependents: Collection<Task> = listOf(pred)
    override val dependencies: MutableCollection<Task> = mutableListOf()

    override fun execute() {
        val task = this.succ(pred)
        if (task != null)
            dependencies += task
    }
}

/**
 * @param b A runnable that decides what to do next, You can also do something here.
 */
infix fun <T: Task> T.then(b: Task.(T) -> Task?): Task = CoupleTask(this, b, true)

/**
 * @param b A runnable that decides what to do next, You can also do something here.
 */
infix fun <T: Task> T.with(b: Task.(T) -> Task?): Task = CoupleTask(this, b, false)