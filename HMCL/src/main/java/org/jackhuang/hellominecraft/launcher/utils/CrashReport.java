/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils;

import java.text.DateFormat;
import java.util.Date;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.launcher.launch.MinecraftCrashAdvicer;
import org.jackhuang.hellominecraft.utils.UpdateChecker;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.views.LogWindow;

/**
 *
 * @author hyh
 */
public class CrashReport implements Thread.UncaughtExceptionHandler {

    boolean enableLogger = false;

    public CrashReport(boolean enableLogger) {
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
            text += MinecraftCrashAdvicer.getAdvice(e, true);
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
