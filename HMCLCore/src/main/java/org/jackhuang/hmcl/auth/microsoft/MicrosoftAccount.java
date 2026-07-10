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

import com.google.gson.JsonObject;
import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.friend.EnumUpdateType;
import org.jackhuang.hmcl.game.friend.FriendControl;
import org.jackhuang.hmcl.game.friend.FriendResponse;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MicrosoftAccount extends OAuthAccount implements FriendControl {

    protected final MicrosoftService service;
    protected UUID profileID;

    private boolean authenticated = false;
    private MicrosoftSession session;

    protected MicrosoftAccount(AccountID accountID, MicrosoftService service, MicrosoftSession session) {
        super(accountID);
        this.service = requireNonNull(service);
        this.session = requireNonNull(session);
        this.profileID = requireNonNull(session.profile().id());
    }

    protected MicrosoftAccount(MicrosoftService service, OAuth.GrantFlow flow) throws AuthenticationException {
        super(AccountID.generate());
        this.service = requireNonNull(service);

        MicrosoftSession acquiredSession = service.authenticate(flow);
        if (acquiredSession.profile() == null) {
            session = service.refresh(acquiredSession);
        } else {
            session = acquiredSession;
        }

        profileID = session.profile().id();
        authenticated = true;
    }

    @Override
    public String getProfileName() {
        return session.profile().name();
    }

    @Override
    public UUID getProfileID() {
        return session.profile().id();
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        if (!authenticated || !session.hasProfileName() || System.currentTimeMillis() > session.notAfter()) {
            if (session.hasProfileName()
                    && service.validate(session.notAfter(), session.tokenType(), session.accessToken())) {
                authenticated = true;
            } else {
                MicrosoftSession acquiredSession = service.refresh(session);
                if (!Objects.equals(acquiredSession.profile().id(), session.profile().id())) {
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
        if (!Objects.equals(profileID, acquiredSession.profile().id())) {
            throw new WrongAccountException(profileID, acquiredSession.profile().id());
        }

        if (acquiredSession.profile() == null) {
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
        service.uploadSkin(session.accessToken(), isSlim, file);
    }

    @Override
    public void writeMetadata(JsonObject metadata) {
        super.writeMetadata(metadata);
        metadata.addProperty("profileID", getProfileID().toString());
    }

    @Override
    public void writePrivateData(JsonObject privateData) {
        super.writePrivateData(privateData);
        session.writePrivateData(privateData);
    }

    public MicrosoftService getService() {
        return service;
    }

    @Override
    public @NotNull ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
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
    public FriendResponse getFriendList() throws IOException {
        return service.getFriendList(session.accessToken());
    }

    @Override
    public void updateFriend(@Nullable String name, @Nullable String uuid, EnumUpdateType updateType) throws IOException {
        service.updateFriend(session.accessToken(), name, uuid, updateType);
    }

    @Override
    public String toString() {
        return "MicrosoftAccount[accountID=" + getAccountID() + ", profileID=" + profileID
                + ", name=" + getProfileName() + "]";
    }
}
