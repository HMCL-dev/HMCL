package org.jackhuang.mojang.authlib.yggdrasil.response;

import org.jackhuang.mojang.authlib.properties.PropertyMap;

public class User {

    private String id;
    private PropertyMap properties;

    public String getId() {
        return this.id;
    }

    public PropertyMap getProperties() {
        return this.properties;
    }
}
