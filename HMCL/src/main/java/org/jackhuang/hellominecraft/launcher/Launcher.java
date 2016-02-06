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
package org.jackhuang.hellominecraft.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.ui.LogWindow;
import org.jackhuang.hellominecraft.launcher.util.MinecraftCrashAdvicer;
import org.jackhuang.hellominecraft.util.DoubleOutputStream;
import org.jackhuang.hellominecraft.util.LauncherPrintStream;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.Utils;

/**
 *
 * @author huangyuhui
 */
public final class Launcher {

    static final Logger LOGGER = Logger.getLogger(Launcher.class.getName());

    static String classPath = "";
    //state String proxyHost = "", proxyPort = "", proxyUsername = "", proxyPassword = "";

    public static void main(String[] args) {
        LOGGER.log(Level.INFO, "*** {0} ***", Main.makeTitle());

        boolean showInfo = false;
        String mainClass = "net.minecraft.client.Minecraft";

        ArrayList<String> cmdList = new ArrayList<>();

        for (String s : args)
            if (s.startsWith("-cp="))
                classPath = classPath.concat(s.substring("-cp=".length()));
            else if (s.startsWith("-mainClass="))
                mainClass = s.substring("-mainClass=".length());
            /*else if (s.startsWith("-proxyHost="))
                proxyHost = s.substring("-proxyHost=".length());
            else if (s.startsWith("-proxyPort="))
                proxyPort = s.substring("-proxyPort=".length());
            else if (s.startsWith("-proxyUsername="))
                proxyUsername = s.substring("-proxyUsername=".length());
            else if (s.startsWith("-proxyPassword="))
                proxyPassword = s.substring("-proxyPassword=".length());*/
            else if (s.equals("-debug"))
                showInfo = true;
            else
                cmdList.add(s);

        String[] tokenized = StrUtils.tokenize(classPath, File.pathSeparator);
        int len = tokenized.length;

        if (showInfo) {
            LogWindow.INSTANCE.setTerminateGame(() -> {
                try {
                    Utils.shutdownForcely(1);
                } catch (Exception e) {
                    MessageBox.Show(C.i18n("launcher.exit_failed"));
                    HMCLog.err("Failed to shutdown forcely", e);
                }
            });
            try {
                File logFile = new File("hmclmc.log");
                if (!logFile.exists() && !logFile.createNewFile())
                    LOGGER.info("Failed to create log file");
                else {
                    FileOutputStream tc = new FileOutputStream(logFile);
                    DoubleOutputStream out = new DoubleOutputStream(tc, System.out);
                    System.setOut(new LauncherPrintStream(out));
                    DoubleOutputStream err = new DoubleOutputStream(tc, System.err);
                    System.setErr(new LauncherPrintStream(err));
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to add log file appender.", e);
            }

            LOGGER.log(Level.INFO, "Arguments: '{'\n{0}\n'}'", StrUtils.parseParams("    ", args, "\n"));
            LOGGER.log(Level.INFO, "Main Class: {0}", mainClass);
            LOGGER.log(Level.INFO, "Class Path: '{'\n{0}\n'}'", StrUtils.parseParams("    ", tokenized, "\n"));
            SwingUtilities.invokeLater(() -> LogWindow.INSTANCE.setVisible(true));
        }
        /*
        if (StrUtils.isNotBlank(proxyHost) && StrUtils.isNotBlank(proxyPort) && MathUtils.canParseInt(proxyPort)) {
            HMCLog.log("Initializing customized proxy");
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", proxyPort);
            if (StrUtils.isNotBlank(proxyUsername) && StrUtils.isNotBlank(proxyPassword))
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(proxyUsername, proxyPassword.toCharArray());
                    }
                });
            //PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Settings.getInstance().getProxyHost(), Integer.parseInt(Settings.getInstance().getProxyPort())));
        } else {
            //PROXY = Proxy.NO_PROXY;
        }*/

        URL[] urls = new URL[len];

        try {
            for (int j = 0; j < len; j++)
                urls[j] = new File(tokenized[j]).toURI().toURL();
        } catch (Throwable e) {
            MessageBox.Show(C.i18n("crash.main_class_not_found"));
            LOGGER.log(Level.SEVERE, "Failed to get classpath.", e);
            return;
        }

        Method minecraftMain;
        URLClassLoader ucl = new URLClassLoader(urls, URLClassLoader.getSystemClassLoader().getParent());
        Thread.currentThread().setContextClassLoader(ucl);
        try {
            minecraftMain = ucl.loadClass(mainClass).getMethod("main", String[].class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException t) {
            MessageBox.Show(C.i18n("crash.main_class_not_found"));
            LOGGER.log(Level.SEVERE, "Minecraft main class not found.", t);
            return;
        }

        LOGGER.info("*** Launching Game ***");

        int flag = 0;
        try {
            minecraftMain.invoke(null, new Object[] { (String[]) cmdList.toArray(new String[cmdList.size()]) });
        } catch (Throwable throwable) {
            String trace = StrUtils.getStackTrace(throwable);
            final String advice = MinecraftCrashAdvicer.getAdvice(trace);
            MessageBox.Show(C.i18n("crash.minecraft") + ": " + advice);

            System.err.println(C.i18n("crash.minecraft"));
            System.err.println(advice);
            System.err.println(trace);
            LogWindow.INSTANCE.setExit(() -> true);
            LogWindow.INSTANCE.setVisible(true);
            flag = 1;
        }

        LOGGER.info("*** Game Exited ***");
        try {
            Utils.shutdownForcely(flag);
        } catch (Exception e) {
            MessageBox.Show(C.i18n("launcher.exit_failed"));
            HMCLog.err("Failed to shutdown forcely", e);
        }
    }
    /*
     * static Object getShutdownHaltLock() {
     * try {
     * Class z = Class.forName("java.lang.Shutdown");
     * Field haltLock = z.getDeclaredField("haltLock");
     * haltLock.setAccessible(true);
     * return haltLock.get(null);
     * } catch (Throwable ex) {
     * ex.printStackTrace();
     * return new Object();
     * }
     * }
     */
}
