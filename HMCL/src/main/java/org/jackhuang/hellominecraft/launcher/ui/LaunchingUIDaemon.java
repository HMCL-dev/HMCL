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
package org.jackhuang.hellominecraft.launcher.ui;

import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.JOptionPane;
import org.jackhuang.hellominecraft.launcher.util.LauncherVisibility;
import org.jackhuang.hellominecraft.launcher.core.launch.GameLauncher;
import org.jackhuang.hellominecraft.launcher.core.launch.LaunchingState;
import org.jackhuang.hellominecraft.launcher.setting.Profile;
import org.jackhuang.hellominecraft.launcher.setting.Settings;
import org.jackhuang.hellominecraft.launcher.util.MinecraftCrashAdvicer;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.Event;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.func.Consumer;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.FileUtils;
import org.jackhuang.hellominecraft.util.sys.JavaProcessMonitor;
import org.jackhuang.hellominecraft.util.ui.LogWindow;
import org.jackhuang.hellominecraft.util.net.WebFrame;

/**
 *
 * @author huangyuhui
 */
public class LaunchingUIDaemon {

    Runnable customizedSuccessEvent = null;

    void runGame(Profile profile) {
        MainFrame.INSTANCE.showMessage(C.i18n("ui.message.launching"));
        profile.launcher().genLaunchCode(value -> {
            value.launchingStateChangedEvent.register(LAUNCHING_STATE_CHANGED);
            value.successEvent.register(LAUNCH_FINISHER);
            value.successEvent.register(customizedSuccessEvent);
        }, MainFrame.INSTANCE::failed, Settings.getInstance().getAuthenticator().getPassword());
    }

    void makeLaunchScript(Profile profile) {
        MainFrame.INSTANCE.showMessage(C.i18n("ui.message.launching"));
        profile.launcher().genLaunchCode(value -> {
            value.launchingStateChangedEvent.register(LAUNCHING_STATE_CHANGED);
            value.successEvent.register(LAUNCH_SCRIPT_FINISHER);
            value.successEvent.register(customizedSuccessEvent);
        }, MainFrame.INSTANCE::failed, Settings.getInstance().getAuthenticator().getPassword());
    }

    private static final Consumer<LaunchingState> LAUNCHING_STATE_CHANGED = t -> {
        String message = null;
        switch (t) {
        case LoggingIn:
            message = "launch.state.logging_in";
            break;
        case GeneratingLaunchingCodes:
            message = "launch.state.generating_launching_codes";
            break;
        case DownloadingLibraries:
            message = "launch.state.downloading_libraries";
            break;
        case DecompressingNatives:
            message = "launch.state.decompressing_natives";
            break;
        }
        MainFrame.INSTANCE.showMessage(C.i18n(message));
    };

    private static final Event<List<String>> LAUNCH_FINISHER = (sender, str) -> {
        final GameLauncher obj = (GameLauncher) sender;
        obj.launchEvent.register(p -> {
            if ((LauncherVisibility) obj.getTag() == LauncherVisibility.CLOSE && !LogWindow.INSTANCE.isVisible()) {
                HMCLog.log("Without the option of keeping the launcher visible, this application will exit and will NOT catch game logs, but you can turn on \"Debug Mode\".");
                System.exit(0);
            } else if ((LauncherVisibility) obj.getTag() == LauncherVisibility.KEEP)
                MainFrame.INSTANCE.closeMessage();
            else {
                if (LogWindow.INSTANCE.isVisible())
                    LogWindow.INSTANCE.setExit(() -> true);
                MainFrame.INSTANCE.dispose();
            }
            JavaProcessMonitor jpm = new JavaProcessMonitor(p);
            jpm.applicationExitedAbnormallyEvent.register(t -> {
                HMCLog.err("The game exited abnormally, exit code: " + t);
                String[] logs = jpm.getJavaProcess().getStdOutLines().toArray(new String[0]);
                String errorText = null;
                for (String s : logs) {
                    int pos = s.lastIndexOf("#@!@#");
                    if (pos >= 0 && pos < s.length() - "#@!@#".length() - 1) {
                        errorText = s.substring(pos + "#@!@#".length()).trim();
                        break;
                    }
                }
                String msg = C.i18n("launch.exited_abnormally") + " exit code: " + t;
                if (errorText != null)
                    msg += ", advice: " + MinecraftCrashAdvicer.getAdvice(FileUtils.readQuietly(new File(errorText)));
                WebFrame f = new WebFrame(logs);
                f.setModal(true);
                f.setTitle(msg);
                f.setVisible(true);
                checkExit((LauncherVisibility) obj.getTag());
            });
            jpm.jvmLaunchFailedEvent.register(t -> {
                HMCLog.err("Cannot create jvm, exit code: " + t);
                WebFrame f = new WebFrame(jpm.getJavaProcess().getStdOutLines().toArray(new String[0]));
                f.setModal(true);
                f.setTitle(C.i18n("launch.cannot_create_jvm") + " exit code: " + t);
                f.setVisible(true);
                checkExit((LauncherVisibility) obj.getTag());
            });
            jpm.stoppedEvent.register(() -> checkExit((LauncherVisibility) obj.getTag()));
            jpm.start();
        });
        try {
            obj.launch(str);
        } catch (IOException e) {
            MainFrame.INSTANCE.failed(C.i18n("launch.failed_creating_process") + "\n" + e.getMessage());
            HMCLog.err("Failed to launch when creating a new process.", e);
        }
        return true;
    };

    private static void checkExit(LauncherVisibility v) {
        if (v != LauncherVisibility.KEEP && !LogWindow.INSTANCE.isVisible()) {
            HMCLog.log("Launcher will exit now.");
            System.exit(0);
        }
    }

    private static final Event<List<String>> LAUNCH_SCRIPT_FINISHER = (sender, str) -> {
        boolean flag = false;
        try {
            String s = JOptionPane.showInputDialog(C.i18n("mainwindow.enter_script_name"));
            if (s != null)
                MessageBox.show(C.i18n("mainwindow.make_launch_succeed") + " " + ((GameLauncher) sender).makeLauncher(s, str).getAbsolutePath());
            flag = true;
        } catch (IOException ex) {
            MessageBox.show(C.i18n("mainwindow.make_launch_script_failed"));
            HMCLog.err("Failed to create script file.", ex);
        }
        MainFrame.INSTANCE.closeMessage();
        return flag;
    };
}
