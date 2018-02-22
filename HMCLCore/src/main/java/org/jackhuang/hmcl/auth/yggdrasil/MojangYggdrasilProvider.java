package org.jackhuang.hmcl.auth.yggdrasil;

import org.jackhuang.hmcl.util.NetworkUtils;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import java.net.URL;
import java.util.UUID;

public class MojangYggdrasilProvider implements YggdrasilProvider {
    public static final MojangYggdrasilProvider INSTANCE = new MojangYggdrasilProvider();

    @Override
    public URL getAuthenticationURL() {
        return NetworkUtils.toURL("https://authserver.mojang.com/authenticate");
    }

    @Override
    public URL getRefreshmentURL() {
        return NetworkUtils.toURL("https://authserver.mojang.com/refresh");
    }

    @Override
    public URL getValidationURL() {
        return NetworkUtils.toURL("https://authserver.mojang.com/validate");
    }

    @Override
    public URL getInvalidationURL() {
        return NetworkUtils.toURL("https://authserver.mojang.com/invalidate");
    }

    @Override
    public URL getProfilePropertiesURL(UUID uuid) {
        return NetworkUtils.toURL("https://sessionserver.mojang.com/session/minecraft/profile/" + UUIDTypeAdapter.fromUUID(uuid));
    }
}
