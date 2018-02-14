/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.util.Charsets;
import org.jackhuang.hmcl.util.NetworkUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.*;

/**
 *
 * @author huang
 */
public final class YggdrasilAccount extends Account {

    private final String username;
    private String password;
    private String userId;
    private String accessToken = null;
    private String clientToken = randomToken();
    private boolean isOnline = false;
    private PropertyMap userProperties = new PropertyMap();
    private GameProfile selectedProfile = null;
    private GameProfile[] profiles;
    private UserType userType = UserType.LEGACY;

    public YggdrasilAccount(String username) {
        this.username = username;
    }

    @Override
    public String getUsername() {
        return username;
    }

    void setPassword(String password) {
        this.password = password;
    }

    public String getCurrentCharacterName() {
        return userId;
    }

    void setUserId(String userId) {
        this.userId = userId;
    }

    void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    String getClientToken() {
        return clientToken;
    }

    void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    PropertyMap getUserProperties() {
        return userProperties;
    }

    public GameProfile getSelectedProfile() {
        return selectedProfile;
    }

    void setSelectedProfile(GameProfile selectedProfile) {
        this.selectedProfile = selectedProfile;
    }

    public boolean isLoggedIn() {
        return StringUtils.isNotBlank(accessToken);
    }

    public boolean canPlayOnline() {
        return isLoggedIn() && selectedProfile != null && isOnline;
    }

    public boolean canLogIn() {
        return !canPlayOnline() && StringUtils.isNotBlank(username)
                && (StringUtils.isNotBlank(password) || StringUtils.isNotBlank(accessToken));
    }

    @Override
    public AuthInfo logIn(MultiCharacterSelector selector, Proxy proxy) throws AuthenticationException {
        if (canPlayOnline())
            return new AuthInfo(selectedProfile, accessToken, userType, GSON.toJson(userProperties));
        else {
            logIn0(proxy);
            if (!isLoggedIn())
                throw new AuthenticationException("Wrong password for account " + username);

            if (selectedProfile == null) {
                if (profiles == null || profiles.length <= 0)
                    throw new NoCharacterException(this);

                selectedProfile = selector.select(this, Arrays.asList(profiles));
            }
            return new AuthInfo(selectedProfile, accessToken, userType, GSON.toJson(userProperties));
        }
    }

    private void logIn0(Proxy proxy) throws AuthenticationException {
        if (StringUtils.isNotBlank(accessToken)) {
            if (StringUtils.isBlank(userId))
                if (StringUtils.isNotBlank(username))
                    userId = username;
                else
                    throw new AuthenticationException("Invalid uuid and username");
            if (checkTokenValidity(proxy)) {
                isOnline = true;
                return;
            }
            logIn1(ROUTE_REFRESH, new RefreshRequest(accessToken, clientToken), proxy);
        } else if (StringUtils.isNotBlank(password))
            logIn1(ROUTE_AUTHENTICATE, new AuthenticationRequest(username, password, clientToken), proxy);
        else
            throw new AuthenticationException("Password cannot be blank");
    }

    private void logIn1(URL url, Object input, Proxy proxy) throws AuthenticationException {
        AuthenticationResponse response = makeRequest(url, input, proxy)
                .orElseThrow(() -> new AuthenticationException("Server response empty"));

        if (!clientToken.equals(response.getClientToken()))
            throw new AuthenticationException("Client token changed");

        if (response.getSelectedProfile() != null)
            userType = UserType.fromLegacy(response.getSelectedProfile().isLegacy());
        else if (response.getAvailableProfiles() != null && response.getAvailableProfiles().length > 0)
            userType = UserType.fromLegacy(response.getAvailableProfiles()[0].isLegacy());

        User user = response.getUser();
        if (user == null || user.getId() == null)
            userId = null;
        else
            userId = user.getId();

        isOnline = true;
        profiles = response.getAvailableProfiles();
        selectedProfile = response.getSelectedProfile();
        userProperties.clear();
        accessToken = response.getAccessToken();

        if (user != null && user.getProperties() != null)
            userProperties.putAll(user.getProperties());
    }

    @Override
    public boolean canPlayOffline() {
        return isLoggedIn() && getSelectedProfile() != null && !canPlayOnline();
    }

    @Override
    public AuthInfo playOffline() {
        if (!canPlayOffline())
            throw new IllegalStateException("Current account " + this + " cannot play offline.");

        return new AuthInfo(selectedProfile, accessToken, userType, GSON.toJson(userProperties));
    }

    @Override
    public void logOut() {
        password = null;
        userId = null;
        accessToken = null;
        isOnline = false;
        userProperties.clear();
        profiles = null;
        selectedProfile = null;
    }

    @Override
    public Map<Object, Object> toStorageImpl() {
        HashMap<Object, Object> result = new HashMap<>();

        result.put(STORAGE_KEY_USER_NAME, getUsername());
        result.put(STORAGE_KEY_CLIENT_TOKEN, getClientToken());
        if (getCurrentCharacterName() != null)
            result.put(STORAGE_KEY_USER_ID, getCurrentCharacterName());
        if (!userProperties.isEmpty())
            result.put(STORAGE_KEY_USER_PROPERTIES, userProperties.toList());
        GameProfile profile = selectedProfile;
        if (profile != null && profile.getName() != null && profile.getId() != null) {
            result.put(STORAGE_KEY_PROFILE_NAME, profile.getName());
            result.put(STORAGE_KEY_PROFILE_ID, profile.getId());

            if (!profile.getProperties().isEmpty())
                result.put(STORAGE_KEY_PROFILE_PROPERTIES, profile.getProperties().toList());
        }

        if (StringUtils.isNotBlank(accessToken))
            result.put(STORAGE_KEY_ACCESS_TOKEN, accessToken);

        return result;
    }

    private Optional<AuthenticationResponse> makeRequest(URL url, Object input, Proxy proxy) throws AuthenticationException {
        try {
            String jsonResult = input == null ? NetworkUtils.doGet(url, proxy) : NetworkUtils.doPost(url, GSON.toJson(input), "application/json", proxy);
            AuthenticationResponse response = GSON.fromJson(jsonResult, AuthenticationResponse.class);
            if (response == null)
                return Optional.empty();
            if (!StringUtils.isBlank(response.getError())) {
                if (response.getErrorMessage() != null)
                    if (response.getErrorMessage().contains("Invalid credentials"))
                        throw new InvalidCredentialsException(this);
                    else if (response.getErrorMessage().contains("Invalid token"))
                        throw new InvalidTokenException(this);
                    else if (response.getErrorMessage().contains("Invalid username or password"))
                        throw new InvalidPasswordException(this);
                throw new AuthenticationException(response.getError() + ": " + response.getErrorMessage());
            }

            return Optional.of(response);
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        } catch (JsonParseException e) {
            throw new AuthenticationException("Unable to parse server response", e);
        }
    }

    private boolean checkTokenValidity(Proxy proxy) {
        if (accessToken == null)
            return false;

        try {
            makeRequest(ROUTE_VALIDATE, new ValidateRequest(accessToken, clientToken), proxy);
            return true;
        } catch (AuthenticationException e) {
            return false;
        }
    }

    public UUID getUUID() {
        if (getSelectedProfile() == null)
            return null;
        else
            return getSelectedProfile().getId();
    }

    public Optional<ProfileTexture> getSkin(GameProfile profile) throws IOException, JsonParseException {
        if (StringUtils.isBlank(userId))
            throw new IllegalStateException("Not logged in");

        ProfileResponse response = GSON.fromJson(NetworkUtils.doGet(NetworkUtils.toURL(BASE_PROFILE + UUIDTypeAdapter.fromUUID(profile.getId()))), ProfileResponse.class);
        if (response.getProperties() == null) return Optional.empty();
        Property textureProperty = response.getProperties().get("textures");
        if (textureProperty == null) return Optional.empty();

        TextureResponse texture;
        String json = new String(Base64.getDecoder().decode(textureProperty.getValue()), Charsets.UTF_8);
        texture = GSON.fromJson(json, TextureResponse.class);
        if (texture == null || texture.getTextures() == null)
            return Optional.empty();

        return Optional.ofNullable(texture.getTextures().get(ProfileTexture.Type.SKIN));
    }

    @Override
    public String toString() {
        return "YggdrasilAccount[username=" + getUsername() + "]";
    }

    private static final String BASE_URL = "https://authserver.mojang.com/";
    private static final String BASE_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final URL ROUTE_AUTHENTICATE = NetworkUtils.toURL(BASE_URL + "authenticate");
    private static final URL ROUTE_REFRESH = NetworkUtils.toURL(BASE_URL + "refresh");
    private static final URL ROUTE_VALIDATE = NetworkUtils.toURL(BASE_URL + "validate");

    static final String STORAGE_KEY_ACCESS_TOKEN = "accessToken";
    static final String STORAGE_KEY_PROFILE_NAME = "displayName";
    static final String STORAGE_KEY_PROFILE_ID = "uuid";
    static final String STORAGE_KEY_PROFILE_PROPERTIES = "profileProperties";
    static final String STORAGE_KEY_USER_NAME = "username";
    static final String STORAGE_KEY_USER_ID = "userid";
    static final String STORAGE_KEY_USER_PROPERTIES = "userProperties";
    static final String STORAGE_KEY_CLIENT_TOKEN = "clientToken";

    public static String randomToken() {
        return UUIDTypeAdapter.fromUUID(UUID.randomUUID());
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, GameProfile.Serializer.INSTANCE)
            .registerTypeAdapter(PropertyMap.class, PropertyMap.Serializer.INSTANCE)
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .create();

}
