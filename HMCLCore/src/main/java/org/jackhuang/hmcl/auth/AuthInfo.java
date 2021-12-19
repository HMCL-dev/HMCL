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
package org.jackhuang.hmcl.auth;

import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.Immutable;

import java.io.IOException;
import java.util.UUID;

/**
 *
 * @author huangyuhui
 */
@Immutable
public class AuthInfo implements AutoCloseable {

    private final String username;
    private final UUID uuid;
    private final String accessToken;
    private final String userProperties;

    public AuthInfo(String username, UUID uuid, String accessToken, String userProperties) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.userProperties = userProperties;
    }

    public String getUsername() {
        return username;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Properties of this user.
     * Don't know the difference between user properties and user property map.
     *
     * @return the user property map in JSON.
     */
    public String getUserProperties() {
        return userProperties;
    }

    /**
     * Called when launching game.
     * @return null if no argument is specified
     */
    public Arguments getLaunchArguments(LaunchOptions options) throws IOException {
        return null;
    }

    @Override
    public void close() throws Exception {
    }
}
