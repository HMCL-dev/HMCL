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
package org.jackhuang.hellominecraft.util.sys;

import java.util.Arrays;
import java.util.HashSet;
import org.jackhuang.hellominecraft.util.CollectionUtils;
import org.jackhuang.hellominecraft.api.HMCAPI;
import org.jackhuang.hellominecraft.api.event.process.JVMLaunchFailedEvent;
import org.jackhuang.hellominecraft.api.event.process.JavaProcessExitedAbnormallyEvent;
import org.jackhuang.hellominecraft.api.event.process.JavaProcessStartingEvent;
import org.jackhuang.hellominecraft.api.event.process.JavaProcessStoppedEvent;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.log.Level;

/**
 *
 * @author huangyuhui
 */
public class JavaProcessMonitor {

    private final HashSet<Thread> al = new HashSet<>();
    private final JavaProcess p;

    public JavaProcessMonitor(JavaProcess p) {
        this.p = p;
    }

    public JavaProcess getJavaProcess() {
        return p;
    }
    
    private Object tag;

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }
    
    public void start() {
        HMCAPI.EVENT_BUS.fireChannel(new JavaProcessStartingEvent(this, p));
        ProcessThread a = new ProcessThread(p);
        a.stopEvent.register(event -> {
            HMCLog.log("Process exit code: " + p.getExitCode());
            if (p.getExitCode() != 0 || StrUtils.containsOne(p.getStdOutLines(),
                                                             Arrays.asList("Unable to launch"),
                                                             x -> Level.guessLevel(x, Level.INFO).lessOrEqual(Level.ERROR)))
                HMCAPI.EVENT_BUS.fireChannel(new JavaProcessExitedAbnormallyEvent(JavaProcessMonitor.this, p));
            if (p.getExitCode() != 0 && StrUtils.containsOne(p.getStdOutLines(),
                                                             Arrays.asList("Could not create the Java Virtual Machine.",
                                                                           "Error occurred during initialization of VM",
                                                                           "A fatal exception has occurred. Program will exit.",
                                                                           "Unable to launch"),
                                                             x -> Level.guessLevel(x, Level.INFO).lessOrEqual(Level.ERROR)))
                HMCAPI.EVENT_BUS.fireChannel(new JVMLaunchFailedEvent(JavaProcessMonitor.this, p));
            processThreadStopped((ProcessThread) event.getSource(), false);
        });
        a.start();
        al.add(a);
    }

    void processThreadStopped(ProcessThread t1, boolean forceTermintate) {
        CollectionUtils.removeIf(al, t -> t == t1 || !t.isAlive());
        if (al.isEmpty() || forceTermintate) {
            for (Thread a : al)
                a.interrupt();
            al.clear();
            HMCAPI.EVENT_BUS.fireChannel(new JavaProcessStoppedEvent(this, p));
        }
    }
}
