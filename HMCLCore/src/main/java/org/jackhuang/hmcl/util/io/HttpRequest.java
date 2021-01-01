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
import org.jackhuang.hmcl.util.function.ExceptionalBiConsumer;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.gson.JsonUtils.GSON;
import static org.jackhuang.hmcl.util.io.NetworkUtils.createHttpConnection;
import static org.jackhuang.hmcl.util.io.NetworkUtils.resolveConnection;

public abstract class HttpRequest {
    protected final URL url;
    protected final String method;
    protected final Map<String, String> headers = new HashMap<>();
    protected ExceptionalBiConsumer<URL, Integer, IOException> responseCodeTester;

    private HttpRequest(URL url, String method) {
        this.url = url;
        this.method = method;
    }

    public HttpRequest accept(String contentType) {
        return header("Accept", contentType);
    }

    public HttpRequest authorization(String token) {
        return header("Authorization", token);
    }

    public HttpRequest contentType(String contentType) {
        return header("Content-Type", contentType);
    }

    public HttpRequest header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public abstract String getString() throws IOException;

    public <T> T getJson(Class<T> typeOfT) throws IOException, JsonParseException {
        return JsonUtils.fromNonNullJson(getString(), typeOfT);
    }

    public HttpRequest filter(ExceptionalBiConsumer<URL, Integer, IOException> responseCodeTester) {
        this.responseCodeTester = responseCodeTester;
        return this;
    }

    protected HttpURLConnection createConnection() throws IOException {
        HttpURLConnection con = createHttpConnection(url);
        con.setRequestMethod(method);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            con.setRequestProperty(entry.getKey(), entry.getValue());
        }
        return con;
    }

    public static class HttpGetRequest extends HttpRequest {
        public HttpGetRequest(URL url) {
            super(url, "GET");
        }

        public String getString() throws IOException {
            HttpURLConnection con = createConnection();
            con = resolveConnection(con);
            return IOUtils.readFullyAsString(con.getInputStream());
        }
    }

    public static class HttpPostRequest extends HttpRequest {
        private byte[] bytes;

        public HttpPostRequest(URL url) {
            super(url, "POST");
        }

        public <T> HttpPostRequest json(Object payload) throws JsonParseException {
            return string(payload instanceof String ? (String) payload : GSON.toJson(payload),
                    "application/json");
        }

        public HttpPostRequest form(Map<String, String> params) {
            return string(NetworkUtils.withQuery("", params), "application/x-www-form-urlencoded");
        }

        public HttpPostRequest string(String payload, String contentType) {
            bytes = payload.getBytes(UTF_8);
            header("Content-Length", "" + bytes.length);
            contentType(contentType + "; charset=utf-8");
            return this;
        }

        public String getString() throws IOException {
            HttpURLConnection con = createConnection();
            con.setDoOutput(true);

            if (responseCodeTester != null) {
                responseCodeTester.accept(url, con.getResponseCode());
            }

            try (OutputStream os = con.getOutputStream()) {
                os.write(bytes);
            }
            return NetworkUtils.readData(con);
        }
    }

    public static HttpGetRequest GET(String url) throws MalformedURLException {
        return GET(new URL(url));
    }

    public static HttpGetRequest GET(URL url) {
        return new HttpGetRequest(url);
    }

    public static HttpPostRequest POST(String url) throws MalformedURLException {
        return POST(new URL(url));
    }

    public static HttpPostRequest POST(URL url) {
        return new HttpPostRequest(url);
    }
}
