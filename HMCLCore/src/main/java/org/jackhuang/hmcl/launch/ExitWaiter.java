/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.JVMLaunchFailedEvent;
import org.jackhuang.hmcl.event.ProcessExitedAbnormallyEvent;
import org.jackhuang.hmcl.event.ProcessStoppedEvent;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.ManagedProcess;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 *
 * @author huangyuhui
 */
final class ExitWaiter implements Runnable {

    private final ManagedProcess process;
    private final Collection<Thread> joins;
    private final BiConsumer<Integer, ProcessListener.ExitType> watcher;

    /**
     * Constructor.
     *
     * @param process the process to wait for
     * @param watcher the callback that will be called after process stops.
     */
    public ExitWaiter(ManagedProcess process, Collection<Thread> joins, BiConsumer<Integer, ProcessListener.ExitType> watcher) {
        this.process = process;
        this.joins = joins;
        this.watcher = watcher;
    }

    @Override
    public void run() {
        try {
            int exitCode = process.getProcess().waitFor();

            for (Thread thread : joins)
                thread.join();

            List<String> errorLines = process.getLines().stream()
                    .filter(Log4jLevel::guessLogLineError).collect(Collectors.toList());
            ProcessListener.ExitType exitType;

            // LaunchWrapper will catch the exception logged and will exit normally.
            if (exitCode != 0 && StringUtils.containsOne(errorLines,
                    "Could not create the Java Virtual Machine.",
                    "Error occurred during initialization of VM",
                    "A fatal exception has occurred. Program will exit.")) {
                EventBus.EVENT_BUS.fireEvent(new JVMLaunchFailedEvent(this, process));
                exitType = ProcessListener.ExitType.JVM_ERROR;
            } else if (exitCode != 0 || StringUtils.containsOne(errorLines, "Unable to launch")) {
                EventBus.EVENT_BUS.fireEvent(new ProcessExitedAbnormallyEvent(this, process));
                exitType = ProcessListener.ExitType.APPLICATION_ERROR;
            } else
                exitType = ProcessListener.ExitType.NORMAL;

            EventBus.EVENT_BUS.fireEvent(new ProcessStoppedEvent(this, process));

            watcher.accept(exitCode, exitType);
        } catch (InterruptedException e) {
            watcher.accept(1, ProcessListener.ExitType.INTERRUPTED);
        }
    }

}
