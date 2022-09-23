package org.jackhuang.hmcl.auth.nide8;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.ServerDisconnectException;
import org.jackhuang.hmcl.auth.ServerResponseMalformedException;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.gson.ValidationTypeAdapterFactory;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.ObservableOptionalCache;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

public class Nide8Service {
    private static final String authenticateUrl = "https://auth.mc-user.com:233/{0}/authserver/authenticate";
    private static final String refreshUrl = "https://auth.mc-user.com:233/{0}/authserver/refresh";
    private static final String validateUrl = "https://auth.mc-user.com:233/{0}/authserver/validate";
    private static final String invalidateUrl = "https://auth.mc-user.com:233/{0}/authserver/invalidate";
    private static final String signoutUrl = "https://auth.mc-user.com:233/{0}/authserver/signout";
    private static final String profileUrl = "https://auth.mc-user.com:233/{0}/sessionserver/session/minecraft/profile/{1}";
    private static final ThreadPoolExecutor POOL = threadPool("Nide8ProfileProperties", true, 2, 20, TimeUnit.SECONDS);

    private final ObservableOptionalCache<Nide8LoginObj, Nide8GameProfile, AuthenticationException> profileRepository;
    private static Nide8InjectorArtifactProvider downloader;

    public Nide8Service(Nide8InjectorArtifactProvider downloader) {
        Nide8Service.downloader = downloader;
        this.profileRepository = new ObservableOptionalCache<>(
                uuid -> {
                    LOG.info("Fetching properties of " + uuid + " from nide8");
                    return getGameProfile(uuid);
                },
                (uuid, e) -> LOG.log(Level.WARNING, "Failed to fetch properties of " + uuid + " from nide8", e),
                POOL);
    }

    public ObservableOptionalCache<Nide8LoginObj, Nide8GameProfile, AuthenticationException> getProfileRepository() {
        return profileRepository;
    }

    public Nide8Session authenticate(String serverId, String username, String password, String clientToken) throws AuthenticationException, MalformedURLException {
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        Objects.requireNonNull(clientToken);

        Map<String, Object> request = new HashMap<>();
        request.put("agent", mapOf(
                pair("name", "Minecraft"),
                pair("version", 666)
        ));
        request.put("username", username);
        request.put("password", password);
        request.put("clientToken", clientToken);
        request.put("requestUser", true);

        return handleAuthenticationResponse(serverId, request(new URL(authenticateUrl.replace("{0}", serverId)), request), clientToken);
    }

    private static Map<String, Object> createRequestWithCredentials(String accessToken, String clientToken) {
        Map<String, Object> request = new HashMap<>();
        request.put("accessToken", accessToken);
        request.put("clientToken", clientToken);
        return request;
    }

    public Nide8Session refresh(String serverID, String accessToken, String clientToken, Nide8GameProfile characterToSelect) throws AuthenticationException, MalformedURLException {
        Objects.requireNonNull(accessToken);
        Objects.requireNonNull(clientToken);

        Map<String, Object> request = createRequestWithCredentials(accessToken, clientToken);
        request.put("requestUser", true);

        if (characterToSelect != null) {
            request.put("selectedProfile", mapOf(
                    pair("id", characterToSelect.getId()),
                    pair("name", characterToSelect.getName())));
        }

        Nide8Session response = handleAuthenticationResponse(serverID, request(new URL(refreshUrl.replace("{0}", serverID)), request), clientToken);

        if (characterToSelect != null) {
            if (response.getSelectedProfile() == null ||
                    !response.getSelectedProfile().getId().equals(characterToSelect.getId())) {
                throw new ServerResponseMalformedException("Failed to select character");
            }
        }

        return response;
    }

    public boolean validate(String serverID, String accessToken) throws
            AuthenticationException, MalformedURLException {
        return validate(serverID, accessToken, null);
    }

    public boolean validate(String serverID, String accessToken, String clientToken) throws AuthenticationException, MalformedURLException {
        Objects.requireNonNull(accessToken);

        try {
            requireEmpty(request(new URL(validateUrl.replace("{0}", serverID)), createRequestWithCredentials(accessToken, clientToken)));
            return true;
        } catch (RemoteAuthenticationException e) {
            if ("ForbiddenOperationException".equals(e.getRemoteName())) {
                return false;
            }
            throw e;
        }
    }

    public void invalidate(String serverID, String accessToken) throws
            AuthenticationException, MalformedURLException {
        invalidate(serverID, accessToken, null);
    }

    public void invalidate(String serverID, String accessToken, String clientToken) throws AuthenticationException, MalformedURLException {
        Objects.requireNonNull(accessToken);

        requireEmpty(request(new URL(invalidateUrl.replace("{0}", serverID)), createRequestWithCredentials(accessToken, clientToken)));
    }

    /**
     * Get complete game profile.
     * <p>
     * Game profile provided from authentication is not complete (no skin data in properties).
     *
     * @param obj the uuid that the character corresponding to.
     * @return the complete game profile(filled with more properties)
     */
    public Optional<Nide8GameProfile> getGameProfile(Nide8LoginObj obj)
            throws AuthenticationException {
        Objects.requireNonNull(obj);

        try {
            return Optional.ofNullable(fromJson(request(new URL(profileUrl.replace("{0}", obj.serverID).replace("{1}", obj.uuid.toString())), null), Nide8GameProfile.class));
        } catch (MalformedURLException e) {
            return Optional.empty();
        }
    }

    public static Optional<Map<TextureType, Texture>> getTextures(Nide8GameProfile profile) throws ServerResponseMalformedException {
        Objects.requireNonNull(profile);

        String encodedTextures = profile.getProperties().get("textures");

        if (encodedTextures != null) {
            byte[] decodedBinary;
            try {
                decodedBinary = Base64.getDecoder().decode(encodedTextures);
            } catch (IllegalArgumentException e) {
                throw new ServerResponseMalformedException(e);
            }
            TextureResponse texturePayload = fromJson(new String(decodedBinary, UTF_8), TextureResponse.class);
            return Optional.ofNullable(texturePayload.textures);
        } else {
            return Optional.empty();
        }
    }

    private static Nide8Session handleAuthenticationResponse(String serverID, String responseText, String clientToken) throws AuthenticationException {
        AuthenticationResponse response = fromJson(responseText, AuthenticationResponse.class);
        handleErrorMessage(response);

        if (!clientToken.equals(response.clientToken))
            throw new AuthenticationException("Client token changed from " + clientToken + " to " + response.clientToken);

        return new Nide8Session(
                downloader,
                serverID,
                response.clientToken,
                response.accessToken,
                response.selectedProfile,
                response.availableProfiles == null ? null : unmodifiableList(response.availableProfiles),
                response.user == null ? null : response.user.getProperties());
    }

    private static void requireEmpty(String response) throws AuthenticationException {
        if (StringUtils.isBlank(response))
            return;

        handleErrorMessage(fromJson(response, ErrorResponse.class));
    }

    private static void handleErrorMessage(ErrorResponse response) throws AuthenticationException {
        if (!StringUtils.isBlank(response.error)) {
            throw new RemoteAuthenticationException(response.error, response.errorMessage, response.cause);
        }
    }

    private static String request(URL url, Object payload) throws AuthenticationException {
        try {
            if (payload == null)
                return doGet(url);
            else
                return doPost(url, payload instanceof String ? (String) payload : GSON.toJson(payload), "application/json");
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        }
    }

    public static String doGet(URL url) throws IOException {
        HttpURLConnection con = NetworkUtils.createHttpConnection(url);
        con.setRequestProperty("User-Agent", "Nide8");
        con = NetworkUtils.resolveConnection(con);
        return IOUtils.readFullyAsString(con.getInputStream(), StandardCharsets.UTF_8);
    }

    public static String doPost(URL url, String post, String contentType) throws IOException {
        byte[] bytes = post.getBytes(UTF_8);

        HttpURLConnection con = NetworkUtils.createHttpConnection(url);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("User-Agent", "Nide8");
        con.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
        con.setRequestProperty("Content-Length", "" + bytes.length);
        try (OutputStream os = con.getOutputStream()) {
            os.write(bytes);
        }
        return NetworkUtils.readData(con);
    }

    private static <T> T fromJson(String text, Class<T> typeOfT) throws ServerResponseMalformedException {
        try {
            return GSON.fromJson(text, typeOfT);
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(text, e);
        }
    }

    private static class TextureResponse {
        public Map<TextureType, Texture> textures;
    }

    private static class AuthenticationResponse extends ErrorResponse {
        public String accessToken;
        public String clientToken;
        public Nide8GameProfile selectedProfile;
        public List<Nide8GameProfile> availableProfiles;
        public Nide8User user;
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

    public static final String PROFILE_URL = "https://aka.ms/MinecraftMigration";
    public static final String MIGRATION_FAQ_URL = "https://help.minecraft.net/hc/en-us/articles/360050865492-JAVA-Account-Migration-FAQ";
    public static final String PURCHASE_URL = "https://www.minecraft.net/store/minecraft-java-bedrock-edition-pc";
}
