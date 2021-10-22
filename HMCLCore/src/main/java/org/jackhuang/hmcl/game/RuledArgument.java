/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.Immutable;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author huangyuhui
 */
@JsonAdapter(RuledArgument.Serializer.class)
@Immutable
public class RuledArgument implements Argument {

    private final List<CompatibilityRule> rules;
    private final List<String> value;

    public RuledArgument() {
        this(null, null);
    }

    public RuledArgument(List<CompatibilityRule> rules, List<String> args) {
        this.rules = rules;
        this.value = args;
    }

    public List<CompatibilityRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public List<String> getValue() {
        return Collections.unmodifiableList(value);
    }

    @Override
    public Object clone() {
        return new RuledArgument(
                rules == null ? null : new ArrayList<>(rules),
                value == null ? null : new ArrayList<>(value)
        );
    }

    @Override
    public List<String> toString(Map<String, String> keys, Map<String, Boolean> features) {
        if (CompatibilityRule.appliesToCurrentEnvironment(rules, features) && value != null)
            return value.stream()
                    .filter(Objects::nonNull)
                    .map(StringArgument::new)
                    .map(str -> str.toString(keys, features).get(0))
                    .collect(Collectors.toList());
        return Collections.emptyList();
    }

    public static class Serializer implements JsonSerializer<RuledArgument>, JsonDeserializer<RuledArgument> {
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

            List<CompatibilityRule> rules = context.deserialize(obj.get("rules"), new TypeToken<List<CompatibilityRule>>() {
            }.getType());

            JsonElement valuesElement;
            if (obj.has("values")) {
                valuesElement = obj.get("values");
            } else if (obj.has("value")) {
                valuesElement = obj.get("value");
            } else {
                throw new JsonParseException("RuledArguments instance does not have either value or values member.");
            }

            List<String> values;
            if (valuesElement.isJsonPrimitive()) {
                values = Collections.singletonList(valuesElement.getAsString());
            } else {
                values = context.deserialize(valuesElement, new TypeToken<List<String>>() {
                }.getType());
            }

            return new RuledArgument(rules, values);
        }

    }
}
