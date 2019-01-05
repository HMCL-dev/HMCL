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
package org.jackhuang.hmcl.auth.authlibinjector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.io.IOUtils.readFullyAsByteArray;
import static org.jackhuang.hmcl.util.io.IOUtils.readFullyWithoutClosing;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;
import java.util.logging.Level;

import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.JsonAdapter;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

@JsonAdapter(AuthlibInjectorServer.Deserializer.class)
public class AuthlibInjectorServer implements Observable {

    private static final int MAX_REDIRECTS = 5;
    private static final Gson GSON = new GsonBuilder().create();

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
            AuthlibInjectorServer server = new AuthlibInjectorServer(url);
            server.refreshMetadata(readFullyWithoutClosing(conn.getInputStream()));
            return server;
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
        AuthlibInjectorServer server = new AuthlibInjectorServer(url);
        server.refreshMetadata();
        return server;
    }

    private String url;
    @Nullable
    private String metadataResponse;
    private long metadataTimestamp;

    @Nullable
    private transient String name;

    private transient boolean metadataRefreshed;
    private transient ObservableHelper helper = new ObservableHelper(this);

    public AuthlibInjectorServer(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public Optional<String> getMetadataResponse() {
        return Optional.ofNullable(metadataResponse);
    }

    public long getMetadataTimestamp() {
        return metadataTimestamp;
    }

    public String getName() {
        return Optional.ofNullable(name)
                .orElse(url);
    }

    public String fetchMetadataResponse() throws IOException {
        if (metadataResponse == null || !metadataRefreshed) {
            refreshMetadata();
        }
        return getMetadataResponse().get();
    }

    public void refreshMetadata() throws IOException {
        refreshMetadata(readFullyAsByteArray(new URL(url).openStream()));
    }

    private void refreshMetadata(byte[] rawResponse) throws IOException {
        long timestamp = System.currentTimeMillis();
        String text = new String(rawResponse, UTF_8);
        try {
            setMetadataResponse(text, timestamp);
        } catch (JsonParseException e) {
            throw new IOException("Malformed response\n" + text, e);
        }

        metadataRefreshed = true;
        LOG.info("authlib-injector server metadata refreshed: " + url);
        Platform.runLater(helper::invalidate);
    }

    private void setMetadataResponse(String metadataResponse, long metadataTimestamp) throws JsonParseException {
        JsonObject response = GSON.fromJson(metadataResponse, JsonObject.class);
        if (response == null) {
            throw new JsonParseException("Metadata response is empty");
        }

        synchronized (this) {
            this.metadataResponse = metadataResponse;
            this.metadataTimestamp = metadataTimestamp;

            Optional<JsonObject> metaObject = tryCast(response.get("meta"), JsonObject.class);

            this.name = metaObject.flatMap(meta -> tryCast(meta.get("serverName"), JsonPrimitive.class).map(JsonPrimitive::getAsString))
                    .orElse(null);
        }
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

    @Override
    public String toString() {
        return name == null ? url : url + " (" + name + ")";
    }

    @Override
    public void addListener(InvalidationListener listener) {
        helper.addListener(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        helper.removeListener(listener);
    }

    public static class Deserializer implements JsonDeserializer<AuthlibInjectorServer> {
        @Override
        public AuthlibInjectorServer deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            JsonObject jsonObj = json.getAsJsonObject();
            AuthlibInjectorServer instance = new AuthlibInjectorServer(jsonObj.get("url").getAsString());

            if (jsonObj.has("name")) {
                instance.name = jsonObj.get("name").getAsString();
            }

            if (jsonObj.has("metadataResponse")) {
                try {
                    instance.setMetadataResponse(jsonObj.get("metadataResponse").getAsString(), jsonObj.get("metadataTimestamp").getAsLong());
                } catch (JsonParseException e) {
                    LOG.log(Level.WARNING, "Ignoring malformed metadata response cache: " + jsonObj.get("metadataResponse"), e);
                }
            }
            return instance;
        }

    }
}
