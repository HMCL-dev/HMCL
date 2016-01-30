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
package org.jackhuang.hellominecraft.launcher.setting;

import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.api.IPlugin;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.OfflineAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.SkinmeAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.core.auth.YggdrasilAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.launch.LaunchOptions;
import org.jackhuang.hellominecraft.launcher.ui.MainFrame;
import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 *
 * @author huangyuhui
 */
public class DefaultPlugin implements IPlugin {

    protected static YggdrasilAuthenticator YGGDRASIL_LOGIN;
    protected static OfflineAuthenticator OFFLINE_LOGIN;
    protected static SkinmeAuthenticator SKINME_LOGIN;

    @Override
    public IMinecraftService provideMinecraftService(Profile profile) {
        return new DefaultMinecraftService(profile);
    }

    @Override
    public void onRegisterAuthenticators(Consumer<IAuthenticator> apply) {
        String clientToken = Settings.getInstance().getClientToken();
        OFFLINE_LOGIN = new OfflineAuthenticator(clientToken);
        OFFLINE_LOGIN.onLoadSettings(Settings.getInstance().getAuthenticatorConfig(OFFLINE_LOGIN.id()));
        YGGDRASIL_LOGIN = new YggdrasilAuthenticator(clientToken);
        YGGDRASIL_LOGIN.onLoadSettings(Settings.getInstance().getAuthenticatorConfig(YGGDRASIL_LOGIN.id()));
        SKINME_LOGIN = new SkinmeAuthenticator(clientToken);
        SKINME_LOGIN.onLoadSettings(Settings.getInstance().getAuthenticatorConfig(SKINME_LOGIN.id()));

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Settings.getInstance().setAuthenticatorConfig(OFFLINE_LOGIN.id(), OFFLINE_LOGIN.onSaveSettings());
                Settings.getInstance().setAuthenticatorConfig(YGGDRASIL_LOGIN.id(), YGGDRASIL_LOGIN.onSaveSettings());
                Settings.getInstance().setAuthenticatorConfig(SKINME_LOGIN.id(), SKINME_LOGIN.onSaveSettings());
            }
        });
        apply.accept(OFFLINE_LOGIN);
        apply.accept(YGGDRASIL_LOGIN);
        apply.accept(SKINME_LOGIN);
    }

    @Override
    public void showUI() {
        MainFrame.showMainFrame();
    }

    @Override
    public void onProcessingLoginResult(UserProfileProvider result) {
    }

    @Override
    public void onProcessingLaunchOptions(LaunchOptions p) {
    }

}
