/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.utils.auth;

import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.launcher.settings.Settings;

/**
 * Login interface
 *
 * @author huangyuhui
 */
public abstract class IAuthenticator {

    public static final YggdrasilAuthenticator YGGDRASIL_LOGIN;
    public static final OfflineAuthenticator OFFLINE_LOGIN;
    public static final SkinmeAuthenticator SKINME_LOGIN;

    public static final List<IAuthenticator> LOGINS;

    static {
        String clientToken = Settings.getInstance().getClientToken();
        LOGINS = new ArrayList<>();
        LOGINS.add(OFFLINE_LOGIN = new OfflineAuthenticator(clientToken));
        LOGINS.add(YGGDRASIL_LOGIN = new YggdrasilAuthenticator(clientToken));
        LOGINS.add(SKINME_LOGIN = new SkinmeAuthenticator(clientToken));
        YGGDRASIL_LOGIN.onLoadSettings(Settings.getInstance().getYggdrasilConfig());

        Runtime.getRuntime().addShutdownHook(new Thread(()
                -> Settings.getInstance().setYggdrasilConfig(YGGDRASIL_LOGIN.onSaveSettings())
        ));
    }

    protected String clientToken;

    public IAuthenticator(String clientToken) {
        this.clientToken = clientToken;
    }

    /**
     * Login Method
     *
     * @param info username & password
     * @return login result
     */
    public abstract UserProfileProvider login(LoginInfo info);

    /**
     *
     * @return the name of login method.
     */
    public abstract String getName();

    /**
     * Has password?
     *
     * @return Will I hide password box?
     */
    public boolean isHidePasswordBox() {
        return false;
    }

    public boolean isLoggedIn() {
        return false;
    }

    public void setRememberMe(boolean is) {

    }

    public abstract UserProfileProvider loginBySettings();

    public abstract void logout();
}
