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
 * @author hyh
 */
public abstract class IAuthenticator {

    public static final YggdrasilAuthenticator yggdrasilLogin;
    public static final OfflineAuthenticator offlineLogin;
    public static final SkinmeAuthenticator skinmeLogin;
    //public static final BestLogin bestLogin;

    public static final List<IAuthenticator> logins;

    static {
	String clientToken = Settings.getInstance().getClientToken();
	logins = new ArrayList<>();
	logins.add(offlineLogin = new OfflineAuthenticator(clientToken));
	logins.add(yggdrasilLogin = new YggdrasilAuthenticator(clientToken));
	logins.add(skinmeLogin = new SkinmeAuthenticator(clientToken));
        //logins.add(bestLogin = new BestLogin(clientToken));
	yggdrasilLogin.onLoadSettings(Settings.getInstance().getYggdrasilConfig());

	Runtime.getRuntime().addShutdownHook(new Thread(() -> 
            Settings.getInstance().setYggdrasilConfig(yggdrasilLogin.onSaveSettings())
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
