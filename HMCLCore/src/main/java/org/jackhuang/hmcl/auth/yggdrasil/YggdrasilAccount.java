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
package org.jackhuang.hmcl.auth.yggdrasil;

import javafx.beans.binding.ObjectBinding;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.nio.file.Path;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class YggdrasilAccount extends ClassicAccount {

    protected final YggdrasilService service;
    protected final UUID profileID;
    protected final String loginName;

    private boolean authenticated = false;
    private YggdrasilSession session;

    protected YggdrasilAccount(AccountID accountID, YggdrasilService service, String loginName, YggdrasilSession session) {
        super(accountID);
        this.service = requireNonNull(service);
        this.loginName = requireNonNull(loginName);
        this.profileID = requireNonNull(session.getSelectedProfile().getId());
        this.session = requireNonNull(session);

        addProfilePropertiesListener();
    }

    protected YggdrasilAccount(YggdrasilService service, String loginName, String password, CharacterSelector selector) throws AuthenticationException {
        super(AccountID.generate());
        this.service = requireNonNull(service);
        this.loginName = requireNonNull(loginName);

        YggdrasilSession acquiredSession = service.authenticate(loginName, password, randomClientToken());
        if (acquiredSession.getSelectedProfile() == null) {
            if (acquiredSession.getAvailableProfiles() == null || acquiredSession.getAvailableProfiles().isEmpty()) {
                throw new NoCharacterException();
            }

            GameProfile characterToSelect = selector.select(service, acquiredSession.getAvailableProfiles());

            session = service.refresh(
                    acquiredSession.getAccessToken(),
                    acquiredSession.getClientToken(),
                    characterToSelect);
            // response validity has been checked in refresh()
        } else {
            session = acquiredSession;
        }

        profileID = session.getSelectedProfile().getId();
        authenticated = true;

        addProfilePropertiesListener();
    }

    private ObjectBinding<Optional<CompleteGameProfile>> profilePropertiesBinding;
    private void addProfilePropertiesListener() {
        // binding() is thread-safe
        // hold the binding so that it won't be garbage-collected
        profilePropertiesBinding = service.getProfileRepository().binding(profileID, true);
        // and it's safe to add a listener to an ObjectBinding which does not have any listener attached before (maybe tricky)
        profilePropertiesBinding.addListener((a, b, c) -> this.invalidate());
    }

    @Override
    public String getLoginName() {
        return loginName;
    }

    @Override
    public String getProfileName() {
        return session.getSelectedProfile().getName();
    }

    @Override
    public UUID getProfileID() {
        return session.getSelectedProfile().getId();
    }

    @Override
    public synchronized AuthInfo logIn() throws AuthenticationException {
        if (!authenticated || !session.hasProfileName()) {
            if (session.hasProfileName() && service.validate(session.getAccessToken(), session.getClientToken())) {
                authenticated = true;
            } else {
                YggdrasilSession acquiredSession;
                try {
                    acquiredSession = service.refresh(session.getAccessToken(), session.getClientToken(), null);
                } catch (RemoteAuthenticationException e) {
                    if ("ForbiddenOperationException".equals(e.getRemoteName())) {
                        throw new CredentialExpiredException(e);
                    } else {
                        throw e;
                    }
                }
                if (acquiredSession.getSelectedProfile() == null ||
                        !acquiredSession.getSelectedProfile().getId().equals(profileID)) {
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
    public synchronized AuthInfo logInWithPassword(String password) throws AuthenticationException {
        YggdrasilSession acquiredSession = service.authenticate(loginName, password, randomClientToken());

        if (acquiredSession.getSelectedProfile() == null) {
            if (acquiredSession.getAvailableProfiles() == null || acquiredSession.getAvailableProfiles().isEmpty()) {
                throw new CharacterDeletedException();
            }

            GameProfile characterToSelect = acquiredSession.getAvailableProfiles().stream()
                    .filter(charatcer -> charatcer.getId().equals(profileID))
                    .findFirst()
                    .orElseThrow(CharacterDeletedException::new);

            session = service.refresh(
                    acquiredSession.getAccessToken(),
                    acquiredSession.getClientToken(),
                    characterToSelect);

        } else {
            if (!acquiredSession.getSelectedProfile().getId().equals(profileID)) {
                throw new CharacterDeletedException();
            }
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
    protected void writeMetadata(Map<Object, Object> metadata) {
        metadata.put("loginName", loginName);
        metadata.put("profileID", UUIDTypeAdapter.fromUUID(profileID));
    }

    @Override
    public Map<Object, Object> toPrivateData() {
        Map<Object, Object> privateData = new HashMap<>(session.toPrivateData());
        service.getProfileRepository().getImmediately(profileID).ifPresent(profile ->
                privateData.put("profileProperties", profile.getProperties()));
        return privateData;
    }

    public YggdrasilService getYggdrasilService() {
        return service;
    }

    @Override
    public void clearCache() {
        authenticated = false;
        service.getProfileRepository().invalidate(profileID);
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
    public boolean canUploadSkin() {
        return true;
    }

    @Override
    public void uploadSkin(boolean isSlim, Path file) throws AuthenticationException, UnsupportedOperationException {
        service.uploadSkin(profileID, session.getAccessToken(), isSlim, file);
    }

    private static String randomClientToken() {
        return UUIDTypeAdapter.fromUUID(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return "YggdrasilAccount[accountID=" + getAccountID() + ", profileID=" + profileID
                + ", loginName=" + loginName + "]";
    }
}
