// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Immutable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import org.jetbrains.annotations.NotNull;

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
public record StringArgument(String argument) implements Argument {

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
    public @NotNull String toString() {
        return argument;
    }

    public static final class Serializer implements JsonSerializer<StringArgument> {
        @Override
        public JsonElement serialize(StringArgument src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.argument());
        }
    }
}
