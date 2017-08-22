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

import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The managed process.
 *
 * @param process the raw system process that this instance manages.
 * @param commands the command line of [process].
 * @see [org.jackhuang.hmcl.launch.ExitWaiter]
 * @see [org.jackhuang.hmcl.launch.StreamPump]
 */
class ManagedProcess(
        val process: Process,
        val commands: List<String>
) {
    /**
     * To save some information you need.
     */
    val properties = mutableMapOf<String, Any>()

    /**
     * The standard output/error lines.
     */
    val lines: Queue<String> = ConcurrentLinkedQueue<String>()

    /**
     * The related threads.
     *
     * If a thread is monitoring this raw process,
     * you are required to add the instance to [relatedThreads].
     */
    val relatedThreads = mutableListOf<Thread>()

    /**
     * True if the managed process is running.
     */
    val isRunning: Boolean = try {
        process.exitValue()
        true
    } catch (ex: IllegalThreadStateException) {
        false
    }

    /**
     * The exit code of raw process.
     */
    val exitCode: Int get() = process.exitValue()

    /**
     * Destroys the raw process and other related threads that are monitoring this raw process.
     */
    fun stop() {
        process.destroy()
        relatedThreads.forEach(Thread::interrupt)
    }

    override fun toString() = "ManagedProcess[commands=$commands, isRunning=$isRunning]"
}