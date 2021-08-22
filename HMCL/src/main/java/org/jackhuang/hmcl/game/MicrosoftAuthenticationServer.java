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
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.thread;

public final class MicrosoftAuthenticationServer extends NanoHTTPD implements MicrosoftService.OAuthSession {
    private final int port;
    private final CompletableFuture<String> future = new CompletableFuture<>();

    private MicrosoftAuthenticationServer(int port) {
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
    public Response serve(IHTTPSession session) {
        if (session.getMethod() != Method.GET || !"/auth-response".equals(session.getUri())) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "");
        }
        Map<String, String> query = mapOf(NetworkUtils.parseQuery(session.getQueryParameterString()));
        if (query.containsKey("code")) {
            future.complete(query.get("code"));
        } else {
            future.completeExceptionally(new AuthenticationException("failed to authenticate"));
        }

        String html;
        try {
            html = IOUtils.readFullyAsString(MicrosoftAuthenticationServer.class.getResourceAsStream("/assets/microsoft_auth.html"));
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
        return newFixedLengthResponse(html);
    }

    public static class Factory implements MicrosoftService.OAuthCallback {

        @Override
        public MicrosoftService.OAuthSession startServer() throws IOException {
            IOException exception = null;
            for (int port : new int[]{29111, 29112, 29113, 29114, 29115}) {
                try {
                    MicrosoftAuthenticationServer server = new MicrosoftAuthenticationServer(port);
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                    return server;
                } catch (IOException e) {
                    exception = e;
                }
            }
            throw exception;
        }

        @Override
        public void openBrowser(String url) throws IOException {
            // TODO: error!
            FXUtils.openLink(url);
        }

        @Override
        public String getClientId() {
            return System.getProperty("hmcl.microsoft.auth.id",
                    JarUtils.thisJar().flatMap(JarUtils::getManifest).map(manifest -> manifest.getMainAttributes().getValue("Microsoft-Auth-Id")).orElse(""));
        }

        @Override
        public String getClientSecret() {
            return System.getProperty("hmcl.microsoft.auth.secret",
                    JarUtils.thisJar().flatMap(JarUtils::getManifest).map(manifest -> manifest.getMainAttributes().getValue("Microsoft-Auth-Secret")).orElse(""));
        }

    }
}
