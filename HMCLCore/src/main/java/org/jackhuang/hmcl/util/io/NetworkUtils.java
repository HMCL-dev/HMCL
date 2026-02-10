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
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class NetworkUtils {
    public static final String USER_AGENT = System.getProperty("http.agent", "HMCL");

    public static final String PARAMETER_SEPARATOR = "&";
    public static final String NAME_VALUE_SEPARATOR = "=";
    public static final int TIME_OUT = 8000;

    private NetworkUtils() {
    }

    public static boolean isLoopbackAddress(URI uri) {
        String host = uri.getHost();
        if (StringUtils.isBlank(host))
            return false;

        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isHttpUri(URI uri) {
        return "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
    }

    public static String addHttpsIfMissing(String url) {
        if (Pattern.compile("^(?<scheme>[a-zA-Z][a-zA-Z0-9+.-]*)://").matcher(url).find())
            return url;

        if (url.startsWith("//"))
            return "https:" + url;
        else
            return "https://" + url;
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

    public static String withQuery(String baseUrl, List<Pair<String, String>> params) {
        StringBuilder sb = new StringBuilder(baseUrl);
        boolean first = true;
        for (Pair<String, String> param : params) {
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

    public static List<URI> withQuery(List<URI> list, Map<String, String> params) {
        return list.stream().map(uri -> URI.create(withQuery(uri.toString(), params))).collect(Collectors.toList());
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
                if (nameValue.length == 0 || nameValue.length > 2) {
                    throw new IllegalArgumentException("bad query string");
                }

                String name = decodeURL(nameValue[0]);
                String value = nameValue.length == 2 ? decodeURL(nameValue[1]) : null;
                result.add(pair(name, value));
            }
        }
        return result;
    }

    public static URI dropQuery(URI u) {
        if (u.getRawQuery() == null && u.getRawFragment() == null) {
            return u;
        }

        try {
            return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), u.getPath(), null, null);
        } catch (URISyntaxException e) {
            throw new AssertionError("Unreachable", e);
        }
    }

    public static URLConnection createConnection(URI uri) throws IOException {
        URLConnection connection;
        try {
            connection = uri.toURL().openConnection();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IOException(e);
        }
        connection.setConnectTimeout(TIME_OUT);
        connection.setReadTimeout(TIME_OUT);
        if (connection instanceof HttpURLConnection httpConnection) {
            httpConnection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag());
            httpConnection.setRequestProperty("User-Agent", USER_AGENT);
            httpConnection.setInstanceFollowRedirects(false);
        }
        return connection;
    }

    public static HttpURLConnection createHttpConnection(String url) throws IOException {
        return (HttpURLConnection) createConnection(toURI(url));
    }

    public static HttpURLConnection createHttpConnection(URI url) throws IOException {
        return (HttpURLConnection) createConnection(url);
    }

    private static void encodeCodePoint(StringBuilder builder, int codePoint) {
        builder.append(encodeURL(Character.toString(codePoint)));
    }

    /**
     * @param location the url to be URL encoded
     * @return encoded URL
     * @see <a href=
     * "https://github.com/curl/curl/blob/3f7b1bb89f92c13e69ee51b710ac54f775aab320/lib/transfer.c#L1427-L1461">Curl</a>
     */
    public static String encodeLocation(String location) {
        int i = 0;
        boolean left = true;
        while (i < location.length()) {
            char ch = location.charAt(i);
            if (ch == ' '
                    || ch == '[' || ch == ']'
                    || ch == '{' || ch == '}'
                    || ch >= 0x80)
                break;
            else if (ch == '?')
                left = false;
            i++;
        }

        if (i == location.length()) {
            // No need to encode
            return location;
        }

        var builder = new StringBuilder(location.length() + 10);
        builder.append(location, 0, i);

        for (; i < location.length(); i++) {
            char ch = location.charAt(i);
            if (ch == ' ') {
                if (left)
                    builder.append("%20");
                else
                    builder.append('+');
            } else if (ch == '?') {
                left = false;
                builder.append('?');
            } else if (ch >= 0x80 || (left && (ch == '[' || ch == ']' || ch == '{' || ch == '}'))) {
                if (Character.isSurrogate(ch)) {
                    if (Character.isHighSurrogate(ch) && i < location.length() - 1) {
                        char ch2 = location.charAt(i + 1);
                        if (Character.isLowSurrogate(ch2)) {
                            int codePoint = Character.toCodePoint(ch, ch2);
                            encodeCodePoint(builder, codePoint);
                            i++;
                            continue;
                        }
                    }

                    // Invalid surrogate pair, encode as U+FFFD (replacement character)
                    encodeCodePoint(builder, 0xfffd);
                    continue;
                }

                encodeCodePoint(builder, ch);
            } else {
                builder.append(ch);
            }
        }

        return builder.toString();
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
    public static HttpURLConnection resolveConnection(HttpURLConnection conn) throws IOException {
        final boolean useCache = conn.getUseCaches();
        int redirect = 0;
        while (true) {
            conn.setUseCaches(useCache);
            conn.setConnectTimeout(TIME_OUT);
            conn.setReadTimeout(TIME_OUT);
            conn.setInstanceFollowRedirects(false);
            Map<String, List<String>> properties = conn.getRequestProperties();
            String method = conn.getRequestMethod();
            int code = conn.getResponseCode();
            if (code >= 300 && code <= 308 && code != 306 && code != 304) {
                String newURL = conn.getHeaderField("Location");
                conn.disconnect();

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

    public static String doGet(String uri) throws IOException {
        return doGet(toURI(uri));
    }

    public static String doGet(URI uri) throws IOException {
        URLConnection connection = createConnection(uri);
        if (connection instanceof HttpURLConnection httpURLConnection) {
            connection = resolveConnection(httpURLConnection);
        }
        return readFullyAsString(connection);
    }

    public static String doGet(List<URI> uris) throws IOException {
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
        byte[] bytes = post.getBytes(UTF_8);

        HttpURLConnection con = createHttpConnection(uri);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
        con.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = con.getOutputStream()) {
            os.write(bytes);
        }
        return readFullyAsString(con);
    }

    static final Pattern CHARSET_REGEX = Pattern.compile("\\s*(charset)\\s*=\\s*['|\"]?(?<charset>[^\"^';,]+)['|\"]?");

    public static Charset getCharsetFromContentType(String contentType) {
        if (contentType == null || contentType.isBlank())
            return UTF_8;

        Matcher matcher = CHARSET_REGEX.matcher(contentType);
        if (matcher.find()) {
            String charsetName = matcher.group("charset");
            try {
                return Charset.forName(charsetName);
            } catch (Throwable e) {
                // Ignore invalid charset
                LOG.warning("Bad charset name: " + charsetName + ", using UTF-8 instead", e);
            }
        }
        return UTF_8;
    }

    public static String readFullyAsString(URLConnection con) throws IOException {
        try {
            var contentEncoding = ContentEncoding.fromConnection(con);
            Charset charset = getCharsetFromContentType(con.getHeaderField("Content-Type"));

            try (InputStream stdout = con.getInputStream()) {
                return IOUtils.readFullyAsString(contentEncoding.wrap(stdout), charset);
            } catch (IOException e) {
                if (con instanceof HttpURLConnection) {
                    try (InputStream stderr = ((HttpURLConnection) con).getErrorStream()) {
                        if (stderr == null)
                            throw e;
                        return IOUtils.readFullyAsString(contentEncoding.wrap(stderr), charset);
                    }
                } else {
                    throw e;
                }
            }
        } finally {
            if (con instanceof HttpURLConnection) {
                ((HttpURLConnection) con).disconnect();
            }
        }
    }

    public static String detectFileName(URI uri) throws IOException {
        HttpURLConnection conn = resolveConnection(createHttpConnection(uri));
        int code = conn.getResponseCode();
        if (code / 100 == 4)
            throw new FileNotFoundException();
        if (code / 100 != 2)
            throw new ResponseCodeException(uri, conn.getResponseCode());

        String disposition = conn.getHeaderField("Content-Disposition");
        if (disposition == null || !disposition.contains("filename=")) {
            String u = conn.getURL().toString();
            return decodeURL(substringAfterLast(u, '/'));
        } else {
            return decodeURL(removeSurrounding(substringAfter(disposition, "filename="), "\""));
        }
    }

    // ==== Shortcut methods for encoding/decoding URLs in UTF-8 ====
    public static String encodeURL(String toEncode) {
        return URLEncoder.encode(toEncode, UTF_8);
    }

    public static String decodeURL(String toDecode) {
        return URLDecoder.decode(toDecode, UTF_8);
    }

    /// @throws IllegalArgumentException if the string is not a valid URI
    public static @NotNull URI toURI(@NotNull String uri) {
        try {
            return new URI(encodeLocation(uri));
        } catch (URISyntaxException e) {
            // Possibly an Internationalized Domain Name (IDN)
            return URI.create(uri);
        }
    }

    public static @NotNull URI toURI(@NotNull URL url) {
        return toURI(url.toExternalForm());
    }

    public static @Nullable URI toURIOrNull(String uri) {
        if (StringUtils.isNotBlank(uri)) {
            try {
                return toURI(uri);
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
