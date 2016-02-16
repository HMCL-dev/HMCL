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
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.launcher.util.MinecraftCrashAdvicer;
import org.jackhuang.hellominecraft.util.DoubleOutputStream;
import org.jackhuang.hellominecraft.util.LauncherPrintStream;

/**
 *
 * @author huangyuhui
 */
public final class Launcher {

    static final Logger LOGGER = Logger.getLogger(Launcher.class.getName());

    static String classPath = "";

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
            else if (s.equals("-debug"))
                showInfo = true;
            else
                cmdList.add(s);

        String[] tokenized = StrUtils.tokenize(classPath, File.pathSeparator);
        int len = tokenized.length;

        if (showInfo) {
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
        }

        URL[] urls = new URL[len];

        try {
            for (int j = 0; j < len; j++)
                urls[j] = new File(tokenized[j]).toURI().toURL();
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to get classpath.", e);
            return;
        }

        Method minecraftMain;
        URLClassLoader ucl = new URLClassLoader(urls, URLClassLoader.getSystemClassLoader().getParent());
        Thread.currentThread().setContextClassLoader(ucl);
        try {
            minecraftMain = ucl.loadClass(mainClass).getMethod("main", String[].class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException t) {
            LOGGER.log(Level.SEVERE, "Minecraft main class not found.", t);
            return;
        }

        LOGGER.info("*** Launching Game ***");

        try {
            minecraftMain.invoke(null, new Object[] { (String[]) cmdList.toArray(new String[cmdList.size()]) });
        } catch (Throwable throwable) {
            String trace = StrUtils.getStackTrace(throwable);
            System.err.println(C.i18n("crash.minecraft"));
            System.err.println(MinecraftCrashAdvicer.getAdvice(trace));
            System.err.println(trace);
        }

        LOGGER.info("*** Game exited ***");
    }
}
