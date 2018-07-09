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
    private UUID characterUUID;

    protected YggdrasilAccount(YggdrasilService service, String username, UUID characterUUID, YggdrasilSession session) {
        this.service = service;
        this.username = username;
        this.session = session;
        this.characterUUID = characterUUID;

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
            if (service.validate(session.getAccessToken(), session.getClientToken())) {
                isOnline = true;
            } else {
                try {
                    updateSession(service.refresh(session.getAccessToken(), session.getClientToken(), null), new SpecificCharacterSelector(characterUUID));
                } catch (RemoteAuthenticationException e) {
                    if ("ForbiddenOperationException".equals(e.getRemoteName())) {
                        throw new CredentialExpiredException(e);
                    } else {
                        throw e;
                    }
                }
            }
        }
        return session.toAuthInfo();
    }

    @Override
    public AuthInfo logInWithPassword(String password) throws AuthenticationException {
        return logInWithPassword(password, new SpecificCharacterSelector(characterUUID));
    }

    protected AuthInfo logInWithPassword(String password, CharacterSelector selector) throws AuthenticationException {
        updateSession(service.authenticate(username, password, UUIDTypeAdapter.fromUUID(UUID.randomUUID())), selector);
        return session.toAuthInfo();
    }

    /**
     * Updates the current session. This method shall be invoked after authenticate/refresh operation.
     * {@link #session} field shall be set only using this method. This method ensures {@link #session}
     * has a profile selected.
     *
     * @param acquiredSession the session acquired by making an authenticate/refresh request
     */
    private void updateSession(YggdrasilSession acquiredSession, CharacterSelector selector) throws AuthenticationException {
        if (acquiredSession.getSelectedProfile() == null) {
            if (acquiredSession.getAvailableProfiles() == null || acquiredSession.getAvailableProfiles().length == 0)
                throw new NoCharacterException(this);

            this.session = service.refresh(
                    acquiredSession.getAccessToken(),
                    acquiredSession.getClientToken(),
                    selector.select(this, Arrays.asList(acquiredSession.getAvailableProfiles())));
        } else {
            this.session = acquiredSession;
        }

        this.characterUUID = this.session.getSelectedProfile().getId();
        invalidate();
    }

    @Override
    public Optional<AuthInfo> playOffline() {
        if (isLoggedIn() && session.getSelectedProfile() != null && !canPlayOnline())
            return Optional.of(session.toAuthInfo());

        return Optional.empty();
    }

    @Override
    public Map<Object, Object> toStorage() {
        if (session == null)
            throw new IllegalStateException("No session is specified");

        HashMap<Object, Object> storage = new HashMap<>();
        storage.put("username", getUsername());
        storage.putAll(session.toStorage());
        return storage;
    }

    @Override
    public UUID getUUID() {
        if (session == null || session.getSelectedProfile() == null)
            return null;
        else
            return session.getSelectedProfile().getId();
    }

    public Optional<Texture> getSkin() throws AuthenticationException {
        return getSkin(session.getSelectedProfile());
    }

    public Optional<Texture> getSkin(GameProfile profile) throws AuthenticationException {
        if (!service.getTextures(profile).isPresent()) {
            profile = service.getCompleteGameProfile(profile.getId()).orElse(profile);
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
