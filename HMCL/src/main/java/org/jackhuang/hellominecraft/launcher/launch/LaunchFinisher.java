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

import java.util.List;
import org.jackhuang.hellominecraft.launcher.settings.LauncherVisibility;
import org.jackhuang.hellominecraft.utils.functions.TrueFunction;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.utils.Event;
import org.jackhuang.hellominecraft.utils.system.JavaProcessMonitor;
import org.jackhuang.hellominecraft.views.LogWindow;

/**
 *
 * @author huangyuhui
 */
public class LaunchFinisher implements Event<List<String>> {

    @Override
    public boolean call(Object sender, List<String> str) {
        final GameLauncher obj = (GameLauncher) sender;
        obj.launchEvent.register((sender1, p) -> {
            if (obj.getProfile().getLauncherVisibility() == LauncherVisibility.CLOSE && !LogWindow.instance.isVisible())
                System.exit(0);
            else if (obj.getProfile().getLauncherVisibility() == LauncherVisibility.KEEP)
                MainFrame.instance.closeMessage();
            else {
                if (LogWindow.instance.isVisible())
                    LogWindow.instance.setExit(TrueFunction.instance);
                MainFrame.instance.dispose();
            }
            JavaProcessMonitor jpm = new JavaProcessMonitor(p);
            jpm.stoppedEvent.register((sender3, t) -> {
                if (obj.getProfile().getLauncherVisibility() != LauncherVisibility.KEEP && !LogWindow.instance.isVisible())
                    System.exit(0);
                return true;
            });
            return true;
        });
        obj.launch(str);
        return true;
    }
}
