/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.functions.TrueFunction;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.utils.CollectionUtils;
import org.jackhuang.hellominecraft.utils.Event;
import org.jackhuang.hellominecraft.utils.system.JavaProcess;
import org.jackhuang.hellominecraft.utils.system.MessageBox;
import org.jackhuang.hellominecraft.utils.system.ProcessThread;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.views.LogWindow;

/**
 *
 * @author huangyuhui
 */
public class LaunchFinisher implements Event<List<String>> {

    private final HashSet<Thread> al = new HashSet<>();

    @Override
    public boolean call(Object sender, List<String> str) {
        final GameLauncher obj = (GameLauncher) sender;
        obj.launchEvent.register((sender1, p) -> {
            if (obj.getProfile().getLauncherVisibility() == 0 && !LogWindow.instance.isVisible())
                System.exit(0);
            else if (obj.getProfile().getLauncherVisibility() == 2)
                MainFrame.instance.closeMessage();
            else {
                if (LogWindow.instance.isVisible())
                    LogWindow.instance.setExit(TrueFunction.instance);
                MainFrame.instance.dispose();
            }
            Event<JavaProcess> event = (sender2, t) -> {
                processThreadStopped((ProcessThread) sender2, obj, t, false);
                return true;
            };
            ProcessThread a = new ProcessThread(p, true, true);
            a.stopEvent.register((sender3, p1) -> {
                if (p1.getExitCode() != 0 && p1.getStdErrLines().size() > 0 && StrUtils.containsOne(p1.getStdErrLines(), Arrays.asList("Could not create the Java Virtual Machine.",
                        "Error occurred during initialization of VM",
                        "A fatal exception has occurred. Program will exit."))) MessageBox.Show(C.i18n("launch.cannot_create_jvm"));
                processThreadStopped((ProcessThread) sender3, obj, p1, false);
                return true;
            });
            a.start();
            al.add(a);
            a = new ProcessThread(p, false, true);
            a.stopEvent.register(event);
            a.start();
            al.add(a);
            a = new ProcessThread(p, false, false);
            a.stopEvent.register(event);
            a.start();
            al.add(a);
            return true;
        });
        obj.launch(str);
        return true;
    }

    void processThreadStopped(ProcessThread t, GameLauncher obj, JavaProcess p, boolean forceTermintate) {
        al.remove(t);
        al.removeAll(CollectionUtils.sortOut(al, t1 -> !t1.isAlive()));
        if (al.isEmpty() || forceTermintate) {
            for (Thread a : al) a.interrupt();
            al.clear();
            GameLauncher.PROCESS_MANAGER.onProcessStopped(p);
            if (obj.getProfile().getLauncherVisibility() != 2 && !LogWindow.instance.isVisible())
                System.exit(0);
        }
    }
}
