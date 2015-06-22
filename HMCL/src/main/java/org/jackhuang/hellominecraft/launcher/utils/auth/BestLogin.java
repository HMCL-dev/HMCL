/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.auth;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import org.jackhuang.hellominecraft.utils.DigestUtils;

/**
 *
 * @author hyh
 */
public final class BestLogin extends IAuthenticator {

    public BestLogin(String clientToken) {
        super(clientToken);
    }

    @Override
    public UserProfileProvider login(LoginInfo info) {
        try {
            String request = "bl:l:" + info.username + ":" + DigestUtils.sha1Hex(info.password);

            Socket socket = new Socket("auth.zhh0000zhh.com", 8);
            OutputStream os = socket.getOutputStream();
            os.write(request.length());
            os.write(request.getBytes());

            UserProfileProvider lr = new UserProfileProvider();
            lr.setSuccess(false);

            InputStream is = socket.getInputStream();
            int code = is.read();
            switch (code) {
                case -1:
                    lr.setErrorReason("internet error.");
                    break;
                case 200:
                    lr.setErrorReason("server restarting.");
                    break;
                case 255:
                    lr.setErrorReason("unknown error");
                    break;
                case 3:
                    lr.setErrorReason("unregistered.");
                    break;
                case 50:
                    lr.setErrorReason("please update your launcher and act your account.");
                    break;
                case 2:
                    lr.setErrorReason("wrong password.");
                    break;
                case 100:
                    lr.setErrorReason("server reloading.");
                    break;
                case 0:
                    lr.setSuccess(true);
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
        } catch (Throwable t) {
            UserProfileProvider lr = new UserProfileProvider();
            lr.setSuccess(false);
            lr.setErrorReason(t.getMessage());
            return lr;
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
