package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response;

import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.properties.PropertyMap;

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
