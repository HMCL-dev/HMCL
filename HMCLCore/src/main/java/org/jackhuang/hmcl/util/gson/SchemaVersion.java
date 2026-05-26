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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

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
    /// The default JSON member name used for schema versions.
    public static final String DEFAULT_MEMBER_NAME = "schemaVersion";

    /// @param major the major schema version
    /// @param minor the minor schema version
    public SchemaVersion {
        if (major < 0) throw new IllegalArgumentException("Major version must be non-negative: " + major);
        if (minor < 0) throw new IllegalArgumentException("Minor version must be non-negative: " + minor);
    }

    /// Parses a schema version string.
    ///
    /// @param version the version string, either `major` or `major.minor`
    /// @return the parsed schema version
    /// @throws IllegalArgumentException if the version string is invalid
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

    /// Reads a schema version from the default `schemaVersion` member of a JSON object.
    ///
    /// @param object the JSON object that contains the schema version
    /// @return the parsed schema version
    /// @throws JsonParseException if the schema version member is missing or invalid
    public static SchemaVersion readFrom(JsonObject object) throws JsonParseException {
        return readFrom(object, DEFAULT_MEMBER_NAME);
    }

    /// Reads a schema version from a JSON object member.
    ///
    /// @param object the JSON object that contains the schema version
    /// @param memberName the JSON member name
    /// @return the parsed schema version
    /// @throws JsonParseException if the schema version member is missing or invalid
    public static SchemaVersion readFrom(JsonObject object, String memberName) throws JsonParseException {
        Objects.requireNonNull(object);
        Objects.requireNonNull(memberName);

        JsonElement element = object.get(memberName);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Invalid schema version member `" + memberName + "`: " + element);
        }

        try {
            return parse(primitive.getAsString());
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Invalid schema version member `" + memberName + "`: " + primitive, e);
        }
    }

    /// Reads and checks the default `schemaVersion` member of a JSON object.
    ///
    /// @param object the JSON object that contains the schema version
    /// @param expected the schema version supported by the current code
    /// @return the schema version check result
    public static CheckResult check(JsonObject object, SchemaVersion expected) {
        return check(object, DEFAULT_MEMBER_NAME, expected);
    }

    /// Reads and checks a schema version JSON object member.
    ///
    /// @param object the JSON object that contains the schema version
    /// @param memberName the JSON member name
    /// @param expected the schema version supported by the current code
    /// @return the schema version check result
    public static CheckResult check(JsonObject object, String memberName, SchemaVersion expected) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(memberName);
        Objects.requireNonNull(expected);

        if (!object.has(memberName)) {
            return new CheckResult(null, expected, CheckResult.Status.MISSING, null);
        }

        try {
            return new CheckResult(readFrom(object, memberName), expected, CheckResult.Status.VALID, null);
        } catch (JsonParseException e) {
            return new CheckResult(null, expected, CheckResult.Status.INVALID, String.valueOf(object.get(memberName)));
        }
    }

    /// Compares this version with another schema version.
    ///
    /// @param o the other version to compare to
    /// @return a negative integer, zero, or a positive integer as this version
    ///         is less than, equal to, or greater than the specified version
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

    /// Result of checking a serialized schema version against the version supported by the current code.
    ///
    /// @param actual the schema version read from serialized data, or `null` when no valid version was read
    /// @param expected the schema version supported by the current code
    /// @param status the schema version check status
    /// @param invalidValue the raw invalid JSON value text, or `null` when the member is valid or missing
    public record CheckResult(@Nullable SchemaVersion actual,
                              SchemaVersion expected,
                              Status status,
                              @Nullable String invalidValue) {
        /// The schema version check status.
        public enum Status {
            /// A schema version member exists and was parsed successfully.
            VALID,

            /// No schema version member exists.
            MISSING,

            /// A schema version member exists but cannot be parsed.
            INVALID
        }

        /// Creates a schema version check result.
        ///
        /// @param actual the schema version read from serialized data, or `null` when no valid version was read
        /// @param expected the schema version supported by the current code
        /// @param status the schema version check status
        /// @param invalidValue the raw invalid JSON value text, or `null` when the member is valid or missing
        public CheckResult {
            Objects.requireNonNull(expected);
            Objects.requireNonNull(status);
            if (status == Status.VALID) {
                Objects.requireNonNull(actual);
            } else if (actual != null) {
                throw new IllegalArgumentException("Only valid schema version checks may have an actual version");
            }

            if (status == Status.INVALID) {
                Objects.requireNonNull(invalidValue);
            } else if (invalidValue != null) {
                throw new IllegalArgumentException("Only invalid schema version checks may have an invalid value");
            }
        }

        /// Returns whether the serialized data does not contain a schema version member.
        public boolean isMissing() {
            return status == Status.MISSING;
        }

        /// Returns whether the serialized data contains an unparseable schema version member.
        public boolean isInvalid() {
            return status == Status.INVALID;
        }

        /// Returns whether the serialized schema is newer than the supported schema.
        public boolean isNewerThanExpected() {
            return actual != null && status == Status.VALID && actual.compareTo(expected) > 0;
        }

        /// Returns whether the serialized schema has a newer major version than the supported schema.
        public boolean hasNewerMajorVersion() {
            return actual != null && status == Status.VALID && actual.major() > expected.major();
        }
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
                String value = in.nextString();
                try {
                    return SchemaVersion.parse(value);
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Invalid schema version: " + value, e);
                }
            } else {
                throw new JsonParseException("Unexpected token: " + in.peek());
            }
        }
    }
}
