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
package org.jackhuang.hellominecraft.svrmgr;

import java.awt.Font;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.views.LogWindow;
import org.jackhuang.hellominecraft.svrmgr.settings.SettingsManager;
import org.jackhuang.hellominecraft.utils.UpdateChecker;
import org.jackhuang.hellominecraft.svrmgr.views.MainWindow;
import org.jackhuang.hellominecraft.utils.VersionNumber;
import org.jackhuang.hellominecraft.lookandfeel.HelloMinecraftLookAndFeel;

/**
 *
 * @author huangyuhui
 */
public class Main {

    public static String launcherName = "Hello Minecraft! Server Manager";
    public static final String PUBLISH_URL = "http://www.mcbbs.net/thread-171239-1-1.html";
    public static byte firstVer = 0, secondVer = 8, thirdVer = 6;

    public static String makeTitle() {
        return launcherName + ' ' + firstVer + '.' + secondVer + '.' + thirdVer;
    }

    public static void main(String[] args) {
        try {
            SettingsManager.load();
            try {
                javax.swing.UIManager.setLookAndFeel(new HelloMinecraftLookAndFeel());
                UIManager.getLookAndFeelDefaults().put("defaultFont", new Font("微软雅黑", Font.PLAIN, 12));
            } catch (ParseException | UnsupportedLookAndFeelException ex) {
                HMCLog.warn("Failed to set look and feel", ex);
            }
            new UpdateChecker(new VersionNumber(firstVer, secondVer, thirdVer), "hmcsm", SettingsManager.settings.checkUpdate, () -> {
                SettingsManager.settings.checkUpdate = false;
            }).start();
            new MainWindow().setVisible(true);
        } catch (Throwable t) {
            HMCLog.err("There's something wrong when running server holder.", t);

            LogWindow.instance.clean();
            LogWindow.instance.log("开服器崩溃了QAQ");
            StringWriter trace = new StringWriter();
            t.printStackTrace(new PrintWriter(trace));
            LogWindow.instance.log(trace.toString());
            LogWindow.instance.setVisible(true);

            System.exit(-1);
        }
    }
}
