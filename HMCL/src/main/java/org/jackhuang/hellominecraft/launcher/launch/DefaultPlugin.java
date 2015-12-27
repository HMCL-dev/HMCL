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
package org.jackhuang.hellominecraft.launcher.launch;

import org.jackhuang.hellominecraft.launcher.api.IPlugin;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.utils.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.OfflineAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.SkinmeAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.utils.auth.YggdrasilAuthenticator;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersionManager;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.utils.functions.Consumer;

/**
 *
 * @author huangyuhui
 */
public class DefaultPlugin implements IPlugin {

    protected static YggdrasilAuthenticator YGGDRASIL_LOGIN;
    protected static OfflineAuthenticator OFFLINE_LOGIN;
    protected static SkinmeAuthenticator SKINME_LOGIN;

    @Override
    public IMinecraftProvider provideMinecraftProvider(Profile profile) {
        return new MinecraftVersionManager(profile);
    }

    @Override
    public void onRegisterAuthenticators(Consumer<IAuthenticator> apply) {
        String clientToken = Settings.getInstance().getClientToken();
        OFFLINE_LOGIN = new OfflineAuthenticator(clientToken);
        YGGDRASIL_LOGIN = new YggdrasilAuthenticator(clientToken);
        YGGDRASIL_LOGIN.onLoadSettings(Settings.getInstance().getYggdrasilConfig());
        SKINME_LOGIN = new SkinmeAuthenticator(clientToken);

        Runtime.getRuntime().addShutdownHook(new Thread(()
            -> Settings.getInstance().setYggdrasilConfig(YGGDRASIL_LOGIN.onSaveSettings())
        ));
        apply.accept(OFFLINE_LOGIN);
        apply.accept(YGGDRASIL_LOGIN);
        apply.accept(SKINME_LOGIN);
    }

    @Override
    public void showUI() {
        MainFrame.showMainFrame(Settings.isFirstLoading());
    }

    @Override
    public void onProcessingLoginResult(UserProfileProvider result) {
    }

    @Override
    public void onInitializingProfile(Profile p) {

    }

}
