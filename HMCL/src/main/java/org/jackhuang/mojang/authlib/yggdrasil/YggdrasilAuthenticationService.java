package org.jackhuang.mojang.authlib.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.jackhuang.mojang.authlib.Agent;
import org.jackhuang.mojang.authlib.GameProfile;
import org.jackhuang.mojang.authlib.GameProfileRepository;
import org.jackhuang.mojang.authlib.HttpAuthenticationService;
import org.jackhuang.mojang.authlib.UserAuthentication;
import org.jackhuang.mojang.authlib.exceptions.AuthenticationException;
import org.jackhuang.mojang.authlib.exceptions.AuthenticationUnavailableException;
import org.jackhuang.mojang.authlib.exceptions.InvalidCredentialsException;
import org.jackhuang.mojang.authlib.exceptions.UserMigratedException;
import org.jackhuang.mojang.authlib.minecraft.MinecraftSessionService;
import org.jackhuang.mojang.authlib.properties.PropertyMap;
import org.jackhuang.mojang.authlib.yggdrasil.response.Response;
import org.jackhuang.mojang.util.UUIDTypeAdapter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.Proxy;
import java.net.URL;
import java.util.UUID;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.StrUtils;

public class YggdrasilAuthenticationService extends HttpAuthenticationService {

    private final String clientToken;
    private final Gson gson;

    public YggdrasilAuthenticationService(Proxy proxy, String clientToken) {
        super(proxy);
        this.clientToken = clientToken;
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(GameProfile.class, new GameProfileSerializer());
        builder.registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer());
        builder.registerTypeAdapter(UUID.class, new UUIDTypeAdapter());
        this.gson = builder.create();
    }

    @Override
    public UserAuthentication createUserAuthentication(Agent agent) {
        return new YggdrasilUserAuthentication(this, agent);
    }

    @Override
    public MinecraftSessionService createMinecraftSessionService() {
        return new YggdrasilMinecraftSessionService(this);
    }

    @Override
    public GameProfileRepository createProfileRepository() {
        return new YggdrasilGameProfileRepository(this);
    }

    protected <T extends Response> T makeRequest(URL url, Object input, Class<T> classOfT) throws AuthenticationException {
        try {
            String jsonResult = input == null ? performGetRequest(url) : performPostRequest(url, this.gson.toJson(input), "application/json");
            Response result = (Response) this.gson.fromJson(jsonResult, classOfT);

            if (result == null) {
                return null;
            }

            if (StrUtils.isNotBlank(result.getError())) {
                if ("UserMigratedException".equals(result.getCause())) {
                    throw new UserMigratedException(result.getErrorMessage());
                }
                if (result.getError().equals("ForbiddenOperationException")) {
                    throw new InvalidCredentialsException(result.getErrorMessage());
                }
                throw new AuthenticationException(result.getErrorMessage());
            }

            return (T) result;
        } catch (IOException | IllegalStateException | JsonParseException e) {
            throw new AuthenticationUnavailableException(C.i18n("login.failed.connect_authentication_server"), e);
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
            if (src.getId() != null) {
                result.add("id", context.serialize(src.getId()));
            }
            if (src.getName() != null) {
                result.addProperty("name", src.getName());
            }
            return result;
        }
    }
}