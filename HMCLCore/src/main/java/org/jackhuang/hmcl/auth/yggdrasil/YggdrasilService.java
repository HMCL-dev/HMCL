package org.jackhuang.hmcl.auth.yggdrasil;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Lang.liftFunction;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.InvalidCredentialsException;
import org.jackhuang.hmcl.auth.InvalidPasswordException;
import org.jackhuang.hmcl.auth.InvalidTokenException;
import org.jackhuang.hmcl.auth.ServerDisconnectException;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.util.NetworkUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public class YggdrasilService {

    private final YggdrasilProvider provider;
    private final Proxy proxy;

    public YggdrasilService(YggdrasilProvider provider) {
        this(provider, Proxy.NO_PROXY);
    }

    public YggdrasilService(YggdrasilProvider provider, Proxy proxy) {
        this.provider = provider;
        this.proxy = proxy;
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

    private Map<String, Object> createRequestWithCredentials(String accessToken, String clientToken) {
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

        return handleAuthenticationResponse(request(provider.getRefreshmentURL(), request), clientToken);
    }

    public boolean validate(String accessToken) throws AuthenticationException {
        return validate(accessToken, null);
    }

    public boolean validate(String accessToken, String clientToken) throws AuthenticationException {
        Objects.requireNonNull(accessToken);

        try {
            requireEmpty(request(provider.getValidationURL(), createRequestWithCredentials(accessToken, clientToken)));
            return true;
        } catch (InvalidCredentialsException | InvalidTokenException e) {
            return false;
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
    public Optional<GameProfile> getCompleteGameProfile(UUID uuid) throws AuthenticationException {
        Objects.requireNonNull(uuid);

        return Optional.ofNullable(fromJson(request(provider.getProfilePropertiesURL(uuid), null), ProfileResponse.class))
                .map(response -> new GameProfile(response.id, response.name, response.properties));
    }

    public Optional<Map<TextureType, Texture>> getTextures(GameProfile profile) throws AuthenticationException {
        Objects.requireNonNull(profile);

        return Optional.ofNullable(profile.getProperties())
                .flatMap(properties -> Optional.ofNullable(properties.get("textures")))
                .map(encodedTextures -> new String(Base64.getDecoder().decode(encodedTextures), UTF_8))
                .map(liftFunction(textures -> fromJson(textures, TextureResponse.class)))
                .flatMap(response -> Optional.ofNullable(response.textures));
    }

    private static YggdrasilSession handleAuthenticationResponse(String responseText, String clientToken) throws AuthenticationException {
        AuthenticationResponse response = fromJson(responseText, AuthenticationResponse.class);
        handleErrorMessage(response);

        if (!clientToken.equals(response.clientToken))
            throw new AuthenticationException("Client token changed from " + clientToken + " to " + response.clientToken);

        return new YggdrasilSession(response.clientToken, response.accessToken, response.selectedProfile, response.availableProfiles, response.user);
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
            if (response.errorMessage != null && "ForbiddenOperationException".equals(response.error)) {
                if (response.errorMessage.contains("Invalid credentials"))
                    throw new InvalidCredentialsException();

                else if (response.errorMessage.contains("Invalid token"))
                    throw new InvalidTokenException();

                else if (response.errorMessage.contains("Invalid username or password"))
                    throw new InvalidPasswordException();
            }

            throw new RemoteAuthenticationException(response.error, response.errorMessage, response.cause);
        }
    }

    private String request(URL url, Object payload) throws AuthenticationException {
        try {
            if (payload == null)
                return NetworkUtils.doGet(url, proxy);
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

    private class ProfileResponse {
        public UUID id;
        public String name;
        public PropertyMap properties;
    }

    private class TextureResponse {
        public Map<TextureType, Texture> textures;
    }

    private class AuthenticationResponse extends ErrorResponse {
        public String accessToken;
        public String clientToken;
        public GameProfile selectedProfile;
        public GameProfile[] availableProfiles;
        public User user;
    }

    private class ErrorResponse {
        public String error;
        public String errorMessage;
        public String cause;
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, GameProfile.Serializer.INSTANCE)
            .registerTypeAdapter(PropertyMap.class, PropertyMap.Serializer.INSTANCE)
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .create();

}
