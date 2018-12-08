/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public final class PropertyMap extends HashMap<String, String> {

    public static PropertyMap fromMap(Map<?, ?> map) {
        PropertyMap propertyMap = new PropertyMap();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof String)
                propertyMap.put((String) entry.getKey(), (String) entry.getValue());
        }
        return propertyMap;
    }

    public static class Serializer implements JsonSerializer<PropertyMap>, JsonDeserializer<PropertyMap> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public PropertyMap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            PropertyMap result = new PropertyMap();
            for (JsonElement element : json.getAsJsonArray())
                if (element instanceof JsonObject) {
                    JsonObject object = (JsonObject) element;
                    result.put(object.get("name").getAsString(), object.get("value").getAsString());
                }

            return result;
        }

        @Override
        public JsonElement serialize(PropertyMap src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray result = new JsonArray();
            for (Map.Entry<String, String> entry : src.entrySet()) {
                JsonObject object = new JsonObject();
                object.addProperty("name", entry.getKey());
                object.addProperty("value", entry.getValue());
                result.add(object);
            }

            return result;
        }
    }

    public static class LegacySerializer
            implements JsonSerializer<PropertyMap> {
        public static final LegacySerializer INSTANCE = new LegacySerializer();

        @Override
        public JsonElement serialize(PropertyMap src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            for (PropertyMap.Entry<String, String> entry : src.entrySet()) {
                JsonArray values = new JsonArray();
                values.add(new JsonPrimitive(entry.getValue()));
                result.add(entry.getKey(), values);
            }
            return result;
        }
    }
}
