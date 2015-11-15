package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.HMCLog;

public class PropertyMap extends HashMap<String, Property> {

    public List<Map<String, String>> list() {
        List<Map<String, String>> properties = new ArrayList<>();
        for (Property profileProperty : values()) {
            Map<String, String> property = new HashMap<>();
            property.put("name", profileProperty.name);
            property.put("value", profileProperty.value);
            properties.add(property);
        }
        return properties;
    }

    public void fromList(List<Map<String, String>> list) {
        try {
            for (Map<String, String> propertyMap : list) {
                String name = propertyMap.get("name");
                String value = propertyMap.get("value");
            }
        } catch (Throwable t) {
            HMCLog.warn("Failed to deserialize properties", t);
        }
    }

    public static class Serializer implements JsonSerializer<PropertyMap>, JsonDeserializer<PropertyMap> {

        @Override
        public PropertyMap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            PropertyMap result = new PropertyMap();
            if ((json instanceof JsonObject)) {
                JsonObject object = (JsonObject) json;

                for (Map.Entry<String, JsonElement> entry : object.entrySet())
                    if (entry.getValue() instanceof JsonArray)
                        for (JsonElement element : (JsonArray) entry.getValue())
                            result.put(entry.getKey(),
                            new Property((String) entry.getKey(), element.getAsString()));
            } else if ((json instanceof JsonArray))
                for (JsonElement element : (JsonArray) json)
                    if ((element instanceof JsonObject)) {
                        JsonObject object = (JsonObject) element;
                        String name = object.getAsJsonPrimitive("name").getAsString();
                        String value = object.getAsJsonPrimitive("value").getAsString();
                    }

            return result;
        }

        @Override
        public JsonElement serialize(PropertyMap src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray result = new JsonArray();
            for (Property property : src.values()) {
                JsonObject object = new JsonObject();
                object.addProperty("name", property.name);
                object.addProperty("value", property.value);
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
            for (String key : src.keySet()) {
                JsonArray values = new JsonArray();
                values.add(new JsonPrimitive(src.get(key).value));
                result.add(key, values);
            }
            return result;
        }
    }
}
