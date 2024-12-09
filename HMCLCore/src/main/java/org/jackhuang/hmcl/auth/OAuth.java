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
package org.jackhuang.hmcl.auth;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.auth.yggdrasil.RemoteAuthenticationException;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public class OAuth {
    private final String authorizationURL;
    private final String accessTokenURL;
    private final String deviceCodeURL;
    private final String tokenURL;

    public OAuth(String authorizationURL, String accessTokenURL, String deviceCodeURL, String tokenURL) {
        this.authorizationURL = authorizationURL;
        this.accessTokenURL = accessTokenURL;
        this.deviceCodeURL = deviceCodeURL;
        this.tokenURL = tokenURL;
    }

    public Result authenticate(GrantFlow grantFlow, OAuthService service) throws AuthenticationException {
        try {
            switch (grantFlow) {
                case AUTHORIZATION_CODE:
                    return authenticateAuthorizationCode(service);
                case DEVICE:
                    return authenticateDevice(service);
                default:
                    throw new UnsupportedOperationException("grant flow " + grantFlow);
            }
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        } catch (InterruptedException e) {
            throw new NoSelectedCharacterException();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof InterruptedException) {
                throw new NoSelectedCharacterException();
            } else {
                throw new ServerDisconnectException(e);
            }
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    private Result authenticateAuthorizationCode(OAuthService service) throws IOException, InterruptedException, JsonParseException, ExecutionException, AuthenticationException {
        Session session = service.callback.startServer();
        service.callback.openBrowser(NetworkUtils.withQuery(authorizationURL, mapOf(
                pair("client_id", service.callback.getClientId()),
                pair("response_type", "code"),
                pair("redirect_uri", session.getRedirectURI()),
                pair("scope", service.scope),
                pair("prompt", "select_account")
        )));
        String code = session.waitFor();

        // Authorization Code -> Token
        AuthorizationResponse response = HttpRequest.POST(accessTokenURL).form(
                        pair("client_id", service.callback.getClientId()),
                        pair("code", code),
                        pair("grant_type", "authorization_code"),
                        pair("client_secret", service.callback.getClientSecret()),
                        pair("redirect_uri", session.getRedirectURI()),
                        pair("scope", service.scope))
                .ignoreHttpCode()
                .retry(5)
                .getJson(AuthorizationResponse.class);
        handleErrorResponse(response);
        return new Result(response.accessToken, response.refreshToken);
    }

    private Result authenticateDevice(OAuthService service) throws IOException, InterruptedException, JsonParseException, AuthenticationException {
        DeviceTokenResponse deviceTokenResponse = HttpRequest.POST(deviceCodeURL)
                .form(pair("client_id", service.callback.getClientId()), pair("scope", service.scope))
                .ignoreHttpCode()
                .retry(5)
                .getJson(DeviceTokenResponse.class);
        handleErrorResponse(deviceTokenResponse);

        service.callback.grantDeviceCode(deviceTokenResponse.userCode,
                deviceTokenResponse.verificationUri,
                deviceTokenResponse.verificationUriComplete);

        if (StringUtils.isBlank(deviceTokenResponse.verificationUriComplete))
            service.callback.openBrowser(deviceTokenResponse.verificationUri);
        else
            service.callback.openBrowser(deviceTokenResponse.verificationUriComplete);

        long startTime = System.nanoTime();
        long interval = TimeUnit.MILLISECONDS.convert(deviceTokenResponse.interval, TimeUnit.SECONDS);

        while (true) {
            Thread.sleep(Math.max(interval, 1));

            // We stop waiting if user does not respond our authentication request in 15 minutes.
            long estimatedTime = System.nanoTime() - startTime;
            if (TimeUnit.SECONDS.convert(estimatedTime, TimeUnit.NANOSECONDS) >= Math.min(deviceTokenResponse.expiresIn, 900)) {
                throw new NoSelectedCharacterException();
            }

            TokenResponse tokenResponse = HttpRequest.POST(tokenURL)
                    .form(
                            pair("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                            pair("device_code", deviceTokenResponse.deviceCode),
                            pair("client_id", service.callback.getClientId()))
                    .ignoreHttpCode()
                    .retry(5)
                    .getJson(TokenResponse.class);

            if ("authorization_pending".equals(tokenResponse.error)) {
                continue;
            }

            if ("expired_token".equals(tokenResponse.error)) {
                throw new NoSelectedCharacterException();
            }

            if ("slow_down".equals(tokenResponse.error)) {
                interval += 5000;
                continue;
            }

            return new Result(tokenResponse.accessToken, tokenResponse.refreshToken, tokenResponse.idToken);
        }
    }

    public Result refresh(String refreshToken, OAuthService service) throws AuthenticationException {
        try {
            Map<String, String> query = mapOf(pair("client_id", service.callback.getClientId()),
                    pair("refresh_token", refreshToken),
                    pair("grant_type", "refresh_token")
            );

            if (!service.callback.isPublicClient()) {
                query.put("client_secret", service.callback.getClientSecret());
            }

            RefreshResponse response = HttpRequest.POST(tokenURL)
                    .form(query)
                    .accept("application/json")
                    .ignoreHttpCode()
                    .retry(5)
                    .getJson(RefreshResponse.class);

            handleErrorResponse(response);

            return new Result(response.accessToken, response.refreshToken, response.idToken);
        } catch (IOException e) {
            throw new ServerDisconnectException(e);
        } catch (JsonParseException e) {
            throw new ServerResponseMalformedException(e);
        }
    }

    private static void handleErrorResponse(ErrorResponse response) throws AuthenticationException {
        if (response.error == null || response.errorDescription == null) {
            return;
        }

        switch (response.error) {
            case "invalid_grant":
                if (response.errorDescription.contains("AADSTS70000")) {
                    throw new CredentialExpiredException();
                }
                break;
        }

        throw new RemoteAuthenticationException(response.error, response.errorDescription, "");
    }

    public interface Session {

        String getRedirectURI();

        /**
         * Wait for authentication
         *
         * @return authentication code
         * @throws InterruptedException if interrupted
         * @throws ExecutionException   if an I/O error occurred.
         */
        String waitFor() throws InterruptedException, ExecutionException;

        default String getIdToken() {
            return null;
        }
    }

    public interface Callback {
        /**
         * Start OAuth callback server at localhost.
         *
         * @throws IOException if an I/O error occurred.
         */
        Session startServer() throws IOException, AuthenticationException;

        void grantDeviceCode(String userCode, String verificationURI, String verificationUriComplete);

        /**
         * Open browser
         *
         * @param url OAuth url.
         */
        void openBrowser(String url) throws IOException;

        String getClientId();

        String getClientSecret();

        boolean isPublicClient();
    }

    public enum GrantFlow {
        AUTHORIZATION_CODE,
        DEVICE,
    }

    public static final class Result {
        private final String accessToken;
        private final String refreshToken;
        private final String idToken;

        public Result(String accessToken, String refreshToken) {
            this(accessToken, refreshToken, null);
        }

        public Result(String accessToken, String refreshToken, String idToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.idToken = idToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getIdToken() {
            return idToken;
        }
    }

    private static class DeviceTokenResponse extends ErrorResponse {
        @SerializedName("user_code")
        public String userCode;

        @SerializedName("device_code")
        public String deviceCode;

        // The URI to be visited for user.
        @SerializedName("verification_uri")
        public String verificationUri;

        @SerializedName("verification_uri_complete")
        public String verificationUriComplete;

        // Lifetime in seconds for device_code and user_code
        @SerializedName("expires_in")
        public int expiresIn;

        // Polling interval
        @SerializedName("interval")
        public int interval;
    }

    private static class TokenResponse extends ErrorResponse {
        @SerializedName("token_type")
        public String tokenType;

        @SerializedName("expires_in")
        public int expiresIn;

        @SerializedName("ext_expires_in")
        public int extExpiresIn;

        @SerializedName("scope")
        public String scope;

        @SerializedName("access_token")
        public String accessToken;

        @SerializedName("refresh_token")
        public String refreshToken;

        /**
         * LittleSkin ID Token
         */
        @SerializedName("id_token")
        public String idToken;
    }

    private static class ErrorResponse {
        @SerializedName("error")
        public String error;

        @SerializedName("error_description")
        public String errorDescription;

        @SerializedName("correlation_id")
        public String correlationId;
    }

    /**
     * Error response: {"error":"invalid_grant","error_description":"The provided
     * value for the 'redirect_uri' is not valid. The value must exactly match the
     * redirect URI used to obtain the authorization
     * code.","correlation_id":"??????"}
     */
    public static class AuthorizationResponse extends ErrorResponse {
        @SerializedName("token_type")
        public String tokenType;

        @SerializedName("expires_in")
        public int expiresIn;

        @SerializedName("scope")
        public String scope;

        @SerializedName("access_token")
        public String accessToken;

        @SerializedName("refresh_token")
        public String refreshToken;

        @SerializedName("user_id")
        public String userId;

        @SerializedName("foci")
        public String foci;
    }

    private static class RefreshResponse extends ErrorResponse {
        @SerializedName("expires_in")
        int expiresIn;

        @SerializedName("access_token")
        String accessToken;

        @SerializedName("refresh_token")
        String refreshToken;

        @SerializedName("id_token")
        String idToken;
    }
}
