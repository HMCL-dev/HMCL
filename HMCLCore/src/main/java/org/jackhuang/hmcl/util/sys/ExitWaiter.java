/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.util.sys;

import java.util.Collection;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.IProcess;
import org.jackhuang.hmcl.api.event.process.JVMLaunchFailedEvent;
import org.jackhuang.hmcl.api.event.process.JavaProcessExitedAbnormallyEvent;
import org.jackhuang.hmcl.api.event.process.JavaProcessStoppedEvent;
import org.jackhuang.hmcl.util.CollectionUtils;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.util.log.Level;

/**
 *
 * @author huang
 */
public class ExitWaiter extends Thread {

    private final ProcessMonitor monitor;
    private final IProcess process;
    private final Runnable callback;

    /**
     * Constructor
     * @param monitor
     * @param callback nullable, will be called when process exited.
     */
    public ExitWaiter(ProcessMonitor monitor, Runnable callback) {
        this.monitor = monitor;
        this.process = monitor.getProcess();
        this.callback = callback;
        
        setName("exit-waitor");
    }

    @Override
    public void run() {
        try {
            int exitCode = process.getRawProcess().waitFor();
            monitor.errorThread.join();
            monitor.inputThread.join();

            Collection<String> errorLines = CollectionUtils.filter(process.getStdOutLines(), str -> Level.isError(Level.guessLevel(str)));

            // LaunchWrapper will terminate the application with exit code 0, though this is error state.
            if (exitCode != 0 || StrUtils.containsOne(errorLines, "Unable to launch"))
                HMCLApi.EVENT_BUS.fireChannel(new JavaProcessExitedAbnormallyEvent(monitor, process));
            else if (exitCode != 0 && StrUtils.containsOne(errorLines,
                    "Could not create the Java Virtual Machine.",
                    "Error occurred during initialization of VM",
                    "A fatal exception has occurred. Program will exit.",
                    "Unable to launch"))
                HMCLApi.EVENT_BUS.fireChannel(new JVMLaunchFailedEvent(monitor, process));
            else
                HMCLApi.EVENT_BUS.fireChannel(new JavaProcessStoppedEvent(this, process));
            
            if (callback != null)
                callback.run();
        } catch (InterruptedException ex) {
        }
    }
}
