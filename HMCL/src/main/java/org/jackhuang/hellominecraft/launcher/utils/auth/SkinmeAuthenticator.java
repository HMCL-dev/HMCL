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

import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.code.DigestUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.views.Selector;

/**
 *
 * @author huangyuhui
 */
public final class SkinmeAuthenticator extends IAuthenticator {

    public SkinmeAuthenticator(String clientToken) {
        super(clientToken);
    }

    public String[] parseType1(String s) {
        return s.split(",");
    }

    public String getCharacter(String user, String hash, String $char) throws Exception {
        return NetUtils.get(
            "http://www.skinme.cc/api/login.php?user=" + user + "&hash=" + hash + (($char == null) ? "" : ("&char=" + $char)));
    }

    @Override
    public UserProfileProvider login(LoginInfo info) throws AuthenticationException {
        UserProfileProvider req = new UserProfileProvider();
        if (info.username == null || !info.username.contains("@"))
            throw new AuthenticationException(C.i18n("login.not_email"));
        try {
            String usr = info.username.toLowerCase();
            String pwd = info.password;

            String str = DigestUtils.sha1Hex(usr);
            String hashCode = DigestUtils.sha1Hex(DigestUtils.md5Hex(DigestUtils.sha1Hex(pwd) + pwd) + str);
            String data = getCharacter(usr, hashCode, null);
            String[] sl = data.split(":");
            if (null != sl[0])
                switch (sl[0]) {
                case "0":
                    if (sl[1].contains("No Valid Character"))
                        sl[1] = C.i18n("login.no_valid_character");
                    throw new AuthenticationException(sl[1]);
                case "1": {
                    String[] s = parseType1(sl[1]);
                    req.setUserName(s[0]);
                    req.setSession(s[1]);
                    req.setUserId(s[1]);
                    req.setAccessToken(s[1]);
                    break;
                }
                case "2": {
                    String[] charators = sl[1].split(";");
                    int len = charators.length;
                    String[] $char = new String[len];
                    String[] user = new String[len];
                    System.out.println(sl[1]);
                    for (int i = 0; i < len; i++) {
                        String[] charator = charators[i].split(",");
                        $char[i] = charator[0];
                        user[i] = charator[1];
                    }
                    Selector s = new Selector(null, user, C.i18n("login.choose_charactor"));
                    s.setVisible(true);
                    if (s.sel == Selector.FAILED_TO_SELECT)
                        throw new AuthenticationException(C.i18n("message.cancelled"));
                    else {
                        int index = s.sel;
                        String character = $char[index];
                        sl = getCharacter(usr, hashCode, character).split(":");
                        String[] s2 = parseType1(sl[1]);
                        req.setUserName(s2[0]);
                        req.setSession(s2[1]);
                        req.setUserId(s2[1]);
                        req.setAccessToken(s2[1]);
                    }
                    break;
                }
                }

            req.setUserType("Legacy");
            return req;
        } catch (Exception e) {
            throw new AuthenticationException(e);
        }
    }

    @Override
    public String getName() {
        return "Skinme";
    }

    @Override
    public UserProfileProvider loginBySettings() {
        return null;
    }

    @Override
    public void logout() {

    }
}
