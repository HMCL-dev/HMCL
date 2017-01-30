/*
 * Hello Minecraft! Server Manager.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.svrmgr;

import java.awt.Font;
import java.text.ParseException;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.ui.LogWindow;
import org.jackhuang.hellominecraft.svrmgr.setting.SettingsManager;
import org.jackhuang.hellominecraft.util.UpdateChecker;
import org.jackhuang.hellominecraft.svrmgr.ui.MainWindow;
import org.jackhuang.hellominecraft.util.VersionNumber;
import org.jackhuang.hellominecraft.lookandfeel.HelloMinecraftLookAndFeel;
import org.jackhuang.hellominecraft.util.MessageBox;

/**
 *
 * @author huangyuhui
 */
public class Main {

    public static String launcherName = "Hello Minecraft! Server Manager";
    public static final String PUBLISH_URL = "http://www.mcbbs.net/thread-171239-1-1.html";
    public static final byte VERSION_FIRST = 0, VERSION_SECOND = 8, VERSION_THIRD = 7;
    public static final UpdateChecker UPDATE_CHECKER = new UpdateChecker(new VersionNumber(VERSION_FIRST, VERSION_SECOND, VERSION_THIRD), "hmcsm");

    public static String makeTitle() {
        return launcherName + ' ' + VERSION_FIRST + '.' + VERSION_SECOND + '.' + VERSION_THIRD;
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
            UPDATE_CHECKER.process(false).reg(t -> MessageBox.show("发现更新！" + t.version)).execute();
            new MainWindow().setVisible(true);
        } catch (Throwable t) {
            HMCLog.err("There's something wrong when running server holder.", t);
            LogWindow.INSTANCE.showAsCrashWindow(false);

            System.exit(-1);
        }
    }
}
