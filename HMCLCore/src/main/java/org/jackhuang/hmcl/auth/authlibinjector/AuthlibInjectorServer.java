/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.auth.authlibinjector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.io.IOUtils.readFullyWithoutClosing;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;

import org.jackhuang.hmcl.util.gson.JsonUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

public class AuthlibInjectorServer {

    private static final int MAX_REDIRECTS = 5;

    public static AuthlibInjectorServer locateServer(String url) throws IOException {
        url = parseInputUrl(url);
        HttpURLConnection conn;
        int redirectCount = 0;
        for (;;) {
            conn = (HttpURLConnection) new URL(url).openConnection();
            Optional<String> ali = getApiLocationIndication(conn);
            if (ali.isPresent()) {
                conn.disconnect();
                url = ali.get();
                if (redirectCount >= MAX_REDIRECTS) {
                    throw new IOException("Exceeded maximum number of redirects (" + MAX_REDIRECTS + ")");
                }
                redirectCount++;
                LOG.info("Redirect API root to: " + url);
            } else {
                break;
            }
        }

        try {
            return parseResponse(url, readFullyWithoutClosing(conn.getInputStream()));
        } finally {
            conn.disconnect();
        }
    }

    private static Optional<String> getApiLocationIndication(URLConnection conn) {
        return Optional.ofNullable(conn.getHeaderFields().get("X-Authlib-Injector-API-Location"))
                .flatMap(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)))
                .flatMap(indication -> {
                    String currentUrl = appendSuffixSlash(conn.getURL().toString());
                    String newUrl;
                    try {
                        newUrl = appendSuffixSlash(new URL(conn.getURL(), indication).toString());
                    } catch (MalformedURLException e) {
                        LOG.warning("Failed to resolve absolute ALI, the header is [" + indication + "]. Ignore it.");
                        return Optional.empty();
                    }

                    if (newUrl.equals(currentUrl)) {
                        return Optional.empty();
                    } else {
                        return Optional.of(newUrl);
                    }
                });
    }

    private static String parseInputUrl(String url) {
        String lowercased = url.toLowerCase();
        if (!lowercased.startsWith("http://") && !lowercased.startsWith("https://")) {
            url = "https://" + url;
        }

        url = appendSuffixSlash(url);
        return url;
    }

    private static String appendSuffixSlash(String url) {
        if (!url.endsWith("/")) {
            return url + "/";
        } else {
            return url;
        }
    }

    public static AuthlibInjectorServer fetchServerInfo(String url) throws IOException {
        try (InputStream in = new URL(url).openStream()) {
            return parseResponse(url, readFullyWithoutClosing(in));
        }
    }

    private static AuthlibInjectorServer parseResponse(String url, byte[] rawResponse) throws IOException {
        try {
            JsonObject response = JsonUtils.fromNonNullJson(new String(rawResponse, UTF_8), JsonObject.class);
            String name = extractServerName(response).orElse(url);
            return new AuthlibInjectorServer(url, name);
        } catch (JsonParseException e) {
            throw new IOException("Malformed response", e);
        }
    }

    private static Optional<String> extractServerName(JsonObject response){
        return tryCast(response.get("meta"), JsonObject.class)
                .flatMap(meta -> tryCast(meta.get("serverName"), JsonPrimitive.class).map(JsonPrimitive::getAsString));
    }

    private String url;
    private String name;

    public AuthlibInjectorServer(String url, String name) {
        this.url = url;
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return url + " (" + name + ")";
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof AuthlibInjectorServer))
            return false;
        AuthlibInjectorServer another = (AuthlibInjectorServer) obj;
        return this.url.equals(another.url);
    }
}
