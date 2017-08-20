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

import org.jackhuang.hmcl.event.EVENT_BUS
import org.jackhuang.hmcl.event.JVMLaunchFailedEvent
import org.jackhuang.hmcl.event.JavaProcessExitedAbnormallyEvent
import org.jackhuang.hmcl.event.JavaProcessStoppedEvent
import org.jackhuang.hmcl.launch.ProcessListener
import java.util.*

/**
 * @param process the process to wait for
 * @param watcher the callback that will be called after process stops.
 */
internal class ExitWaiter(val process: JavaProcess, val joins: Collection<Thread>, val watcher: (Int, ProcessListener.ExitType) -> Unit) : Runnable {
    override fun run() {
        try {
            process.process.waitFor()

            joins.forEach { it.join() }

            val exitCode = process.exitCode
            val lines = LinkedList<String>()
            lines.addAll(process.stdErrLines)
            lines.addAll(process.stdOutLines)
            val errorLines = lines.filter(::guessLogLineError)
            val exitType: ProcessListener.ExitType
            // LaunchWrapper will catch the exception logged and will exit normally.
            if (exitCode != 0 || errorLines.containsOne("Unable to launch")) {
                EVENT_BUS.fireEvent(JavaProcessExitedAbnormallyEvent(this, process))
                exitType = ProcessListener.ExitType.APPLICATION_ERROR
            } else if (exitCode != 0 && errorLines.containsOne(
                    "Could not create the Java Virtual Machine.",
                    "Error occurred during initialization of VM",
                    "A fatal exception has occurred. Program will exit.",
                    "Unable to launch")) {
                EVENT_BUS.fireEvent(JVMLaunchFailedEvent(this, process))
                exitType = ProcessListener.ExitType.JVM_ERROR
            } else
                exitType = ProcessListener.ExitType.NORMAL

            EVENT_BUS.fireEvent(JavaProcessStoppedEvent(this, process))

            watcher(exitCode, exitType)
        } catch (e: InterruptedException) {
            watcher(1, ProcessListener.ExitType.NORMAL)
        }
    }
}