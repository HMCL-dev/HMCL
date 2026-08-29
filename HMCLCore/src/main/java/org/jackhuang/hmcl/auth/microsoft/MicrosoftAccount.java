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
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.Nullable;

import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.List;
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
                refreshSession();
            }
        }

        return session.toAuthInfo();
    }

    /// Refreshes the Minecraft access token using the stored refresh token.
    ///
    /// This is the shared token-refresh path used both by {@link #logIn()} and
    /// by the cape operations when the server rejects the current access token.
    /// It never opens a browser and therefore does not perform OAuth itself.
    ///
    /// @throws AuthenticationException when the refresh fails or the selected profile changes
    private void refreshSession() throws AuthenticationException {
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

    /// Returns every cape owned by this account, as reported by Minecraft Services.
    ///
    /// The token is handled internally: the session is made valid first and,
    /// when the server reports `401 Unauthorized`, the session is refreshed once
    /// and the profile is re-fetched.
    ///
    /// @return the cape list (possibly empty); it is not mutable
    /// @throws AuthenticationException when the profile cannot be loaded
    public List<MicrosoftService.MinecraftProfileResponseCape> getCapes() throws AuthenticationException {
        logIn();
        try {
            return readCapes();
        } catch (AuthenticationException e) {
            if (isUnauthorized(e)) {
                refreshSession();
                return readCapes();
            }
            throw e;
        }
    }

    /// Activates an owned cape for this account.
    ///
    /// The caller uses the returned cape list directly, avoiding an additional
    /// `GET /minecraft/profile` after the change.
    ///
    /// @param capeId the server-side cape ID to activate
    /// @return the updated cape list, as returned by the activation request
    /// @throws AuthenticationException on failure, or when the server rejects the token
    public List<MicrosoftService.MinecraftProfileResponseCape> showCape(String capeId) throws AuthenticationException {
        requireNonNull(capeId);

        logIn();
        MicrosoftService.MinecraftProfileResponse profile;
        try {
            profile = service.showCape(session.accessToken(), capeId);
        } catch (AuthenticationException e) {
            if (isUnauthorized(e)) {
                refreshSession();
                profile = service.showCape(session.accessToken(), capeId);
            } else {
                throw e;
            }
        }
        clearProfileCache();
        return capesOf(profile);
    }

    /// Removes this account's active cape.
    ///
    /// @return the updated cape list, or `null` when the server returned no profile
    /// @throws AuthenticationException on failure, or when the server rejects the token
    public @Nullable List<MicrosoftService.MinecraftProfileResponseCape> hideCape() throws AuthenticationException {
        logIn();
        MicrosoftService.MinecraftProfileResponse profile;
        try {
            profile = service.hideCape(session.accessToken());
        } catch (AuthenticationException e) {
            if (isUnauthorized(e)) {
                refreshSession();
                profile = service.hideCape(session.accessToken());
            } else {
                throw e;
            }
        }
        clearProfileCache();
        return profile == null ? null : capesOf(profile);
    }

    /// Reads the current cape list straight from the Minecraft Services profile.
    private List<MicrosoftService.MinecraftProfileResponseCape> readCapes() throws AuthenticationException {
        return service.getCompleteProfile(session.getAuthorization())
                .map(profile -> profile.capes)
                .filter(Objects::nonNull)
                .map(capes -> capes.stream().filter(Objects::nonNull).toList())
                .orElse(List.of());
    }

    /// Extracts the cape list from a Minecraft Services profile.
    private static List<MicrosoftService.MinecraftProfileResponseCape> capesOf(MicrosoftService.MinecraftProfileResponse profile) {
        return profile.capes == null
                ? List.of()
                : profile.capes.stream().filter(Objects::nonNull).toList();
    }

    /// Invalidates the cached profile so the UI re-fetches the server state after a cape change.
    private void clearProfileCache() {
        service.getProfileRepository().invalidate(profileID);
        invalidate();
    }

    /// Reports whether the exception chain contains a `401 Unauthorized` HTTP response.
    ///
    /// The Minecraft Services cape endpoints are contacted through [MicrosoftService],
    /// which wraps a non-2xx HTTP status into [ServerDisconnectException] while keeping
    /// the original [ResponseCodeException] in the cause chain.
    private static boolean isUnauthorized(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ResponseCodeException responseCodeException
                    && responseCodeException.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
