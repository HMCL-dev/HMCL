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

import org.jackhuang.hmcl.util.Lang;

import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public abstract class AccountFactory<T extends Account> {

    public final T fromUsername(String username) {
        return fromUsername(username, "", null);
    }

    public final T fromUsername(String username, String password) {
        return fromUsername(username, password, null);
    }

    public abstract T fromUsername(String username, String password, Object additionalData);

    protected abstract T fromStorageImpl(Map<Object, Object> storage);

    public final T fromStorage(Map<Object, Object> storage) {
        T account = fromStorageImpl(storage);
        Map<?, ?> properties = Lang.get(storage, "properties", Map.class).orElse(null);
        if (properties == null) return account;
        account.getProperties().putAll(properties);
        return account;
    }
}
