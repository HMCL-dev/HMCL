/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.auth.demo;

import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 *
 * @author huang
 */
public class DemoAccount extends Account {

    private final String username;
    private final UUID uuid;

    protected DemoAccount(String username, UUID uuid) {
        this.username = requireNonNull(username);
        this.uuid = requireNonNull(uuid);

        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
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
    public String getIdentifier() {
        return "demo:" + username;
    }

    public AuthInfo logIn() throws AuthenticationException {
        // Using "legacy" user type here because "mojang" user type may cause "invalid session token" or "disconnected" when connecting to a game server.
        return new DemoAuthInfo(username, uuid, UUIDTypeAdapter.fromUUID(UUID.randomUUID()), AuthInfo.USER_TYPE_MSA, "{}");
    }

    private class DemoAuthInfo extends AuthInfo {
        public DemoAuthInfo(String username, UUID uuid, String accessToken, String userType, String userProperties) {
            super(username, uuid, accessToken, userType, userProperties);
        }

        @Override
        public Arguments getLaunchArguments(LaunchOptions options) {
            Arguments arguments = new Arguments();
            arguments = arguments.addGameArguments("--demo");
            return arguments;
        }

        @Override
        public void close() throws IOException {
            // Do nothing.
        }
    }

    @Override
    public AuthInfo playOffline() throws AuthenticationException {
        return logIn();
    }

    @Override
    public Map<Object, Object> toStorage() {
        return mapOf(
                pair("uuid", UUIDTypeAdapter.fromUUID(uuid)),
                pair("username", username)
        );
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return super.getTextures();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", username)
                .append("uuid", uuid)
                .toString();
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DemoAccount))
            return false;
        DemoAccount another = (DemoAccount) obj;
        return isPortable() == another.isPortable() && username.equals(another.username);
    }
}
