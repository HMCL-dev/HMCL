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
package org.jackhuang.hmcl.core.auth.yggdrasil;

import org.jackhuang.hmcl.api.auth.AuthenticationException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jackhuang.hmcl.util.ArrayUtils;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.net.NetUtils;
import org.jackhuang.hmcl.util.StrUtils;

public class YggdrasilAuthentication {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, new GameProfile.GameProfileSerializer())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter()).create();

    protected static final String BASE_URL = "https://authserver.mojang.com/";
    protected static final URL ROUTE_AUTHENTICATE = NetUtils.constantURL(BASE_URL + "authenticate");
    protected static final URL ROUTE_REFRESH = NetUtils.constantURL(BASE_URL + "refresh");
    protected static final URL ROUTE_VALIDATE = NetUtils.constantURL(BASE_URL + "validate");

    protected static final String STORAGE_KEY_ACCESS_TOKEN = "accessToken";
    protected static final String STORAGE_KEY_PROFILE_NAME = "displayName";
    protected static final String STORAGE_KEY_PROFILE_ID = "uuid";
    protected static final String STORAGE_KEY_PROFILE_PROPERTIES = "profileProperties";
    protected static final String STORAGE_KEY_USER_NAME = "username";
    protected static final String STORAGE_KEY_USER_ID = "userid";
    protected static final String STORAGE_KEY_USER_PROPERTIES = "userProperties";

    private final Proxy proxy;
    private final String clientToken;
    private final PropertyMap userProperties = new PropertyMap();

    private String userid, username, password, accessToken;
    private UserType userType;
    private GameProfile selectedProfile;
    private GameProfile[] profiles;
    private boolean isOnline;

    public YggdrasilAuthentication(Proxy proxy, String clientToken) {
        this.proxy = proxy;
        this.clientToken = clientToken;
    }

    // <editor-fold defaultstate="collapsed" desc="Get/Set">
    public void setUserName(String username) {
        if ((isLoggedIn()) && (canPlayOnline()))
            throw new IllegalStateException("Cannot change username while logged in & online");

        this.username = username;
    }

    public void setPassword(String password) {
        if ((isLoggedIn()) && (canPlayOnline()) && (StrUtils.isNotBlank(password)))
            throw new IllegalStateException("Cannot set password while logged in & online");

        this.password = password;
    }

    public GameProfile getSelectedProfile() {
        return this.selectedProfile;
    }

    public String getUserId() {
        return this.userid;
    }

    public PropertyMap getUserProperties() {
        if (isLoggedIn())
            return (PropertyMap) userProperties.clone();
        return new PropertyMap();
    }

    public GameProfile[] getAvailableProfiles() {
        if (profiles == null)
            return null;
        else
            return profiles.clone();
    }

    public String getAuthenticatedToken() {
        return this.accessToken;
    }

    public String getClientToken() {
        return clientToken;
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Log In/Out">
    public boolean canPlayOnline() {
        return isLoggedIn() && getSelectedProfile() != null && this.isOnline;
    }

    public boolean canLogIn() {
        return !canPlayOnline() && StrUtils.isNotBlank(username) && (StrUtils.isNotBlank(password) || StrUtils.isNotBlank(getAuthenticatedToken()));
    }

    public boolean isLoggedIn() {
        return StrUtils.isNotBlank(this.accessToken);
    }

    public void logIn() throws AuthenticationException {
        if (StrUtils.isBlank(username))
            throw new AuthenticationException(C.i18n("login.invalid_username"));

        if (StrUtils.isNotBlank(getAuthenticatedToken())) {
            if (StrUtils.isBlank(getUserId()))
                if (StrUtils.isNotBlank(username))
                    userid = username;
                else
                    throw new AuthenticationException(C.i18n("login.invalid_uuid_and_username"));
            if (checkTokenValidity()) {
                isOnline = true;
                return;
            }
            logInImpl(ROUTE_REFRESH, new RefreshRequest(getAuthenticatedToken(), clientToken));
        } else if (StrUtils.isNotBlank(password))
            logInImpl(ROUTE_AUTHENTICATE, new AuthenticationRequest(username, password, clientToken));
        else
            throw new AuthenticationException(C.i18n("login.invalid_password"));
    }

    private void logInImpl(URL url, Object input) throws AuthenticationException {
        Response response = makeRequest(url, input, Response.class);

        if (!clientToken.equals(response.clientToken))
            throw new AuthenticationException(C.i18n("login.changed_client_token"));

        if (response.selectedProfile != null)
            userType = UserType.byLegacy(response.selectedProfile.isLegacy());
        else if (ArrayUtils.isNotEmpty(response.availableProfiles))
            userType = UserType.byLegacy(response.availableProfiles[0].isLegacy());

        User user = response.user;
        userid = user != null && user.getId() != null ? user.getId() : username;

        isOnline = true;
        profiles = response.availableProfiles;
        selectedProfile = response.selectedProfile;
        userProperties.clear();
        this.accessToken = response.accessToken;

        if (user != null && user.getProperties() != null)
            userProperties.putAll(user.getProperties());
    }

    protected <T extends Response> T makeRequest(URL url, Object input, Class<T> clazz)
            throws AuthenticationException {
        try {
            String jsonResult = input == null ? NetUtils.get(url, proxy) : NetUtils.post(url, GSON.toJson(input), "application/json", proxy);
            T response = (T) GSON.fromJson(jsonResult, clazz);
            if (response == null)
                return null;

            if (StrUtils.isNotBlank(response.error)) {
                HMCLog.err("Failed to log in, the auth server returned an error: " + response.error + ", message: " + response.errorMessage + ", cause: " + response.cause);
                if (response.errorMessage != null && response.errorMessage.contains("Invalid token"))
                    response.errorMessage = C.i18n("login.invalid_token");
                throw new AuthenticationException("Request error: " + response.errorMessage);
            }

            return response;
        } catch (IOException | IllegalStateException | JsonParseException e) {
            throw new AuthenticationException(C.i18n("login.failed.connect_authentication_server"), e);
        }
    }

    protected boolean checkTokenValidity() {
        ValidateRequest request = new ValidateRequest(this);
        try {
            makeRequest(ROUTE_VALIDATE, request, Response.class);
            return true;
        } catch (AuthenticationException ex) {
            return false;
        }
    }

    public void logOut() {
        password = null;
        userid = null;
        selectedProfile = null;
        userProperties.clear();

        accessToken = null;
        profiles = null;
        isOnline = false;
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Settings Storage">
    public void loadFromStorage(Map<String, Object> credentials) {
        logOut();

        try {
            setUserName((String) credentials.get(STORAGE_KEY_USER_NAME));

            if (credentials.containsKey(STORAGE_KEY_USER_ID))
                userid = (String) credentials.get(STORAGE_KEY_USER_ID);
            else
                userid = username;

            if (credentials.containsKey(STORAGE_KEY_USER_PROPERTIES))
                userProperties.fromList((List<Map<String, String>>) credentials.get(STORAGE_KEY_USER_PROPERTIES));

            if ((credentials.containsKey(STORAGE_KEY_PROFILE_NAME)) && (credentials.containsKey(STORAGE_KEY_PROFILE_ID))) {
                GameProfile profile = new GameProfile(UUIDTypeAdapter.fromString((String) credentials.get(STORAGE_KEY_PROFILE_ID)), (String) credentials.get(STORAGE_KEY_PROFILE_NAME));
                if (credentials.containsKey(STORAGE_KEY_PROFILE_PROPERTIES))
                    profile.properties.fromList((List<Map<String, String>>) credentials.get(STORAGE_KEY_PROFILE_PROPERTIES));
                selectedProfile = profile;
            }

            this.accessToken = (String) credentials.get(STORAGE_KEY_ACCESS_TOKEN);
        } catch (Exception e) {
            HMCLog.err("Failed to load yggdrasil authenticator settings, maybe its format is malformed.", e);

            logOut();
        }
    }

    public Map<String, Object> saveForStorage() {
        Map<String, Object> result = new HashMap<>();

        if (username != null)
            result.put(STORAGE_KEY_USER_NAME, username);
        if (getUserId() != null)
            result.put(STORAGE_KEY_USER_ID, getUserId());

        if (!getUserProperties().isEmpty())
            result.put(STORAGE_KEY_USER_PROPERTIES, getUserProperties().list());

        GameProfile sel = getSelectedProfile();
        if (sel != null) {
            result.put(STORAGE_KEY_PROFILE_NAME, sel.name);
            result.put(STORAGE_KEY_PROFILE_ID, sel.id);
            if (!sel.properties.isEmpty())
                result.put(STORAGE_KEY_PROFILE_PROPERTIES, sel.properties.list());
        }

        if (StrUtils.isNotBlank(getAuthenticatedToken()))
            result.put(STORAGE_KEY_ACCESS_TOKEN, getAuthenticatedToken());

        return result;
    }

    // </editor-fold>
}
