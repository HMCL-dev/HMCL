package org.jackhuang.hellominecraftlauncher.plugin.logins;


import com.google.gson.Gson;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import com.mojang.authlib.yggdrasil.request.NameValue;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JOptionPane;
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
public class YggdrasilLogin extends Login {
    
    YggdrasilAuthenticationService service;
    YggdrasilUserAuthentication ua;

    public YggdrasilLogin(String clientToken) {
        super(clientToken);
        service = new YggdrasilAuthenticationService(Proxy.NO_PROXY, clientToken);
        ua = (YggdrasilUserAuthentication)service.createUserAuthentication(Agent.MINECRAFT);
    }

    @Override
    public LoginResult login(LoginInfo info) {
        String usr = info.username;
        String pwd = info.password;
        
        ua.setPassword(pwd);
        ua.setUsername(usr);
        LoginResult result = new LoginResult();
        try {
            ua.logIn();
            if(!ua.isLoggedIn()) throw new Exception(C.I18N.getString("WrongPassword"));
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
                    username = JOptionPane.showInputDialog(C.I18N.getString("NoCharacter"));
                }
            } else {
                username = selectedProfile.getName();
            }
            result.username = username;
            result.success = true;
            result.userId = ua.getUserID();
            ArrayList<NameValue> al = ua.getUser().getProperties();
            HashMap<String, ArrayList<String>> userProperties = new HashMap<String, ArrayList<String>>();
            if(al != null && !al.isEmpty()) {
                for(NameValue nv : al) {
                    ArrayList<String> al2 = new ArrayList<String>();
                    al2.add(nv.value);
                    userProperties.put(nv.name, al2);
                }
            }
            result.userProperties = new Gson().toJson(userProperties);
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
    public boolean isLoggedIn() {
        return ua.isLoggedIn();
    }

    @Override
    public String getName() {
        return C.I18N.getString("YggdrasilLogin");
    }

    @Override
    public Map onSaveSettings() {
        return ua.saveForStorage();
    }

    @Override
    public void onLoadSettings(Map settings) {
        if(settings == null) return;
        ua.loadFromStorage(settings);
    }

    @Override
    public LoginResult loginBySettings() {
        LoginResult info = new LoginResult();
        try {
            ua.logIn();
            if(!ua.isLoggedIn()) throw new Exception(C.I18N.getString("WrongPassword"));
            GameProfile profile = ua.getSelectedProfile();
            info.username = profile.getName();
            info.success = true;
            info.userId = profile.getId();
            info.accessToken = ua.getAuthenticatedToken();
        } catch (Exception ex) {
            info.error = ex.getMessage();
            info.success = false;
            info.username = ua.getUserID();
        }
        return info;
    }

    @Override
    public void logout() {
        ua.logOut();
    }
}
