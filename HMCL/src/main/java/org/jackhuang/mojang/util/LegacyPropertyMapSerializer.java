/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.mojang.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import org.jackhuang.mojang.authlib.properties.PropertyMap;

/**
 *
 * @author hyh
 */
public class LegacyPropertyMapSerializer
	implements JsonSerializer<PropertyMap> {

    @Override
    public JsonElement serialize(PropertyMap src, Type typeOfSrc, JsonSerializationContext context) {
	JsonObject result = new JsonObject();

	for (String key : src.keySet()) {
	    JsonArray values = new JsonArray();

	    values.add(new JsonPrimitive(src.get(key).getValue()));

	    result.add(key, values);
	}

	return result;
    }
}
