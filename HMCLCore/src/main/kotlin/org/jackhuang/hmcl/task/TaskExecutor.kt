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
import org.jackhuang.hmcl.util.LOG
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level

class TaskExecutor(private val task: Task) {
    var taskListener: TaskListener? = null

    var canceled = false
        private set
    val totTask = AtomicInteger(0)
    val variables = AutoTypingMap<String>(mutableMapOf())
    var lastException: Exception? = null
        private set
    private val workerQueue = ConcurrentLinkedQueue<Future<*>>()

    /**
     * Start the subscription and run all registered tasks asynchronously.
     */
    fun start() {
        workerQueue.add(Scheduler.NEW_THREAD.schedule {
            if (!executeTasks(listOf(task)))
                taskListener?.onTerminate()
        })
    }

    @Throws(InterruptedException::class)
    fun test(): Boolean {
        var flag = true
        val future = Scheduler.NEW_THREAD.schedule {
            if (!executeTasks(listOf(task))) {
                taskListener?.onTerminate()
                flag = false
            }
        }
        workerQueue.add(future)
        future!!.get()
        return flag
    }

    /**
     * Cancel the subscription ant interrupt all tasks.
     */
    fun cancel() {
        canceled = true

        while (!workerQueue.isEmpty())
            workerQueue.poll()?.cancel(true)
    }

    private fun executeTasks(tasks: Collection<Task>): Boolean {
        if (tasks.isEmpty())
            return true

        totTask.addAndGet(tasks.size)
        val success = AtomicBoolean(true)
        val counter = CountDownLatch(tasks.size)
        for (task in tasks) {
            if (canceled)
                return false
            val invoker = Invoker(task, counter, success)
            val future = task.scheduler.schedule(invoker)
            if (future != null)
                workerQueue.add(future)
        }
        if (canceled)
            return false
        try {
            counter.await()
            return success.get() && !canceled
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            // Once interrupted, we are aborting the subscription.
            // and operations fail.
            return false
        }
    }

    private fun executeTask(t: Task): Boolean {
        if (canceled)
            return false

        if (!t.hidden)
            LOG.fine("Executing task: ${t.title}")
        taskListener?.onReady(t)
        val doDependentsSucceeded = executeTasks(t.dependents)

        var flag = false
        try {
            if (!doDependentsSucceeded && t.reliesOnDependents || canceled)
                throw SilentException()

            t.variables = variables
            t.execute()
            if (t is TaskResult<*>)
                variables[t.id] = t.result

            if (!executeTasks(t.dependencies) && t.reliesOnDependencies)
                throw IllegalStateException("Subtasks failed for ${t.title}")

            flag = true
            if (!t.hidden)
                LOG.finer("Task finished: ${t.title}")

            if (!t.hidden) {
                t.onDone(TaskEvent(source = this, task = t, failed = false))
                taskListener?.onFinished(t)
            }
        } catch (e: InterruptedException) {
            if (!t.hidden) {
                lastException = e
                LOG.log(Level.FINE, "Task aborted: ${t.title}", e)
                t.onDone(TaskEvent(source = this, task = t, failed = true))
                taskListener?.onFailed(t, e)
            }
        } catch (e: SilentException) {
            // nothing here
        } catch (e: Exception) {
            if (!t.hidden) {
                lastException = e
                LOG.log(Level.SEVERE, "Task failed: ${t.title}", e)
                t.onDone(TaskEvent(source = this, task = t, failed = true))
                taskListener?.onFailed(t, e)
            }
        } finally {
            t.variables = null
        }
        return flag
    }

    private inner class Invoker(private val task: Task, private val latch: CountDownLatch, private val boolean: AtomicBoolean): Callable<Unit> {
        override fun call() {
            try {
                Thread.currentThread().name = task.title
                if (!executeTask(task))
                    boolean.set(false)
            } finally {
                latch.countDown()
            }
        }
    }
}