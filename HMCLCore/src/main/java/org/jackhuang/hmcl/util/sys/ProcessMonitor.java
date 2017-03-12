/*
 * Hello Minecraft!.
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.process.JVMLaunchFailedEvent;
import org.jackhuang.hmcl.api.event.process.JavaProcessExitedAbnormallyEvent;
import org.jackhuang.hmcl.api.event.process.JavaProcessStartingEvent;
import org.jackhuang.hmcl.api.event.process.JavaProcessStoppedEvent;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.log.Level;
import org.jackhuang.hmcl.api.IProcess;
import org.jackhuang.hmcl.api.event.SimpleEvent;
import org.jackhuang.hmcl.api.func.Consumer;

/**
 *
 * @author huangyuhui
 */
public class ProcessMonitor {

    public static final HashSet<ProcessMonitor> MONITORS = new HashSet<>();

    private final CountDownLatch latch = new CountDownLatch(2);
    ProcessThread inputThread, errorThread;
    private final IProcess p;

    public ProcessMonitor(IProcess p) {
        this.p = p;
        inputThread = new ProcessThread(this, false);
        errorThread = new ProcessThread(this, true);
        inputThread.stopEvent.register(this::threadStopped);
        inputThread.stopEvent.register(event -> processThreadStopped((ProcessThread) event.getSource()));
        errorThread.stopEvent.register(this::threadStopped);
    }

    public IProcess getProcess() {
        return p;
    }

    private Object tag;

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public void registerPrintlnEvent(Consumer<PrintlnEvent> c) {
        inputThread.printlnEvent.register(c);
        errorThread.printlnEvent.register(c);
    }

    public void start() {
        hasFired = false;
        MONITORS.add(this);
        HMCLApi.EVENT_BUS.fireChannel(new JavaProcessStartingEvent(this, p));
        inputThread.start();
        errorThread.start();
    }

    private void threadStopped(SimpleEvent<IProcess> event) {
        latch.countDown();
        ProcessThread t = (ProcessThread) event.getSource();
        HMCLog.log("Process exit code: " + p.getExitCode());
        if (p.getExitCode() != 0 || StrUtils.containsOne(t.getLines(),
                Arrays.asList("Unable to launch"),
                x -> Level.guessLevel(x, Level.INFO).lessOrEqual(Level.ERROR)))
            synchronized (this) {
                if (!hasFired) {
                    hasFired = true;
                    HMCLApi.EVENT_BUS.fireChannel(new JavaProcessExitedAbnormallyEvent(ProcessMonitor.this, p));
                }
            }
        if (p.getExitCode() != 0 && StrUtils.containsOne(t.getLines(),
                Arrays.asList("Could not create the Java Virtual Machine.",
                        "Error occurred during initialization of VM",
                        "A fatal exception has occurred. Program will exit.",
                        "Unable to launch"),
                x -> Level.guessLevel(x, Level.INFO).lessOrEqual(Level.ERROR)))
            synchronized (this) {
                if (!hasFired) {
                    hasFired = true;
                    HMCLApi.EVENT_BUS.fireChannel(new JVMLaunchFailedEvent(ProcessMonitor.this, p));
                }
            }
    }

    boolean hasFired = false;

    private void processThreadStopped(ProcessThread t1) {
        MONITORS.remove(this);
        errorThread.interrupt();
        HMCLApi.EVENT_BUS.fireChannel(new JavaProcessStoppedEvent(this, p));
    }

    public static void stopAll() {
        for (ProcessMonitor monitor : MONITORS) {
            monitor.getProcess().getRawProcess().destroy();
            monitor.inputThread.interrupt();
            monitor.errorThread.interrupt();
        }
    }

    public void waitForCommandLineCompletion() {
        try {
            latch.await();
        } catch (InterruptedException ignore) {
            HMCLog.warn("Thread has been interrupted.", ignore);
        }
    }
}
