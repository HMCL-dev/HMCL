/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.littleskin;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.util.logging.Logger;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.*;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * @author Glavo
 */
public class LittleSkinSession {
    private final String accessToken;
    private final String refreshToken;
    private final LittleSkinIdToken idToken;

    public LittleSkinSession(String accessToken, String refreshToken, LittleSkinIdToken idToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.idToken = idToken;

        if (accessToken != null) Logger.registerAccessToken(accessToken);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public LittleSkinIdToken getIdToken() {
        return idToken;
    }

    public static LittleSkinSession fromStorage(Map<?, ?> storage) {
        return null; // TODO
    }

    public Map<Object, Object> toStorage() {
        requireNonNull(idToken);

        return mapOf(
                pair("accessToken", accessToken),
                pair("refreshToken", refreshToken),
                pair("idToken", idToken));
    }

    public AuthInfo toAuthInfo() {
        requireNonNull(idToken);
        CompleteGameProfile selectedProfile = idToken.getSelectedProfile();
        selectedProfile.validate();

        return new AuthInfo(selectedProfile.getName(), selectedProfile.getId(), accessToken, AuthInfo.USER_TYPE_MSA, "{}");
    }
}
