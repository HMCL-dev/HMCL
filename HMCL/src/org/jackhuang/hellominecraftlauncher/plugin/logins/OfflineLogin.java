package org.jackhuang.hellominecraftlauncher.plugin.logins;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import org.jackhuang.hellominecraftlauncher.apis.handlers.Login;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginInfo;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginResult;
import org.jackhuang.hellominecraftlauncher.utilities.C;

/**
 *
 * @author hyh
 */
public class OfflineLogin extends Login {

    public OfflineLogin(String clientToken) {
        super(clientToken);
    }

    @Override
    public LoginResult login(LoginInfo info) {
        LoginResult result = new LoginResult();
        result.success = true;
        result.username = info.username;
        result.session = result.userId = result.accessToken = "no";
        result.userType = "Offline";
        return result;
    }

    @Override
    public String getName() {
        return C.I18N.getString("OfflineLogin");
    }

    @Override
    public boolean isHidePasswordBox() {
        return true;
    }

    @Override
    public LoginResult loginBySettings() {
        return null;
    }

    @Override
    public void logout() {
    }
    
}
