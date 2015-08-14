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
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.functions.NonConsumer;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher;
import org.jackhuang.hellominecraft.launcher.utils.CrashReporter;
import org.jackhuang.hellominecraft.logging.Configuration;
import org.jackhuang.hellominecraft.logging.appender.ConsoleAppender;
import org.jackhuang.hellominecraft.logging.layout.DefaultLayout;
import org.jackhuang.hellominecraft.views.LogWindow;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.lookandfeel.HelloMinecraftLookAndFeel;
import org.jackhuang.hellominecraft.utils.system.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.system.OS;

/**
 *
 * @author huangyuhui
 */
public final class Main implements NonConsumer {

    public static String launcherName = "Hello Minecraft! Launcher";
    public static byte firstVer = 2, secondVer = 3, thirdVer = 4, forthVer = 10;
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
            Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(true));

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
            } catch (ParseException | UnsupportedLookAndFeelException ex) {
                HMCLog.warn("Failed to set look and feel...", ex);
            }

            Settings.UPDATE_CHECKER.start();

            if (StrUtils.isNotBlank(Settings.getInstance().getProxyHost()) && StrUtils.isNotBlank(Settings.getInstance().getProxyPort())) {
                System.setProperty("http.proxyHost", Settings.getInstance().getProxyHost());
                System.setProperty("http.proxyPort", Settings.getInstance().getProxyPort());
                if (StrUtils.isNotBlank(Settings.getInstance().getProxyUserName()) && StrUtils.isNotBlank(Settings.getInstance().getProxyPassword()))
                    Authenticator.setDefault(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(Settings.getInstance().getProxyUserName(), Settings.getInstance().getProxyPassword().toCharArray());
                        }
                    });
            }
            
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
                MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
            Map<String, String> map = Settings.UPDATE_CHECKER.download_link;
            String url = C.URL_PUBLISH;
            if (map != null)
                if (map.containsKey(OS.os().checked_name))
                    url = map.get(OS.os().checked_name);
                else if (map.containsKey(OS.UNKOWN.checked_name))
                    url = map.get(OS.UNKOWN.checked_name);
            if (url == null) url = C.URL_PUBLISH;
            try {
                java.awt.Desktop.getDesktop().browse(new URI(url));
            } catch (URISyntaxException | IOException e) {
                HMCLog.warn("Failed to browse uri: " + url, e);

                Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                cb.setContents(new StringSelection(url), null);
                MessageBox.Show(C.i18n("update.no_browser"));
            }
        } else
            Settings.getInstance().setCheckUpdate(false);
    }

    public static void invokeUpdate() {
        if (Settings.getInstance().isCheckUpdate()) update();
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
