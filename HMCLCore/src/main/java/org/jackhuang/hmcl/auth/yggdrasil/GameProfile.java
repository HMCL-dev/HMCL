/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.*;
import org.jackhuang.hmcl.auth.UserType;
import org.jackhuang.hmcl.util.Immutable;

import java.lang.reflect.Type;
import java.util.UUID;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class GameProfile {

    private final UUID id;
    private final String name;
    private final PropertyMap properties;
    private final boolean legacy;

    public GameProfile() {
        this(null, null);
    }

    public GameProfile(UUID id, String name) {
        this(id, name, new PropertyMap());
    }

    public GameProfile(UUID id, String name, PropertyMap properties) {
        this(id, name, properties, false);
    }

    public GameProfile(UUID id, String name, PropertyMap properties, boolean legacy) {
        this.id = id;
        this.name = name;
        this.properties = properties;
        this.legacy = legacy;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * @return nullable
     */
    public PropertyMap getProperties() {
        return properties;
    }

    public boolean isLegacy() {
        return legacy;
    }

    public UserType getUserType() {
        return UserType.fromLegacy(isLegacy());
    }

    public static class Serializer implements JsonSerializer<GameProfile>, JsonDeserializer<GameProfile> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public JsonElement serialize(GameProfile src, Type type, JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            if (src.getId() != null)
                result.add("id", context.serialize(src.getId()));
            if (src.getName() != null)
                result.addProperty("name", src.getName());
            return result;
        }

        @Override
        public GameProfile deserialize(JsonElement je, Type type, JsonDeserializationContext context) throws JsonParseException {
            if (!(je instanceof JsonObject))
                throw new JsonParseException("The json element is not a JsonObject.");

            JsonObject json = (JsonObject) je;

            UUID id = json.has("id") ? context.deserialize(json.get("id"), UUID.class) : null;
            String name = json.has("name") ? json.getAsJsonPrimitive("name").getAsString() : null;
            return new GameProfile(id, name);
        }
    }

}
