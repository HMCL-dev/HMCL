/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.IOException;
import java.util.List;
import javax.swing.JOptionPane;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.utils.Event;
import org.jackhuang.hellominecraft.utils.MessageBox;

/**
 *
 * @author huangyuhui
 */
public class LaunchScriptFinisher implements Event<List<String>> {

    @Override
    public boolean call(Object sender, List str) {
        boolean flag = false;
        try {
            String s = JOptionPane.showInputDialog(C.i18n("mainwindow.enter_script_name"));
            if (s != null)
                MessageBox.Show(C.i18n("mainwindow.make_launch_succeed") + " " + ((GameLauncher) sender).makeLauncher(s, str).getAbsolutePath());
            flag = true;
        } catch (IOException ex) {
            MessageBox.Show(C.i18n("mainwindow.make_launch_script_failed"));
            HMCLog.err("Failed to create script file.", ex);
        }
        MainFrame.INSTANCE.closeMessage();
        return flag;
    }

}
