/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.launcher.core.auth;

/**
 *
 * @author huangyuhui
 */
public final class UserProfileProvider {

    public String getUserName() {
        return username;
    }

    public UserProfileProvider setUserName(String username) {
        this.username = username;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public UserProfileProvider setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getSession() {
        return session;
    }

    public UserProfileProvider setSession(String session) {
        this.session = session;
        return this;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public UserProfileProvider setAccessToken(String accessToken) {
        if (accessToken == null)
            accessToken = "0";
        this.accessToken = accessToken;
        return this;
    }

    public String getUserProperties() {
        return userProperties;
    }

    public UserProfileProvider setUserProperties(String userProperties) {
        this.userProperties = userProperties;
        return this;
    }

    public String getUserPropertyMap() {
        return userPropertyMap;
    }

    public UserProfileProvider setUserPropertyMap(String userPropertyMap) {
        this.userPropertyMap = userPropertyMap;
        return this;
    }

    public String getOtherInfo() {
        return otherInfo;
    }

    public UserProfileProvider setOtherInfo(String otherInfo) {
        this.otherInfo = otherInfo;
        return this;
    }

    public String getClientIdentifier() {
        return clientIdentifier;
    }

    public UserProfileProvider setClientIdentifier(String clientIdentifier) {
        this.clientIdentifier = clientIdentifier;
        return this;
    }

    public String getUserType() {
        return userType;
    }

    public UserProfileProvider setUserType(String userType) {
        this.userType = userType;
        return this;
    }

    private String username = "";
    private String userId = "";
    private String session = "";
    private String accessToken = "";
    private String userProperties = "{}";
    private String userPropertyMap = "{}";
    private String otherInfo = "";
    private String clientIdentifier = "";
    private String userType = "Offline";
}
