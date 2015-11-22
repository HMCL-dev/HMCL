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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import org.jackhuang.hellominecraft.utils.code.DigestUtils;

/**
 *
 * @author huangyuhui
 */
public final class BestLogin extends IAuthenticator {

    public BestLogin(String clientToken) {
        super(clientToken);
    }

    @Override
    public UserProfileProvider login(LoginInfo info) throws AuthenticationException {
        try {
            String request = "bl:l:" + info.username + ":" + DigestUtils.sha1Hex(info.password);

            Socket socket = new Socket("auth.zhh0000zhh.com", 8);
            OutputStream os = socket.getOutputStream();
            os.write(request.length());
            os.write(request.getBytes());

            UserProfileProvider lr = new UserProfileProvider();

            InputStream is = socket.getInputStream();
            int code = is.read();
            switch (code) {
                case -1:
                    throw new AuthenticationException("internet error.");
                case 200:
                    throw new AuthenticationException("server restarting.");
                case 255:
                    throw new AuthenticationException("unknown error");
                case 3:
                    throw new AuthenticationException("unregistered.");
                case 50:
                    throw new AuthenticationException("please update your launcher and act your account.");
                case 2:
                    throw new AuthenticationException("wrong password.");
                case 100:
                    throw new AuthenticationException("server reloading.");
                case 0:
                    byte[] b = new byte[64];
                    is.read(b, 0, b.length);
                    String[] ss = new String(b).split(":");
                    lr.setUserName(info.username);
                    lr.setUserId(ss[1]);
                    lr.setSession(ss[0]);
                    lr.setAccessToken(ss[0]);
                    break;
            }
            lr.setUserType("Legacy");
            return lr;
        } catch (IOException t) {
            throw new AuthenticationException(t);
        }
    }

    @Override
    public String getName() {
        return "BestLogin";
    }

    @Override
    public UserProfileProvider loginBySettings() {
        return null;
    }

    @Override
    public void logout() {
    }

}
