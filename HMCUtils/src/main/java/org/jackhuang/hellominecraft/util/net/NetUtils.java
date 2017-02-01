/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.util.net;

import org.jackhuang.hellominecraft.util.log.HMCLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import org.jackhuang.hellominecraft.util.code.Charsets;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 *
 * @author huangyuhui
 */
public final class NetUtils {

    private NetUtils() {
    }

    public static HttpURLConnection createConnection(URL url, Proxy proxy) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection(proxy);
        con.setDoInput(true);
        con.setUseCaches(false);
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        return con;
    }

    public static String get(String url, String encoding) throws IOException {
        return IOUtils.toString(new URL(url).openConnection().getInputStream());
    }

    public static String get(String url) throws IOException {
        return get(url, IOUtils.DEFAULT_CHARSET);
    }

    public static String get(URL url, Proxy proxy) throws IOException {
        return readData(createConnection(url, proxy));
    }

    public static String post(URL u, Map<String, String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet())
                sb.append(e.getKey()).append("=").append(e.getValue()).append("&");
            sb.deleteCharAt(sb.length() - 1);
        }
        return post(u, sb.toString());
    }

    public static String post(URL u, String post) throws IOException {
        return post(u, post, "application/x-www-form-urlencoded");
    }

    public static String post(URL u, String post, String contentType) throws IOException {
        return post(u, post, contentType, Proxy.NO_PROXY);
    }

    public static String post(URL u, String post, String contentType, Proxy proxy) throws IOException {
        byte[] bytes = post.getBytes(Charsets.UTF_8);

        HttpURLConnection con = createConnection(u, proxy);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
        con.setRequestProperty("Content-Length", "" + bytes.length);
        OutputStream os = null;
        try {
            os = con.getOutputStream();
            IOUtils.write(bytes, os);
        } finally {
            IOUtils.closeQuietly(os);
        }
        return readData(con);
    }

    private static String readData(HttpURLConnection con) throws IOException {
        InputStream is = null;
        try {
            is = con.getInputStream();
            return IOUtils.toString(is, Charsets.UTF_8);
        } catch (IOException e) {
            IOUtils.closeQuietly(is);
            is = con.getErrorStream();
            if (is != null)
                return IOUtils.toString(is, Charsets.UTF_8);
            throw e;
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static URL constantURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException ex) {
            HMCLog.err("Failed to get url instance: " + url, ex);
            return null;
        }
    }
}
