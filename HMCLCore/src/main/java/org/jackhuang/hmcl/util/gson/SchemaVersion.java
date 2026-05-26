/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/// Semantic version marker for a serialized data schema.
///
/// The JSON representation is a string in `major.minor` form. The adapter also accepts a bare
/// `major` string and treats it as `major.0` for compatibility with compact schema version markers.
///
/// Config files use the following compatibility policy:
///
/// - When the major version differs from the supported schema, the config must be rejected.
/// - When only the minor version differs, the config may be read but must not be overwritten.
/// - When both major and minor versions match, the config may be read and saved normally.
///
/// @param major the major schema version
/// @param minor the minor schema version
/// @author Glavo
@JsonSerializable
@JsonAdapter(SchemaVersion.Adapter.class)
@NotNullByDefault
public record SchemaVersion(int major, int minor) implements Comparable<SchemaVersion> {

    public SchemaVersion {
        if (major < 0) throw new IllegalArgumentException("Major version must be non-negative: " + major);
        if (minor < 0) throw new IllegalArgumentException("Minor version must be non-negative: " + minor);
    }

    public static SchemaVersion parse(String version) {
        int dot = version.indexOf('.');

        try {
            if (dot == -1) {
                return new SchemaVersion(Integer.parseInt(version), 0);
            } else {
                return new SchemaVersion(Integer.parseInt(version.substring(0, dot)), Integer.parseInt(version.substring(dot + 1)));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid schema version: " + version, e);
        }
    }

    @Override
    public int compareTo(SchemaVersion o) {
        return major != o.major
                ? Integer.compare(major, o.major)
                : Integer.compare(minor, o.minor);
    }

    /// Returns the canonical `major.minor` string representation.
    @Override
    public String toString() {
        return major + "." + minor;
    }

    /// Gson adapter for the string representation of [SchemaVersion].
    ///
    /// Null JSON values are preserved as null. Non-string values are rejected because schema
    /// versions are intentionally serialized as stable textual identifiers.
    @NotNullByDefault
    public static final class Adapter extends TypeAdapter<@Nullable SchemaVersion> {
        /// Writes the version as a `major.minor` string, or JSON null when the value is null.
        @Override
        public void write(JsonWriter out, @Nullable SchemaVersion value) throws IOException {
            if (value != null) {
                out.value(value.toString());
            } else {
                out.nullValue();
            }
        }

        /// Reads a schema version from a string or null JSON token.
        ///
        /// Accepted strings are `major` and `major.minor`, where both parts must be parseable
        /// decimal integers. The `major` form is normalized to a zero minor version.
        @Override
        public @Nullable SchemaVersion read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            if (in.peek() == JsonToken.STRING) {
                try {
                    return SchemaVersion.parse(in.nextString());
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Invalid schema version: " + in.nextString(), e);
                }
            } else {
                throw new JsonParseException("Unexpected token: " + in.peek());
            }
        }
    }
}
