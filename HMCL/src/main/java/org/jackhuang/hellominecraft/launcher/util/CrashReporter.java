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
package org.jackhuang.hellominecraft.launcher.util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import static org.jackhuang.hellominecraft.launcher.Main.LAUNCHER_VERSION;
import org.jackhuang.hellominecraft.launcher.setting.Settings;
import org.jackhuang.hellominecraft.util.NetUtils;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.system.OS;
import org.jackhuang.hellominecraft.util.ui.LogWindow;

/**
 *
 * @author huangyuhui
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {

    private static final Logger LOGGER = Logger.getLogger(CrashReporter.class.getName());

    boolean enableLogger = false;

    public CrashReporter(boolean enableLogger) {
        this.enableLogger = enableLogger;
    }

    public boolean checkThrowable(Throwable e) {
        String s = StrUtils.getStackTrace(e);
        if (s.contains("MessageBox") || s.contains("AWTError"))
            return false;
        else if (s.contains("JFileChooser") || s.contains("JceSecurityManager")) {
            LOGGER.severe("Is not your operating system installed completely? ");
            return false;
        }
        if (s.contains("sun.awt.shell.Win32ShellFolder2") || s.contains("UnsatisfiedLinkError")) {
            LOGGER.severe(C.i18n("crash.user_fault"));
            try {
                showMessage(C.i18n("crash.user_fault"));
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Failed to show message", t);
            }
            return false;
        } else if (s.contains("java.awt.HeadlessException")) {
            LOGGER.severe(C.i18n("crash.headless"));
            try {
                showMessage(C.i18n("crash.headless"));
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Failed to show message", t);
            }
            return false;
        } else if (s.contains("java.lang.NoClassDefFoundError") || s.contains("java.lang.VerifyError") || s.contains("java.lang.NoSuchMethodError") || s.contains("java.lang.IncompatibleClassChangeError") || s.contains("java.lang.ClassFormatError")) {
            LOGGER.severe(C.i18n("crash.NoClassDefFound"));
            try {
                showMessage(C.i18n("crash.NoClassDefFound"));
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Failed to show message", t);
            }
            return false;
        } else if (s.contains("java.lang.OutOfMemoryError")) {
            LOGGER.severe("FUCKING MEMORY LIMIT!");
            return false;
        }
        return true;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        String s = StrUtils.getStackTrace(e);
        if (!s.contains("org.jackhuang.hellominecraft"))
            return;
        try {
            String text = "\n---- Hello Minecraft! Crash Report ----\n";
            text += "  Version: " + LAUNCHER_VERSION + "\n";
            text += "  Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n";
            text += "  Thread: " + t.toString() + "\n";
            text += "\n  Content: \n    ";
            text += s + "\n\n";
            text += "-- System Details --\n";
            text += "  Operating System: " + OS.getSystemVersion() + "\n";
            text += "  Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor") + "\n";
            text += "  Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor") + "\n";
            if (enableLogger)
                HMCLog.err(text);
            else
                System.out.println(text);

            if (checkThrowable(e) && !System.getProperty("java.vm.name").contains("OpenJDK")) {
                SwingUtilities.invokeLater(() -> LogWindow.INSTANCE.showAsCrashWindow(Settings.UPDATE_CHECKER.OUT_DATED));
                if (!Settings.UPDATE_CHECKER.OUT_DATED)
                    reportToServer(text, s);
            }
        } catch (Throwable ex) {
            LOGGER.log(Level.SEVERE, "Failed to caught exception", ex);
            LOGGER.log(Level.SEVERE, "There is the original exception", e);
        }
    }

    void showMessage(String s) {
        try {
            MessageBox.Show(s, "ERROR", MessageBox.ERROR_MESSAGE);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "ERROR", e);
        }
    }

    private static final HashSet<String> THROWABLE_SET = new HashSet<>();

    void reportToServer(final String text, String stacktrace) {
        if (THROWABLE_SET.contains(stacktrace))
            return;
        THROWABLE_SET.add(stacktrace);
        new Thread(() -> {
            HashMap<String, String> map = new HashMap<>();
            map.put("CrashReport", text);
            try {
                NetUtils.post(NetUtils.constantURL("http://huangyuhui.duapp.com/crash.php"), map);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to post HMCL server.", ex);
            }
        }).start();
    }

}
