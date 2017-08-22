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

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Determines how a task is executed.
 *
 * @see [Task.scheduler]
 */
interface Scheduler {
    /**
     * Schedules the given task.
     * @return the future, null if future is not supported.
     */
    fun schedule(block: () -> Unit): Future<*>? = schedule(Callable { block() })

    /**
     * Schedules the given task.
     * @return the future, null if future is not supported.
     */
    fun schedule(block: Callable<Unit>): Future<*>?

    private class SchedulerImpl(val executor: (Runnable) -> Unit) : Scheduler {
        override fun schedule(block: Callable<Unit>): Future<*>? {
            val latch = CountDownLatch(1)
            val wrapper = AtomicReference<Exception>()
            executor.invoke(Runnable {
                try {
                    block.call()
                } catch (e: Exception) {
                    wrapper.set(e)
                } finally {
                    latch.countDown()
                }
            })
            return object : Future<Unit> {
                override fun get(timeout: Long, unit: TimeUnit) {
                    latch.await(timeout, unit)
                    val e = wrapper.get()
                    if (e != null) throw ExecutionException(e)
                }
                override fun get() {
                    latch.await()
                    val e = wrapper.get()
                    if (e != null) throw ExecutionException(e)
                }
                override fun isDone() = latch.count == 0L
                override fun isCancelled() = false
                override fun cancel(mayInterruptIfRunning: Boolean) = false
            }
        }
    }

    private class SchedulerExecutorService(val executorService: ExecutorService) : Scheduler {
        override fun schedule(block: Callable<Unit>): Future<*> = executorService.submit(block)
    }

    companion object Schedulers {
        private val CACHED_EXECUTOR: ExecutorService by lazy {
            ThreadPoolExecutor(0, Integer.MAX_VALUE,
                    60L, TimeUnit.SECONDS,
                    SynchronousQueue<Runnable>());
        }

        private val IO_EXECUTOR: ExecutorService by lazy {
            Executors.newFixedThreadPool(6) { r: Runnable ->
                val thread: Thread = Executors.defaultThreadFactory().newThread(r)
                thread.isDaemon = true
                thread
            }
        }

        private val SINGLE_EXECUTOR: ExecutorService by lazy {
            Executors.newSingleThreadExecutor { r: Runnable ->
                val thread: Thread = Executors.defaultThreadFactory().newThread(r)
                thread.isDaemon = true
                thread
            }
        }

        val IMMEDIATE: Scheduler = object : Scheduler {
            override fun schedule(block: Callable<Unit>): Future<*>? {
                block.call()
                return null
            }
        }

        /**
         * A scheduler for JavaFX UI operations.
         */
        val JAVAFX: Scheduler = SchedulerImpl(javafx.application.Platform::runLater)

        /**
         * A scheduler for Swing UI operations.
         */
        val SWING: Scheduler = SchedulerImpl(javax.swing.SwingUtilities::invokeLater)

        /**
         * A scheduler that always create new threads to execute tasks.
         * For tasks that do not do heavy operations.
         */
        val NEW_THREAD: Scheduler = SchedulerExecutorService(CACHED_EXECUTOR)

        /**
         * A scheduler that exclusively executes tasks that do I/O operations.
         * The number tasks that do I/O operations in the meantime cannot be larger then 6.
         */
        val IO: Scheduler = SchedulerExecutorService(IO_EXECUTOR)

        /**
         * A scheduler that exclusively executes tasks that do computations.
         * The best way to do computations is an event queue.
         */
        val COMPUTATION: Scheduler = SchedulerExecutorService(SINGLE_EXECUTOR)

        /**
         * The default scheduler for tasks to be executed.
         * @see [Task.scheduler]
         */
        val DEFAULT = NEW_THREAD

        /**
         * Shut down all executor services to guarantee that the application can stop implicitly.
         */
        fun shutdown() {
            CACHED_EXECUTOR.shutdown()
            IO_EXECUTOR.shutdown()
            SINGLE_EXECUTOR.shutdown()
        }
    }
}