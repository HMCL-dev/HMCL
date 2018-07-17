/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.util;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

/**
 * A deserializer that supports deserializing strings and **numbers** into enums.
 *
 * @author yushijinhun
 */
public class EnumOrdinalDeserializer<T extends Enum<T>> implements JsonDeserializer<T> {

    private Map<String, T> mapping = new HashMap<>();

    public EnumOrdinalDeserializer(Class<T> enumClass) {
        for (T constant : enumClass.getEnumConstants()) {
            mapping.put(String.valueOf(constant.ordinal()), constant);
            String name = constant.name();
            try {
                SerializedName annotation = enumClass.getField(name).getAnnotation(SerializedName.class);
                if (annotation != null) {
                    name = annotation.value();
                    for (String alternate : annotation.alternate()) {
                        mapping.put(alternate, constant);
                    }
                }
            } catch (NoSuchFieldException e) {
                throw new AssertionError(e);
            }
            mapping.put(name, constant);
        }
    }

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return mapping.get(json.getAsString());
    }

}
