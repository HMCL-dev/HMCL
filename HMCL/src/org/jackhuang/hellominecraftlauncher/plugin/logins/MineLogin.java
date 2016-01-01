package org.jackhuang.hellominecraftlauncher.plugin.logins;


import java.security.MessageDigest;
import org.jackhuang.hellominecraftlauncher.apis.handlers.Login;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginInfo;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginResult;
import org.jackhuang.hellominecraftlauncher.apis.Plugin;
import org.jackhuang.hellominecraftlauncher.utilities.C;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author hyh
 */
public class MineLogin extends Login {
        
    private static final char e[] = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f'
    };
    
    public static String hash(String type, String source) {
        try {
            StringBuilder stringbuilder;
            MessageDigest md = MessageDigest.getInstance(type);
            md.update(source.getBytes());
            byte[] bytes = md.digest();
            int s2 = bytes.length;
            stringbuilder = new StringBuilder(s2 << 1);
            for (int i1 = 0; i1 < s2; i1++) {
                stringbuilder.append(e[bytes[i1] >> 4 & 0xf]);
                stringbuilder.append(e[bytes[i1] & 0xf]);
            }

            return stringbuilder.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public MineLogin(String clientToken) {
        super(clientToken);
    }

    @Override
    public LoginResult login(LoginInfo info) {
        String usr = info.username.toLowerCase();
        String pwd = info.password;
        
        String str = hash("SHA1", usr);
        String hashCode = hash("MD5", pwd);
        String data = org.jackhuang.hellominecraftlauncher.apis.HttpGet.sendGetRequest(
                "http://www.minelogin.cc/ml/login.php",
                "username=" + usr + "&hash=" + hashCode + "&launcher=HelloMinecraftLauncher");
        LoginResult req = new LoginResult();
        req.username = usr;
        req.session = req.userId = req.accessToken = "minelogin";
        try {
            int requestNumber = Integer.parseInt(data);
            req.success = false;
            java.util.ResourceBundle bundle = C.I18N; // NOI18N
            switch(requestNumber) {
                case 0:
                    req.error = bundle.getString("MineLogin0");
                    break;
                case 1:
                    req.error = bundle.getString("MineLogin1");
                    break;
                case 2:
                    req.error = bundle.getString("MineLogin2");
                    break;
                case 3:
                    req.error = bundle.getString("MineLogin3");
                    break;
                case 4:
                    req.error = bundle.getString("MineLogin4");
                    break;
                case 5:
                    req.error = bundle.getString("MineLogin5");
                    break;
                case 100:
                    req.success = true;
                    break;
                default:
                    req.error = "Unkown result: " + data;
                    break;
            }          
        } catch(Exception e) {
            e.printStackTrace();
            req.success = false;
            req.error = data;
        }
        req.userType = "MineLogin";
        return req;
    }

    @Override
    public String getName() {
        return "MineLogin";
    }

    @Override
    public LoginResult loginBySettings() {
        return null;
    }

    @Override
    public void logout() {
        
    }
}