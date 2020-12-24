/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.microsoft;

import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class MicrosoftAccount extends Account {

    protected final MicrosoftService service;
    protected UUID characterUUID;

    private boolean authenticated = false;
    private MicrosoftSession session;

    protected MicrosoftAccount(MicrosoftService service, MicrosoftSession session) {
        this.service = requireNonNull(service);
        this.session = requireNonNull(session);
        this.characterUUID = requireNonNull(session.getProfile().getId());
    }

    protected MicrosoftAccount(MicrosoftService service, CharacterSelector characterSelector) throws AuthenticationException {
        this.service = requireNonNull(service);

        MicrosoftSession acquiredSession = service.authenticate();
        if (acquiredSession.getProfile() == null) {
            session = service.refresh(acquiredSession);
        } else {
            session = acquiredSession;
        }

        characterUUID = session.getProfile().getId();
        authenticated = true;
    }

    @Override
    public String getUsername() {
        // TODO: email of Microsoft account is blocked by oauth.
        return "";
    }

    @Override
    public String getCharacter() {
        return session.getProfile().getName();
    }

    @Override
    public UUID getUUID() {
        return session.getProfile().getId();
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        if (!authenticated) {
            if (service.validate(session.getTokenType(), session.getAccessToken())) {
                authenticated = true;
            } else {
                MicrosoftSession acquiredSession = service.authenticate();
                if (acquiredSession.getProfile() == null) {
                    session = service.refresh(acquiredSession);
                } else {
                    session = acquiredSession;
                }

                characterUUID = session.getProfile().getId();
                authenticated = true;
            }
        }

        return session.toAuthInfo();
    }

    @Override
    public AuthInfo logInWithPassword(String password) throws AuthenticationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AuthInfo> playOffline() {
        return Optional.of(session.toAuthInfo());
    }

    @Override
    public Map<Object, Object> toStorage() {
        return session.toStorage();
    }

    public MicrosoftService getService() {
        return service;
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return BindingMapping.of(service.getProfileRepository().binding(session.getAuthorization()))
                .map(profile -> profile.flatMap(MicrosoftService::getTextures));

    }

    @Override
    public void clearCache() {
        authenticated = false;
    }

    @Override
    public String toString() {
        return "MicrosoftAccount[uuid=" + characterUUID + ", name=" + getCharacter() + "]";
    }

    @Override
    public int hashCode() {
        return characterUUID.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MicrosoftAccount that = (MicrosoftAccount) o;
        return characterUUID.equals(that.characterUUID);
    }
}
