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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.ServerDisconnectException;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.gson.ValidationTypeAdapterFactory;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.ObservableOptionalCache;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableList;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

public class YggdrasilService {

    private static final ThreadPoolExecutor POOL = threadPool("ProfileProperties", true, 2, 10, TimeUnit.SECONDS);

    public static final YggdrasilService MOJANG = new YggdrasilService(new MojangYggdrasilProvider());

    private final YggdrasilProvider provider;
    private final ObservableOptionalCache<UUID, CompleteGameProfile, AuthenticationException> profileRepository;

    public YggdrasilService(YggdrasilProvider provider) {
        this.provider = provider;
        this.profileRepository = new ObservableOptionalCache<>(
                uuid -> {
                    LOG.info("Fetching properties of " + uuid + " from " + provider);
                    return getCompleteGameProfile(uuid);
                },
                (uuid, e) -> LOG.log(Level.WARNING, "Failed to fetch properties of " + uuid + " from " + provider, e),
                POOL);
    }

    public ObservableOptionalCache<UUID, CompleteGameProfile, AuthenticationException> getProfileRepository() {
        return profileRepository;
    }

    public YggdrasilSession authenticate(String username, String password, String clientToken) throws AuthenticationException {
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        Objects.requireNonNull(clientToken);

        Map<String, Object> request = new HashMap<>();
        request.put("agent", mapOf(
                pair("name", "Minecraft"),
                pair("version", 1)
        ));
        request.put("username", username);
        request.put("password", password);
        request.put("clientToken", clientToken);
        request.put("requestUser", true);

        return handleAuthenticationResponse(request(provider.getAuthenticationURL(), request), clientToken);
    }

    private static Map<String, Object> createRequestWithCredentials(String accessToken, String clientToken) {
        Map<String, Object> request = new HashMap<>();
        request.put("accessToken", accessToken);
        request.put("clientToken", clientToken);
        return request;
    }

    public YggdrasilSession refresh(String accessToken, String clientToken, GameProfile characterToSelect) throws AuthenticationException {
        Objects.requireNonNull(accessToken);
        Objects.requireNonNull(clientToken);

        Map<String, Object> request = createRequestWithCredentials(accessToken, clientToken);
        request.put("requestUser", true);

        if (characterToSelect != null) {
            request.put("selectedProfile", mapOf(
                    pair("id", characterToSelect.getId()),
                    pair("name", characterToSelect.getName())));
        }

        YggdrasilSession response = handleAuthenticationResponse(request(provider.getRefreshmentURL(), request), clientToken);

        if (characterToSelect != null) {
            if (response.getSelectedProfile() == null ||
                    !response.getSelectedProfile().getId().equals(characterToSelect.getId())) {
                throw new ServerResponseMalformedException("Failed to select character");
            }
        }

        return response;
    }

    public boolean validate(String accessToken) throws AuthenticationException {
        return validate(accessToken, null);
    }

    public boolean validate(String accessToken, String clientToken) throws AuthenticationException {
        Objects.requireNonNull(accessToken);

        try {
            requireEmpty(request(provider.getValidationURL(), createRequestWithCredentials(accessToken, clientToken)));
            return true;
        } catch (RemoteAuthenticationException e) {
            if ("ForbiddenOperationException".equals(e.getRemoteName())) {
                return false;
            }
            throw e;
        }
    }

    public void invalidate(String accessToken) throws AuthenticationException {
        invalidate(accessToken, null);
    }

    public void invalidate(String accessToken, String clientToken) throws AuthenticationException {
        Objects.requireNonNull(accessToken);

        requireEmpty(request(provider.getInvalidationURL(), createRequestWithCredentials(accessToken, clientToken)));
    }

    /**
     * Get complete game profile.
     *
     * Game profile provided from authentication is not complete (no skin data in properties).
     *
     * @param uuid the uuid that the character corresponding to.
     * @return the complete game profile(filled with more properties)
     */
    public Optional<CompleteGameProfile> getCompleteGameProfile(UUID uuid) throws AuthenticationException {
        Objects.requireNonNull(uuid);

        return Optional.ofNullable(fromJson(request(provider.getProfilePropertiesURL(uuid), null), CompleteGameProfile.class));
    }

    public static Optional<Map<TextureType, Texture>> getTextures(CompleteGameProfile profile) throws ServerResponseMalformedException {
        Objects.requireNonNull(profile);

        String encodedTextures = profile.getProperties().get("textures");

        if (encodedTextures != null) {
            TextureResponse texturePayload = fromJson(new String(Base64.getDecoder().decode(encodedTextures), UTF_8), TextureResponse.class);
            return Optional.ofNullable(texturePayload.textures);
        } else {
            return Optional.empty();
        }
    }

    private static YggdrasilSession handleAuthenticationResponse(String responseText, String clientToken) throws AuthenticationException {
        AuthenticationResponse response = fromJson(responseText, AuthenticationResponse.class);
        handleErrorMessage(response);

        if (!clientToken.equals(response.clientToken))
            throw new AuthenticationException("Client token changed from " + clientToken + " to " + response.clientToken);

        return new YggdrasilSession(
                response.clientToken,
                response.accessToken,
                response.selectedProfile,
                response.availableProfiles == null ? null : unmodifiableList(response.availableProfiles),
                response.user);
    }

    private static void requireEmpty(String response) throws AuthenticationException {
        if (StringUtils.isBlank(response))
            return;

        try {
            handleErrorMessage(fromJson(response, ErrorResponse.class));
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    private static void handleErrorMessage(ErrorResponse response) throws AuthenticationException {
        if (!StringUtils.isBlank(response.error)) {
            throw new RemoteAuthenticationException(response.error, response.errorMessage, response.cause);
        }
    }

    private static String request(URL url, Object payload) throws AuthenticationException {
        try {
            if (payload == null)
                return NetworkUtils.doGet(url);
            else
                return NetworkUtils.doPost(url, payload instanceof String ? (String) payload : GSON.toJson(payload), "application/json");
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        }
    }

    private static <T> T fromJson(String text, Class<T> typeOfT) throws ServerResponseMalformedException {
        try {
            return GSON.fromJson(text, typeOfT);
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    private static class TextureResponse {
        public Map<TextureType, Texture> textures;
    }

    private static class AuthenticationResponse extends ErrorResponse {
        public String accessToken;
        public String clientToken;
        public GameProfile selectedProfile;
        public List<GameProfile> availableProfiles;
        public User user;
    }

    private static class ErrorResponse {
        public String error;
        public String errorMessage;
        public String cause;
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE)
            .create();

}
