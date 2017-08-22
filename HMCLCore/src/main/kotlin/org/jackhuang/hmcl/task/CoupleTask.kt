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

import org.jackhuang.hmcl.util.AutoTypingMap

/**
 * A task that combines two tasks and make sure [pred] runs before [succ].
 *
 * @param pred the task that runs before [succ]
 * @param succ a callback that returns the task runs after [pred], [succ] will be executed asynchronously. You can do something that relies on the result of [pred].
 * @param reliant true if this task chain will be broken when task [pred] fails.
 */
internal class CoupleTask<P: Task>(pred: P, private val succ: (AutoTypingMap<String>) -> Task?, override val reliant: Boolean) : Task() {
    override val hidden: Boolean = true

    override val dependents: Collection<Task> = listOf(pred)
    override val dependencies: MutableCollection<Task> = mutableListOf()

    override fun execute() {
        val task = this.succ(variables!!)
        if (task != null)
            dependencies += task
    }
}

/**
 * @param b A runnable that decides what to do next, You can also do something here.
 */
infix fun <T: Task> T.then(b: (AutoTypingMap<String>) -> Task?): Task = CoupleTask(this, b, true)

/**
 * @param b A runnable that decides what to do next, You can also do something here.
 */
infix fun <T: Task> T.with(b: (AutoTypingMap<String>) -> Task?): Task = CoupleTask(this, b, false)