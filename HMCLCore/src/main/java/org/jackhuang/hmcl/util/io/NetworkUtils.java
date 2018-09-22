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
package org.jackhuang.hmcl.util.io;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;

import org.jackhuang.hmcl.util.Lang;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.StringUtils.*;

/**
 *
 * @author huangyuhui
 */
public final class NetworkUtils {

    private NetworkUtils() {
    }

    public static String withQuery(String baseUrl, Map<String, String> params) {
        try {
            StringBuilder sb = new StringBuilder(baseUrl);
            boolean first = true;
            for (Entry<String, String> param : params.entrySet()) {
                if (first) {
                    sb.append('?');
                    first = false;
                } else {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(param.getValue(), "UTF-8"));
            }
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static HttpURLConnection createConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        return connection;
    }

    public static String doGet(URL url) throws IOException {
        return IOUtils.readFullyAsString(createConnection(url).getInputStream());
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

    public static String doPost(URL url, String post, String contentType) throws IOException {
        byte[] bytes = post.getBytes(UTF_8);

        HttpURLConnection con = createConnection(url);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
        con.setRequestProperty("Content-Length", "" + bytes.length);
        try (OutputStream os = con.getOutputStream()) {
            os.write(bytes);
        }
        return readData(con);
    }

    public static String readData(HttpURLConnection con) throws IOException {
        try {
            try (InputStream stdout = con.getInputStream()) {
                return IOUtils.readFullyAsString(stdout, UTF_8);
            }
        } catch (IOException e) {
            try (InputStream stderr = con.getErrorStream()) {
                if (stderr == null) throw e;
                return IOUtils.readFullyAsString(stderr, UTF_8);
            }
        }
    }

    public static String detectFileName(URL url) throws IOException {
        HttpURLConnection conn = createConnection(url);
        conn.connect();
        if (conn.getResponseCode() / 100 != 2)
            throw new IOException("Response code " + conn.getResponseCode());

        return detectFileName(conn);
    }

    public static String detectFileName(HttpURLConnection conn) {
        String disposition = conn.getHeaderField("Content-Disposition");
        if (disposition == null || !disposition.contains("filename=")) {
            String u = conn.getURL().toString();
            return Lang.invoke(() -> URLDecoder.decode(substringAfterLast(u, '/'), StandardCharsets.UTF_8.name()));
        } else
            return Lang.invoke(() -> URLDecoder.decode(removeSurrounding(substringAfter(disposition, "filename="), "\""), StandardCharsets.UTF_8.name()));
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

    public static boolean URLExists(URL url) throws IOException {
        try (InputStream stream = url.openStream()) {
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }
}
