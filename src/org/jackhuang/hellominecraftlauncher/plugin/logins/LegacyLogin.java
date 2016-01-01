package org.jackhuang.hellominecraftlauncher.plugin.logins;


import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.legacy.LegacyAuthenticationService;
import com.mojang.authlib.legacy.LegacyUserAuthentication;
import java.net.Proxy;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.jackhuang.hellominecraftlauncher.apis.handlers.Login;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginInfo;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginResult;
import org.jackhuang.hellominecraftlauncher.apis.Selector;
import org.jackhuang.hellominecraftlauncher.utilities.C;

/**
 *
 * @author hyh
 */
public class LegacyLogin extends Login {
    LegacyAuthenticationService service;
    LegacyUserAuthentication ua;

    public LegacyLogin(String clientToken) {
        super(clientToken);
        service = new LegacyAuthenticationService(Proxy.NO_PROXY);
        ua = (LegacyUserAuthentication) service.createUserAuthentication(Agent.MINECRAFT);
    }
    
    
    @Override
    public LoginResult login(LoginInfo info) {
        String usr = info.username;
        String pwd = info.password;
        if(ua.isLoggedIn()) ua.logOut();
        ua.setPassword(pwd);
        ua.setUsername(usr);
        return loginBySettings();
    }

    @Override
    public String getName() {
        return C.I18N.getString("LegacyLogin");
    }
    
    @Override
    public boolean isHidePasswordBox() {
        return false;
    }

    @Override
    public LoginResult loginBySettings() {
        LoginResult result = new LoginResult();
        try {
            ua.logIn();
            if(!ua.canPlayOnline()) throw new Exception(C.I18N.getString("WrongPassword"));
            GameProfile selectedProfile = ua.getSelectedProfile();
            GameProfile[] profiles = ua.getAvailableProfiles();
            String[] names;
            String username;
            if(selectedProfile == null) {
                if(ArrayUtils.isNotEmpty(profiles)) {
                    names = new String[profiles.length];
                    for(int i = 0; i < profiles.length; i++) {
                        names[i] = profiles[i].getName();
                    }
                    Selector s = new Selector(null, true, names, C.I18N.getString("PleaseChooseCharacter"));
                    s.setVisible(true);
                    username = names[s.sel];
                } else {
                    throw new Exception(C.I18N.getString("NoCharacter"));
                }
            } else {
                username = selectedProfile.getName();
            }
            result.username = username;
            result.success = true;
            result.userId = ua.getUserID();
            result.accessToken = result.session = ua.getAuthenticatedToken();
        } catch (Exception ex) {
            result.error = ex.getMessage();
            result.success = false;
            result.username = ua.getUserID();
        }
        result.userType = "Legacy";
        return result;
    }

    @Override
    public void logout() {
        ua.logOut();
    }

    @Override
    public void onLoadSettings(Map settings) {
        if(settings == null) return;
        ua.loadFromStorage(settings);
    }

    @Override
    public Map onSaveSettings() {
        return ua.saveForStorage();
    }
    
}
