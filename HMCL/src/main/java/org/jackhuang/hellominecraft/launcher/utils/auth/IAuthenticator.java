/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.auth;

import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.launcher.utils.settings.Settings;

/**
 * Login interface
 *
 * @author hyh
 */
public abstract class IAuthenticator {

    public static final YggdrasilAuthenticator yggdrasilLogin;
    public static final OfflineAuthenticator offlineLogin;
    public static final SkinmeAuthenticator skinmeLogin;
    //public static final BestLogin bestLogin;

    public static final List<IAuthenticator> logins;

    static {
	String clientToken = Settings.s().getClientToken();
	logins = new ArrayList<>();
	logins.add(offlineLogin = new OfflineAuthenticator(clientToken));
	logins.add(yggdrasilLogin = new YggdrasilAuthenticator(clientToken));
	logins.add(skinmeLogin = new SkinmeAuthenticator(clientToken));
        //logins.add(bestLogin = new BestLogin(clientToken));
	yggdrasilLogin.onLoadSettings(Settings.s().getYggdrasilConfig());

	Runtime.getRuntime().addShutdownHook(new Thread() {

	    @Override
	    public void run() {
		Settings.s().setYggdrasilConfig(yggdrasilLogin.onSaveSettings());
	    }
	});
    }

    protected String clientToken;

    public IAuthenticator(String clientToken) {
	this.clientToken = clientToken;
    }

    /**
     * Login Method
     *
     * @param info username & password
     * @return login result
     */
    public abstract UserProfileProvider login(LoginInfo info);

    /**
     *
     * @return the name of login method.
     */
    public abstract String getName();

    /**
     * Has password?
     *
     * @return Will I hide password box?
     */
    public boolean isHidePasswordBox() {
	return false;
    }

    public boolean isLoggedIn() {
	return false;
    }

    public void setRememberMe(boolean is) {

    }

    public abstract UserProfileProvider loginBySettings();

    public abstract void logout();
}
