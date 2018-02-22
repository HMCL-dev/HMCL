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
package org.jackhuang.hmcl.auth.yggdrasil;

/**
 *
 * @author huangyuhui
 */
final class AuthenticationResponse extends ErrorResponse {

    private final String accessToken;
    private final String clientToken;
    private final GameProfile selectedProfile;
    private final GameProfile[] availableProfiles;
    private final User user;

    public AuthenticationResponse() {
        this(null, null, null, null, null, null, null, null);
    }

    public AuthenticationResponse(String accessToken, String clientToken, GameProfile selectedProfile, GameProfile[] availableProfiles, User user, String error, String errorMessage, String cause) {
        super(error, errorMessage, cause);

        this.accessToken = accessToken;
        this.clientToken = clientToken;
        this.selectedProfile = selectedProfile;
        this.availableProfiles = availableProfiles;
        this.user = user;
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

    public GameProfile[] getAvailableProfiles() {
        return availableProfiles;
    }

    public User getUser() {
        return user;
    }

}
