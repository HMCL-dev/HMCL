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

    /// How long a fetched Minecraft Services profile stays valid before a new
    /// `GET /minecraft/profile` is allowed.
    private static final long PROFILE_CACHE_TTL_MILLIS = 30_000L;

    /// Fixed cooldown applied after an HTTP 429 response. During this window no
    /// new profile/cape request is issued.
    private static final long RATE_LIMIT_COOLDOWN_MILLIS = 10_000L;

    protected final MicrosoftService service;
    protected UUID profileID;

    private boolean authenticated = false;
    private MicrosoftSession session;

    /// Last Minecraft Services profile fetched for this account, and the time it
    /// was fetched. Written before `cachedProfileAt` so a stale timestamp can
    /// only trigger an extra refresh, never serve stale data as fresh.
    private volatile MicrosoftService.MinecraftProfileResponse cachedProfile;
    private volatile long cachedProfileAt;
    /// Timestamp until which all Minecraft Services profile/cape requests are
    /// blocked after a 429 response.
    private volatile long minecraftServicesRateLimitUntil;

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

    /// Returns the Minecraft Services profile for this account, using a short-lived
    /// cache that also deduplicates concurrent calls and honors a rate-limit cooldown.
    ///
    /// Consecutive callers within the TTL share the cached profile; concurrent
    /// callers whose cache has expired share a single `GET /minecraft/profile`
    /// because this method is synchronized on the account instance.
    ///
    /// @return the profile
    /// @throws AuthenticationException when the profile cannot be loaded, or during a
    ///         429 cooldown when no cached profile exists
    public synchronized MicrosoftService.MinecraftProfileResponse getMinecraftProfile() throws AuthenticationException {
        long now = System.currentTimeMillis();

        if (now < minecraftServicesRateLimitUntil) {
            MicrosoftService.MinecraftProfileResponse cached = cachedProfile;
            if (cached != null) {
                return cached;
            }
            throw new MicrosoftService.MinecraftServicesRateLimitException();
        }

        MicrosoftService.MinecraftProfileResponse cached = cachedProfile;
        if (cached != null && now - cachedProfileAt < PROFILE_CACHE_TTL_MILLIS) {
            return cached;
        }

        return fetchProfile();
    }

    /// Returns every cape owned by this account, as reported by Minecraft Services.
    ///
    /// @return the cape list (possibly empty); it is not mutable
    /// @throws AuthenticationException when the profile cannot be loaded
    public List<MicrosoftService.MinecraftProfileResponseCape> getCapes() throws AuthenticationException {
        return capesOf(getMinecraftProfile());
    }

    /// Activates an owned cape for this account.
    ///
    /// The caller uses the returned cape list directly, avoiding an additional
    /// `GET /minecraft/profile` after the change.
    ///
    /// @param capeId the server-side cape ID to activate
    /// @return the updated cape list, as returned by the activation request
    /// @throws AuthenticationException on failure, or when the server is rate-limited
    public List<MicrosoftService.MinecraftProfileResponseCape> showCape(String capeId) throws AuthenticationException {
        requireNonNull(capeId);

        checkRateLimit();
        logIn();
        MicrosoftService.MinecraftProfileResponse profile;
        try {
            profile = service.showCape(session.accessToken(), capeId);
        } catch (AuthenticationException e) {
            if (isUnauthorized(e)) {
                refreshSession();
                profile = service.showCape(session.accessToken(), capeId);
            } else {
                if (isRateLimited(e)) {
                    enterRateLimitCooldown();
                    throw new MicrosoftService.MinecraftServicesRateLimitException();
                }
                throw e;
            }
        }
        cacheProfile(profile);
        return capesOf(profile);
    }

    /// Removes this account's active cape.
    ///
    /// @return the updated cape list, or `null` when the server returned no profile
    /// @throws AuthenticationException on failure, or when the server is rate-limited
    public @Nullable List<MicrosoftService.MinecraftProfileResponseCape> hideCape() throws AuthenticationException {
        checkRateLimit();
        logIn();
        MicrosoftService.MinecraftProfileResponse profile;
        try {
            profile = service.hideCape(session.accessToken());
        } catch (AuthenticationException e) {
            if (isUnauthorized(e)) {
                refreshSession();
                profile = service.hideCape(session.accessToken());
            } else {
                if (isRateLimited(e)) {
                    enterRateLimitCooldown();
                    throw new MicrosoftService.MinecraftServicesRateLimitException();
                }
                throw e;
            }
        }
        if (profile != null) {
            cacheProfile(profile);
            return capesOf(profile);
        }
        // DELETE returned no body: keep owned capes locally; never re-GET here.
        return null;
    }

    /// Fetches the profile from the server, refreshing a stale token once on 401
    /// and applying a cooldown on 429. Callers must hold the account lock.
    private MicrosoftService.MinecraftProfileResponse fetchProfile() throws AuthenticationException {
        logIn();
        MicrosoftService.MinecraftProfileResponse profile;
        try {
            profile = readProfileFromServer();
        } catch (AuthenticationException e) {
            if (isUnauthorized(e)) {
                refreshSession();
                profile = readProfileFromServer();
            } else {
                if (isRateLimited(e)) {
                    enterRateLimitCooldown();
                    throw new MicrosoftService.MinecraftServicesRateLimitException();
                }
                throw e;
            }
        }
        cacheProfile(profile);
        return profile;
    }

    /// Performs a single `GET /minecraft/profile` without caching or rate-limit logic.
    private MicrosoftService.MinecraftProfileResponse readProfileFromServer() throws AuthenticationException {
        return service.getCompleteProfile(session.getAuthorization())
                .orElseThrow(() -> new ServerResponseMalformedException("Empty Minecraft profile"));
    }

    /// Stores the freshly fetched profile and its timestamp.
    private void cacheProfile(MicrosoftService.MinecraftProfileResponse profile) {
        cachedProfile = profile;
        cachedProfileAt = System.currentTimeMillis();
    }

    /// Throws when the rate-limit cooldown is still active.
    private void checkRateLimit() throws AuthenticationException {
        if (System.currentTimeMillis() < minecraftServicesRateLimitUntil) {
            throw new MicrosoftService.MinecraftServicesRateLimitException();
        }
    }

    /// Records a fixed cooldown after a 429 response.
    private void enterRateLimitCooldown() {
        minecraftServicesRateLimitUntil = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MILLIS;
    }

    /// Extracts the cape list from a Minecraft Services profile.
    private static List<MicrosoftService.MinecraftProfileResponseCape> capesOf(MicrosoftService.MinecraftProfileResponse profile) {
        return profile.capes == null
                ? List.of()
                : profile.capes.stream().filter(Objects::nonNull).toList();
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

    /// Reports whether the exception chain contains a `429 Too Many Requests` HTTP response.
    private static boolean isRateLimited(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ResponseCodeException responseCodeException
                    && responseCodeException.getResponseCode() == 429) {
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
