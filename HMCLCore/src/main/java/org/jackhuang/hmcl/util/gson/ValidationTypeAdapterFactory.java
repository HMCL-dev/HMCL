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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 *
 * @author huangyuhui
 */
public final class ValidationTypeAdapterFactory implements TypeAdapterFactory {

    public static final ValidationTypeAdapterFactory INSTANCE = new ValidationTypeAdapterFactory();

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> tt) {
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, tt);
        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter writer, T t) throws IOException {
                if (t instanceof Validation)
                    ((Validation) t).validate();

                delegate.write(writer, t);
            }

            @Override
            public T read(JsonReader reader) throws IOException {
                T t = delegate.read(reader);
                if (t instanceof Validation)
                    ((Validation) t).validate();
                return t;
            }
        };
    }
}
