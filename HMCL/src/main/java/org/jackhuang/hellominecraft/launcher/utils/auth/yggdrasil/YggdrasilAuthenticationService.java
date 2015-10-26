package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.properties.PropertyMap;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.UUID;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.GameProfile.GameProfileSerializer;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response.Response;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;

public class YggdrasilAuthenticationService {
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
    
    protected <T extends Response> T makeRequest(URL url, Object input, Class<T> classOfT) throws AuthenticationException {
        try {
            String jsonResult = input == null ? NetUtils.doGet(url) : NetUtils.post(url, this.gson.toJson(input), "application/json", proxy);
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
}
