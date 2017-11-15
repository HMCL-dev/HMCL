/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.core.version;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 *
 * @author huang
 */
public interface Argument extends Cloneable {

    /**
     * Parse this argument in form: ${key name} or simply a string.
     *
     * @param keys the parse map
     * @param features the map that contains some features such as 'is_demo_user', 'has_custom_resolution'
     * @return parsed argument element, empty if this argument is ignored and will not be added.
     */
    List<String> toString(Map<String, String> keys, Map<String, Boolean> features);
    
    public Object clone();

    public static class ArgumentSerializer implements JsonDeserializer<Argument>, JsonSerializer<Argument> {

        @Override
        public Argument deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive())
                return new StringArgument(json.getAsString());
            else
                return context.deserialize(json, RuledArgument.class);
        }

        @Override
        public JsonElement serialize(Argument src, Type typeOfSrc, JsonSerializationContext context) {
            if (src instanceof StringArgument)
                return new JsonPrimitive(((StringArgument) src).argument);
            else if (src instanceof RuledArgument)
                return context.serialize(src, RuledArgument.class);
            else
                throw new AssertionError("Unrecognized argument type: " + src);
        }

    }
}
