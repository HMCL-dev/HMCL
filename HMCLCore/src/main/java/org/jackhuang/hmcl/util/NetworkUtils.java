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
package org.jackhuang.hmcl.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.StringUtils.*;

/**
 *
 * @author huangyuhui
 */
public final class NetworkUtils {

    private NetworkUtils() {
    }

    private static Supplier<String> userAgentSupplier = RandomUserAgent::randomUserAgent;

    public static String getUserAgent() {
        return userAgentSupplier.get();
    }

    public static void setUserAgentSupplier(Supplier<String> userAgentSupplier) {
        NetworkUtils.userAgentSupplier = Objects.requireNonNull(userAgentSupplier);
    }

    public static HttpURLConnection createConnection(URL url, Proxy proxy) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", getUserAgent());
        return connection;
    }

    public static String doGet(URL url) throws IOException {
        return IOUtils.readFullyAsString(createConnection(url, Proxy.NO_PROXY).getInputStream());
    }

    public static String doGet(URL url, Proxy proxy) throws IOException {
        return IOUtils.readFullyAsString(createConnection(url, proxy).getInputStream());
    }

    public static String doPost(URL u, Map<String, String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet())
                sb.append(e.getKey()).append("=").append(e.getValue()).append("&");
            sb.deleteCharAt(sb.length() - 1);
        }
        return doPost(u, sb.toString());
    }

    public static String doPost(URL u, String post) throws IOException {
        return doPost(u, post, "application/x-www-form-urlencoded");
    }

    public static String doPost(URL u, String post, String contentType) throws IOException {
        return doPost(u, post, contentType, Proxy.NO_PROXY);
    }

    public static String doPost(URL url, String post, String contentType, Proxy proxy) throws IOException {
        byte[] bytes = post.getBytes(UTF_8);

        HttpURLConnection con = createConnection(url, proxy);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
        con.setRequestProperty("Content-Length", "" + bytes.length);
        OutputStream os = null;
        try {
            os = con.getOutputStream();
            if (os != null)
                os.write(bytes);
        } finally {
            IOUtils.closeQuietly(os);
        }
        return readData(con);
    }

    public static String readData(HttpURLConnection con) throws IOException {
        InputStream is = null;
        try {
            is = con.getInputStream();
            return IOUtils.readFullyAsString(is, UTF_8);
        } catch (IOException e) {
            IOUtils.closeQuietly(is);
            is = con.getErrorStream();
            if (is != null)
                return IOUtils.readFullyAsString(is, UTF_8);
            throw e;
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static String detectFileName(URL url) throws IOException {
        return detectFileName(url, Proxy.NO_PROXY);
    }

    public static String detectFileName(URL url, Proxy proxy) throws IOException {
        HttpURLConnection conn = createConnection(url, proxy);
        conn.connect();
        if (conn.getResponseCode() / 100 != 2)
            throw new IOException("Response code " + conn.getResponseCode());

        return detectFileName(conn);
    }

    public static String detectFileName(HttpURLConnection conn) {
        String disposition = conn.getHeaderField("Content-Disposition");
        if (disposition == null || !disposition.contains("filename=")) {
            String u = conn.getURL().toString();
            return substringAfterLast(u, '/');
        } else
            return removeSurrounding(substringAfter(disposition, "filename="), "\"");
    }

    public static URL toURL(String str) {
        return Lang.invoke(() -> new URL(str));
    }

    public static boolean isURL(String str) {
        try {
            new URL(str);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
