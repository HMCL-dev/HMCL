/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.util;

import javafx.application.Platform;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.ui.CrashWindow;
import org.jackhuang.hmcl.ui.construct.MessageBox;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;

/**
 * @author huangyuhui
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {

    private static final HashMap<String, String> SOURCE = new HashMap<String, String>() {
        {
            put("javafx.fxml.LoadException", Launcher.i18n("crash.NoClassDefFound"));
            put("UnsatisfiedLinkError", Launcher.i18n("crash.user_fault"));
            put("java.lang.NoClassDefFoundError", Launcher.i18n("crash.NoClassDefFound"));
            put("java.lang.VerifyError", Launcher.i18n("crash.NoClassDefFound"));
            put("java.lang.NoSuchMethodError", Launcher.i18n("crash.NoClassDefFound"));
            put("java.lang.IncompatibleClassChangeError", Launcher.i18n("crash.NoClassDefFound"));
            put("java.lang.ClassFormatError", Launcher.i18n("crash.NoClassDefFound"));
            put("java.lang.OutOfMemoryError", "FUCKING MEMORY LIMIT!");
            put("Trampoline", Launcher.i18n("launcher.update_java"));
            put("com.sun.javafx.css.StyleManager.findMatchingStyles", Launcher.i18n("launcher.update_java"));
            put("NoSuchAlgorithmException", "Has your operating system been installed completely or is a ghost system?");
        }
    };

    private boolean checkThrowable(Throwable e) {
        String s = StringUtils.getStackTrace(e);
        for (HashMap.Entry<String, String> entry : SOURCE.entrySet())
            if (s.contains(entry.getKey())) {
                if (StringUtils.isNotBlank(entry.getValue())) {
                    String info = entry.getValue();
                    Logging.LOG.severe(info);
                    try {
                        MessageBox.show(info);
                    } catch (Throwable t) {
                        Logging.LOG.log(Level.SEVERE, "Unable to show message", t);
                    }
                }
                return false;
            }
        return true;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        String stackTrace = StringUtils.getStackTrace(e);
        if (!stackTrace.contains("org.jackhuang"))
            return;

        if (THROWABLE_SET.contains(stackTrace))
            return;
        THROWABLE_SET.add(stackTrace);

        try {
            String text = "---- Hello Minecraft! Crash Report ----\n" +
                    "  Version: " + Launcher.VERSION + "\n" +
                    "  Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n" +
                    "  Thread: " + t.toString() + "\n" +
                    "\n  Content: \n    " +
                    stackTrace + "\n\n" +
                    "-- System Details --\n" +
                    "  Operating System: " + System.getProperty("os.name") + ' ' + OperatingSystem.SYSTEM_VERSION + "\n" +
                    "  Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor") + "\n" +
                    "  Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor") + "\n";

            Logging.LOG.log(Level.SEVERE, text);

            if (checkThrowable(e) && !text.contains("OpenJDK")) {
                Platform.runLater(() -> new CrashWindow(text).show());
                if (!Launcher.UPDATE_CHECKER.isOutOfDate())
                    reportToServer(text);
            }
        } catch (Throwable ex) {
            Logging.LOG.log(Level.SEVERE, "Unable to caught exception", ex);
            Logging.LOG.log(Level.SEVERE, "There is the original exception", e);
        }
    }

    private static final HashSet<String> THROWABLE_SET = new HashSet<>();

    private void reportToServer(final String text) {
        Thread t = new Thread(() -> {
            HashMap<String, String> map = new HashMap<>();
            map.put("crash_report", text);
            map.put("version", Launcher.VERSION);
            map.put("log", Logging.getLogs());
            try {
                String response = NetworkUtils.doPost(NetworkUtils.toURL("http://huangyuhui.duapp.com/hmcl/crash.php"), map);
                if (StringUtils.isNotBlank(response))
                    Logging.LOG.log(Level.SEVERE, "Crash server response: " + response);
            } catch (IOException ex) {
                Logging.LOG.log(Level.SEVERE, "Unable to post HMCL server.", ex);
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
