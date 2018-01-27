/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
import org.jackhuang.hmcl.Main;
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
            put("UnsatisfiedLinkError", Main.i18n("crash.user_fault"));
            put("java.lang.NoClassDefFoundError", Main.i18n("crash.NoClassDefFound"));
            put("java.lang.VerifyError", Main.i18n("crash.NoClassDefFound"));
            put("java.lang.NoSuchMethodError", Main.i18n("crash.NoClassDefFound"));
            put("java.lang.IncompatibleClassChangeError", Main.i18n("crash.NoClassDefFound"));
            put("java.lang.ClassFormatError", Main.i18n("crash.NoClassDefFound"));
            put("java.lang.OutOfMemoryError", "FUCKING MEMORY LIMIT!");
            put("Trampoline", Main.i18n("launcher.update_java"));
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
        String s = StringUtils.getStackTrace(e);
        if (!s.contains("org.jackhuang"))
            return;

        try {
            StringBuilder builder = new StringBuilder();
            builder.append("---- Hello Minecraft! Crash Report ----\n");
            builder.append("  Version: " + Main.VERSION + "\n");
            builder.append("  Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
            builder.append("  Thread: ").append(t.toString()).append("\n");
            builder.append("\n  Content: \n    ");
            builder.append(s).append("\n\n");
            builder.append("-- System Details --\n");
            builder.append("  Operating System: ").append(OperatingSystem.SYSTEM_VERSION).append("\n");
            builder.append("  Java Version: ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("java.vendor")).append("\n");
            builder.append("  Java VM Version: ").append(System.getProperty("java.vm.name")).append(" (").append(System.getProperty("java.vm.info")).append("), ").append(System.getProperty("java.vm.vendor")).append("\n");
            String text = builder.toString();

            Logging.LOG.log(Level.SEVERE, text);

            if (checkThrowable(e) && !System.getProperty("java.vm.name").contains("OpenJDK")) {
                Platform.runLater(() -> new CrashWindow(text).show());
                if (!Main.UPDATE_CHECKER.isOutOfDate())
                    reportToServer(text, s);
            }
        } catch (Throwable ex) {
            Logging.LOG.log(Level.SEVERE, "Unable to caught exception", ex);
            Logging.LOG.log(Level.SEVERE, "There is the original exception", e);
        }
    }

    private static final HashSet<String> THROWABLE_SET = new HashSet<>();

    private void reportToServer(final String text, String stacktrace) {
        if (THROWABLE_SET.contains(stacktrace) || stacktrace.contains("Font") || stacktrace.contains("InternalError"))
            return;
        THROWABLE_SET.add(stacktrace);
        Thread t = new Thread(() -> {
            HashMap<String, String> map = new HashMap<>();
            map.put("crash_report", text);
            map.put("version", Main.VERSION);
            try {
                NetworkUtils.doPost(NetworkUtils.toURL("http://huangyuhui.duapp.com/hmcl/crash.php"), map);
            } catch (IOException ex) {
                Logging.LOG.log(Level.SEVERE, "Unable to post HMCL server.", ex);
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
