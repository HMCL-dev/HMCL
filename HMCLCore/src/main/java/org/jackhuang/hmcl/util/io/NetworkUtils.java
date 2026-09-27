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

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.time.Duration;
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

    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final int TIMEOUT_MILLIS = (int) TIMEOUT.toMillis();

    private NetworkUtils() {
    }

    /// Resolves the URL host and returns whether it is a loopback address.
    /// Returns `false` when the host is absent or cannot be resolved.
    public static boolean isLoopbackAddress(WebURL url) {
        return isLoopbackHost(url.getHost());
    }

    /// Resolves the host of a JDK URI, including addresses supplied by a proxy selector.
    /// Returns `false` when the host is absent or cannot be resolved.
    public static boolean isLoopbackAddress(URI uri) {
        return isLoopbackHost(uri.getHost());
    }

    /// Returns whether the host resolves to a loopback address.
    private static boolean isLoopbackHost(@Nullable String host) {
        if (StringUtils.isBlank(host))
            return false;

        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /// Returns whether the URL uses HTTP or HTTPS.
    public static boolean isHttpUri(WebURL url) {
        return "http".equals(url.getScheme()) || "https".equals(url.getScheme());
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

    /// Returns URLs with their queries replaced by the form-encoded non-null parameters.
    /// Fragments and existing path escapes are preserved. If no non-null parameters are supplied,
    /// returns a copy of the list with the URLs unchanged.
    public static List<WebURL> withQuery(List<WebURL> list, Map<String, @Nullable String> params) {
        String query = withQuery("", params);
        if (query.isEmpty())
            return new ArrayList<>(list);
        return list.stream().map(uri -> WebURL.newBuilder(uri).setRawQuery(query).build()).collect(Collectors.toList());
    }

    /// Parses the URL query as form-encoded name/value pairs.
    public static List<Pair<String, String>> parseQuery(WebURL url) {
        return parseQuery(url.getRawQuery());
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

    /// Returns the URL without its query or fragment, preserving encoded path delimiters.
    public static WebURL dropQuery(WebURL u) {
        if (u.getRawQuery() == null && u.getRawFragment() == null) {
            return u;
        }

        return WebURL.newBuilder(u).setRawQuery(null).setRawFragment(null).build();
    }

    private static final List<Pair<String, String>> API_KEYS;

    static {
        if (CurseForgeRemoteAddonRepository.API_KEY.isEmpty()) {
            API_KEYS = List.of();
        } else {
            API_KEYS = List.of(
                    pair("api.curseforge.com", CurseForgeRemoteAddonRepository.API_KEY),
                    pair("forgecdn.net", CurseForgeRemoteAddonRepository.API_KEY)
            );
        }
    }

    private static boolean matchDomainSuffix(String domain, String suffix) {
        return domain.endsWith(suffix)
                && (domain.length() == suffix.length() || domain.charAt(domain.length() - suffix.length() - 1) == '.');
    }

    public static void injectApiKey(WebURL url, URLConnection connection) {
        if (!(connection instanceof HttpURLConnection))
            return;

        if (connection.getRequestProperty("x-api-key") != null) {
            return;
        }

        String host = url.getHost();
        if (host == null || host.isEmpty())
            return;

        for (Pair<String, String> pair : API_KEYS) {
            String hostSuffix = pair.getKey();
            if (matchDomainSuffix(host, hostSuffix)) {
                connection.addRequestProperty("x-api-key", pair.getValue());
                return;
            }
        }
    }

    public static URLConnection createConnection(WebURL url) throws IOException {
        URLConnection connection;
        try {
            connection = url.toURL().openConnection();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IOException(e);
        }
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        if (connection instanceof HttpURLConnection httpConnection) {
            httpConnection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag());
            httpConnection.setRequestProperty("User-Agent", USER_AGENT);
            httpConnection.setInstanceFollowRedirects(false);
            injectApiKey(url, connection);
        }
        return connection;
    }

    public static HttpURLConnection createHttpConnection(WebURL url) throws IOException {
        return (HttpURLConnection) createConnection(url);
    }

    public static HttpURLConnection createHttpConnection(String url) throws IOException {
        return (HttpURLConnection) createConnection(WebURL.parse(url));
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
            conn.setConnectTimeout(TIMEOUT_MILLIS);
            conn.setReadTimeout(TIMEOUT_MILLIS);
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

                WebURL redirectedUrl = WebURL.of(conn.getURL()).resolve(newURL);
                HttpURLConnection redirected = (HttpURLConnection) redirectedUrl.toURL().openConnection();
                properties.forEach((key, value) -> value.forEach(element -> redirected.addRequestProperty(key, element)));
                injectApiKey(redirectedUrl, redirected);
                redirected.setRequestMethod(method);
                conn = redirected;
                ++redirect;
            } else {
                break;
            }
        }
        return conn;
    }

    public static String doGet(String url) throws IOException {
        return doGet(WebURL.parse(url));
    }

    /// Reads a URL as text, following HTTP redirects and decoding the response content.
    public static String doGet(WebURL url) throws IOException {
        URLConnection connection = createConnection(url);
        if (connection instanceof HttpURLConnection httpURLConnection) {
            connection = resolveConnection(httpURLConnection);
        }
        return readFullyAsString(connection);
    }

    /// Tries candidate URLs in order until one succeeds, or throws their I/O failures.
    public static String doGet(List<WebURL> urls) throws IOException {
        @Nullable List<IOException> exceptions = null;
        for (WebURL url : urls) {
            try {
                return doGet(url);
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

    /// Posts UTF-8 form content and reads the response as text.
    public static String doPost(WebURL url, String post) throws IOException {
        return doPost(url, post, "application/x-www-form-urlencoded");
    }

    /// Form-encodes the parameters and posts them to the URL.
    public static String doPost(WebURL url, @Nullable Map<String, String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet())
                sb.append(encodeURL(e.getKey())).append("=").append(encodeURL(e.getValue())).append("&");
            sb.deleteCharAt(sb.length() - 1);
        }
        return doPost(url, sb.toString());
    }

    /// Posts UTF-8 content with the given media type and reads the response as text.
    public static String doPost(WebURL url, String post, String contentType) throws IOException {
        byte[] bytes = post.getBytes(UTF_8);

        HttpURLConnection con = createHttpConnection(url);
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

    /// Reads a successful HTTP response's filename from its disposition header or final URL.
    public static String detectFileName(WebURL url) throws IOException {
        HttpURLConnection conn = resolveConnection(createHttpConnection(url));
        int code = conn.getResponseCode();
        if (code / 100 == 4)
            throw new FileNotFoundException();
        if (code / 100 != 2)
            throw new ResponseCodeException(url, conn.getResponseCode());

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

    /// Parses an absolute URL, or returns `null` for null, blank, or invalid input.
    public static @Nullable WebURL toWebURLOrNull(@Nullable String url) {
        return StringUtils.isBlank(url) ? null : WebURL.tryParse(url);
    }
}
