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

import org.jackhuang.hmcl.util.LOG
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import kotlin.concurrent.thread

class TaskExecutor() {
    var taskListener: TaskListener? = null

    var canceled = false
        private set
    private val totTask = AtomicInteger(0)
    private val taskQueue = ConcurrentLinkedQueue<Task>()
    private val workerQueue = ConcurrentLinkedQueue<Future<*>>()

    /**
     * Submit a task to subscription to run.
     * You can submit a task even when started this subscription.
     * Thread-safe function.
     */
    fun submit(task: Task): TaskExecutor {
        taskQueue.add(task)
        return this
    }

    /**
     * Start the subscription and run all registered tasks asynchronously.
     */
    fun start() {
        thread {
            totTask.addAndGet(taskQueue.size)
            while (!taskQueue.isEmpty() && !canceled) {
                val task = taskQueue.poll()
                if (task != null) {
                    val future = task.scheduler.schedule(Runnable { executeTask(task) })
                    try {
                        future?.get()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
            if (!canceled)
                taskListener?.onTerminate()
        }
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
        try {
            counter.await()
            return success.get()
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
            if (!doDependentsSucceeded && t.reliant)
                throw SilentException()

            t.execute()
            flag = true
            if (!t.hidden)
                LOG.finer("Task finished: ${t.title}")
            executeTasks(t.dependencies)
            if (!t.hidden) {
                t.onDone(TaskEvent(source = this, task = t, failed = false))
                taskListener?.onFinished(t)
            }
        } catch (e: InterruptedException) {
            if (!t.hidden) {
                LOG.log(Level.FINE, "Task aborted: ${t.title}", e)
                t.onDone(TaskEvent(source = this, task = t, failed = true))
                taskListener?.onFailed(t)
            }
        } catch (e: SilentException) {
            // nothing here
        } catch (e: Exception) {
            if (!t.hidden) {
                LOG.log(Level.SEVERE, "Task failed: ${t.title}", e)
                t.onDone(TaskEvent(source = this, task = t, failed = true))
                taskListener?.onFailed(t)
            }
        }
        return flag
    }

    private inner class Invoker(val task: Task, val latch: CountDownLatch, val boolean: AtomicBoolean): Runnable {
        override fun run() {
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