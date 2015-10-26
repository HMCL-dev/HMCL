/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.utils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
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
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader br = new InputStreamReader(is, encoding)) {
            int len;
            char[] buf = new char[16384];
            while ((len = br.read(buf)) != -1)
                sb.append(buf, 0, len);
        }
        return sb.toString();
    }

    public static String doGet(String url, String encoding) throws IOException {
        return getStreamContent(new URL(url).openConnection().getInputStream());
    }

    public static String doGet(String url) throws IOException {
        return doGet(url, DEFAULT_CHARSET);
    }

    public static String doGet(URL url) throws IOException {
        return doGet(url, Proxy.NO_PROXY);
    }

    public static String doGet(URL url, Proxy proxy) throws IOException {
        return getStreamContent(url.openConnection(proxy).getInputStream());
    }

    /**
     * Sends an HTTP GET request to a url
     *
     * @param endpoint - The URL of the server. (Example: "
     * http://www.yahoo.com/search")
     * @param requestParameters - all the request parameters (Example:
     * "param1=val1&param2=val2"). Note: This method will add the question mark
     * (?) to the request - DO NOT add it yourself
     * @return - The response from the end point
     */
    public static String sendGetRequest(String endpoint,
            String requestParameters) {
        String result = null;
        if (endpoint.startsWith("http://"))
            // Send a GET request to the servlet
            try {
                // Construct data
                StringBuilder data = new StringBuilder();
                // Send data
                String urlStr = endpoint;
                if (requestParameters != null && requestParameters.length() > 0)
                    urlStr += "?" + requestParameters;
                URL url = new URL(urlStr);
                URLConnection conn = url.openConnection();

                // Get the response
                InputStreamReader r = new InputStreamReader(conn.getInputStream());
                StringBuffer sb;
                BufferedReader rd = new BufferedReader(r);
                sb = new StringBuffer();
                String line;
                while ((line = rd.readLine()) != null)
                    sb.append(line);
                result = sb.toString();
            } catch (Exception e) {
                HMCLog.warn("Failed to send get request.", e);
            }
        return result;
    }
    
    public static String post(URL u, Map<String, String> params) {
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

    public static String post(URL u, String post) {
        return post(u, post, "application/x-www-form-urlencoded");
    }
    
    public static String post(URL u, String post, String contentType) {
        return post(u, post, contentType, Proxy.NO_PROXY);
    }
    
    public static String post(URL u, String post, String contentType, Proxy proxy) {
        try {
            HttpURLConnection con = (HttpURLConnection) u.openConnection(proxy);
            con.setRequestMethod(METHOD_POST);
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setUseCaches(false);
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            con.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
            con.setRequestProperty("Content-Length", "" + post.getBytes(DEFAULT_CHARSET).length);
            OutputStream os = null;
            try {
                os = con.getOutputStream();
                IOUtils.write(post, os, DEFAULT_CHARSET);
            } finally {
                if (os != null) IOUtils.closeQuietly(os);
            }
            
            String result = null;
            InputStream is = null;
            try {
                is = con.getInputStream();
                result = getStreamContent(is);
            } catch(IOException ex) {
                if (is != null) IOUtils.closeQuietly(is);
                is = con.getErrorStream();
                result = getStreamContent(is);
            }

            con.disconnect();
            return result;
        } catch (Exception e) {
            HMCLog.warn("Failed to post.", e);
            return null;
        }
    }
    private static final String METHOD_POST = "POST";
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
