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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.UpdateChecker;
import org.jackhuang.hellominecraft.utils.system.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.views.LogWindow;

/**
 *
 * @author huangyuhui
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {

    boolean enableLogger = false;

    public CrashReporter(boolean enableLogger) {
        this.enableLogger = enableLogger;
    }

    public boolean checkThrowable(Throwable e) {
        String s = StrUtils.getStackTrace(e);
        if (s.contains("sun.awt.shell.Win32ShellFolder2") || s.contains("UnsatisfiedLinkError")) {
            System.out.println(C.i18n("crash.user_fault"));
            try {
                MessageBox.Show(C.i18n("crash.user_fault"));
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return false;
        } else if (s.contains("java.awt.HeadlessException")) {
            System.out.println(C.i18n("crash.headless"));
            try {
                MessageBox.Show(C.i18n("crash.headless"));
            } catch (Throwable t) {
            }
            return false;
        } else if(s.contains("java.lang.NoClassDefFoundError")) {
            System.out.println(C.i18n("crash.NoClassDefFound"));
            try {
                MessageBox.Show(C.i18n("crash.NoClassDefFound"));
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return false;
        }
        return true;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        String s = StrUtils.getStackTrace(e);
        if (!s.contains("org.jackhuang.hellominecraft")) return;
        try {
            String text = "\n---- Hello Minecraft! Crash Report ----\n";
            text += "  Version: " + Main.makeVersion() + "\n";
            text += "  Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n";
            text += "  Thread: " + t.toString() + "\n";
            text += "\n  Content: \n    ";
            text += s + "\n\n";
            text += "-- System Details --\n";
            text += "  Operating System: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ") version " + System.getProperty("os.version") + "\n";
            text += "  Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor") + "\n";
            text += "  Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor") + "\n";
            if (enableLogger) HMCLog.err(text);
            else System.out.println(text);

            if (checkThrowable(e)) {
                SwingUtilities.invokeLater(() -> LogWindow.instance.showAsCrashWindow(UpdateChecker.OUT_DATED));
                if (!UpdateChecker.OUT_DATED)
                    reportToServer(text, s);
            }
        } catch (Throwable ex) {
            try {
                MessageBox.Show(e.getMessage() + "\n" + ex.getMessage(), "ERROR", MessageBox.ERROR_MESSAGE);
            } catch (Throwable exx) {
                System.out.println("Failed to catch exception thrown by " + t + " on " + Main.makeVersion() + ".");
                exx.printStackTrace();
            }
        }
    }

    private static final HashSet<String> throwableSet = new HashSet<>();

    void reportToServer(String text, String stacktrace) {
        if (throwableSet.contains(stacktrace)) return;
        throwableSet.add(stacktrace);
        new Thread(() -> {
            HashMap<String, String> map = new HashMap<>();
            map.put("CrashReport", text);
            System.out.println(NetUtils.post(NetUtils.constantURL("http://huangyuhui.duapp.com/crash.php"), map));
        }).start();
    }

}
