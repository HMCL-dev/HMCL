/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.auth.yggdrasil;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterDeletedException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.CredentialExpiredException;
import org.jackhuang.hmcl.auth.NoCharacterException;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

public class YggdrasilAccount extends Account {

    private final YggdrasilService service;
    private final UUID characterUUID;
    private final String username;

    private boolean authenticated = false;
    private YggdrasilSession session;

    protected YggdrasilAccount(YggdrasilService service, String username, YggdrasilSession session) {
        this.service = requireNonNull(service);
        this.username = requireNonNull(username);
        this.characterUUID = requireNonNull(session.getSelectedProfile().getId());
        this.session = requireNonNull(session);
    }

    protected YggdrasilAccount(YggdrasilService service, String username, String password, CharacterSelector selector) throws AuthenticationException {
        this.service = requireNonNull(service);
        this.username = requireNonNull(username);

        YggdrasilSession acquiredSession = service.authenticate(username, password, randomClientToken());
        if (acquiredSession.getSelectedProfile() == null) {
            if (acquiredSession.getAvailableProfiles() == null || acquiredSession.getAvailableProfiles().isEmpty()) {
                throw new NoCharacterException();
            }

            GameProfile characterToSelect = selector.select(service, acquiredSession.getAvailableProfiles());

            session = service.refresh(
                    acquiredSession.getAccessToken(),
                    acquiredSession.getClientToken(),
                    characterToSelect);
        } else {
            session = acquiredSession;
        }

        characterUUID = session.getSelectedProfile().getId();
        authenticated = true;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getCharacter() {
        return session.getSelectedProfile().getName();
    }

    @Override
    public UUID getUUID() {
        return session.getSelectedProfile().getId();
    }

    @Override
    public synchronized AuthInfo logIn() throws AuthenticationException {
        if (!authenticated) {
            if (service.validate(session.getAccessToken(), session.getClientToken())) {
                authenticated = true;
            } else {
                try {
                    session = service.refresh(session.getAccessToken(), session.getClientToken(), null);
                } catch (RemoteAuthenticationException e) {
                    if ("ForbiddenOperationException".equals(e.getRemoteName())) {
                        throw new CredentialExpiredException(e);
                    } else {
                        throw e;
                    }
                }

                authenticated = true;
                invalidate();
            }
        }

        return session.toAuthInfo();
    }

    @Override
    public synchronized AuthInfo logInWithPassword(String password) throws AuthenticationException {
        YggdrasilSession acquiredSession = service.authenticate(username, password, randomClientToken());

        if (acquiredSession.getSelectedProfile() == null) {
            if (acquiredSession.getAvailableProfiles() == null || acquiredSession.getAvailableProfiles().isEmpty()) {
                throw new CharacterDeletedException();
            }

            GameProfile characterToSelect = acquiredSession.getAvailableProfiles().stream()
                    .filter(charatcer -> charatcer.getId().equals(characterUUID))
                    .findFirst()
                    .orElseThrow(CharacterDeletedException::new);

            session = service.refresh(
                    acquiredSession.getAccessToken(),
                    acquiredSession.getClientToken(),
                    characterToSelect);

        } else {
            if (!acquiredSession.getSelectedProfile().getId().equals(characterUUID)) {
                throw new CharacterDeletedException();
            }
            session = acquiredSession;
        }

        authenticated = true;
        invalidate();
        return session.toAuthInfo();
    }

    @Override
    public Optional<AuthInfo> playOffline() {
        return Optional.of(session.toAuthInfo());
    }

    @Override
    public Map<Object, Object> toStorage() {
        Map<Object, Object> storage = new HashMap<>();
        storage.put("username", username);
        storage.putAll(session.toStorage());
        service.getProfileRepository().getImmediately(characterUUID).ifPresent(profile -> {
            storage.put("profileProperties", profile.getProperties());
        });
        return storage;
    }

    public YggdrasilService getYggdrasilService() {
        return service;
    }

    @Override
    public void clearCache() {
        authenticated = false;
        service.getProfileRepository().invalidate(characterUUID);
    }

    private static String randomClientToken() {
        return UUIDTypeAdapter.fromUUID(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return "YggdrasilAccount[uuid=" + characterUUID + ", username=" + username + "]";
    }

    @Override
    public int hashCode() {
        return characterUUID.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof YggdrasilAccount))
            return false;
        YggdrasilAccount another = (YggdrasilAccount) obj;
        return characterUUID.equals(another.characterUUID);
    }
}
