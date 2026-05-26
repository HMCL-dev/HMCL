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

/// Identifies the data format used by a serialized file.
///
/// The JSON representation is a string in `id/major.minor` form, for example
/// `hmcl.config/3.0`.
///
/// File formats use the following compatibility policy:
///
/// - When the format ID differs from the expected format, the file must be rejected.
/// - When the major version differs from the supported format, the file must be rejected.
/// - When only the minor version differs, the file may be read but must not be overwritten.
/// - When both major and minor versions match, the file may be read and saved normally.
///
/// @param id the stable format identifier
/// @param version the format version
/// @author Glavo
@JsonSerializable
@JsonAdapter(FileFormat.Adapter.class)
@NotNullByDefault
public record FileFormat(String id, FormatVersion version) {
    /// The default JSON member name used for file formats.
    public static final String DEFAULT_MEMBER_NAME = "format";

    /// @param id the stable format identifier
    /// @param version the format version
    public FileFormat {
        Objects.requireNonNull(id);
        Objects.requireNonNull(version);

        if (!isValidId(id)) {
            throw new IllegalArgumentException("Invalid file format ID: " + id);
        }
    }

    /// Parses a file format string.
    ///
    /// @param format the file format string in `id/major.minor` form
    /// @return the parsed file format
    /// @throws IllegalArgumentException if the format string is invalid
    public static FileFormat parse(String format) {
        int slash = format.indexOf('/');
        if (slash <= 0 || slash != format.lastIndexOf('/') || slash == format.length() - 1) {
            throw new IllegalArgumentException("Invalid file format: " + format);
        }

        try {
            return new FileFormat(format.substring(0, slash), FormatVersion.parse(format.substring(slash + 1)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid file format: " + format, e);
        }
    }

    /// Reads a file format from the default `format` member of a JSON object.
    ///
    /// @param object the JSON object that contains the file format
    /// @return the parsed file format
    /// @throws JsonParseException if the format member is missing or invalid
    public static FileFormat readFrom(JsonObject object) throws JsonParseException {
        return readFrom(object, DEFAULT_MEMBER_NAME);
    }

    /// Reads a file format from a JSON object member.
    ///
    /// @param object the JSON object that contains the file format
    /// @param memberName the JSON member name
    /// @return the parsed file format
    /// @throws JsonParseException if the format member is missing or invalid
    public static FileFormat readFrom(JsonObject object, String memberName) throws JsonParseException {
        Objects.requireNonNull(object);
        Objects.requireNonNull(memberName);

        JsonElement element = object.get(memberName);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Invalid file format member `" + memberName + "`: " + element);
        }

        try {
            return parse(primitive.getAsString());
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Invalid file format member `" + memberName + "`: " + primitive, e);
        }
    }

    /// Reads and checks the default `format` member of a JSON object.
    ///
    /// @param object the JSON object that contains the file format
    /// @param expected the file format supported by the current code
    /// @return the file format check result
    public static CheckResult check(JsonObject object, FileFormat expected) {
        return check(object, DEFAULT_MEMBER_NAME, expected);
    }

    /// Reads and checks a file format JSON object member.
    ///
    /// @param object the JSON object that contains the file format
    /// @param memberName the JSON member name
    /// @param expected the file format supported by the current code
    /// @return the file format check result
    public static CheckResult check(JsonObject object, String memberName, FileFormat expected) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(memberName);
        Objects.requireNonNull(expected);

        if (!object.has(memberName)) {
            return new CheckResult(null, expected, CheckResult.Status.MISSING, null);
        }

        try {
            FileFormat actual = readFrom(object, memberName);
            return new CheckResult(actual, expected, actual.id.equals(expected.id)
                    ? CheckResult.Status.VALID
                    : CheckResult.Status.UNEXPECTED_ID, null);
        } catch (JsonParseException e) {
            return new CheckResult(null, expected, CheckResult.Status.INVALID, String.valueOf(object.get(memberName)));
        }
    }

    /// Returns the canonical `id/major.minor` string representation.
    @Override
    public String toString() {
        return id + "/" + version;
    }

    /// Returns whether a format ID is valid.
    private static boolean isValidId(String id) {
        if (id.isEmpty()) {
            return false;
        }

        for (int i = 0; i < id.length(); i++) {
            char ch = id.charAt(i);
            if (ch == '/' || Character.isWhitespace(ch)) {
                return false;
            }
        }

        return true;
    }

    /// Result of checking a serialized file format against the format supported by the current code.
    ///
    /// @param actual the file format read from serialized data, or `null` when no valid format was read
    /// @param expected the file format supported by the current code
    /// @param status the file format check status
    /// @param invalidValue the raw invalid JSON value text, or `null` when the member is valid or missing
    public record CheckResult(@Nullable FileFormat actual,
                              FileFormat expected,
                              Status status,
                              @Nullable String invalidValue) {
        /// The file format check status.
        public enum Status {
            /// A file format member exists, was parsed successfully, and has the expected ID.
            VALID,

            /// No file format member exists.
            MISSING,

            /// A file format member exists but cannot be parsed.
            INVALID,

            /// A file format member exists and was parsed, but its ID differs from the expected ID.
            UNEXPECTED_ID
        }

        /// Creates a file format check result.
        ///
        /// @param actual the file format read from serialized data, or `null` when no valid format was read
        /// @param expected the file format supported by the current code
        /// @param status the file format check status
        /// @param invalidValue the raw invalid JSON value text, or `null` when the member is valid or missing
        public CheckResult {
            Objects.requireNonNull(expected);
            Objects.requireNonNull(status);
            if (status == Status.VALID || status == Status.UNEXPECTED_ID) {
                Objects.requireNonNull(actual);
            } else if (actual != null) {
                throw new IllegalArgumentException("Only parsed file format checks may have an actual format");
            }

            if (status == Status.INVALID) {
                Objects.requireNonNull(invalidValue);
            } else if (invalidValue != null) {
                throw new IllegalArgumentException("Only invalid file format checks may have an invalid value");
            }
        }

        /// Returns whether the serialized data does not contain a file format member.
        public boolean isMissing() {
            return status == Status.MISSING;
        }

        /// Returns whether the serialized data contains an unparseable file format member.
        public boolean isInvalid() {
            return status == Status.INVALID;
        }

        /// Returns whether the serialized data uses an unexpected file format ID.
        public boolean isUnexpectedId() {
            return status == Status.UNEXPECTED_ID;
        }

        /// Returns whether the serialized file format is newer than the supported format.
        public boolean isNewerThanExpected() {
            return status == Status.VALID && actual != null && actual.version.compareTo(expected.version) > 0;
        }

        /// Returns whether the serialized file format has a newer major version than the supported format.
        public boolean hasNewerMajorVersion() {
            return status == Status.VALID && actual != null && actual.version.major() > expected.version.major();
        }
    }

    /// Gson adapter for the string representation of [FileFormat].
    ///
    /// Null JSON values are preserved as null. Non-string values are rejected because file
    /// formats are intentionally serialized as stable textual identifiers.
    @NotNullByDefault
    public static final class Adapter extends TypeAdapter<@Nullable FileFormat> {
        /// Writes the format as `id/major.minor`, or JSON null when the value is null.
        @Override
        public void write(JsonWriter out, @Nullable FileFormat value) throws IOException {
            if (value != null) {
                out.value(value.toString());
            } else {
                out.nullValue();
            }
        }

        /// Reads a file format from a string or null JSON token.
        ///
        /// Accepted strings are in `id/major.minor` form.
        @Override
        public @Nullable FileFormat read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            if (in.peek() == JsonToken.STRING) {
                String value = in.nextString();
                try {
                    return FileFormat.parse(value);
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Invalid file format: " + value, e);
                }
            } else {
                throw new JsonParseException("Unexpected token: " + in.peek());
            }
        }
    }
}
