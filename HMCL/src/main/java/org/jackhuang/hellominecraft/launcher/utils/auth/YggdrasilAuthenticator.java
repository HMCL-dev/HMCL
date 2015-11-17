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

import com.google.gson.GsonBuilder;
import java.net.Proxy;
import java.util.Map;
import javax.swing.JOptionPane;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.views.Selector;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.GameProfile;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.PropertyMap;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.YggdrasilAuthentication;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.UUIDTypeAdapter;

/**
 *
 * @author huangyuhui
 */
public final class YggdrasilAuthenticator extends IAuthenticator {

    YggdrasilAuthentication ua;

    public YggdrasilAuthenticator(String clientToken) {
        super(clientToken);
        ua = new YggdrasilAuthentication(Proxy.NO_PROXY, clientToken);
    }

    @Override
    public UserProfileProvider login(LoginInfo info) {
        if (ua.canPlayOnline()) {
            UserProfileProvider result = new UserProfileProvider();
            result.setUserName(info.username);
            result.setSuccess(true);
            result.setUserId(UUIDTypeAdapter.fromUUID(ua.getSelectedProfile().id));
            result.setUserProperties(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.LegacySerializer()).create().toJson(ua.getUserProperties()));
            result.setUserPropertyMap(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create().toJson(ua.getUserProperties()));
            result.setAccessToken(ua.getAuthenticatedToken());
            result.setSession(ua.getAuthenticatedToken());
            result.setUserType("mojang");
            return result;
        }
        UserProfileProvider result = new UserProfileProvider();
        String usr = info.username;
        if (info.username == null || !info.username.contains("@")) {
            result.setSuccess(false);
            result.setErrorReason(C.i18n("login.not_email"));
            return result;
        }
        String pwd = info.password;

        if (!ua.isLoggedIn())
            ua.setPassword(pwd);
        ua.setUsername(usr);
        try {
            ua.logIn();
            if (!ua.isLoggedIn())
                throw new Exception(C.i18n("login.wrong_password"));
            GameProfile selectedProfile = ua.getSelectedProfile();
            GameProfile[] profiles = ua.getAvailableProfiles();
            String[] names;
            String username;
            if (selectedProfile == null)
                if (ArrayUtils.isNotEmpty(profiles)) {
                    names = new String[profiles.length];
                    for (int i = 0; i < profiles.length; i++)
                        names[i] = profiles[i].name;
                    Selector s = new Selector(null, names, C.i18n("login.choose_charactor"));
                    s.setVisible(true);
                    selectedProfile = profiles[s.sel];
                    username = names[s.sel];
                } else
                    username = JOptionPane.showInputDialog(C.i18n("login.no_charactor"));
            else
                username = selectedProfile.name;
            result.setUserName(username);
            result.setSuccess(true);
            result.setUserId(selectedProfile == null ? OfflineAuthenticator.getUUIDFromUserName(username) : UUIDTypeAdapter.fromUUID(selectedProfile.id));
            result.setUserProperties(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.LegacySerializer()).create().toJson(ua.getUserProperties()));
            result.setUserPropertyMap(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create().toJson(ua.getUserProperties()));
            String authToken = ua.getAuthenticatedToken();
            if (authToken == null)
                authToken = "0";
            result.setAccessToken(authToken);
            result.setSession(authToken);
        } catch (Exception ex) {
            result.setErrorReason(ex.getMessage());
            result.setSuccess(false);
            result.setUserName(ua.getUserID());

            HMCLog.err("Failed to login by yggdrasil authentication.", ex);
        }
        result.setUserType("mojang");
        return result;
    }

    @Override
    public boolean isLoggedIn() {
        return ua.isLoggedIn();
    }

    @Override
    public String getName() {
        return C.i18n("login.methods.yggdrasil");
    }

    public Map onSaveSettings() {
        return ua.saveForStorage();
    }

    public void onLoadSettings(Map settings) {
        if (settings == null)
            return;
        ua.loadFromStorage(settings);
    }

    @Override
    public UserProfileProvider loginBySettings() {
        UserProfileProvider info = new UserProfileProvider();
        try {
            ua.logIn();
            if (!ua.isLoggedIn())
                throw new Exception(C.i18n("login.wrong_password"));
            GameProfile profile = ua.getSelectedProfile();
            info.setUserName(profile.name);
            info.setSuccess(true);
            info.setUserId(profile.id.toString());
            info.setAccessToken(ua.getAuthenticatedToken());
        } catch (Exception ex) {
            info.setErrorReason(ex.getMessage());
            info.setSuccess(false);
            info.setUserName(ua.getUserID());
        }
        return info;
    }

    @Override
    public void logout() {
        ua.logOut();
    }
}
