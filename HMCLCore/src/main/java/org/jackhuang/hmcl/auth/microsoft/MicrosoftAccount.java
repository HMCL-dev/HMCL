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
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MicrosoftAccount extends OAuthAccount {

    protected final MicrosoftService service;
    protected UUID profileID;

    private boolean authenticated = false;
    private MicrosoftSession session;

    protected MicrosoftAccount(AccountID accountID, MicrosoftService service, MicrosoftSession session) {
        super(accountID);
        this.service = requireNonNull(service);
        this.session = requireNonNull(session);
        this.profileID = requireNonNull(session.getProfile().getId());
    }

    protected MicrosoftAccount(MicrosoftService service, OAuth.GrantFlow flow) throws AuthenticationException {
        super(AccountID.generate());
        this.service = requireNonNull(service);

        MicrosoftSession acquiredSession = service.authenticate(flow);
        if (acquiredSession.getProfile() == null) {
            session = service.refresh(acquiredSession);
        } else {
            session = acquiredSession;
        }

        profileID = session.getProfile().getId();
        authenticated = true;
    }

    @Override
    public String getProfileName() {
        return session.getProfile().getName();
    }

    @Override
    public UUID getProfileID() {
        return session.getProfile().getId();
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        if (!authenticated || !session.hasProfileName() || System.currentTimeMillis() > session.getNotAfter()) {
            if (session.hasProfileName()
                    && service.validate(session.getNotAfter(), session.getTokenType(), session.getAccessToken())) {
                authenticated = true;
            } else {
                MicrosoftSession acquiredSession = service.refresh(session);
                if (!Objects.equals(acquiredSession.getProfile().getId(), session.getProfile().getId())) {
                    throw new ServerResponseMalformedException("Selected profile changed");
                }
                if (!acquiredSession.hasProfileName()) {
                    throw new ServerResponseMalformedException("Profile name is missing");
                }

                session = acquiredSession;

                authenticated = true;
                invalidate();
            }
        }

        return session.toAuthInfo();
    }

    @Override
    public AuthInfo logInWhenCredentialsExpired() throws AuthenticationException {
        MicrosoftSession acquiredSession = service.authenticate(OAuth.GrantFlow.DEVICE);
        if (!Objects.equals(profileID, acquiredSession.getProfile().getId())) {
            throw new WrongAccountException(profileID, acquiredSession.getProfile().getId());
        }

        if (acquiredSession.getProfile() == null) {
            session = service.refresh(acquiredSession);
        } else {
            session = acquiredSession;
        }

        authenticated = true;
        invalidate();
        return session.toAuthInfo();
    }

    @Override
    public AuthInfo playOffline() throws AuthenticationException {
        if (!session.hasProfileName()) {
            throw new CredentialExpiredException("Profile name is missing");
        }

        return session.toAuthInfo();
    }

    @Override
    public boolean canUploadSkin() {
        return true;
    }

    @Override
    public void uploadSkin(boolean isSlim, Path file) throws AuthenticationException, UnsupportedOperationException {
        service.uploadSkin(session.getAccessToken(), isSlim, file);
    }

    @Override
    public Map<Object, Object> toStorage() {
        Map<Object, Object> storage = session.toStorage();
        addAccountID(storage);
        return storage;
    }

    public MicrosoftService getService() {
        return service;
    }

    @Override
    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return BindingMapping.of(service.getProfileRepository().binding(getProfileID()))
                .map(profile -> profile.flatMap(it -> {
                    try {
                        return YggdrasilService.getTextures(it);
                    } catch (ServerResponseMalformedException e) {
                        LOG.warning("Failed to parse texture payload", e);
                        return Optional.empty();
                    }
                }));
    }

    @Override
    public void clearCache() {
        authenticated = false;
        service.getProfileRepository().invalidate(profileID);
    }

    @Override
    public String toString() {
        return "MicrosoftAccount[accountID=" + getAccountID() + ", profileID=" + profileID
                + ", name=" + getProfileName() + "]";
    }
}
