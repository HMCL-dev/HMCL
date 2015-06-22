package org.jackhuang.mojang.authlib.yggdrasil.response;

import org.jackhuang.mojang.authlib.properties.PropertyMap;
import java.util.UUID;

public class HasJoinedMinecraftServerResponse extends Response {

    private UUID id;
    private PropertyMap properties;

    public UUID getId() {
        return this.id;
    }

    public PropertyMap getProperties() {
        return this.properties;
    }
}
