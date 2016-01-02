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
package org.jackhuang.hellominecraft.launcher.utils.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.launcher.api.PluginManager;

/**
 * Login interface
 *
 * @author huangyuhui
 */
public abstract class IAuthenticator {

    public static final List<IAuthenticator> LOGINS = new ArrayList<>();

    static {
        PluginManager.NOW_PLUGIN.onRegisterAuthenticators(LOGINS::add);
    }

    protected String clientToken, username;

    public IAuthenticator(String clientToken) {
        this.clientToken = clientToken;
    }

    /**
     * Login Method
     *
     * @param info username & password
     *
     * @return login result
     *
     * @throws
     * org.jackhuang.hellominecraft.launcher.utils.auth.AuthenticationException
     */
    public abstract UserProfileProvider login(LoginInfo info) throws AuthenticationException;

    public abstract String id();

    /**
     *
     * @return the name of login method.
     */
    public abstract String getName();

    /**
     * Has password?
     *
     * @return has password?
     */
    public boolean hasPassword() {
        return true;
    }

    public boolean isLoggedIn() {
        return false;
    }

    public void setRememberMe(boolean is) {

    }

    public abstract UserProfileProvider loginBySettings() throws AuthenticationException;

    public abstract void logout();

    public Map onSaveSettings() {
        HashMap m = new HashMap();
        m.put("IAuthenticator_UserName", username);
        return m;
    }

    public void onLoadSettings(Map m) {
        if (m == null)
            return;
        username = (String) m.get("IAuthenticator_UserName");
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String s) {
        username = s;
    }
}
