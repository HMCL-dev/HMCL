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

import org.jackhuang.hmcl.util.DigestUtils;

import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public class OfflineAccountFactory extends AccountFactory<OfflineAccount> {
    public static final OfflineAccountFactory INSTANCE = new OfflineAccountFactory();

    private OfflineAccountFactory() {
    }

    @Override
    public OfflineAccount fromUsername(String username, String password, Object additionalData) {
        return new OfflineAccount(username, getUUIDFromUserName(username));
    }

    @Override
    public OfflineAccount fromStorageImpl(Map<Object, Object> storage) {
        Object username = storage.get("username");
        if (username == null || !(username instanceof String))
            throw new IllegalStateException("Offline account configuration malformed.");

        Object uuid = storage.get("uuid");
        if (uuid == null || !(uuid instanceof String))
            uuid = getUUIDFromUserName((String) username);

        return new OfflineAccount((String) username, (String) uuid);
    }

    private static String getUUIDFromUserName(String username) {
        return DigestUtils.md5Hex(username);
    }

}
