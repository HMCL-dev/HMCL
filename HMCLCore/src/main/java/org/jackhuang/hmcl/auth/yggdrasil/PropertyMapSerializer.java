// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;

public class PropertyMapSerializer implements JsonSerializer<Map<String, String>>, JsonDeserializer<Map<String, String>> {

    @Override
    public Map<String, String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonElement element : json.getAsJsonArray())
            if (element instanceof JsonObject) {
                JsonObject object = (JsonObject) element;
                result.put(object.get("name").getAsString(), object.get("value").getAsString());
            }

        return unmodifiableMap(result);
    }

    @Override
    public JsonElement serialize(Map<String, String> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray result = new JsonArray();
        src.forEach((k, v) -> {
            JsonObject object = new JsonObject();
            object.addProperty("name", k);
            object.addProperty("value", v);
            result.add(object);
        });
        return result;
    }
}
