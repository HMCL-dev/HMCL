/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.functions.TrueDoneListener;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.views.LogWindow;
import org.jackhuang.hellominecraft.launcher.launch.MinecraftCrashAdvicer;
import org.jackhuang.hellominecraft.utils.DoubleOutputStream;
import org.jackhuang.hellominecraft.utils.JdkVersion;
import org.jackhuang.hellominecraft.utils.LauncherPrintStream;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.OS;
import org.jackhuang.hellominecraft.utils.Utils;

/**
 *
 * @author hyh
 */
public final class Launcher {

    private static final Launcher instance = new Launcher();

    public static void println(String s) {
        System.out.println(s);
    }

    public static void main(String[] args) {
        println("*** " + Main.makeTitle() + " ***");

        Thread.currentThread().setName("launcher");
        try {
            File logFile = new File("hmclmc.log");
            if (!logFile.exists()) logFile.createNewFile();
            FileOutputStream tc = new FileOutputStream(logFile);
            DoubleOutputStream out = new DoubleOutputStream(tc, System.out);
            System.setOut(new LauncherPrintStream(out));
            DoubleOutputStream err = new DoubleOutputStream(tc, System.err);
            System.setErr(new LauncherPrintStream(err));
        } catch (Exception e) {
            println("Failed to add log file appender.");
            e.printStackTrace();
        }

        LogWindow.instance.setTerminateGame(Utils::shutdownForcely);

        boolean showInfo = false;
        String classPath = "";
        String mainClass = "net.minecraft.client.Minecraft";

        ArrayList<String> cmdList = new ArrayList<>();

        for (String s : args)
            if (s.startsWith("-cp=")) classPath = classPath.concat(s.substring("-cp=".length()));
            else if (s.startsWith("-mainClass=")) mainClass = s.substring("-mainClass=".length());
            else if (s.equals("-debug")) showInfo = true;
            else cmdList.add(s);

        String[] cmds = (String[]) cmdList.toArray(new String[cmdList.size()]);

        String[] tokenized = StrUtils.tokenize(classPath, File.pathSeparator);
        int len = tokenized.length;

        if (showInfo) {
            println("Arguments: {\n" + StrUtils.parseParams("    ", args, "\n") + "\n}");
            println("Main Class: " + mainClass);
            println("Class Path: {\n" + StrUtils.parseParams("    ", tokenized, "\n") + "\n}");
            SwingUtilities.invokeLater(() -> LogWindow.instance.setVisible(true));
        }

        URL[] urls = new URL[len];

        try {
            for (int j = 0; j < len; j++)
                urls[j] = new File(tokenized[j]).toURI().toURL();
        } catch (Throwable e) {
            MessageBox.Show(C.i18n("crash.main_class_not_found"));
            println("Failed to get classpath.");
            e.printStackTrace();
            return;
        }

        if (!JdkVersion.isJava64Bit() && OS.is64Bit())
            MessageBox.Show(C.i18n("advice.os64butjdk32"));

        Method minecraftMain;
        try {
            minecraftMain = new URLClassLoader(urls).loadClass(mainClass).getMethod("main", String[].class);
        } catch (Throwable t) {
            MessageBox.Show(C.i18n("crash.main_class_not_found"));
            println("Minecraft main class not found.");
            return;
        }

        println("*** Launching Game ***");

        try {
            minecraftMain.invoke(null, new Object[]{cmds});
        } catch (Throwable throwable) {
            HMCLog.err("Cought exception!");
            final StringWriter trace = new StringWriter();
            PrintWriter writer = new PrintWriter(trace);
            throwable.printStackTrace(writer);
            final String advice = MinecraftCrashAdvicer.getAdvice(throwable);
            MessageBox.Show(C.i18n("crash.minecraft") + ": " + advice);

            LogWindow.instance.log(C.i18n("crash.minecraft"));
            LogWindow.instance.log(advice);
            LogWindow.instance.log(trace.toString());
            LogWindow.instance.setExit(TrueDoneListener.instance);
            LogWindow.instance.setVisible(true);
        }

        println("*** Game Exited ***");
    }
}
