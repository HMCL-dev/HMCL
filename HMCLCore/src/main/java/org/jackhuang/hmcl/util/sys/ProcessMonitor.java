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

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.process.JavaProcessStartingEvent;
import org.jackhuang.hmcl.api.IProcess;
import org.jackhuang.hmcl.api.func.Consumer;

/**
 *
 * @author huangyuhui
 */
public class ProcessMonitor {

    public static final HashSet<ProcessMonitor> MONITORS = new HashSet<>();

    private final CountDownLatch latch = new CountDownLatch(2);
    ProcessThread inputThread;
    ProcessThread errorThread;
    ExitWaiter waitorThread;
    WaitForThread waitForThread;
    private final IProcess p;

    public ProcessMonitor(IProcess p) {
        this.p = p;
        inputThread = new ProcessThread(this, false);
        errorThread = new ProcessThread(this, true);
        waitorThread = new ExitWaiter(this, this::processThreadStopped);
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
        MONITORS.add(this);
        HMCLApi.EVENT_BUS.fireChannel(new JavaProcessStartingEvent(this, p));
        inputThread.start();
        errorThread.start();
    }

    private void processThreadStopped() {
        MONITORS.remove(this);
    }

    public static void stopAll() {
        for (ProcessMonitor monitor : MONITORS) {
            monitor.getProcess().getRawProcess().destroy();
            monitor.inputThread.interrupt();
            monitor.errorThread.interrupt();
            monitor.waitorThread.interrupt();
        }
    }

    public void waitForCommandLineCompletion() {
        try {
            latch.await();
        } catch (InterruptedException ignore) {
        }
    }
}
