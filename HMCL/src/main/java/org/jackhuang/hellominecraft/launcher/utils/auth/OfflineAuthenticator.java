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
package org.jackhuang.hellominecraft.launcher.utils.auth;

import java.util.HashMap;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.code.DigestUtils;

/**
 *
 * @author huangyuhui
 */
public final class OfflineAuthenticator extends IAuthenticator {

    Map<String, String> uuidMap = new HashMap<>();

    public OfflineAuthenticator(String clientToken) {
        super(clientToken);
    }

    @Override
    public void onLoadSettings(Map m) {
        super.onLoadSettings(m);
        if (m == null)
            return;
        Object o = m.get("uuidMap");
        if (o != null && o instanceof Map)
            uuidMap = (Map<String, String>) o;
    }

    @Override
    public Map onSaveSettings() {
        Map m = super.onSaveSettings();
        m.put("uuidMap", m);
        return m;
    }

    @Override
    public UserProfileProvider login(LoginInfo info) throws AuthenticationException {
        if (StrUtils.isBlank(info.username))
            throw new AuthenticationException(C.i18n("login.no_Player007"));
        UserProfileProvider result = new UserProfileProvider();
        result.setUserName(info.username);
        String uuid = getUUIDFromUserName(info.username);
        if (uuidMap != null && uuid.contains(uuid))
            uuid = uuidMap.get(info.username);
        else {
            if (uuidMap == null)
                uuidMap = new HashMap<>();
            uuidMap.put(info.username, uuid);
        }
        result.setSession(uuid);
        result.setUserId(uuid);
        result.setAccessToken(uuid);
        result.setUserType("Legacy");
        return result;
    }

    public static String getUUIDFromUserName(String str) {
        return DigestUtils.md5Hex(str);
    }

    @Override
    public String id() {
        return "offline";
    }

    @Override
    public String getName() {
        return C.i18n("login.methods.offline");
    }

    @Override
    public boolean hasPassword() {
        return false;
    }

    @Override
    public UserProfileProvider loginBySettings() {
        return null;
    }

    @Override
    public void logout() {
    }

}
