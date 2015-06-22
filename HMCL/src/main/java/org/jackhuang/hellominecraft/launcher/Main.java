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
package org.jackhuang.hellominecraft.launcher;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.functions.DoneListener0;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher;
import org.jackhuang.hellominecraft.launcher.utils.CrashReport;
import org.jackhuang.hellominecraft.logging.Configuration;
import org.jackhuang.hellominecraft.logging.appender.ConsoleAppender;
import org.jackhuang.hellominecraft.logging.layout.DefaultLayout;
import org.jackhuang.hellominecraft.views.LogWindow;
import org.jackhuang.hellominecraft.launcher.utils.settings.Settings;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.lookandfeel.HelloMinecraftLookAndFeel;
import org.jackhuang.hellominecraft.utils.MessageBox;

/**
 *
 * @author hyh
 */
public final class Main implements DoneListener0 {

    public static String launcherName = "Hello Minecraft! Launcher";
    public static byte firstVer = 2, secondVer = 3, thirdVer = 2;
    public static int minimumLauncherVersion = 16;

    /**
     * Make the version of HMCL.
     *
     * @return the version: firstVer.secondVer.thirdVer
     */
    public static String makeVersion() {
        return "" + firstVer + '.' + secondVer + '.' + thirdVer;
    }

    /**
     * Make the main window title.
     *
     * @return the MainWindow title.
     */
    public static String makeTitle() {
        return launcherName + ' ' + makeVersion();
    }

    public static final Main instance = new Main();

    public static void main(String[] args) {
        {
            Thread.setDefaultUncaughtExceptionHandler(new CrashReport(true));

            try {
                File file = new File("hmcl.log");
                if (!file.exists())
                    file.createNewFile();
                Configuration.DEFAULT.appenders.add(new ConsoleAppender("File", new DefaultLayout(), true, new FileOutputStream(file), true));
            } catch (IOException ex) {
                System.err.println("Failed to add log appender File because an error occurred while creating or opening hmcl.log");
                ex.printStackTrace();
            }

            HMCLog.log("*** " + Main.makeTitle() + " ***");

            LogWindow.instance.clean();
            LogWindow.instance.setTerminateGame(GameLauncher.PROCESS_MANAGER::stopAllProcesses);

            try {
                UIManager.setLookAndFeel(new HelloMinecraftLookAndFeel());
            } catch (Throwable ex) {
                HMCLog.warn("Failed to set look and feel...", ex);
            }

            Settings.UPDATE_CHECKER.start();

            MainFrame.showMainFrame(Settings.isFirstLoad());
        }
    }

    @Override
    public void onDone() {
        GameLauncher.PROCESS_MANAGER.stopAllProcesses();
    }

    public static void update() {
        if (MessageBox.Show(C.i18n("update.newest_version") + Settings.UPDATE_CHECKER.getNewVersion().firstVer + "." + Settings.UPDATE_CHECKER.getNewVersion().secondVer + "." + Settings.UPDATE_CHECKER.getNewVersion().thirdVer + "\n"
                + C.i18n("update.should_open_link"),
                MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
            try {
                java.awt.Desktop.getDesktop().browse(new URI(C.URL_PUBLISH));
            } catch (Throwable e) {
                HMCLog.warn("Failed to browse uri: " + C.URL_PUBLISH, e);

                Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                cb.setContents(new StringSelection(C.URL_PUBLISH), null);
                MessageBox.Show(C.i18n("update.no_browser"));
            }
        else
            Settings.s().setCheckUpdate(false);
    }

    public static void invokeUpdate() {
        if (Settings.s().isCheckUpdate()) update();
        MainFrame.instance.invokeUpdate();
    }

    public static ImageIcon getIcon(String path) {
        try {
            return new ImageIcon(Main.class.getResource("/org/jackhuang/hellominecraft/launcher/" + path));
        } catch (Exception e) {
            HMCLog.err("Failed to load icon", e);
            return null;
        }
    }
}
