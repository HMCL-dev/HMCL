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
package org.jackhuang.hmcl.core.auth.yggdrasil;

public class RefreshRequest {

    private String clientToken;
    private String accessToken;
    private GameProfile selectedProfile;
    private boolean requestUser = true;

    public RefreshRequest(String accessToken, String clientToken) {
        this.clientToken = clientToken;
        this.accessToken = accessToken;
    }

    public String getClientToken() {
        return clientToken;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public GameProfile getSelectedProfile() {
        return selectedProfile;
    }

    public void setSelectedProfile(GameProfile selectedProfile) {
        this.selectedProfile = selectedProfile;
    }

    public boolean isRequestUser() {
        return requestUser;
    }

    public void setRequestUser(boolean requestUser) {
        this.requestUser = requestUser;
    }

}
