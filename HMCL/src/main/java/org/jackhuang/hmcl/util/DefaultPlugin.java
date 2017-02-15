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
package org.jackhuang.hmcl.util;

import java.util.ArrayList;
import javax.swing.JFrame;
import org.jackhuang.hmcl.api.ui.AddTabCallback;
import org.jackhuang.hmcl.api.IPlugin;
import org.jackhuang.hmcl.core.auth.OfflineAuthenticator;
import org.jackhuang.hmcl.core.auth.YggdrasilAuthenticator;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.ui.GameSettingsPanel;
import org.jackhuang.hmcl.ui.LauncherSettingsPanel;
import org.jackhuang.hmcl.ui.MainPagePanel;
import org.jackhuang.hmcl.api.auth.IAuthenticator;
import org.jackhuang.hmcl.api.func.Consumer;
import org.jackhuang.hmcl.api.ui.Theme;
import org.jackhuang.hmcl.laf.LAFTheme;

/**
 *
 * @author huangyuhui
 */
public class DefaultPlugin implements IPlugin {

    public DefaultPlugin() {
        for (Theme t : LAFTheme.THEMES)
            Theme.THEMES.put(t.getId(), t);
    }

    ArrayList<IAuthenticator> auths = new ArrayList<>();

    @Override
    public void onRegisterAuthenticators(Consumer<IAuthenticator> apply) {
        String clientToken = Settings.getInstance().getClientToken();
        auths.add(new OfflineAuthenticator(clientToken));
        auths.add(new YggdrasilAuthenticator(clientToken));

        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (IAuthenticator i : auths)
                    Settings.getInstance().setAuthenticatorConfig(i.id(), i.onSaveSettings());
            }));
        } catch(IllegalStateException ignore) { // Shutdown in progress
        }
        for (IAuthenticator i : auths) {
            i.onLoadSettings(Settings.getInstance().getAuthenticatorConfig(i.id()));
            apply.accept(i);
        }
    }

    @Override
    public void onAddTab(JFrame frame, AddTabCallback callback) {
        callback.addTab(new MainPagePanel(), "main", C.i18n("launcher.title.main"));
        callback.addTab(new GameSettingsPanel(), "game", C.i18n("launcher.title.game"));
        callback.addTab(new LauncherSettingsPanel(), "launcher", C.i18n("launcher.title.launcher"));
    }

}
