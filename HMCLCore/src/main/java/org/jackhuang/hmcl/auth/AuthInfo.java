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

import java.util.UUID;

import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.util.Immutable;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class AuthInfo {

    private final String username;
    private final UUID uuid;
    private final String accessToken;
    private final UserType userType;
    private final String userProperties;
    private final Arguments arguments;

    public AuthInfo(String username, UUID uuid, String accessToken, UserType userType, String userProperties) {
        this(username, uuid, accessToken, userType, userProperties, null);
    }

    public AuthInfo(String username, UUID uuid, String accessToken, UserType userType, String userProperties, Arguments arguments) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.userType = userType;
        this.userProperties = userProperties;
        this.arguments = arguments;
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

    public UserType getUserType() {
        return userType;
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
     * @return null if no argument is specified
     */
    public Arguments getArguments() {
        return arguments;
    }

    public AuthInfo withArguments(Arguments arguments) {
        return new AuthInfo(username, uuid, accessToken, userType, userProperties, arguments);
    }
}
