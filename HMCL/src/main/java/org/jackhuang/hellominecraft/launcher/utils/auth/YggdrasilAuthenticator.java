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
import org.jackhuang.mojang.authlib.GameProfile;
import org.jackhuang.mojang.authlib.UserType;
import org.jackhuang.mojang.authlib.properties.PropertyMap;
import org.jackhuang.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import org.jackhuang.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import org.jackhuang.mojang.util.LegacyPropertyMapSerializer;
import org.jackhuang.mojang.util.UUIDTypeAdapter;

/**
 *
 * @author huangyuhui
 */
public final class YggdrasilAuthenticator extends IAuthenticator {

    YggdrasilAuthenticationService service;
    YggdrasilUserAuthentication ua;

    public YggdrasilAuthenticator(String clientToken) {
        super(clientToken);
        service = new YggdrasilAuthenticationService(Proxy.NO_PROXY, clientToken);
        ua = (YggdrasilUserAuthentication) service.createUserAuthentication();
    }

    @Override
    public UserProfileProvider login(LoginInfo info) {
        if (ua.canPlayOnline()) {
            UserProfileProvider result = new UserProfileProvider();
            result.setUserName(info.username);
            result.setSuccess(true);
            result.setUserId(UUIDTypeAdapter.fromUUID(ua.getSelectedProfile().getId()));
            result.setUserProperties(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new LegacyPropertyMapSerializer()).create().toJson(ua.getUserProperties()));
            result.setUserPropertyMap(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create().toJson(ua.getUserProperties()));
            result.setAccessToken(ua.getAuthenticatedToken());
            result.setSession(ua.getAuthenticatedToken());
            result.setUserType(ua.getUserType().getName());
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
            if (!ua.isLoggedIn()) throw new Exception(C.i18n("login.wrong_password"));
            GameProfile selectedProfile = ua.getSelectedProfile();
            GameProfile[] profiles = ua.getAvailableProfiles();
            String[] names;
            String username;
            if (selectedProfile == null)
                if (ArrayUtils.isNotEmpty(profiles)) {
                    names = new String[profiles.length];
                    for (int i = 0; i < profiles.length; i++)
                        names[i] = profiles[i].getName();
                    Selector s = new Selector(null, names, C.i18n("login.choose_charactor"));
                    s.setVisible(true);
                    selectedProfile = profiles[s.sel];
                    username = names[s.sel];
                } else
                    username = JOptionPane.showInputDialog(C.i18n("login.no_charactor"));
            else
                username = selectedProfile.getName();
            result.setUserName(username);
            result.setSuccess(true);
            result.setUserId(selectedProfile == null ? OfflineAuthenticator.getUUIDFromUserName(username) : UUIDTypeAdapter.fromUUID(selectedProfile.getId()));
            result.setUserProperties(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new LegacyPropertyMapSerializer()).create().toJson(ua.getUserProperties()));
            result.setUserPropertyMap(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create().toJson(ua.getUserProperties()));
            String authToken = ua.getAuthenticatedToken();
            if (authToken == null) authToken = "0";
            result.setAccessToken(authToken);
            result.setSession(authToken);
            result.setUserType(ua.getUserType().getName());
        } catch (Exception ex) {
            result.setErrorReason(ex.getMessage());
            result.setSuccess(false);
            result.setUserName(ua.getUserID());
            result.setUserType(UserType.MOJANG.getName());

            HMCLog.err("Failed to login by yggdrasil authentication.", ex);
        }
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
        if (settings == null) return;
        ua.loadFromStorage(settings);
    }

    @Override
    public UserProfileProvider loginBySettings() {
        UserProfileProvider info = new UserProfileProvider();
        try {
            ua.logIn();
            if (!ua.isLoggedIn()) throw new Exception(C.i18n("login.wrong_password"));
            GameProfile profile = ua.getSelectedProfile();
            info.setUserName(profile.getName());
            info.setSuccess(true);
            info.setUserId(profile.getId().toString());
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
