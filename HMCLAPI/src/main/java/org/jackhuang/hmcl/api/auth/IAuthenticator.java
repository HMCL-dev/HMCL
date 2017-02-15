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
package org.jackhuang.hmcl.api.auth;

import java.util.Map;

/**
 *
 * @author huang
 */
public interface IAuthenticator {

    /**
     *
     * @return the name of login method.
     */
    String getName();

    String getPassword();

    String getUserName();

    /**
     * Has password?
     *
     * @return has password?
     */
    boolean hasPassword();

    String id();

    boolean isLoggedIn();

    void logOut();

    /**
     * Login Method
     *
     * @param info username & password
     *
     * @return login result
     *
     * @throws
     * org.jackhuang.hmcl.core.auth.AuthenticationException
     */
    UserProfileProvider login(LoginInfo info) throws AuthenticationException;

    UserProfileProvider loginBySettings() throws AuthenticationException;

    void onLoadSettings(Map<?, ?> m);

    Map<?, ?> onSaveSettings();

    void setPassword(String password);

    void setRememberMe(boolean is);

    void setUserName(String s);
    
}
