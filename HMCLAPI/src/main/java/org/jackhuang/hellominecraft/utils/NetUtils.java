/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.system.IOUtils;

/**
 *
 * @author huang
 */
public final class NetUtils {

    public static byte[] getBytesFromStream(InputStream is) throws IOException {
        ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream();
        byte[] arrayOfByte1 = new byte[1024];
        int i;
        while ((i = is.read(arrayOfByte1)) >= 0)
            localByteArrayOutputStream.write(arrayOfByte1, 0, i);
        is.close();
        return localByteArrayOutputStream.toByteArray();
    }

    public static String getStreamContent(InputStream is) throws IOException {
        return getStreamContent(is, DEFAULT_CHARSET);
    }

    public static String getStreamContent(InputStream is, String encoding)
    throws IOException {
        if (is == null) return null;
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader br = new InputStreamReader(is, encoding)) {
            int len;
            char[] buf = new char[16384];
            while ((len = br.read(buf)) != -1)
                sb.append(buf, 0, len);
        }
        return sb.toString();
    }

    public static String get(String url, String encoding) throws IOException {
        return getStreamContent(new URL(url).openConnection().getInputStream());
    }

    public static String get(String url) throws IOException {
        return get(url, DEFAULT_CHARSET);
    }

    public static String get(URL url) throws IOException {
        return get(url, Proxy.NO_PROXY);
    }

    public static String get(URL url, Proxy proxy) throws IOException {
        return getStreamContent(url.openConnection(proxy).getInputStream());
    }

    public static String post(URL u, Map<String, String> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                sb.append(e.getKey());
                sb.append("=");
                sb.append(e.getValue());
                sb.append("&");
            }
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
        HttpURLConnection con = (HttpURLConnection) u.openConnection(proxy);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setDoInput(true);
        con.setUseCaches(false);
        con.setConnectTimeout(30000);
        con.setReadTimeout(30000);
        con.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
        byte[] bytes = post.getBytes(DEFAULT_CHARSET);
        con.setRequestProperty("Content-Length", "" + bytes.length);
        con.connect();
        OutputStream os = null;
        try {
            os = con.getOutputStream();
            IOUtils.write(bytes, os);
        } finally {
            IOUtils.closeQuietly(os);
        }

        String result;
        InputStream is = null;
        try {
            is = con.getInputStream();
            result = getStreamContent(is);
        } catch (IOException ex) {
            IOUtils.closeQuietly(is);
            is = con.getErrorStream();
            result = getStreamContent(is);
        }

        con.disconnect();
        return result;
    }
    
    private static final String DEFAULT_CHARSET = "UTF-8";

    public static URL constantURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException ex) {
            HMCLog.err("Failed to get url instance: " + url, ex);
            return null;
        }
    }

    public static URL concatenateURL(URL url, String query) {
        try {
            if ((url.getQuery() != null) && (url.getQuery().length() > 0))
                return new URL(url.getProtocol(), url.getHost(), url.getPort(), new StringBuilder().append(url.getFile()).append("&").append(query).toString());
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), new StringBuilder().append(url.getFile()).append("?").append(query).toString());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Could not concatenate given URL with GET arguments!", ex);
        }
    }
}
