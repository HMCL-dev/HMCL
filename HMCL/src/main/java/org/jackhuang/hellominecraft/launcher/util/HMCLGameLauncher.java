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
package org.jackhuang.hellominecraft.launcher.util;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.api.PluginManager;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.auth.AuthenticationException;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.core.launch.DefaultGameLauncher;
import org.jackhuang.hellominecraft.launcher.core.launch.GameLauncher;
import org.jackhuang.hellominecraft.launcher.core.launch.LaunchOptions;
import org.jackhuang.hellominecraft.launcher.setting.Profile;
import org.jackhuang.hellominecraft.launcher.setting.Settings;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.EventHandler;
import org.jackhuang.hellominecraft.util.func.Consumer;
import org.jackhuang.hellominecraft.util.log.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class HMCLGameLauncher {

    Profile profile;
    boolean isLaunching = false;

    public final EventHandler<Boolean> launchingStateChanged = new EventHandler<>(this);

    public HMCLGameLauncher(Profile p) {
        this.profile = p;
    }

    void setLaunching(boolean isLaunching) {
        if (isLaunching != this.isLaunching)
            launchingStateChanged.execute(isLaunching);
        this.isLaunching = isLaunching;
    }

    public void genLaunchCode(final Consumer<GameLauncher> listener, final Consumer<String> failed, String passwordIfNeeded) {
        if (isLaunching || profile == null)
            return;
        setLaunching(true);
        HMCLog.log("Start generating launching command...");
        File file = profile.getCanonicalGameDirFile();
        if (!file.exists()) {
            failed.accept(C.i18n("minecraft.wrong_path"));
            setLaunching(false);
            return;
        }

        if (profile.getSelectedVersion() == null) {
            failed.accept(C.i18n("minecraft.no_selected_version"));
            setLaunching(false);
            return;
        }

        final IAuthenticator l = IAuthenticator.LOGINS.get(Settings.getInstance().getLoginType());
        final LoginInfo li = new LoginInfo(l.getUserName(), l.isLoggedIn() || !l.hasPassword() ? null : passwordIfNeeded);
        Thread t = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Game Launcher");
                try {
                    LaunchOptions options = profile.getSelectedVersionSetting().createLaunchOptions(profile.getCanonicalGameDirFile());
                    PluginManager.plugin().onProcessingLaunchOptions(options);
                    DefaultGameLauncher gl = new DefaultGameLauncher(options, profile.service(), li, l);
                    gl.setTag(profile.getSelectedVersionSetting().getLauncherVisibility());
                    gl.successEvent.register(() -> setLaunching(false));
                    listener.accept(gl);
                    gl.makeLaunchCommand();
                } catch (GameException e) {
                    failed.accept(C.i18n("launch.failed") + ", " + e.getMessage());
                    setLaunching(false);
                } catch (AuthenticationException e) {
                    failed.accept(C.i18n("login.failed") + ", " + e.getMessage());
                    setLaunching(false);
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

}
