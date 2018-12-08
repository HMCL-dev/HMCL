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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Immutable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author huangyuhui
 */
@JsonAdapter(StringArgument.Serializer.class)
@Immutable
public final class StringArgument implements Argument {

    private final String argument;

    public StringArgument(String argument) {
        this.argument = argument;
    }

    public String getArgument() {
        return argument;
    }

    @Override
    public List<String> toString(Map<String, String> keys, Map<String, Boolean> features) {
        String res = argument;
        Pattern pattern = Pattern.compile("\\$\\{(.*?)}");
        Matcher m = pattern.matcher(argument);
        while (m.find()) {
            String entry = m.group();
            res = res.replace(entry, keys.getOrDefault(entry, entry));
        }
        return Collections.singletonList(res);
    }

    @Override
    public String toString() {
        return argument;
    }

    public class Serializer implements JsonSerializer<StringArgument> {
        @Override
        public JsonElement serialize(StringArgument src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getArgument());
        }
    }
}
