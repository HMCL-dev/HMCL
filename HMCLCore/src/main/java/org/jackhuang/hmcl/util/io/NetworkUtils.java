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

import org.jackhuang.hmcl.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.*;

/**
 * @author huangyuhui
 */
public final class NetworkUtils {
    public static final String PARAMETER_SEPARATOR = "&";
    public static final String NAME_VALUE_SEPARATOR = "=";
    private static final int TIME_OUT = 8000;

    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIME_OUT))
            .build();

    private NetworkUtils() {
    }

    public static boolean isHttpUri(URI uri) {
        return "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
    }

    public static String withQuery(String baseUrl, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(baseUrl);
        boolean first = true;
        for (Entry<String, String> param : params.entrySet()) {
            if (param.getValue() == null)
                continue;
            if (first) {
                if (!baseUrl.isEmpty()) {
                    sb.append('?');
                }
                first = false;
            } else {
                sb.append(PARAMETER_SEPARATOR);
            }
            sb.append(encodeURL(param.getKey()));
            sb.append(NAME_VALUE_SEPARATOR);
            sb.append(encodeURL(param.getValue()));
        }
        return sb.toString();
    }

    public static List<Pair<String, String>> parseQuery(URI uri) {
        return parseQuery(uri.getRawQuery());
    }

    public static List<Pair<String, String>> parseQuery(String queryParameterString) {
        if (queryParameterString == null) return Collections.emptyList();

        List<Pair<String, String>> result = new ArrayList<>();

        try (Scanner scanner = new Scanner(queryParameterString)) {
            scanner.useDelimiter("&");
            while (scanner.hasNext()) {
                String[] nameValue = scanner.next().split(NAME_VALUE_SEPARATOR);
                if (nameValue.length <= 0 || nameValue.length > 2) {
                    throw new IllegalArgumentException("bad query string");
                }

                String name = decodeURL(nameValue[0]);
                String value = nameValue.length == 2 ? decodeURL(nameValue[1]) : null;
                result.add(pair(name, value));
            }
        }
        return result;
    }

    public static URLConnection createConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(TIME_OUT);
        connection.setReadTimeout(TIME_OUT);
        connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag());
        return connection;
    }

    public static HttpURLConnection createHttpConnection(URL url) throws IOException {
        return (HttpURLConnection) createConnection(url);
    }

    /**
     * @param location the url to be URL encoded
     * @return encoded URL
     * @see <a href=
     * "https://github.com/curl/curl/blob/3f7b1bb89f92c13e69ee51b710ac54f775aab320/lib/transfer.c#L1427-L1461">Curl</a>
     */
    public static String encodeLocation(String location) {
        StringBuilder sb = new StringBuilder();
        boolean left = true;
        for (char ch : location.toCharArray()) {
            switch (ch) {
                case ' ':
                    if (left)
                        sb.append("%20");
                    else
                        sb.append('+');
                    break;
                case '?':
                    left = false;
                    // fallthrough
                default:
                    if (ch >= 0x80)
                        sb.append(encodeURL(Character.toString(ch)));
                    else
                        sb.append(ch);
                    break;
            }
        }

        return sb.toString();
    }

    public static HttpURLConnection resolveConnection(HttpURLConnection conn) throws IOException {
        return resolveConnection(conn, null);
    }

    /**
     * This method is a work-around that aims to solve problem when "Location" in
     * stupid server's response is not encoded.
     *
     * @param conn the stupid http connection.
     * @return manually redirected http connection.
     * @throws IOException if an I/O error occurs.
     * @see <a href="https://github.com/curl/curl/issues/473">Issue with libcurl</a>
     */
    public static HttpURLConnection resolveConnection(HttpURLConnection conn, List<String> redirects) throws IOException {
        int redirect = 0;
        while (true) {
            conn.setUseCaches(false);
            conn.setConnectTimeout(TIME_OUT);
            conn.setReadTimeout(TIME_OUT);
            conn.setInstanceFollowRedirects(false);
            Map<String, List<String>> properties = conn.getRequestProperties();
            String method = conn.getRequestMethod();
            int code = conn.getResponseCode();
            if (code >= 300 && code <= 307 && code != 306 && code != 304) {
                String newURL = conn.getHeaderField("Location");
                conn.disconnect();

                if (redirects != null) {
                    redirects.add(newURL);
                }
                if (redirect > 20) {
                    throw new IOException("Too much redirects");
                }

                HttpURLConnection redirected = (HttpURLConnection) new URL(conn.getURL(), encodeLocation(newURL))
                        .openConnection();
                properties
                        .forEach((key, value) -> value.forEach(element -> redirected.addRequestProperty(key, element)));
                redirected.setRequestMethod(method);
                conn = redirected;
                ++redirect;
            } else {
                break;
            }
        }
        return conn;
    }

    public static <T> HttpResponse<T> resolveResponse(HttpResponse<T> response,
                                                      HttpResponse.BodyHandler<T> responseBodyHandler,
                                                      @Nullable List<URI> redirects) throws IOException {
        assert response.request().method().equals("GET");

        int redirect = 0;
        while (true) {
            int code = response.statusCode();
            URI oldUri = response.uri();
            String originMethod = response.request().method();
            if (code >= 300 && code <= 308 && code != 306 && code != 304) {
                URI newUri = oldUri.resolve(response.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("no location header")));
                if (!newUri.getScheme().equals("http") && !newUri.getScheme().equals("https"))
                    throw new IOException("bad redirect target: " + newUri);

                if (redirects != null)
                    redirects.add(newUri);
                if (redirect > 20)
                    throw new IOException("Too much redirects");

                HttpRequest.Builder builder = HttpRequest.newBuilder(newUri);
                switch (code) {
                    case 301:
                    case 302:
                        if (originMethod.equals("POST"))
                            builder.GET();
                        else
                            builder.method(originMethod, response.request().bodyPublisher()
                                    .orElse(HttpRequest.BodyPublishers.noBody()));
                        break;
                    case 303:
                        builder.GET();
                        break;
                    case 307:
                    case 308:
                        builder.method(originMethod, response.request().bodyPublisher()
                                .orElse(HttpRequest.BodyPublishers.noBody()));
                }

                response.request().headers().map().forEach((key, values) -> {
                    for (String value : values) {
                        builder.setHeader(key, value);
                    }
                });

                try {
                    response = HTTP_CLIENT.send(builder.build(), responseBodyHandler);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }

                redirect++;
            } else {
                return response;
            }
        }
    }

    public static <T> T readResponse(HttpResponse<T> response) throws IOException {
        if (response.statusCode() / 100 == 4) {
            throw new FileNotFoundException(response.uri().toString());
        }

        if (response.statusCode() / 100 != 2) {
            throw new ResponseCodeException(response.uri().toURL(), response.statusCode());
        }

        return response.body();
    }

    public static String doGet(URI uri) throws IOException {
        try {
            var request = HttpRequest.newBuilder(uri).build();
            var bodyHandler = HttpResponse.BodyHandlers.ofString();
            HttpResponse<String> response = resolveResponse(
                    HTTP_CLIENT.send(request, bodyHandler), bodyHandler, null);
            readResponse(response);
            return response.body();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    public static String doGet(List<URI> uris) throws IOException { // TODO: rename
        List<IOException> exceptions = null;
        for (URI uri : uris) {
            try {
                return doGet(uri);
            } catch (IOException e) {
                if (exceptions == null) {
                    exceptions = new ArrayList<>(1);
                }
                exceptions.add(e);
            }
        }

        if (exceptions == null) {
            throw new IOException("No candidate URL");
        } else if (exceptions.size() == 1) {
            throw exceptions.get(0);
        } else {
            IOException exception = new IOException("Failed to doGet");
            for (IOException e : exceptions) {
                exception.addSuppressed(e);
            }
            throw exception;
        }
    }

    public static String doPost(URI uri, String post) throws IOException {
        return doPost(uri, post, "application/x-www-form-urlencoded");
    }

    public static String doPost(URI u, Map<String, String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet())
                sb.append(e.getKey()).append("=").append(e.getValue()).append("&");
            sb.deleteCharAt(sb.length() - 1);
        }
        return doPost(u, sb.toString());
    }

    public static String doPost(URI uri, String post, String contentType) throws IOException {
        try {
            return readResponse(HTTP_CLIENT.send(HttpRequest.newBuilder(uri)
                            .POST(HttpRequest.BodyPublishers.ofString(post))
                            .setHeader("Content-Type", contentType + "; charset=utf-8")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()));
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    public static String readData(HttpURLConnection con) throws IOException {
        try {
            try (InputStream stdout = con.getInputStream()) {
                return IOUtils.readFullyAsString("gzip".equals(con.getContentEncoding()) ? IOUtils.wrapFromGZip(stdout) : stdout);
            }
        } catch (IOException e) {
            try (InputStream stderr = con.getErrorStream()) {
                if (stderr == null)
                    throw e;
                return IOUtils.readFullyAsString("gzip".equals(con.getContentEncoding()) ? IOUtils.wrapFromGZip(stderr) : stderr);
            }
        }
    }

    public static String detectFileName(URI uri) throws IOException {
        return detectFileName(uri.toURL());
    }

    public static String detectFileName(URL url) throws IOException {
        HttpURLConnection conn = resolveConnection(createHttpConnection(url));
        int code = conn.getResponseCode();
        if (code / 100 == 4)
            throw new FileNotFoundException();
        if (code / 100 != 2)
            throw new IOException(url + ": response code " + conn.getResponseCode());

        return detectFileName(conn);
    }

    public static String detectFileName(HttpURLConnection conn) {
        String disposition = conn.getHeaderField("Content-Disposition");
        if (disposition == null || !disposition.contains("filename=")) {
            String u = conn.getURL().toString();
            return decodeURL(substringAfterLast(u, '/'));
        } else {
            return decodeURL(removeSurrounding(substringAfter(disposition, "filename="), "\""));
        }
    }

    public static URL toURL(String str) {
        try {
            return new URL(str);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static URL toURL(URI uri) {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean urlExists(URL url) throws IOException {
        HttpURLConnection con = createHttpConnection(url);
        con = resolveConnection(con);
        int responseCode = con.getResponseCode();
        con.disconnect();
        return responseCode / 100 == 2;
    }

    // ==== Shortcut methods for encoding/decoding URLs in UTF-8 ====
    public static String encodeURL(String toEncode) {
        return URLEncoder.encode(toEncode, UTF_8);
    }

    public static String decodeURL(String toDecode) {
        return URLDecoder.decode(toDecode, UTF_8);
    }
    // ====
}
