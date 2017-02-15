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
package org.jackhuang.hmcl.core.auth;

import org.jackhuang.hmcl.api.auth.IAuthenticator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hmcl.api.PluginManager;

/**
 * Login interface
 *
 * @author huangyuhui
 */
public abstract class AbstractAuthenticator implements IAuthenticator {

    public static final List<IAuthenticator> LOGINS = new ArrayList<>();

    static {
        PluginManager.fireRegisterAuthenticators(LOGINS::add);
    }

    protected String clientToken, username, password;

    public AbstractAuthenticator(String clientToken) {
        this.clientToken = clientToken;
    }

    public String getClientToken() {
        return clientToken;
    }


    /**
     * Has password?
     *
     * @return has password?
     */
    @Override
    public boolean hasPassword() {
        return true;
    }

    @Override
    public boolean isLoggedIn() {
        return false;
    }

    @Override
    public void setRememberMe(boolean is) {

    }

    @Override
    public Map<?, ?> onSaveSettings() {
        HashMap<String, String> m = new HashMap<>();
        m.put("IAuthenticator_UserName", username);
        return m;
    }

    @Override
    public void onLoadSettings(Map<?, ?> m) {
        if (m == null)
            return;
        Object o = m.get("IAuthenticator_UserName");
        username = o instanceof String ? (String) o : "";
    }

    @Override
    public String getUserName() {
        return username;
    }

    @Override
    public void setUserName(String s) {
        username = s;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }
}
