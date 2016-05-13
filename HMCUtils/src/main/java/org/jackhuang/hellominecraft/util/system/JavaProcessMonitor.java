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
package org.jackhuang.hellominecraft.util.system;

import java.util.Arrays;
import java.util.HashSet;
import org.jackhuang.hellominecraft.util.CollectionUtils;
import org.jackhuang.hellominecraft.util.EventHandler;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
public class JavaProcessMonitor {

    private final HashSet<Thread> al = new HashSet<>();
    /**
     * this event will be executed only if the application returned 0.
     */
    public final EventHandler<JavaProcess> stoppedEvent = new EventHandler<>(this);
    /**
     * When the monitored application exited with exit code not zero, this event
     * will be executed. Event args is the exit code.
     */
    public final EventHandler<Integer> applicationExitedAbnormallyEvent = new EventHandler<>(this);
    /**
     * When jvm crashed, this event will be executed. Event args is the exit
     * code.
     */
    public final EventHandler<Integer> jvmLaunchFailedEvent = new EventHandler<>(this);
    private final JavaProcess p;

    public JavaProcessMonitor(JavaProcess p) {
        this.p = p;
    }

    public JavaProcess getJavaProcess() {
        return p;
    }

    public void start() {
        ProcessThread a = new ProcessThread(p);
        a.stopEvent.register((sender, t) -> {
            HMCLog.log("Process exit code: " + t.getExitCode());
            if (t.getExitCode() != 0 || StrUtils.containsOne(t.getStdOutLines(),
                                                             Arrays.asList("Unable to launch"),
                                                             x -> Level.guessLevel(x, Level.INFO).lessOrEqual(Level.ERROR)))
                applicationExitedAbnormallyEvent.execute(t.getExitCode());
            if (t.getExitCode() != 0 && StrUtils.containsOne(t.getStdOutLines(),
                                                             Arrays.asList("Could not create the Java Virtual Machine.",
                                                                           "Error occurred during initialization of VM",
                                                                           "A fatal exception has occurred. Program will exit.",
                                                                           "Unable to launch"),
                                                             x -> Level.guessLevel(x, Level.INFO).lessOrEqual(Level.ERROR)))
                jvmLaunchFailedEvent.execute(t.getExitCode());
            processThreadStopped((ProcessThread) sender, false);
            return true;
        });
        a.start();
        al.add(a);
    }

    void processThreadStopped(ProcessThread t, boolean forceTermintate) {
        al.remove(t);
        al.removeAll(CollectionUtils.map(al, t1 -> !t1.isAlive()));
        if (al.isEmpty() || forceTermintate) {
            for (Thread a : al)
                a.interrupt();
            al.clear();
            stoppedEvent.execute(p);
        }
    }
}
