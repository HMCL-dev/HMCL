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

import java.util.ArrayList;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.api.IPlugin;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.OfflineAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.core.auth.YggdrasilAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.launch.LaunchOptions;
import org.jackhuang.hellominecraft.launcher.setting.Profile;
import org.jackhuang.hellominecraft.launcher.setting.Settings;
import org.jackhuang.hellominecraft.launcher.ui.MainFrame;
import org.jackhuang.hellominecraft.util.EventHandler;
import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 *
 * @author huangyuhui
 */
public class DefaultPlugin implements IPlugin {

    ArrayList<IAuthenticator> auths = new ArrayList<>();

    @Override
    public IMinecraftService provideMinecraftService(Object profile) {
        return new HMCLMinecraftService((Profile) profile);
    }

    @Override
    public void onRegisterAuthenticators(Consumer<IAuthenticator> apply) {
        String clientToken = Settings.getInstance().getClientToken();
        auths.add(new OfflineAuthenticator(clientToken));
        auths.add(new YggdrasilAuthenticator(clientToken));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (IAuthenticator i : auths)
                Settings.getInstance().setAuthenticatorConfig(i.id(), i.onSaveSettings());
        }));
        for (IAuthenticator i : auths) {
            i.onLoadSettings(Settings.getInstance().getAuthenticatorConfig(i.id()));
            apply.accept(i);
        }
    }

    @Override
    public void showUI() {
        MainFrame.showMainFrame();
    }

    @Override
    public void onProcessingLoginResult(UserProfileProvider result) {
    }

    public transient final EventHandler<LaunchOptions> onProcessingLaunchOptionsEvent = new EventHandler<>(this);

    @Override
    public void onProcessingLaunchOptions(LaunchOptions p) {
        onProcessingLaunchOptionsEvent.execute(p);
    }

}
