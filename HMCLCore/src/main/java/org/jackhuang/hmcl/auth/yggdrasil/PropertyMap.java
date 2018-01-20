/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
import org.jackhuang.hmcl.util.Lang;

import java.lang.reflect.Type;
import java.util.*;

public final class PropertyMap extends HashMap<String, Property> {

    public List<Map<String, String>> toList() {
        List<Map<String, String>> properties = new ArrayList<>();
        for (Property profileProperty : values()) {
            Map<String, String> property = new HashMap<>();
            property.put("name", profileProperty.getName());
            property.put("value", profileProperty.getValue());
            properties.add(property);
        }
        return properties;
    }

    public void fromList(List<?> list) {
        for (Object propertyMap : list) {
            if (!(propertyMap instanceof Map<?, ?>))
                continue;
            Optional<String> name = Lang.get((Map<?, ?>) propertyMap, "name", String.class);
            Optional<String> value = Lang.get((Map<?, ?>) propertyMap, "value", String.class);
            if (name.isPresent() && value.isPresent())
                put(name.get(), new Property(name.get(), value.get()));
        }
    }

    public static class Serializer implements JsonSerializer<PropertyMap>, JsonDeserializer<PropertyMap> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public PropertyMap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            PropertyMap result = new PropertyMap();
            if (json instanceof JsonObject) {
                for (Map.Entry<String, JsonElement> entry : ((JsonObject) json).entrySet())
                    if (entry.getValue() instanceof JsonArray)
                        for (JsonElement element : (JsonArray) entry.getValue())
                            result.put(entry.getKey(), new Property(entry.getKey(), element.getAsString()));
            } else if ((json instanceof JsonArray))
                for (JsonElement element : (JsonArray) json)
                    if ((element instanceof JsonObject)) {
                        JsonObject object = (JsonObject) element;
                        String name = object.getAsJsonPrimitive("name").getAsString();
                        String value = object.getAsJsonPrimitive("value").getAsString();
                        result.put(name, new Property(name, value));
                    }

            return result;
        }

        @Override
        public JsonElement serialize(PropertyMap src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray result = new JsonArray();
            for (Property property : src.values()) {
                JsonObject object = new JsonObject();
                object.addProperty("name", property.getName());
                object.addProperty("value", property.getValue());
                result.add(object);
            }

            return result;
        }
    }

    public static class LegacySerializer
            implements JsonSerializer<PropertyMap> {

        @Override
        public JsonElement serialize(PropertyMap src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            for (PropertyMap.Entry<String, Property> entry : src.entrySet()) {
                JsonArray values = new JsonArray();
                values.add(new JsonPrimitive(entry.getValue().getValue()));
                result.add(entry.getKey(), values);
            }
            return result;
        }
    }
}
