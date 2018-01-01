/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.auth;

import java.net.Proxy;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public abstract class Account {

    public abstract String getUsername();

    public AuthInfo logIn() throws AuthenticationException {
        return logIn(Proxy.NO_PROXY);
    }

    public abstract AuthInfo logIn(Proxy proxy) throws AuthenticationException;

    public abstract void logOut();

    public abstract Map<Object, Object> toStorage();
}
