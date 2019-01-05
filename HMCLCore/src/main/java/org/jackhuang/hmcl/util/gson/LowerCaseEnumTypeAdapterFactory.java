/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

/**
 *
 * @author huangyuhui
 */
public final class LowerCaseEnumTypeAdapterFactory implements TypeAdapterFactory {

    public static final LowerCaseEnumTypeAdapterFactory INSTANCE = new LowerCaseEnumTypeAdapterFactory();

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> tt) {
        Class<? super T> rawType = tt.getRawType();
        if (!rawType.isEnum())
            return null;

        HashMap<String, T> lowercaseToConstant = new HashMap<>();
        for (Object constant : rawType.getEnumConstants())
            lowercaseToConstant.put(toLowercase(constant), (T) constant);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter writer, T t) throws IOException {
                if (t == null)
                    writer.nullValue();
                else
                    writer.value(toLowercase(t));
            }

            @Override
            public T read(JsonReader reader) throws IOException {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    return null;
                }
                return lowercaseToConstant.get(reader.nextString().toLowerCase());
            }
        };
    }

    private static String toLowercase(Object o) {
        return o.toString().toLowerCase(Locale.US);
    }
}
