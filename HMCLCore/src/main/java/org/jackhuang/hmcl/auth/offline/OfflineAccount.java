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
package org.jackhuang.hmcl.auth.offline;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.UserType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

/**
 *
 * @author huang
 */
public class OfflineAccount extends Account {

    private final String username;
    private final UUID uuid;

    OfflineAccount(String username, UUID uuid) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(uuid);

        this.username = username;
        this.uuid = uuid;

        if (StringUtils.isBlank(username))
            throw new IllegalArgumentException("Username cannot be blank");
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getCharacter() {
        return username;
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        if (StringUtils.isBlank(username))
            throw new AuthenticationException("Username cannot be empty");

        return new AuthInfo(username, uuid, UUIDTypeAdapter.fromUUID(UUID.randomUUID()), UserType.MOJANG, "{}");
    }

    @Override
    public AuthInfo logInWithPassword(String password) throws AuthenticationException {
        return logIn();
    }

    @Override
    public boolean canPlayOffline() {
        return false;
    }

    @Override
    public AuthInfo playOffline() {
        throw new IllegalStateException();
    }

    @Override
    public Map<Object, Object> toStorage() {
        return mapOf(
                pair("uuid", UUIDTypeAdapter.fromUUID(uuid)),
                pair("username", username)
        );
    }

    @Override
    public void clearCache() {
        // Nothing to clear.
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", username)
                .append("uuid", uuid)
                .toString();
    }
}
