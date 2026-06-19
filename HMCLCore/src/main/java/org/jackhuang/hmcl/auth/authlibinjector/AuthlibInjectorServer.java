/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import static java.util.Collections.emptyMap;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.NotNullByDefault;
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
@NotNullByDefault
public class AuthlibInjectorServer implements Observable {

    private static final Gson GSON = new GsonBuilder().create();

    public static AuthlibInjectorServer locateServer(String url) throws IOException {
        try {
            url = NetworkUtils.addHttpsIfMissing(url);

            WebURL webURL = WebURL.parseBrowserInput(url);
            url = webURL.toString();

            HttpURLConnection conn = NetworkUtils.createHttpConnection(webURL);
            conn = NetworkUtils.resolveConnection(conn);

            String ali = conn.getHeaderField("x-authlib-injector-api-location");
            if (ali != null) {
                WebURL absoluteAli = WebURL.parse(ali, WebURL.of(conn.getURL()));
                if (!urlEqualsIgnoreSlash(webURL.toString(), absoluteAli.toString())) {
                    conn.disconnect();
                    url = absoluteAli.toString();
                    conn = NetworkUtils.resolveConnection(NetworkUtils.createHttpConnection(absoluteAli));
                }
            }

            if (!url.endsWith("/"))
                url += "/";

            try {
                AuthlibInjectorServer server = new AuthlibInjectorServer(url);
                server.refreshMetadata(NetworkUtils.readFullyAsString(conn));
                return server;
            } finally {
                conn.disconnect();
            }
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }

    private static boolean urlEqualsIgnoreSlash(String a, String b) {
        if (!a.endsWith("/"))
            a += "/";
        if (!b.endsWith("/"))
            b += "/";
        return a.equals(b);
    }

    private final String url;
    @Nullable
    private transient String metadataResponse;
    private transient long metadataTimestamp;

    @Nullable
    private transient String name;
    private transient Map<String, String> links = emptyMap();
    private transient boolean nonEmailLogin;

    private transient boolean metadataRefreshed;
    private final transient ObservableHelper helper = new ObservableHelper(this);
    private final transient YggdrasilService yggdrasilService;

    public AuthlibInjectorServer(String url) {
        this.url = url;
        this.yggdrasilService = new YggdrasilService(new AuthlibInjectorProvider(url));
    }

    public String getUrl() {
        return url;
    }

    public YggdrasilService getYggdrasilService() {
        return yggdrasilService;
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

    public Map<String, String> getLinks() {
        return links;
    }

    public boolean isNonEmailLogin() {
        return nonEmailLogin;
    }

    public String fetchMetadataResponse() throws IOException {
        if (metadataResponse == null || !metadataRefreshed) {
            refreshMetadata();
        }
        return getMetadataResponse().get();
    }

    public void refreshMetadata() throws IOException {
        refreshMetadata(HttpRequest.GET(url).getString());
    }

    private void refreshMetadata(String text) throws IOException {
        long timestamp = System.currentTimeMillis();
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
            this.links = metaObject.flatMap(meta -> tryCast(meta.get("links"), JsonObject.class))
                    .map(linksObject -> {
                        Map<String, String> converted = new LinkedHashMap<>();
                        linksObject.entrySet().forEach(
                                entry -> tryCast(entry.getValue(), JsonPrimitive.class).ifPresent(element -> {
                                    converted.put(entry.getKey(), element.getAsString());
                                }));
                        return converted;
                    })
                    .orElse(emptyMap());
            this.nonEmailLogin = metaObject.flatMap(meta -> tryCast(meta.get("feature.non_email_login"), JsonPrimitive.class))
                    .map(it -> it.getAsBoolean())
                    .orElse(false);
        }
    }

    /// Restores a cached metadata response without marking it as freshly fetched.
    public void restoreMetadataCache(String metadataResponse, long metadataTimestamp) throws JsonParseException {
        setMetadataResponse(metadataResponse, metadataTimestamp);
    }

    public void invalidateMetadataCache() {
        metadataRefreshed = false;
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
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

    @NotNullByDefault
    public static class Deserializer implements JsonDeserializer<AuthlibInjectorServer> {
        @Override
        public AuthlibInjectorServer deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            JsonObject jsonObj = json.getAsJsonObject();
            return new AuthlibInjectorServer(jsonObj.get("url").getAsString());
        }

    }
}
