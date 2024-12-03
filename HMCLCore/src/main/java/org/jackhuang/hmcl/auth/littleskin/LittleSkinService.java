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

import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorProvider;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.io.IOException;

/**
 * @author Glavo
 */
public final class LittleSkinService extends OAuthService {
    private static final String API_ROOT = "https://littleskin.cn/api/yggdrasil/";
    private static final OAuth OAUTH = new OAuth(
            "https://littleskin.cn/api/yggdrasil/authserver/oauth",
            "https://littleskin.cn/oauth/token",
            "https://open.littleskin.cn/oauth/device_code",
            "https://open.littleskin.cn/oauth/token"
    );
    private static final String USER_INFO_API = "https://littleskin.cn/api/user";
    private static final String SCOPE = "User.Read Player.ReadWrite Yggdrasil.MinecraftToken.Create";
    private static final YggdrasilService SERVICE = new YggdrasilService(new AuthlibInjectorProvider(API_ROOT));

    public LittleSkinService(OAuth.Callback callback) {
        super(OAUTH, SCOPE, callback);
    }

    public YggdrasilSession authenticate() throws AuthenticationException {
        try {
            OAuth.Result result = OAUTH.authenticate(OAuth.GrantFlow.DEVICE, this);
            UserInfo userInfo = HttpRequest.POST(USER_INFO_API)
                    .authorization("Bearer", result.getAccessToken())
                    .getJson(UserInfo.class);


        } catch (IOException e) {
            throw new RuntimeException(e); // TODO
        }

        return null; // TODO
    }

    /**
     * <code>
     * {
     * "uid": 1,
     * "email": "example@example.com",
     * "nickname": "name",
     * "avatar": 0,
     * "score": 1000,
     * "permission": 0,
     * "last_sign_at": "2020-01-01 00:00:00",
     * "register_at": "2020-01-01 00:00:00",
     * "verified": true
     * }
     * </code>
     *
     * @see <a href="https://blessing.netlify.app/api/user.html#%E7%94%A8%E6%88%B7">Blessing Skin Web API</a>
     */
    private static final class UserInfo {
        public int uid;
        public String email;
        public String nickname;
        public boolean verified;
    }
}
