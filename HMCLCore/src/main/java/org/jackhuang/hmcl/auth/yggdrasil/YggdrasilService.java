package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.util.*;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.*;

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
        request.put("agent", Lang.mapOf(
                new Pair<>("name", "Minecraft"),
                new Pair<>("version", 1)
        ));
        request.put("username", username);
        request.put("password", password);
        request.put("clientToken", clientToken);
        request.put("requestUser", true);

        return handle(request(provider.getAuthenticationURL(), request), clientToken);
    }

    public YggdrasilSession refresh(String accessToken, String clientToken) throws AuthenticationException {
        Objects.requireNonNull(accessToken);
        Objects.requireNonNull(clientToken);

        return handle(request(provider.getRefreshmentURL(), new RefreshRequest(accessToken, clientToken)), clientToken);
    }

    public boolean validate(String accessToken) throws AuthenticationException {
        return validate(accessToken, null);
    }

    public boolean validate(String accessToken, String clientToken) throws AuthenticationException {
        Objects.requireNonNull(accessToken);

        try {
            requireEmpty(request(provider.getValidationURL(), new RefreshRequest(accessToken, clientToken)));
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

        requireEmpty(request(provider.getInvalidationURL(), GSON.toJson(new RefreshRequest(accessToken, clientToken))));
    }

    /**
     * Get complete game profile.
     *
     * Game profile provided from authentication is not complete (no skin data in properties).
     *
     * @param userId the userId that the character corresponding to.
     * @return the complete game profile(filled with more properties), null if character corresponding to {@code userId} does not exist.
     * @throws AuthenticationException if an I/O error occurred or server response malformed.
     */
    public GameProfile getCompleteGameProfile(UUID userId) throws AuthenticationException {
        Objects.requireNonNull(userId);

        ProfileResponse response = fromJson(request(provider.getProfilePropertiesURL(userId), null), ProfileResponse.class);
        if (response == null)
            return null;

        return new GameProfile(response.getId(), response.getName(), response.getProperties());
    }

    public Optional<Map<TextureType, Texture>> getTextures(GameProfile profile) throws AuthenticationException {
        Objects.requireNonNull(profile);

        return Optional.ofNullable(profile.getProperties())
                .map(properties -> properties.get("textures"))
                .map(encodedTextures -> new String(Base64.getDecoder().decode(encodedTextures), Charsets.UTF_8))
                .map(Lang.liftFunction(textures -> fromJson(textures, TextureResponse.class)))
                .map(TextureResponse::getTextures);
    }

    private String request(URL url, Object input) throws AuthenticationException {
        try {
            if (input == null)
                return NetworkUtils.doGet(url, proxy);
            else
                return NetworkUtils.doPost(url, input instanceof String ? (String) input : GSON.toJson(input), "application/json");
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        }
    }

    private static YggdrasilSession handle(String responseText, String clientToken) throws AuthenticationException {
        AuthenticationResponse response = fromJson(responseText, AuthenticationResponse.class);
        handleErrorMessage(response);

        if (!clientToken.equals(response.getClientToken()))
            throw new AuthenticationException("Client token changed from " + response.getClientToken() + " to " + clientToken);

        return new YggdrasilSession(response.getAccessToken(), response.getSelectedProfile(), response.getAvailableProfiles(), response.getUser());
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
        if (!StringUtils.isBlank(response.getError())) {
            if (response.getErrorMessage() != null)
                if (response.getErrorMessage().contains("Invalid credentials"))
                    throw new InvalidCredentialsException();
                else if (response.getErrorMessage().contains("Invalid token"))
                    throw new InvalidTokenException();
                else if (response.getErrorMessage().contains("Invalid username or password"))
                    throw new InvalidPasswordException();
            throw new RemoteAuthenticationException(response.getError(), response.getErrorMessage(), response.getCause());
        }
    }

    private static <T> T fromJson(String text, Class<T> typeOfT) throws ServerResponseMalformedException {
        try {
            return GSON.fromJson(text, typeOfT);
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GameProfile.class, GameProfile.Serializer.INSTANCE)
            .registerTypeAdapter(PropertyMap.class, PropertyMap.Serializer.INSTANCE)
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .create();

    private static final class RefreshRequest {

        private final String accessToken;
        private final String clientToken;
        private final GameProfile selectedProfile;
        private final boolean requestUser;

        public RefreshRequest(String accessToken, String clientToken) {
            this(accessToken, clientToken, null);
        }

        public RefreshRequest(String accessToken, String clientToken, GameProfile selectedProfile) {
            this(accessToken, clientToken, selectedProfile, true);
        }

        public RefreshRequest(String accessToken, String clientToken, GameProfile selectedProfile, boolean requestUser) {
            this.accessToken = accessToken;
            this.clientToken = clientToken;
            this.selectedProfile = selectedProfile;
            this.requestUser = requestUser;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getClientToken() {
            return clientToken;
        }

        public GameProfile getSelectedProfile() {
            return selectedProfile;
        }

        public boolean isRequestUser() {
            return requestUser;
        }

    }

    private static final class ProfileResponse {
        private final UUID id;
        private final String name;
        private final PropertyMap properties;

        public ProfileResponse() {
            this(UUID.randomUUID(), "", null);
        }

        public ProfileResponse(UUID id, String name, PropertyMap properties) {
            this.id = id;
            this.name = name;
            this.properties = properties;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public PropertyMap getProperties() {
            return properties;
        }
    }
}
