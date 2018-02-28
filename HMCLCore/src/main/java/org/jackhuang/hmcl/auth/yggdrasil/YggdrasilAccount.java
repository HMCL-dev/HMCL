/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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

import com.google.gson.GsonBuilder;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import java.util.*;

/**
 *
 * @author huangyuhui
 */
public class YggdrasilAccount extends Account {

    private final String username;
    private final YggdrasilService service;
    private boolean isOnline = false;
    private YggdrasilSession session;
    private final String clientToken;
    private String character;

    protected YggdrasilAccount(YggdrasilService service, String username, String clientToken, String character, YggdrasilSession session) {
        this.service = service;
        this.username = username;
        this.session = session;
        this.clientToken = clientToken;
        this.character = character;

        if (session == null || session.getSelectedProfile() == null || StringUtils.isBlank(session.getAccessToken()))
            this.session = null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getCharacter() {
        return session.getSelectedProfile().getName();
    }

    public boolean isLoggedIn() {
        return session != null && StringUtils.isNotBlank(session.getAccessToken());
    }

    public boolean canPlayOnline() {
        return isLoggedIn() && session.getSelectedProfile() != null && isOnline;
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        if (!canPlayOnline()) {
            logInWithToken();
            selectProfile(new SpecificCharacterSelector(character));
        }
        return toAuthInfo();
    }

    @Override
    public final AuthInfo logInWithPassword(String password) throws AuthenticationException {
        return logInWithPassword(password, new SpecificCharacterSelector(character));
    }

    protected AuthInfo logInWithPassword(String password, CharacterSelector selector) throws AuthenticationException {
        session = service.authenticate(username, password, clientToken);
        selectProfile(selector);
        return toAuthInfo();
    }

    private void selectProfile(CharacterSelector selector) throws AuthenticationException {
        if (session.getSelectedProfile() == null) {
            if (session.getAvailableProfiles() == null || session.getAvailableProfiles().length <= 0)
                throw new NoCharacterException(this);

            session.setSelectedProfile(selector.select(this, Arrays.asList(session.getAvailableProfiles())));
        }

        character = session.getSelectedProfile().getName();
    }

    private void logInWithToken() throws AuthenticationException {
        if (service.validate(session.getAccessToken(), clientToken)) {
            isOnline = true;
            return;
        }
        session = service.refresh(session.getAccessToken(), clientToken);
    }

    private AuthInfo toAuthInfo() {
        GameProfile profile = session.getSelectedProfile();

        return new AuthInfo(profile.getName(), UUIDTypeAdapter.fromUUID(profile.getId()), session.getAccessToken(), profile.getUserType(),
                new GsonBuilder().registerTypeAdapter(PropertyMap.class, PropertyMap.LegacySerializer.INSTANCE).create().toJson(Optional.ofNullable(session.getUser()).map(User::getProperties).orElseGet(PropertyMap::new)));
    }

    @Override
    public boolean canPlayOffline() {
        return isLoggedIn() && session.getSelectedProfile() != null && !canPlayOnline();
    }

    @Override
    public AuthInfo playOffline() {
        if (!canPlayOffline())
            throw new IllegalStateException("Current account " + this + " cannot play offline.");

        return toAuthInfo();
    }

    @Override
    public void logOut() {
        isOnline = false;
        session = null;
    }

    @Override
    public Map<Object, Object> toStorage() {
        HashMap<Object, Object> storage = new HashMap<>();

        storage.put("username", getUsername());
        storage.put("clientToken", clientToken);
        storage.put("character", character);
        if (session != null)
            storage.putAll(session.toStorage());

        return storage;
    }

    public UUID getUUID() {
        if (session == null)
            return null;
        else
            return session.getSelectedProfile().getId();
    }

    public Optional<Texture> getSkin() throws AuthenticationException {
        return getSkin(session.getSelectedProfile());
    }

    public Optional<Texture> getSkin(GameProfile profile) throws AuthenticationException {
        if (!service.getTextures(profile).isPresent()) {
            session.setAvailableProfile(profile = service.getCompleteGameProfile(profile.getId()));
        }

        return service.getTextures(profile).map(map -> map.get(TextureType.SKIN));
    }

    @Override
    public void clearCache() {
        Optional.ofNullable(session)
                .map(YggdrasilSession::getSelectedProfile)
                .map(GameProfile::getProperties)
                .ifPresent(it -> it.remove("textures"));
    }

    @Override
    public String toString() {
        return "YggdrasilAccount[username=" + getUsername() + "]";
    }

}
