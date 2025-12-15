/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/// @author Glavo
public final class PathTypeAdapter extends TypeAdapter<Path> {

    public static final PathTypeAdapter INSTANCE = new PathTypeAdapter();

    @Override
    public Path read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String value = in.nextString();
        if (File.separatorChar == '\\')
            value = value.replace('/', '\\');
        return Path.of(value);
    }

    @Override
    public void write(JsonWriter out, Path path) throws IOException {
        if (path != null) {
            if (path.getFileSystem() != FileSystems.getDefault())
                throw new IOException("Unsupported file system: " + path.getFileSystem());

            String value = path.toString();
            if (!path.isAbsolute() && File.separatorChar == '\\')
                value = value.replace('\\', '/');
            out.value(value);
        } else {
            out.nullValue();
        }
    }
}
