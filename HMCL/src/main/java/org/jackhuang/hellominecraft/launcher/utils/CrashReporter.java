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
package org.jackhuang.hellominecraft.launcher.utils;

import java.text.DateFormat;
import java.util.Date;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.launcher.launch.MinecraftCrashAdvicer;
import org.jackhuang.hellominecraft.utils.UpdateChecker;
import org.jackhuang.hellominecraft.utils.system.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.views.LogWindow;

/**
 *
 * @author hyh
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {

    boolean enableLogger = false;

    public CrashReporter(boolean enableLogger) {
        this.enableLogger = enableLogger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            String text = "\n---- Hello Minecraft! Crash Report ----\n";
            text += "  Version: " + Main.makeVersion() + "\n";
            text += "  Time: " + DateFormat.getDateInstance().format(new Date()) + "\n";
            text += "  Thread: " + t.toString() + "\n";
            text += "\n  Advice: \n    ";
            text += MinecraftCrashAdvicer.getAdvice(StrUtils.getStackTrace(e), true);
            text += "\n  Content: \n    ";
            text += StrUtils.getStackTrace(e) + "\n\n";
            text += "-- System Details --\n";
            text += "  Operating System: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ") version " + System.getProperty("os.version") + "\n";
            text += "  Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor") + "\n";
            text += "  Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor") + "\n";
            if (enableLogger) HMCLog.err(text);
            else System.out.println(text);
            SwingUtilities.invokeLater(() -> LogWindow.instance.showAsCrashWindow(UpdateChecker.OUT_DATED));
        } catch (Throwable ex) {
            try {
                MessageBox.Show(e.getMessage() + "\n" + ex.getMessage(), "ERROR", MessageBox.ERROR_MESSAGE);
            } catch (Throwable exx) {
                System.out.println("Failed to catch exception thrown by " + t + " on " + Main.makeVersion() + ".");
                exx.printStackTrace();
            }
        }
    }

}
