package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.properties.PropertyMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.UUID;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response.Response;
import org.jackhuang.hellominecraft.logging.logger.Logger;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.Utils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;

public class YggdrasilAuthenticationService {

    private static final Logger LOGGER = new Logger("HttpAuthenticationService");
    private final Proxy proxy;

    private final String clientToken;
    private final Gson gson;

    public YggdrasilAuthenticationService(Proxy proxy, String clientToken) {
        this.proxy = proxy;
        this.clientToken = clientToken;
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(GameProfile.class, new GameProfileSerializer());
        builder.registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer());
        builder.registerTypeAdapter(UUID.class, new UUIDTypeAdapter());
        this.gson = builder.create();
    }

    public YggdrasilUserAuthentication createUserAuthentication() {
        return new YggdrasilUserAuthentication(this);
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
        if (query == null)
            return "";
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

    protected <T extends Response> T makeRequest(URL url, Object input, Class<T> classOfT) throws AuthenticationException {
        try {
            String jsonResult = input == null ? performGetRequest(url) : performPostRequest(url, this.gson.toJson(input), "application/json");
            Response result = (Response) this.gson.fromJson(jsonResult, classOfT);

            if (result == null)
                return null;

            if (StrUtils.isNotBlank(result.error))
                throw new AuthenticationException("InvalidCredentials " + result.errorMessage);

            return (T) result;
        } catch (IOException | IllegalStateException | JsonParseException e) {
            throw new AuthenticationException(C.i18n("login.failed.connect_authentication_server"), e);
        }
    }

    public String getClientToken() {
        return this.clientToken;
    }

    private static class GameProfileSerializer implements JsonSerializer<GameProfile>, JsonDeserializer<GameProfile> {

        @Override
        public GameProfile deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject object = (JsonObject) json;
            UUID id = object.has("id") ? (UUID) context.deserialize(object.get("id"), UUID.class) : null;
            String name = object.has("name") ? object.getAsJsonPrimitive("name").getAsString() : null;
            return new GameProfile(id, name);
        }

        @Override
        public JsonElement serialize(GameProfile src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            if (src.id != null)
                result.add("id", context.serialize(src.id));
            if (src.name != null)
                result.addProperty("name", src.name);
            return result;
        }
    }
}
