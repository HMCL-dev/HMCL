/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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

import java.util.Map;
import java.util.UUID;

/**
 *
 * @author huangyuhui
 */
public abstract class Account {

    /**
     * @return the account name (mostly email)
     */
    public abstract String getUsername();

    /**
     *
     * @return the character name
     */
    public abstract String getCharacter();

    /**
     * @return the UUID
     */
    public abstract UUID getUUID();

    /**
     * log in.
     * @return the specific player's info.
     * @throws AuthenticationException if server error occurred.
     */
    public abstract AuthInfo logIn() throws AuthenticationException;

    public abstract AuthInfo logInWithPassword(String password) throws AuthenticationException;

    public abstract boolean canPlayOffline();

    /**
     * Play offline.
     * @return the specific offline player's info.
     */
    public abstract AuthInfo playOffline();

    public abstract void logOut();

    public abstract Map<Object, Object> toStorage();

    public abstract void clearCache();
}
