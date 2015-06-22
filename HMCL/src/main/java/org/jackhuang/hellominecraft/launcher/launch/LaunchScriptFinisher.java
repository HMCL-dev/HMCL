/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
            if(s != null) MessageBox.Show(C.i18n("mainwindow.make_launch_succeed") + " " + ((GameLauncher)sender).makeLauncher(s, str).getAbsolutePath());
            flag = true;
        } catch (IOException ex) {
            MessageBox.Show(C.i18n("mainwindow.make_launch_script_failed"));
            HMCLog.err("Failed to create script file.", ex);
        }
        MainFrame.instance.closeMessage();
        return flag;
    }

}
