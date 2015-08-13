package org.jackhuang.mojang.authlib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import org.jackhuang.hellominecraft.logging.logger.Logger;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.Utils;

public abstract class HttpAuthenticationService extends BaseAuthenticationService {

    private static final Logger LOGGER = new Logger("HttpAuthenticationService");
    private final Proxy proxy;

    protected HttpAuthenticationService(Proxy proxy) {
        this.proxy = proxy;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    protected HttpURLConnection createUrlConnection(URL url) throws IOException {
        LOGGER.debug("Opening connection to " + url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(this.proxy);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        return connection;
    }

    public String performPostRequest(URL url, String post, String contentType) throws IOException {
        Utils.requireNonNull(url);
        Utils.requireNonNull(post);
        Utils.requireNonNull(contentType);
        HttpURLConnection connection = createUrlConnection(url);
        byte[] postAsBytes = post.getBytes("UTF-8");

        connection.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
        connection.setRequestProperty("Content-Length", "" + postAsBytes.length);
        connection.setDoOutput(true);

        LOGGER.debug("Writing POST data to " + url + ": " + post);

        OutputStream outputStream = null;
        try {
            outputStream = connection.getOutputStream();
            IOUtils.write(postAsBytes, outputStream);
        } finally {
            IOUtils.closeQuietly(outputStream);
        }

        LOGGER.debug("Reading data from " + url);

        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
            String result = NetUtils.getStreamContent(inputStream, "UTF-8");
            LOGGER.debug("Successful read, server response was " + connection.getResponseCode());
            LOGGER.debug("Response: " + result);
            String str1 = result;
            return str1;
        } catch (IOException e) {
            IOUtils.closeQuietly(inputStream);
            inputStream = connection.getErrorStream();

            if (inputStream != null) {
                LOGGER.debug("Reading error page from " + url);
                String result = NetUtils.getStreamContent(inputStream, "UTF-8");
                LOGGER.debug("Successful read, server response was " + connection.getResponseCode());
                LOGGER.debug("Response: " + result);
                String str2 = result;
                return str2;
            }
            LOGGER.debug("Request failed", e);
            throw e;
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    public String performGetRequest(URL url)
            throws IOException {
        Utils.requireNonNull(url);
        HttpURLConnection connection = createUrlConnection(url);

        LOGGER.debug("Reading data from " + url);

        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
            String result = NetUtils.getStreamContent(inputStream, "UTF-8");
            LOGGER.debug("Successful read, server response was " + connection.getResponseCode());
            LOGGER.debug("Response: " + result);
            String str1 = result;
            return str1;
        } catch (IOException e) {
            IOUtils.closeQuietly(inputStream);
            inputStream = connection.getErrorStream();

            if (inputStream != null) {
                LOGGER.debug("Reading error page from " + url);
                String result = NetUtils.getStreamContent(inputStream, "UTF-8");
                LOGGER.debug("Successful read, server response was " + connection.getResponseCode());
                LOGGER.debug("Response: " + result);
                String str2 = result;
                return str2;
            }
            LOGGER.debug("Request failed", e);
            throw e;
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    public static String buildQuery(Map<String, Object> query) {
        if (query == null) return "";
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if (builder.length() > 0)
                builder.append('&');
            try {
                builder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("Unexpected exception building query", e);
            }

            if (entry.getValue() != null) {
                builder.append('=');
                try {
                    builder.append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    LOGGER.error("Unexpected exception building query", e);
                }
            }
        }

        return builder.toString();
    }
}
