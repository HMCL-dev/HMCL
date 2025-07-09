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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorProvider;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.util.JWTToken;
import org.jackhuang.hmcl.util.javafx.ObservableOptionalCache;

import java.nio.file.Path;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author Glavo
 */
public final class LittleSkinService extends OAuthService {
    public static final String API_ROOT = "https://littleskin.cn/api/yggdrasil/";
    private static final OAuth OAUTH = new OAuth(
            "https://littleskin.cn/api/yggdrasil/authserver/oauth",
            "https://littleskin.cn/oauth/token",
            "https://open.littleskin.cn/oauth/device_code",
            "https://open.littleskin.cn/oauth/token"
    );
    private static final String SCOPE = "openid offline_access User.Read Player.ReadWrite Yggdrasil.PlayerProfiles.Select Yggdrasil.Server.Join";

    private final YggdrasilService yggdrasilService = new YggdrasilService(new AuthlibInjectorProvider(API_ROOT));

    public LittleSkinService(OAuth.Callback callback) {
        super(OAUTH, SCOPE, callback);
    }

    public ObservableOptionalCache<UUID, CompleteGameProfile, AuthenticationException> getProfileRepository() {
        return yggdrasilService.getProfileRepository();
    }

    private static LittleSkinSession fromResult(OAuth.Result result) throws JsonParseException {
        LittleSkinIdToken idToken = JWTToken.parse(LittleSkinIdToken.class, result.getIdToken()).getPayload();
        idToken.validate();
        return new LittleSkinSession(result.getAccessToken(), result.getRefreshToken(), idToken);
    }

    public LittleSkinSession authenticate() throws AuthenticationException {
        try {
            return fromResult(OAUTH.authenticate(OAuth.GrantFlow.DEVICE, this));
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    public LittleSkinSession refresh(LittleSkinSession oldSession) throws AuthenticationException {
        try {
            return fromResult(OAUTH.refresh(oldSession.getRefreshToken(), this));
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    public boolean validate(LittleSkinSession session) throws AuthenticationException {
        requireNonNull(session);

        if (System.currentTimeMillis() > session.getIdToken().getExpirationTime()) {
            return false;
        }

        return yggdrasilService.validate(session.getAccessToken(), null);
    }

    public void uploadSkin(UUID uuid, String accessToken, boolean isSlim, Path file) throws AuthenticationException, UnsupportedOperationException {
        yggdrasilService.uploadSkin(uuid, accessToken, isSlim, file);
    }
}
