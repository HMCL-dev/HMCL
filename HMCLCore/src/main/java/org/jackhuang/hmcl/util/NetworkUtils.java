/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
import java.net.Proxy;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import static org.jackhuang.hmcl.util.StringUtils.*;

/**
 *
 * @author huangyuhui
 */
public final class NetworkUtils {

    private NetworkUtils() {
    }

    private static final X509TrustManager XTM = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static final HostnameVerifier HNV = (a, b) -> true;

    private static volatile boolean initHttps = false;

    private static synchronized void initHttps() {
        if (initHttps)
            return;

        initHttps = true;

        System.setProperty("https.protocols", "SSLv3,TLSv1");
        try {
            SSLContext c = SSLContext.getInstance("SSL");
            c.init(null, new X509TrustManager[] { XTM }, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(c.getSocketFactory());
        } catch (GeneralSecurityException e) {
        }
        HttpsURLConnection.setDefaultHostnameVerifier(HNV);
    }

    private static Supplier<String> userAgentSupplier = () -> RandomUserAgent.randomUserAgent();

    public static String getUserAgent() {
        return userAgentSupplier.get();
    }

    public static void setUserAgentSupplier(Supplier<String> userAgentSupplier) {
        Objects.requireNonNull(userAgentSupplier);
        NetworkUtils.userAgentSupplier = userAgentSupplier;
    }

    public static HttpURLConnection createConnection(URL url, Proxy proxy) throws IOException {
        initHttps();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", getUserAgent());
        return connection;
    }

    public static String doGet(URL url) throws IOException {
        return IOUtils.readFullyAsString(url.openConnection().getInputStream());
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

    public static String doPost(URL u, String post, String contentType, Proxy proxy) throws IOException {
        byte[] bytes = post.getBytes(Charsets.UTF_8);

        HttpURLConnection con = createConnection(u, proxy);
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
            return IOUtils.readFullyAsString(is, Charsets.UTF_8);
        } catch (IOException e) {
            IOUtils.closeQuietly(is);
            is = con.getErrorStream();
            if (is != null)
                return IOUtils.readFullyAsString(is, Charsets.UTF_8);
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
}
