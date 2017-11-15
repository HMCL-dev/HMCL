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
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jackhuang.hmcl.util.CollectionUtils;

/**
 *
 * @author huang
 */
public class RuledArgument implements Argument {
    public List<Rules> rules;
    public List<String> value;

    public RuledArgument() {
    }
    
    public RuledArgument(List<Rules> rules, List<String> args) {
        this.rules = rules;
        this.value = args;
    }

    @Override
    public Object clone() {
        try {
            RuledArgument ret = (RuledArgument) super.clone();
            ret.rules = CollectionUtils.deepCopy(rules, value -> (Rules) value.clone());
            ret.value = CollectionUtils.copy(value);
            return ret;
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError(ex);
        }
    }

    @Override
    public List<String> toString(Map<String, String> keys, Map<String, Boolean> features) {
        if (!Rules.allow(rules, features))
            return Collections.EMPTY_LIST;
        else
            return CollectionUtils.map(value, a -> new StringArgument(a).toString(keys, features).get(0));
    }
    
    public static class RuledArgumentSerializer implements JsonSerializer<RuledArgument>, JsonDeserializer<RuledArgument> {

        @Override
        public JsonElement serialize(RuledArgument src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.add("rules", context.serialize(src.rules));
            obj.add("value", context.serialize(src.value));
            return obj;
        }

        @Override
        public RuledArgument deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            RuledArgument a = new RuledArgument();
            a.rules = context.deserialize(obj.get("rules"), new TypeToken<List<Rules>>(){}.getType());
            if (obj.get("value").isJsonPrimitive())
                a.value = Collections.singletonList(obj.get("value").getAsString());
            else
                a.value = context.<List<String>>deserialize(obj.get("value"), new TypeToken<List<String>>(){}.getType());
            return a;
        }
        
    }
}
