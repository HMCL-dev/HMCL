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
import java.util.HashMap;
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
     * @return the UUID
     */
    public abstract UUID getUUID();

    /**
     * log in.
     * @param selector selects a character
     * @return the specific player's info.
     * @throws AuthenticationException if server error occurred.
     */
    public AuthInfo logIn(MultiCharacterSelector selector) throws AuthenticationException {
        return logIn(selector, Proxy.NO_PROXY);
    }

    /**
     * log in.
     * @param selector selects a character
     * @param proxy by which connect to the server
     * @return the specific player's info.
     * @throws AuthenticationException if server error occurred.
     */
    public abstract AuthInfo logIn(MultiCharacterSelector selector, Proxy proxy) throws AuthenticationException;

    public AuthInfo logInWithPassword(MultiCharacterSelector selector, String password) throws AuthenticationException {
        return logInWithPassword(selector, password, Proxy.NO_PROXY);
    }

    public abstract AuthInfo logInWithPassword(MultiCharacterSelector selector, String password, Proxy proxy) throws AuthenticationException;

    public abstract boolean canPlayOffline();

    /**
     * Play offline.
     * @return the specific offline player's info.
     */
    public abstract AuthInfo playOffline();

    public abstract void logOut();

    protected abstract Map<Object, Object> toStorageImpl();

    public final Map<Object, Object> toStorage() {
        Map<Object, Object> storage = toStorageImpl();
        if (!getProperties().isEmpty())
            storage.put("properties", getProperties());
        return storage;
    }

    private Map<Object, Object> properties = new HashMap<>();

    /**
     * To save some necessary extra information here.
     * @return the property map.
     */
    public final Map<Object, Object> getProperties() {
        return properties;
    }

    public abstract void clearCache();
}
