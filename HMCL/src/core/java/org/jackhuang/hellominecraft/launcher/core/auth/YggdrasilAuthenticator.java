/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.launcher.core.auth;

import com.google.gson.GsonBuilder;
import java.net.Proxy;
import java.util.Map;
import javax.swing.JOptionPane;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.ArrayUtils;
import org.jackhuang.hellominecraft.launcher.core.auth.yggdrasil.GameProfile;
import org.jackhuang.hellominecraft.launcher.core.auth.yggdrasil.UUIDTypeAdapter;
import org.jackhuang.hellominecraft.launcher.core.auth.yggdrasil.PropertyMap;
import org.jackhuang.hellominecraft.launcher.core.auth.yggdrasil.YggdrasilAuthentication;
import org.jackhuang.hellominecraft.util.ui.SwingUtils;

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
    public UserProfileProvider login(LoginInfo info) throws AuthenticationException {
        UserProfileProvider result = new UserProfileProvider();
        if (ua.canPlayOnline()) {
            result.setUserName(info.username)
                    .setUserId(UUIDTypeAdapter.fromUUID(ua.getSelectedProfile().id));
        } else {
            String usr = info.username;
            if (info.username == null || !info.username.contains("@"))
                throw new AuthenticationException(C.i18n("login.not_email"));
            String pwd = info.password;

            if (!ua.isLoggedIn())
                ua.setPassword(pwd);
            ua.setUserName(usr);
            ua.logIn();
            if (!ua.isLoggedIn())
                throw new AuthenticationException(C.i18n("login.wrong_password"));
            GameProfile selectedProfile = ua.getSelectedProfile();
            GameProfile[] profiles = ua.getAvailableProfiles();
            String username;
            if (selectedProfile == null)
                if (ArrayUtils.isNotEmpty(profiles)) {
                    String[] names = new String[profiles.length];
                    for (int i = 0; i < profiles.length; i++)
                        names[i] = profiles[i].name;
                    int sel = SwingUtils.select(names, C.i18n("login.choose_charactor"));
                    if (sel == -1)
                        throw new AuthenticationException("No selection");
                    selectedProfile = profiles[sel];
                    username = names[sel];
                } else
                    username = JOptionPane.showInputDialog(C.i18n("login.no_charactor"));
            else
                username = selectedProfile.name;
            if (username == null)
                throw new AuthenticationException("No player");
            result.setUserName(username)
                    .setUserId(selectedProfile == null ? OfflineAuthenticator.getUUIDFromUserName(username) : UUIDTypeAdapter.fromUUID(selectedProfile.id));
        }
        return result.setUserType("mojang")
                .setUserProperties(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.LegacySerializer()).create().toJson(ua.getUserProperties()))
                .setUserPropertyMap(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create().toJson(ua.getUserProperties()))
                .setAccessToken(ua.getAuthenticatedToken())
                .setSession(ua.getAuthenticatedToken());
    }

    @Override
    public boolean isLoggedIn() {
        return ua.isLoggedIn();
    }

    @Override
    public String id() {
        return "yggdrasil";
    }

    @Override
    public String getName() {
        return C.i18n("login.methods.yggdrasil");
    }

    @Override
    public Map onSaveSettings() {
        Map m = ua.saveForStorage();
        m.putAll(super.onSaveSettings());
        return m;
    }

    @Override
    public void onLoadSettings(Map settings) {
        super.onLoadSettings(settings);
        if (settings == null)
            return;
        ua.loadFromStorage(settings);
    }

    @Override
    public UserProfileProvider loginBySettings() throws AuthenticationException {
        UserProfileProvider result = new UserProfileProvider();
        ua.logIn();
        if (!ua.isLoggedIn())
            throw new AuthenticationException(C.i18n("login.wrong_password"));
        GameProfile profile = ua.getSelectedProfile();
        result.setUserName(profile.name);
        result.setUserId(profile.id.toString());
        result.setUserProperties(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.LegacySerializer()).create().toJson(ua.getUserProperties()));
        result.setUserPropertyMap(new GsonBuilder().registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer()).create().toJson(ua.getUserProperties()));
        result.setAccessToken(ua.getAuthenticatedToken());
        result.setSession(ua.getAuthenticatedToken());
        return result;
    }

    @Override
    public void logOut() {
        ua.logOut();
    }
}
