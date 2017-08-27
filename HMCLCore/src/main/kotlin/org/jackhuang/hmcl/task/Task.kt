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

import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.ReadOnlyDoubleWrapper
import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.ReadOnlyStringWrapper
import org.jackhuang.hmcl.event.EventManager
import org.jackhuang.hmcl.util.AutoTypingMap
import org.jackhuang.hmcl.util.updateAsync
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

/**
 * Disposable task.
 *
 * @see [TaskExecutor]
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
     * True if requires all [dependents] finishing successfully.
     *
     * **Note** if this field is set false, you are not supposed to invoke [run]
     * @defaultValue true
     */
    open val reliesOnDependents: Boolean = true

    /**
     * True if requires all [dependencies] finishing successfully.
     *
     * **Note** if this field is set false, you are not supposed to invoke [run]
     * @defaultValue false
     */
    open val reliesOnDependencies: Boolean = true

    open var title: String = this.javaClass.toString()

    var variables: AutoTypingMap<String>? = null

    /**
     * @see Thread.isInterrupted
     * @throws InterruptedException if current thread is interrupted
     */
    @Throws(Exception::class)
    abstract fun execute()

    infix fun then(b: Task): Task = CoupleTask(this, { b }, true)
    infix fun with(b: Task): Task = CoupleTask(this, { b }, false)

    /**
     * The collection of sub-tasks that should execute **before** this task running.
     */
    open val dependents: Collection<Task> = emptySet()

    /**
     * The collection of sub-tasks that should execute **after** this task running.
     */
    open val dependencies: Collection<Task> = emptySet()

    protected open val progressInterval = 1000L
    private var lastTime = Long.MIN_VALUE
    private val progressUpdate = AtomicReference<Double>()
    private val progressPropertyImpl = ReadOnlyDoubleWrapper(this, "progress", 0.0)
    val progressProperty: ReadOnlyDoubleProperty = progressPropertyImpl.readOnlyProperty
        @JvmName("progressProperty") get
    protected fun updateProgress(progress: Int, total: Int) = updateProgress(1.0 * progress / total)
    protected fun updateProgress(progress: Double) {
        val now = System.currentTimeMillis()
        if (now - lastTime >= progressInterval) {
            updateProgressImmediately(progress)
            lastTime = now
        }
    }

    protected fun updateProgressImmediately(progress: Double) {
        progressPropertyImpl.updateAsync(progress, progressUpdate)
    }

    private val messageUpdate = AtomicReference<String>()
    private val messagePropertyImpl = ReadOnlyStringWrapper(this, "message", null)
    val messageProperty: ReadOnlyStringProperty = messagePropertyImpl.readOnlyProperty
        @JvmName("messageProperty") get
    protected fun updateMessage(newMessage: String) = messagePropertyImpl.updateAsync(newMessage, messageUpdate)

    val onDone = EventManager<TaskEvent>()

    /**
     * **Note** [reliesOnDependents] and [reliesOnDependencies] does not work here, which is always treated as true here.
     */
    @Throws(Exception::class)
    fun run() {
        dependents.forEach(subTaskRunnable)
        execute()
        dependencies.forEach(subTaskRunnable)
        onDone(TaskEvent(this, this, false))
    }

    private val subTaskRunnable = { task: Task ->
        this.messagePropertyImpl.bind(task.messagePropertyImpl)
        this.progressPropertyImpl.bind(task.progressPropertyImpl)
        task.run()
        this.messagePropertyImpl.unbind()
        this.progressPropertyImpl.unbind()
    }

    fun executor() = TaskExecutor(this)
    fun executor(taskListener: TaskListener) = TaskExecutor(this).apply { this.taskListener = taskListener }
    fun start() = executor().start()
    fun test() = executor().test()
    fun subscribe(subscriber: Task) = TaskExecutor(with(subscriber)).apply { start() }

    fun subscribe(scheduler: Scheduler = Scheduler.DEFAULT, closure: (AutoTypingMap<String>) -> Unit) = subscribe(task(scheduler, closure))

    override fun toString(): String {
        return title
    }
}

fun task(scheduler: Scheduler = Scheduler.DEFAULT, closure: (AutoTypingMap<String>) -> Unit): Task = SimpleTask(closure, scheduler)
fun <V> taskResult(id: String, callable: Callable<V>): TaskResult<V> = TaskCallable(id, callable)
fun <V> taskResult(id: String, callable: (AutoTypingMap<String>) -> V): TaskResult<V> = TaskCallable2(id, callable)