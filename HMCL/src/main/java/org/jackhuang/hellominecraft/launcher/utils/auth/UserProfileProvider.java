/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.utils.auth;

/**
 *
 * @author hyh
 */
public final class UserProfileProvider {

    public String getUserName() {
        return username;
    }

    public void setUserName(String username) {
        this.username = username;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public boolean isSuccessful() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorReason() {
        return error;
    }

    public void setErrorReason(String error) {
        this.error = error;
    }

    public String getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(String userProperties) {
        this.userProperties = userProperties;
    }

    public String getUserPropertyMap() {
        return userPropertyMap;
    }

    public void setUserPropertyMap(String userPropertyMap) {
        this.userPropertyMap = userPropertyMap;
    }

    public String getOtherInfo() {
        return otherInfo;
    }

    public void setOtherInfo(String otherInfo) {
        this.otherInfo = otherInfo;
    }

    public String getClientIdentifier() {
        return clientIdentifier;
    }

    public void setClientIdentifier(String clientIdentifier) {
        this.clientIdentifier = clientIdentifier;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }
    
    private String username = "";
    private String userId = "";
    private String session = "";
    private String accessToken = "";
    private boolean success = false;
    private String error = "";
    private String userProperties = "{}"; 
    private String userPropertyMap = "{}";
    private String otherInfo = "";
    private String clientIdentifier = "";
    private String userType = "Offline";
}
