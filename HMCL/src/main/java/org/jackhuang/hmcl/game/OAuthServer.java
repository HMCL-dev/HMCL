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
package org.jackhuang.hmcl.game;

import fi.iki.elonen.NanoHTTPD;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class OAuthServer extends NanoHTTPD implements OAuth.Session {
    private final int port;
    private final CompletableFuture<String> future = new CompletableFuture<>();

    public static String lastlyOpenedURL;

    private String idToken;

    private OAuthServer(int port) {
        super(port);

        this.port = port;
    }

    @Override
    public String getRedirectURI() {
        return String.format("http://localhost:%d/auth-response", port);
    }

    @Override
    public String waitFor() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public String getIdToken() {
        return idToken;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (!"/auth-response".equals(session.getUri())) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "");
        }

        if (session.getMethod() == Method.POST) {
            Map<String, String> files = new HashMap<>();
            try {
                session.parseBody(files);
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to read post data", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "");
            } catch (ResponseException re) {
                return newFixedLengthResponse(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
            }
        } else if (session.getMethod() == Method.GET) {
            // do nothing
        } else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "");
        }
        String parameters = session.getQueryParameterString();

        Map<String, String> query = mapOf(NetworkUtils.parseQuery(parameters));
        if (query.containsKey("code")) {
            idToken = query.get("id_token");
            future.complete(query.get("code"));
        } else {
            Logging.LOG.warning("Error: " + parameters);
            future.completeExceptionally(new AuthenticationException("failed to authenticate"));
        }

        String html;
        try {
            html = IOUtils.readFullyAsString(OAuthServer.class.getResourceAsStream("/assets/microsoft_auth.html"), StandardCharsets.UTF_8)
                    .replace("%close-page%", i18n("account.methods.microsoft.close_page"));
        } catch (IOException e) {
            Logging.LOG.log(Level.SEVERE, "Failed to load html");
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "");
        }
        thread(() -> {
            try {
                Thread.sleep(1000);
                stop();
            } catch (InterruptedException e) {
                Logging.LOG.log(Level.SEVERE, "Failed to sleep for 1 second");
            }
        });
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html);
    }

    public static class Factory implements OAuth.Callback {
        public final EventManager<GrantDeviceCodeEvent> onGrantDeviceCode = new EventManager<>();
        public final EventManager<OpenBrowserEvent> onOpenBrowser = new EventManager<>();

        @Override
        public OAuth.Session startServer() throws IOException, AuthenticationException {
            if (StringUtils.isBlank(getClientId())) {
                throw new MicrosoftAuthenticationNotSupportedException();
            }

            IOException exception = null;
            for (int port : new int[]{29111, 29112, 29113, 29114, 29115}) {
                try {
                    OAuthServer server = new OAuthServer(port);
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
                    return server;
                } catch (IOException e) {
                    exception = e;
                }
            }
            throw exception;
        }

        @Override
        public void grantDeviceCode(String userCode, String verificationURI) {
            onGrantDeviceCode.fireEvent(new GrantDeviceCodeEvent(this, userCode, verificationURI));
        }

        @Override
        public void openBrowser(String url) throws IOException {
            lastlyOpenedURL = url;
            FXUtils.openLink(url);

            onOpenBrowser.fireEvent(new OpenBrowserEvent(this, url));
        }

        @Override
        public String getClientId() {
            return System.getProperty("hmcl.microsoft.auth.id",
                    JarUtils.getManifestAttribute("Microsoft-Auth-Id", ""));
        }

        @Override
        public String getClientSecret() {
            return System.getProperty("hmcl.microsoft.auth.secret",
                    JarUtils.getManifestAttribute("Microsoft-Auth-Secret", ""));
        }

        @Override
        public boolean isPublicClient() {
            return true; // We have turned on the device auth flow.
        }
    }

    public static class GrantDeviceCodeEvent extends Event {
        private final String userCode;
        private final String verificationUri;

        public GrantDeviceCodeEvent(Object source, String userCode, String verificationUri) {
            super(source);
            this.userCode = userCode;
            this.verificationUri = verificationUri;
        }

        public String getUserCode() {
            return userCode;
        }

        public String getVerificationUri() {
            return verificationUri;
        }
    }

    public static class OpenBrowserEvent extends Event {
        private final String url;

        public OpenBrowserEvent(Object source, String url) {
            super(source);
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }

    public static class MicrosoftAuthenticationNotSupportedException extends AuthenticationException {
    }
}
