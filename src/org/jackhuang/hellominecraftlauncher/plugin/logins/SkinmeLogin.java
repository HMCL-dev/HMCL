package org.jackhuang.hellominecraftlauncher.plugin.logins;

import java.security.MessageDigest;
import org.jackhuang.hellominecraftlauncher.apis.handlers.Login;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginInfo;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginResult;
import org.jackhuang.hellominecraftlauncher.apis.Selector;
import org.jackhuang.hellominecraftlauncher.utilities.C;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author hyh
 */
public class SkinmeLogin extends Login {

    public SkinmeLogin(String clientToken) {
        super(clientToken);
    }
    
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
    
    public String[] parseType1(String s) {
        return s.split(",");
    }
    
    public String getCharacter(String user, String hash, String $char) {
        if($char == null) {
            return org.jackhuang.hellominecraftlauncher.apis.HttpGet.sendGetRequest(
                "http://www.skinme.cc/api/login.php", "user=" + user + "&hash=" + hash);
        } else {
            return org.jackhuang.hellominecraftlauncher.apis.HttpGet.sendGetRequest(
                "http://www.skinme.cc/api/login.php", "user=" + user + "&hash=" + hash + "&char=" + $char);
        }
    }

    @Override
    public LoginResult login(LoginInfo info) {
        String usr = info.username.toLowerCase();
        String pwd = info.password;
        
        String str = hash("SHA1", usr);
        String hashCode = hash("SHA1", hash("MD5", hash("SHA1", pwd) + pwd) + str);
        System.out.println("hashCode=" + hashCode);
        String data = getCharacter(usr, hashCode, null);
        String[] sl = data.split(":");
        LoginResult req = new LoginResult();
        if("0".equals(sl[0])) {
            req.success = false;
            req.error = sl[1];
        } else if("1".equals(sl[0])) {
            req.success = true;
            String[] s = parseType1(sl[1]);
            req.username = s[0];
            req.session = req.userId = req.accessToken = s[1];
        } else if("2".equals(sl[0])) {
            req.success = true;
            String[] charators = sl[1].split(";");
            int len = charators.length;
            String[] $char = new String[len];
            String[] user = new String[len];
            System.out.println(sl[1]);
            for(int i = 0; i < len; i++) {
                String[] charator = charators[i].split(",");
                $char[i] = charator[0];
                user[i] = charator[1];
            }
            Selector s = new Selector(null, true, user, C.I18N.getString("PleaseChooseCharacter"));
            s.setVisible(true);
            if(s.sel == Selector.failedToSel) {
                req.success = false;
                req.error = "Canceled";
            } else {
                int index = s.sel;
                String character = $char[index];
                sl = getCharacter(usr, hashCode, character).split(":");
                String[] s2 = parseType1(sl[1]);
                req.username = s2[0];
                req.session = req.userId = req.accessToken = s2[1];
            }
        }
        
        req.userType = "Skinme";
        return req;
    }
    
    @Override
    public String getName() {
        return "Skinme";
    }

    @Override
    public LoginResult loginBySettings() {
        return null;
    }

    @Override
    public void logout() {
        
    }
}
