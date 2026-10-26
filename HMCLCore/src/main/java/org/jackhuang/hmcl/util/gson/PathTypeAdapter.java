// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
