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
package org.jackhuang.hmcl.util.io;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.function.ExceptionalBiConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.wrap;
import static org.jackhuang.hmcl.util.gson.JsonUtils.GSON;
import static org.jackhuang.hmcl.util.io.NetworkUtils.createHttpConnection;
import static org.jackhuang.hmcl.util.io.NetworkUtils.resolveConnection;

public abstract class HttpRequest {
    protected final String url;
    protected final String method;
    protected final Map<String, String> headers = new HashMap<>();
    protected ExceptionalBiConsumer<URL, Integer, IOException> responseCodeTester;
    protected final Set<Integer> toleratedHttpCodes = new HashSet<>();
    protected int retryTimes = 1;
    protected boolean ignoreHttpCode;

    private HttpRequest(String url, String method) {
        this.url = url;
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public HttpRequest accept(String contentType) {
        return header("Accept", contentType);
    }

    public HttpRequest authorization(String token) {
        return header("Authorization", token);
    }

    public HttpRequest authorization(String tokenType, String tokenString) {
        return authorization(tokenType + " " + tokenString);
    }

    public HttpRequest authorization(Authorization authorization) {
        return authorization(authorization.getTokenType(), authorization.getAccessToken());
    }

    public HttpRequest header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public HttpRequest ignoreHttpCode() {
        ignoreHttpCode = true;
        return this;
    }

    public HttpRequest retry(int retryTimes) {
        if (retryTimes < 1) {
            throw new IllegalArgumentException("retryTimes >= 1");
        }
        this.retryTimes = retryTimes;
        return this;
    }

    public abstract String getString() throws IOException;

    public CompletableFuture<String> getStringAsync() {
        return CompletableFuture.supplyAsync(wrap(this::getString), Schedulers.io());
    }

    public <T> T getJson(Class<T> typeOfT) throws IOException, JsonParseException {
        return JsonUtils.fromNonNullJson(getString(), typeOfT);
    }

    public <T> T getJson(TypeToken<T> type) throws IOException, JsonParseException {
        return JsonUtils.fromNonNullJson(getString(), type);
    }

    public <T> CompletableFuture<T> getJsonAsync(Class<T> typeOfT) {
        return getStringAsync().thenApplyAsync(jsonString -> JsonUtils.fromNonNullJson(jsonString, typeOfT));
    }

    public <T> CompletableFuture<T> getJsonAsync(TypeToken<T> type) {
        return getStringAsync().thenApplyAsync(jsonString -> JsonUtils.fromNonNullJson(jsonString, type));
    }

    public HttpRequest ignoreHttpErrorCode(int code) {
        toleratedHttpCodes.add(code);
        return this;
    }

    public HttpURLConnection createConnection() throws IOException {
        HttpURLConnection con = createHttpConnection(url);
        con.setRequestMethod(method);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            con.setRequestProperty(entry.getKey(), entry.getValue());
        }
        return con;
    }

    public static class HttpGetRequest extends HttpRequest {
        protected HttpGetRequest(String url) {
            super(url, "GET");
        }

        public String getString() throws IOException {
            return getStringWithRetry(() -> {
                HttpURLConnection con = createConnection();
                con = resolveConnection(con);
                return IOUtils.readFullyAsString("gzip".equals(con.getContentEncoding()) ? IOUtils.wrapFromGZip(con.getInputStream()) : con.getInputStream());
            }, retryTimes);
        }
    }

    public static final class HttpPostRequest extends HttpRequest {
        private byte[] bytes;

        private HttpPostRequest(String url) {
            super(url, "POST");
        }

        public HttpPostRequest contentType(String contentType) {
            headers.put("Content-Type", contentType);
            return this;
        }

        public HttpPostRequest json(Object payload) throws JsonParseException {
            return string(payload instanceof String ? (String) payload : GSON.toJson(payload), "application/json");
        }

        public HttpPostRequest form(Map<String, String> params) {
            return string(NetworkUtils.withQuery("", params), "application/x-www-form-urlencoded");
        }

        @SafeVarargs
        public final HttpPostRequest form(Pair<String, String>... params) {
            return form(mapOf(params));
        }

        public HttpPostRequest string(String payload, String contentType) {
            bytes = payload.getBytes(UTF_8);
            header("Content-Length", "" + bytes.length);
            contentType(contentType + "; charset=utf-8");
            return this;
        }

        public String getString() throws IOException {
            return getStringWithRetry(() -> {
                HttpURLConnection con = createConnection();
                con.setDoOutput(true);

                try (OutputStream os = con.getOutputStream()) {
                    os.write(bytes);
                }

                URL url = new URL(this.url);

                if (con.getResponseCode() / 100 != 2) {
                    if (!ignoreHttpCode && !toleratedHttpCodes.contains(con.getResponseCode())) {
                        try {
                            throw new ResponseCodeException(url.toString(), con.getResponseCode(), NetworkUtils.readFullyAsString(con));
                        } catch (IOException e) {
                            throw new ResponseCodeException(url.toString(), con.getResponseCode(), e);
                        }
                    }
                }

                return NetworkUtils.readFullyAsString(con);
            }, retryTimes);
        }
    }

    public static HttpGetRequest GET(String url) {
        return new HttpGetRequest(url);
    }

    @SafeVarargs
    public static HttpGetRequest GET(String url, Pair<String, String>... query) {
        return GET(NetworkUtils.withQuery(url, mapOf(query)));
    }

    public static HttpPostRequest POST(String url) throws MalformedURLException {
        return new HttpPostRequest(url);
    }

    private static String getStringWithRetry(ExceptionalSupplier<String, IOException> supplier, int retryTimes) throws IOException {
        Throwable exception = null;
        for (int i = 0; i < retryTimes; i++) {
            try {
                return supplier.get();
            } catch (Throwable e) {
                exception = e;
            }
        }
        if (exception != null) {
            if (exception instanceof IOException) {
                throw (IOException) exception;
            } else {
                throw new IOException(exception);
            }
        }
        throw new IOException("retry 0");
    }

    public interface Authorization {
        String getTokenType();

        String getAccessToken();
    }
}
