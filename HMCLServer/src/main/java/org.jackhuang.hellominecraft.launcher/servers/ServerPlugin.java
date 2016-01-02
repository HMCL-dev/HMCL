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
package org.jackhuang.hellominecraft.launcher.servers;

import org.jackhuang.hellominecraft.launcher.api.IPlugin;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.servers.mfcraft.CheckModsMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.servers.mfcraft.MFCraftAuthenticator;
import org.jackhuang.hellominecraft.launcher.servers.mfcraft.Servers;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.utils.auth.AuthenticationException;
import org.jackhuang.hellominecraft.launcher.utils.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.utils.auth.YggdrasilAuthenticator;
import org.jackhuang.hellominecraft.launcher.version.ServerInfo;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.launcher.views.ServerListView;
import org.jackhuang.hellominecraft.utils.functions.Consumer;
import org.jackhuang.hellominecraft.views.Selector;

/**
 *
 * @author huangyuhui
 */
public class ServerPlugin implements IPlugin {

    protected static YggdrasilAuthenticator YGGDRASIL_LOGIN;
    protected static MFCraftAuthenticator MFCRAFT_LOGIN;

    @Override
    public IMinecraftProvider provideMinecraftProvider(Profile profile) {
        return new CheckModsMinecraftProvider(profile);
    }

    @Override
    public void onRegisterAuthenticators(Consumer<IAuthenticator> apply) {
        String clientToken = Settings.getInstance().getClientToken();
        MFCRAFT_LOGIN = new MFCraftAuthenticator(clientToken);
        MFCRAFT_LOGIN.onLoadSettings(Settings.getInstance().getAuthenticatorConfig(MFCRAFT_LOGIN.id()));
        YGGDRASIL_LOGIN = new YggdrasilAuthenticator(clientToken);
        YGGDRASIL_LOGIN.onLoadSettings(Settings.getInstance().getAuthenticatorConfig(YGGDRASIL_LOGIN.id()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Settings.getInstance().setAuthenticatorConfig(MFCRAFT_LOGIN.id(), MFCRAFT_LOGIN.onSaveSettings());
            Settings.getInstance().setAuthenticatorConfig(YGGDRASIL_LOGIN.id(), YGGDRASIL_LOGIN.onSaveSettings());
        }));
        apply.accept(MFCRAFT_LOGIN);
        apply.accept(YGGDRASIL_LOGIN);
    }

    @Override
    public void showUI() {
        MainFrame.showMainFrame();
    }

    public static ServerInfo lastServerInfo;

    @Override
    public void onProcessingLoginResult(UserProfileProvider result) throws AuthenticationException {
        Servers s = Servers.getInstance();
        String[] sel = new String[s.areas.size()];
        for (int i = 0; i < sel.length; i++)
            sel[i] = s.areas.get(i).name;
        Selector selector = new Selector(null, sel, "选择你要登录的服务器大区");
        int ind = selector.getChoice();
        for (ServerInfo si : s.areas.get(ind).servers)
            si.downloadIcon();
        ServerListView slv = new ServerListView(s.areas.get(ind).servers.toArray(new ServerInfo[0]));
        int c = slv.getChoice();
        if (c == -1)
            throw new AuthenticationException("未选择服务器");
        lastServerInfo = s.areas.get(ind).servers.get(ind);
        result.setServer(lastServerInfo);
    }

    @Override
    public void onInitializingProfile(Profile p) {
        p.initialize(1);
    }

}
