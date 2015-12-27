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
package org.jackhuang.hellominecraft.launcher.servers.mfcraft;

import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.utils.auth.AuthenticationException;
import org.jackhuang.hellominecraft.launcher.utils.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.LoginInfo;
import static org.jackhuang.hellominecraft.launcher.utils.auth.OfflineAuthenticator.getUUIDFromUserName;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.version.ServerInfo;
import org.jackhuang.hellominecraft.utils.NetUtils;

/**
 *
 * @author huangyuhui
 */
public class MFCraftAuthenticator extends IAuthenticator {

    public MFCraftAuthenticator(String clientToken) {
        super(clientToken);
    }

    @Override
    public UserProfileProvider login(LoginInfo info) throws AuthenticationException {
        try {
            UserProfileProvider result = new UserProfileProvider();
            String url = String.format("http://zh.mfcraft.cn/index.php?c=user_public&a=clientlogin&user=%s&pass=%s", info.username, info.password);
            String response = NetUtils.get(url);
            if (response.contains("error"))
                throw new AuthenticationException(C.i18n("login.wrong_password"));
            result.setUserName(info.username);
            String uuid = getUUIDFromUserName(info.username);
            result.setSession(uuid);
            result.setUserId(uuid);
            result.setAccessToken(uuid);
            result.setUserType("Legacy");
            return result;
        } catch (IOException | JsonSyntaxException ex) {
            throw new AuthenticationException(C.i18n("login.failed.connect_authentication_server"), ex);
        }

    }

    @Override
    public String getName() {
        return "MFCraft";
    }

    @Override
    public UserProfileProvider loginBySettings() throws AuthenticationException {
        return null;
    }

    @Override
    public void logout() {
    }

}
