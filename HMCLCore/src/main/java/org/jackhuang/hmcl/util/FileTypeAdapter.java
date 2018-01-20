/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import com.google.gson.*;

import java.io.File;
import java.lang.reflect.Type;

/**
 *
 * @author huangyuhui
 */
public final class FileTypeAdapter implements JsonSerializer<File>, JsonDeserializer<File> {

    public static final FileTypeAdapter INSTANCE = new FileTypeAdapter();

    private FileTypeAdapter() {
    }

    @Override
    public JsonElement serialize(File t, Type type, JsonSerializationContext jsc) {
        if (t == null)
            return JsonNull.INSTANCE;
        else
            return new JsonPrimitive(t.getPath());
    }

    @Override
    public File deserialize(JsonElement je, Type type, JsonDeserializationContext jdc) throws JsonParseException {
        if (je == null)
            return null;
        else
            return new File(je.getAsString());
    }

}
