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
package org.jackhuang.hmcl.auth.yggdrasil;

/**
 *
 * @author huang
 */
public final class RefreshRequest {

    private final String accessToken;
    private final String clientToken;
    private final GameProfile selectedProfile;
    private final boolean requestUser;

    public RefreshRequest(String accessToken, String clientToken) {
        this(accessToken, clientToken, null);
    }

    public RefreshRequest(String accessToken, String clientToken, GameProfile selectedProfile) {
        this(accessToken, clientToken, selectedProfile, true);
    }

    public RefreshRequest(String accessToken, String clientToken, GameProfile selectedProfile, boolean requestUser) {
        this.accessToken = accessToken;
        this.clientToken = clientToken;
        this.selectedProfile = selectedProfile;
        this.requestUser = requestUser;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getClientToken() {
        return clientToken;
    }

    public GameProfile getSelectedProfile() {
        return selectedProfile;
    }

    public boolean isRequestUser() {
        return requestUser;
    }

}
