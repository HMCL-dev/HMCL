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

import javafx.beans.property.ReadOnlyDoubleWrapper
import javafx.beans.property.ReadOnlyStringWrapper
import org.jackhuang.hmcl.event.EventManager
import org.jackhuang.hmcl.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

/**
 * Disposable task.
 */
abstract class Task {
    /**
     * True if not logging when executing this task.
     */
    open val hidden: Boolean = false

    /**
     * The scheduler that decides how this task runs.
     */
    open val scheduler: Scheduler = Scheduler.DEFAULT

    /**
     * True if requires all dependent tasks finishing successfully.
     *
     * **Note** if this field is set false, you are not supposed to invoke [run]
     */
    open val reliant: Boolean = true

    var title: String = this.javaClass.toString()

    /**
     * @see Thread.isInterrupted
     * @throws InterruptedException if current thread is interrupted
     */
    @Throws(Exception::class)
    abstract fun execute()

    infix fun parallel(couple: Task): Task = ParallelTask(this, couple)

    /**
     * The collection of sub-tasks that should execute before this task running.
     */
    open val dependents: Collection<Task> = emptySet()

    /**
     * The collection of sub-tasks that should execute after this task running.
     */
    open val dependencies: Collection<Task> = emptySet()

    protected open val progressInterval = 1000L
    private var lastTime = Long.MIN_VALUE
    private val progressUpdate = AtomicReference<Double>()
    val progressProperty = ReadOnlyDoubleWrapper(this, "progress", 0.0)
    protected fun updateProgress(progress: Int, total: Int) = updateProgress(1.0 * progress / total)
    protected fun updateProgress(progress: Double) {
        val now = System.currentTimeMillis()
        if (now - lastTime >= progressInterval) {
            progressProperty.updateAsync(progress, progressUpdate)
            lastTime = now
        }
    }

    private val messageUpdate = AtomicReference<String>()
    val messageProperty = ReadOnlyStringWrapper(this, "message", null)
    protected fun updateMessage(newMessage: String) = messageProperty.updateAsync(newMessage, messageUpdate)

    val onDone = EventManager<TaskEvent>()

    /**
     * **Note** reliant does not work here, which is always treated as true here.
     */
    @Throws(Exception::class)
    fun run() {
        dependents.forEach(subTaskRunnable)
        execute()
        dependencies.forEach(subTaskRunnable)
        onDone(TaskEvent(this, this, false))
    }

    private val subTaskRunnable = { task: Task ->
        this.messageProperty.bind(task.messageProperty)
        this.progressProperty.bind(task.progressProperty)
        task.run()
        this.messageProperty.unbind()
        this.progressProperty.unbind()
    }

    fun executor() = TaskExecutor().submit(this)

    fun subscribe(subscriber: Task) {
        executor().submit(subscriber).start()
    }

    fun subscribe(scheduler: Scheduler = Scheduler.DEFAULT, closure: () -> Unit) = subscribe(Task.of(closure, scheduler))

    override fun toString(): String {
        return title
    }

    companion object {
        fun of(closure: () -> Unit, scheduler: Scheduler = Scheduler.DEFAULT): Task = SimpleTask(closure, scheduler)
        fun <V> of(callable: Callable<V>): TaskResult<V> = TaskCallable(callable)
    }
}