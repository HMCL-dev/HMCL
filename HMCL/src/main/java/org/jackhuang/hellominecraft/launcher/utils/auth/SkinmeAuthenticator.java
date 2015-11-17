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

import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
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
        if ($char == null)
            return NetUtils.doGet(
            "http://www.skinme.cc/api/login.php?user=" + user + "&hash=" + hash);
        else
            return NetUtils.doGet(
            "http://www.skinme.cc/api/login.php?user=" + user + "&hash=" + hash + "&char=" + $char);
    }

    @Override
    public UserProfileProvider login(LoginInfo info) {
        UserProfileProvider req = new UserProfileProvider();
        if (info.username == null || !info.username.contains("@")) {
            req.setSuccess(false);
            req.setErrorReason(C.i18n("login.not_email"));
            return req;
        }
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
                        req.setSuccess(false);
                        if (sl[1].contains("No Valid Character"))
                            sl[1] = C.i18n("login.no_valid_character");
                        req.setErrorReason(sl[1]);
                        break;
                    case "1": {
                        req.setSuccess(true);
                        String[] s = parseType1(sl[1]);
                        req.setUserName(s[0]);
                        req.setSession(s[1]);
                        req.setUserId(s[1]);
                        req.setAccessToken(s[1]);
                        break;
                    }
                    case "2": {
                        req.setSuccess(true);
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
                        if (s.sel == Selector.failedToSel) {
                            req.setSuccess(false);
                            req.setErrorReason(C.i18n("message.cancelled"));
                        } else {
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
            HMCLog.warn("Failed to login skinme.", e);

            req.setUserName(info.username);
            req.setSuccess(false);
            req.setUserType("Legacy");
            req.setErrorReason(e.getMessage());
            return req;
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
