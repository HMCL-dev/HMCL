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

import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class AuthInfo {

    private final String username;
    private final String userId;
    private final String authToken;
    private final UserType userType;
    private final String userProperties;
    private final String userPropertyMap;
    private final Arguments arguments;

    public AuthInfo(String username, String userId, String authToken) {
        this(username, userId, authToken, UserType.LEGACY);
    }

    public AuthInfo(String username, String userId, String authToken, UserType userType) {
        this(username, userId, authToken, userType, "{}");
    }

    public AuthInfo(String username, String userId, String authToken, UserType userType, String userProperties) {
        this(username, userId, authToken, userType, userProperties, "{}");
    }

    public AuthInfo(String username, String userId, String authToken, UserType userType, String userProperties, String userPropertyMap) {
        this(username, userId, authToken, userType, userProperties, userPropertyMap, null);
    }

    public AuthInfo(String username, String userId, String authToken, UserType userType, String userProperties, String userPropertyMap, Arguments arguments) {
        this.username = username;
        this.userId = userId;
        this.authToken = authToken;
        this.userType = userType;
        this.userProperties = userProperties;
        this.userPropertyMap = userPropertyMap;
        this.arguments = arguments;
    }

    public AuthInfo(GameProfile profile, String authToken, UserType userType, String userProperties) {
        this(profile.getName(), UUIDTypeAdapter.fromUUID(profile.getId()), authToken, userType, userProperties);
    }

    public String getUsername() {
        return username;
    }

    public String getUserId() {
        return userId;
    }

    public String getAuthToken() {
        return authToken;
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
     * Properties of this user.
     * Don't know the difference between user properties and user property map.
     *
     * @return the user property map in JSON.
     */
    public String getUserPropertyMap() {
        return userPropertyMap;
    }

    public Arguments getArguments() {
        return arguments;
    }

    public AuthInfo setArguments(Arguments arguments) {
        return new AuthInfo(username, userId, authToken, userType, userProperties, userPropertyMap, arguments);
    }
}
