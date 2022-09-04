/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.gson.*;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.javafx.ObservableOptionalCache;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

public class MicrosoftService {
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final ThreadPoolExecutor POOL = threadPool("MicrosoftProfileProperties", true, 2, 10,
            TimeUnit.SECONDS);

    private final OAuth.Callback callback;

    private final ObservableOptionalCache<UUID, CompleteGameProfile, AuthenticationException> profileRepository;

    public MicrosoftService(OAuth.Callback callback) {
        this.callback = requireNonNull(callback);
        this.profileRepository = new ObservableOptionalCache<>(uuid -> {
            LOG.info("Fetching properties of " + uuid);
            return getCompleteGameProfile(uuid);
        }, (uuid, e) -> LOG.log(Level.WARNING, "Failed to fetch properties of " + uuid, e), POOL);
    }

    public ObservableOptionalCache<UUID, CompleteGameProfile, AuthenticationException> getProfileRepository() {
        return profileRepository;
    }

    public MicrosoftSession authenticate() throws AuthenticationException {
        try {
            OAuth.Result result = OAuth.MICROSOFT.authenticate(OAuth.GrantFlow.DEVICE, new OAuth.Options(SCOPE, callback));
            return authenticateViaLiveAccessToken(result.getAccessToken(), result.getRefreshToken());
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    public MicrosoftSession refresh(MicrosoftSession oldSession) throws AuthenticationException {
        try {
            OAuth.Result result = OAuth.MICROSOFT.refresh(oldSession.getRefreshToken(), new OAuth.Options(SCOPE, callback));
            return authenticateViaLiveAccessToken(result.getAccessToken(), result.getRefreshToken());
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    private String getUhs(XBoxLiveAuthenticationResponse response, String existingUhs) throws AuthenticationException {
        if (response.errorCode != 0) {
            throw new XboxAuthorizationException(response.errorCode, response.redirectUrl);
        }

        if (response.displayClaims == null || response.displayClaims.xui == null || response.displayClaims.xui.size() == 0 || !response.displayClaims.xui.get(0).containsKey("uhs")) {
            LOG.log(Level.WARNING, "Unrecognized xbox authorization response " + GSON.toJson(response));
            throw new NoXuiException();
        }

        String uhs = (String) response.displayClaims.xui.get(0).get("uhs");
        if (existingUhs != null) {
            if (!Objects.equals(uhs, existingUhs)) {
                throw new ServerResponseMalformedException("uhs mismatched");
            }
        }
        return uhs;
    }

    private MicrosoftSession authenticateViaLiveAccessToken(String liveAccessToken, String liveRefreshToken) throws IOException, JsonParseException, AuthenticationException {
        // Authenticate with XBox Live
        XBoxLiveAuthenticationResponse xboxResponse = HttpRequest
                .POST("https://user.auth.xboxlive.com/user/authenticate")
                .json(mapOf(
                        pair("Properties",
                                mapOf(pair("AuthMethod", "RPS"), pair("SiteName", "user.auth.xboxlive.com"),
                                        pair("RpsTicket", "d=" + liveAccessToken))),
                        pair("RelyingParty", "http://auth.xboxlive.com"), pair("TokenType", "JWT")))
                .retry(5)
                .accept("application/json").getJson(XBoxLiveAuthenticationResponse.class);

        String uhs = getUhs(xboxResponse, null);

        // Authenticate Minecraft with XSTS
        XBoxLiveAuthenticationResponse minecraftXstsResponse = HttpRequest
                .POST("https://xsts.auth.xboxlive.com/xsts/authorize")
                .json(mapOf(
                        pair("Properties",
                                mapOf(pair("SandboxId", "RETAIL"),
                                        pair("UserTokens", Collections.singletonList(xboxResponse.token)))),
                        pair("RelyingParty", "rp://api.minecraftservices.com/"), pair("TokenType", "JWT")))
                .ignoreHttpErrorCode(401)
                .retry(5)
                .getJson(XBoxLiveAuthenticationResponse.class);

        getUhs(minecraftXstsResponse, uhs);

        // Authenticate with Minecraft
        MinecraftLoginWithXBoxResponse minecraftResponse = HttpRequest
                .POST("https://api.minecraftservices.com/authentication/login_with_xbox")
                .json(mapOf(pair("identityToken", "XBL3.0 x=" + uhs + ";" + minecraftXstsResponse.token)))
                .retry(5)
                .accept("application/json").getJson(MinecraftLoginWithXBoxResponse.class);

        long notAfter = minecraftResponse.expiresIn * 1000L + System.currentTimeMillis();

        // Get Minecraft Account UUID
        MinecraftProfileResponse profileResponse = getMinecraftProfile(minecraftResponse.tokenType, minecraftResponse.accessToken);
        handleErrorResponse(profileResponse);

        return new MicrosoftSession(minecraftResponse.tokenType, minecraftResponse.accessToken, notAfter, liveRefreshToken,
                new MicrosoftSession.User(minecraftResponse.username), new MicrosoftSession.GameProfile(profileResponse.id, profileResponse.name));
    }

    public Optional<MinecraftProfileResponse> getCompleteProfile(String authorization) throws AuthenticationException {
        try {
            return Optional.ofNullable(
                    HttpRequest.GET("https://api.minecraftservices.com/minecraft/profile")
                            .authorization(authorization).getJson(MinecraftProfileResponse.class));
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    public boolean validate(long notAfter, String tokenType, String accessToken) throws AuthenticationException {
        requireNonNull(tokenType);
        requireNonNull(accessToken);

        if (System.currentTimeMillis() > notAfter) {
            return false;
        }

        try {
            getMinecraftProfile(tokenType, accessToken);
            return true;
        } catch (ResponseCodeException e) {
            return false;
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        }
    }

    private static void handleErrorResponse(MinecraftErrorResponse response) throws AuthenticationException {
        if (response.error != null) {
            throw new RemoteAuthenticationException(response.error, response.errorMessage, response.developerMessage);
        }
    }

    public static Optional<Map<TextureType, Texture>> getTextures(MinecraftProfileResponse profile) {
        Objects.requireNonNull(profile);

        Map<TextureType, Texture> textures = new EnumMap<>(TextureType.class);

        if (!profile.skins.isEmpty()) {
            textures.put(TextureType.SKIN, new Texture(profile.skins.get(0).url, null));
        }
        // if (!profile.capes.isEmpty()) {
        // textures.put(TextureType.CAPE, new Texture(profile.capes.get(0).url, null);
        // }

        return Optional.of(textures);
    }

    private static void getXBoxProfile(String uhs, String xstsToken) throws IOException {
        HttpRequest.GET("https://profile.xboxlive.com/users/me/profile/settings",
                        pair("settings", "GameDisplayName,AppDisplayName,AppDisplayPicRaw,GameDisplayPicRaw,"
                                + "PublicGamerpic,ShowUserAsAvatar,Gamerscore,Gamertag,ModernGamertag,ModernGamertagSuffix,"
                                + "UniqueModernGamertag,AccountTier,TenureLevel,XboxOneRep,"
                                + "PreferredColor,Location,Bio,Watermarks," + "RealName,RealNameOverride,IsQuarantined"))
                .accept("application/json")
                .authorization(String.format("XBL3.0 x=%s;%s", uhs, xstsToken))
                .header("x-xbl-contract-version", "3")
                .getString();
    }

    private static MinecraftProfileResponse getMinecraftProfile(String tokenType, String accessToken)
            throws IOException, AuthenticationException {
        HttpURLConnection conn = HttpRequest.GET("https://api.minecraftservices.com/minecraft/profile")
                .authorization(tokenType, accessToken)
                .createConnection();
        int responseCode = conn.getResponseCode();
        if (responseCode == HTTP_NOT_FOUND) {
            throw new NoMinecraftJavaEditionProfileException();
        } else if (responseCode != 200) {
            throw new ResponseCodeException(new URL("https://api.minecraftservices.com/minecraft/profile"), responseCode);
        }

        String result = NetworkUtils.readData(conn);
        return JsonUtils.fromNonNullJson(result, MinecraftProfileResponse.class);
    }

    public Optional<CompleteGameProfile> getCompleteGameProfile(UUID uuid) throws AuthenticationException {
        Objects.requireNonNull(uuid);

        return Optional.ofNullable(GSON.fromJson(request(NetworkUtils.toURL("https://sessionserver.mojang.com/session/minecraft/profile/" + UUIDTypeAdapter.fromUUID(uuid)), null), CompleteGameProfile.class));
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
            throw new ServerResponseMalformedException(text, e);
        }
    }

    public static class XboxAuthorizationException extends AuthenticationException {
        private final long errorCode;
        private final String redirect;

        public XboxAuthorizationException(long errorCode, String redirect) {
            this.errorCode = errorCode;
            this.redirect = redirect;
        }

        public long getErrorCode() {
            return errorCode;
        }

        public String getRedirect() {
            return redirect;
        }

        public static final long MISSING_XBOX_ACCOUNT = 2148916233L;
        public static final long COUNTRY_UNAVAILABLE = 2148916235L;
        public static final long ADD_FAMILY = 2148916238L;
    }

    public static class NoMinecraftJavaEditionProfileException extends AuthenticationException {
    }

    public static class NoXuiException extends AuthenticationException {
    }

    private static class XBoxLiveAuthenticationResponseDisplayClaims {
        List<Map<Object, Object>> xui;
    }

    private static class MicrosoftErrorResponse {
        @SerializedName("XErr")
        long errorCode;

        @SerializedName("Message")
        String message;

        @SerializedName("Redirect")
        String redirectUrl;
    }

    /**
     * Success Response: { "IssueInstant":"2020-12-07T19:52:08.4463796Z",
     * "NotAfter":"2020-12-21T19:52:08.4463796Z", "Token":"token", "DisplayClaims":{
     * "xui":[ { "uhs":"userhash" } ] } }
     * <p>
     * Error response: { "Identity":"0", "XErr":2148916238, "Message":"",
     * "Redirect":"https://start.ui.xboxlive.com/AddChildToFamily" }
     * <p>
     * XErr Candidates: 2148916233 = missing XBox account 2148916238 = child account
     * not linked to a family
     */
    private static class XBoxLiveAuthenticationResponse extends MicrosoftErrorResponse {
        @SerializedName("IssueInstant")
        String issueInstant;

        @SerializedName("NotAfter")
        String notAfter;

        @SerializedName("Token")
        String token;

        @SerializedName("DisplayClaims")
        XBoxLiveAuthenticationResponseDisplayClaims displayClaims;
    }

    private static class MinecraftLoginWithXBoxResponse {
        @SerializedName("username")
        String username;

        @SerializedName("roles")
        List<String> roles;

        @SerializedName("access_token")
        String accessToken;

        @SerializedName("token_type")
        String tokenType;

        @SerializedName("expires_in")
        int expiresIn;
    }

    private static class MinecraftStoreResponseItem {
        @SerializedName("name")
        String name;
        @SerializedName("signature")
        String signature;
    }

    private static class MinecraftStoreResponse extends MinecraftErrorResponse {
        @SerializedName("items")
        List<MinecraftStoreResponseItem> items;

        @SerializedName("signature")
        String signature;

        @SerializedName("keyId")
        String keyId;
    }

    public static class MinecraftProfileResponseSkin implements Validation {
        public String id;
        public String state;
        public String url;
        public String variant; // CLASSIC, SLIM
        public String alias;

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            Validation.requireNonNull(id, "id cannot be null");
            Validation.requireNonNull(state, "state cannot be null");
            Validation.requireNonNull(url, "url cannot be null");
            Validation.requireNonNull(variant, "variant cannot be null");
        }
    }

    public static class MinecraftProfileResponseCape {

    }

    public static class MinecraftProfileResponse extends MinecraftErrorResponse implements Validation {
        @SerializedName("id")
        UUID id;
        @SerializedName("name")
        String name;
        @SerializedName("skins")
        List<MinecraftProfileResponseSkin> skins;
        @SerializedName("capes")
        List<MinecraftProfileResponseCape> capes;

        @Override
        public void validate() throws JsonParseException, TolerableValidationException {
            Validation.requireNonNull(id, "id cannot be null");
            Validation.requireNonNull(name, "name cannot be null");
            Validation.requireNonNull(skins, "skins cannot be null");
            Validation.requireNonNull(capes, "capes cannot be null");
        }
    }

    private static class MinecraftErrorResponse {
        public String path;
        public String errorType;
        public String error;
        public String errorMessage;
        public String developerMessage;
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE)
            .create();

}
