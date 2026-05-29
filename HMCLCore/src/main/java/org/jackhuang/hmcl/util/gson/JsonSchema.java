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
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/// Stores a raw JSON schema string and, when possible, its parsed HMCL schema identifier.
///
/// The JSON representation is always a string. HMCL-owned schemas use the following
/// fixed URL form, but other strings can still be represented as unparseable schemas:
///
/// `https://schemas.glavo.site/hmcl/<id>/<version>`
///
/// Canonical URLs written by this class use `major.minor.patch` versions. Parsing
/// also accepts `major.minor` versions and treats the missing patch number as `0`.
///
/// Parsed HMCL schemas use the following compatibility policy:
///
/// - When the schema string is not parseable as an HMCL schema URL, the file must be rejected.
/// - When the schema ID differs from the expected schema, the file must be rejected.
/// - When the major version is not supported by the current code, the file must be rejected.
/// - When the minor version is newer, the file may be read but must not be overwritten.
/// - When only the patch version differs, the file may be read and saved while preserving the original schema string
///   and unknown serialized members.
///
/// @param value the raw JSON schema string
/// @param parsed the parsed HMCL schema identifier, or `null` when the string is not parseable
/// @author Glavo
@JsonSerializable
@JsonAdapter(JsonSchema.Adapter.class)
@NotNullByDefault
public record JsonSchema(String value, @Nullable Parsed parsed) {
    /// The default JSON member name used for schema strings.
    public static final String DEFAULT_MEMBER_NAME = "$schema";

    /// The HMCL schema URL prefix.
    private static final String URL_PREFIX = "https://schemas.glavo.site/hmcl/";

    /// @param value the raw JSON schema string
    /// @param parsed the parsed HMCL schema identifier, or `null` when the string is not parseable
    public JsonSchema {
        Objects.requireNonNull(value);
        if (parsed != null && !parsed.equals(parseSchemaUrl(value))) {
            throw new IllegalArgumentException("Parsed schema does not match raw schema string: " + value);
        }
    }

    /// Creates a schema from any raw string value.
    ///
    /// @param value the raw JSON schema string
    public JsonSchema(String value) {
        this(value, parseSchemaUrl(value));
    }

    /// Creates a parsed HMCL schema from an ID and version.
    ///
    /// @param id the stable schema identifier
    /// @param version the schema version
    public JsonSchema(String id, Version version) {
        this(new Parsed(id, version));
    }

    /// Creates a schema from a parsed HMCL schema identifier.
    private JsonSchema(Parsed parsed) {
        this(parsed.url(), parsed);
    }

    /// Reads a schema string from the default schema member of a container object.
    ///
    /// @param object the container object that contains the schema member
    /// @return the schema string and optional parsed identifier
    /// @throws JsonParseException if the schema member is missing or is not a string
    public static JsonSchema readFromMember(JsonObject object) throws JsonParseException {
        return readFromMember(object, DEFAULT_MEMBER_NAME);
    }

    /// Reads a schema string from a member of a container object.
    ///
    /// @param object the container object that contains the schema member
    /// @param memberName the JSON member name
    /// @return the schema string and optional parsed identifier
    /// @throws JsonParseException if the schema member is missing or is not a string
    public static JsonSchema readFromMember(JsonObject object, String memberName) throws JsonParseException {
        Objects.requireNonNull(object);
        Objects.requireNonNull(memberName);

        return parseElement(object.get(memberName), "member `" + memberName + "`");
    }

    /// Reads and checks the default schema member of a JSON object.
    ///
    /// @param object the JSON object that contains the schema string
    /// @param expected the schema supported by the current code
    /// @return the schema check result
    public static CheckResult check(JsonObject object, JsonSchema expected) {
        return check(object, DEFAULT_MEMBER_NAME, expected);
    }

    /// Reads and checks a schema string JSON object member.
    ///
    /// @param object the JSON object that contains the schema string
    /// @param memberName the JSON member name
    /// @param expected the schema supported by the current code
    /// @return the schema check result
    public static CheckResult check(JsonObject object, String memberName, JsonSchema expected) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(memberName);
        Objects.requireNonNull(expected);
        if (!expected.isParsed()) {
            throw new IllegalArgumentException("Expected JSON schema must be parseable: " + expected);
        }

        if (!object.has(memberName)) {
            return new CheckResult(null, expected, CheckResult.Status.MISSING, null);
        }

        JsonSchema actual;
        try {
            actual = readFromMember(object, memberName);
        } catch (JsonParseException e) {
            return new CheckResult(null, expected, CheckResult.Status.INVALID, String.valueOf(object.get(memberName)));
        }

        @Nullable Parsed actualParsed = actual.parsed;
        if (actualParsed == null) {
            return new CheckResult(actual, expected, CheckResult.Status.UNPARSEABLE, null);
        }

        return new CheckResult(actual, expected, actualParsed.id.equals(Objects.requireNonNull(expected.parsed).id)
                ? CheckResult.Status.VALID
                : CheckResult.Status.UNEXPECTED_ID, null);
    }

    /// Parses a schema string from a JSON element.
    private static JsonSchema parseElement(@Nullable JsonElement element, String source) throws JsonParseException {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Invalid JSON schema " + source + ": " + element);
        }

        return new JsonSchema(primitive.getAsString());
    }

    /// Parses an HMCL schema URL, returning `null` for any other string.
    private static @Nullable Parsed parseSchemaUrl(String value) {
        Objects.requireNonNull(value);

        if (!value.startsWith(URL_PREFIX)) {
            return null;
        }

        String path = value.substring(URL_PREFIX.length());
        int slash = path.indexOf('/');
        if (slash <= 0 || slash != path.lastIndexOf('/') || slash == path.length() - 1) {
            return null;
        }

        String id = path.substring(0, slash);
        String versionString = path.substring(slash + 1);
        if (!isValidId(id)) {
            return null;
        }

        if (versionString.isEmpty()) {
            return null;
        }

        Version version;
        try {
            version = Version.parse(versionString);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (!isAcceptedVersionString(versionString, version)) {
            return null;
        }

        return new Parsed(id, version);
    }

    /// Returns whether a raw version string is an accepted representation of a parsed version.
    private static boolean isAcceptedVersionString(String versionString, Version version) {
        return versionString.equals(version.toString())
                || (version.patch() == 0 && versionString.equals(version.major() + "." + version.minor()));
    }

    /// Returns whether this schema string is parseable as an HMCL schema URL.
    public boolean isParsed() {
        return parsed != null;
    }

    /// Returns the parsed HMCL schema ID, or `null` when the schema string is not parseable.
    public @Nullable String id() {
        return parsed != null ? parsed.id : null;
    }

    /// Returns the parsed HMCL schema version, or `null` when the schema string is not parseable.
    public @Nullable Version version() {
        return parsed != null ? parsed.version : null;
    }

    /// Returns the raw schema string.
    public String url() {
        return value;
    }

    /// Returns the raw schema string.
    @Override
    public String toString() {
        return value;
    }

    /// Returns whether a schema ID is valid.
    private static boolean isValidId(String id) {
        if (id.isEmpty()) {
            return false;
        }

        char first = id.charAt(0);
        if (first < 'a' || first > 'z') {
            return false;
        }

        for (int i = 1; i < id.length(); i++) {
            char ch = id.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-')) {
                return false;
            }
        }

        return true;
    }

    /// Parsed identifier for an HMCL schema URL.
    ///
    /// @param id the stable schema identifier
    /// @param version the schema version
    @NotNullByDefault
    public record Parsed(String id, Version version) {
        /// @param id the stable schema identifier
        /// @param version the schema version
        public Parsed {
            Objects.requireNonNull(id);
            Objects.requireNonNull(version);

            if (!isValidId(id)) {
                throw new IllegalArgumentException("Invalid JSON schema ID: " + id);
            }
        }

        /// Returns the canonical schema URL.
        public String url() {
            return URL_PREFIX + id + "/" + version;
        }
    }

    /// Result of checking a serialized schema string against the schema supported by the current code.
    ///
    /// @param actual the schema string read from serialized data, or `null` when no schema string was read
    /// @param expected the schema supported by the current code
    /// @param status the schema check status
    /// @param invalidValue the raw invalid JSON value text, or `null` when the member is a string or is missing
    public record CheckResult(@Nullable JsonSchema actual,
                              JsonSchema expected,
                              Status status,
                              @Nullable String invalidValue) {
        /// The schema check status.
        public enum Status {
            /// A schema member exists, was parsed successfully, and has the expected ID.
            VALID,

            /// No schema member exists.
            MISSING,

            /// A schema member exists but is not a string.
            INVALID,

            /// A schema member exists as a string but cannot be parsed as an HMCL schema URL.
            UNPARSEABLE,

            /// A schema member exists and was parsed, but its ID differs from the expected ID.
            UNEXPECTED_ID
        }

        /// Creates a schema check result.
        ///
        /// @param actual the schema string read from serialized data, or `null` when no schema string was read
        /// @param expected the schema supported by the current code
        /// @param status the schema check status
        /// @param invalidValue the raw invalid JSON value text, or `null` when the member is a string or is missing
        public CheckResult {
            Objects.requireNonNull(expected);
            Objects.requireNonNull(status);
            if (!expected.isParsed()) {
                throw new IllegalArgumentException("Expected JSON schema must be parseable: " + expected);
            }

            if (status == Status.VALID || status == Status.UNEXPECTED_ID || status == Status.UNPARSEABLE) {
                Objects.requireNonNull(actual);
            } else if (actual != null) {
                throw new IllegalArgumentException("Only present JSON schema checks may have an actual schema");
            }

            if ((status == Status.VALID || status == Status.UNEXPECTED_ID) && !Objects.requireNonNull(actual).isParsed()) {
                throw new IllegalArgumentException("Only parsed JSON schema checks may be valid or use an unexpected ID");
            }

            if (status == Status.UNPARSEABLE && Objects.requireNonNull(actual).isParsed()) {
                throw new IllegalArgumentException("Only unparseable JSON schema strings may have the unparseable status");
            }

            if (status == Status.INVALID) {
                Objects.requireNonNull(invalidValue);
            } else if (invalidValue != null) {
                throw new IllegalArgumentException("Only invalid JSON schema checks may have an invalid value");
            }
        }

        /// Returns whether the serialized data does not contain a schema member.
        public boolean isMissing() {
            return status == Status.MISSING;
        }

        /// Returns whether the serialized data contains a non-string schema member.
        public boolean isInvalid() {
            return status == Status.INVALID;
        }

        /// Returns whether the serialized data contains a schema string that is not parseable as an HMCL schema URL.
        public boolean isUnparseable() {
            return status == Status.UNPARSEABLE;
        }

        /// Returns whether the serialized data uses an unexpected schema ID.
        public boolean isUnexpectedId() {
            return status == Status.UNEXPECTED_ID;
        }

        /// Returns whether the serialized schema uses a major version unsupported by the current code.
        public boolean hasUnsupportedMajorVersion() {
            return status == Status.VALID
                    && Objects.requireNonNull(actual).parsed.version.major() != Objects.requireNonNull(expected.parsed).version.major();
        }

        /// Returns whether the serialized schema has a newer minor version than the supported schema.
        public boolean hasNewerMinorVersion() {
            return status == Status.VALID
                    && Objects.requireNonNull(actual).parsed.version.major() == Objects.requireNonNull(expected.parsed).version.major()
                    && actual.parsed.version.minor() > expected.parsed.version.minor();
        }

        /// Returns whether the serialized schema has the same major and minor version as the supported schema.
        public boolean hasSameMajorAndMinorVersion() {
            return status == Status.VALID
                    && Objects.requireNonNull(actual).parsed.version.major() == Objects.requireNonNull(expected.parsed).version.major()
                    && actual.parsed.version.minor() == expected.parsed.version.minor();
        }
    }

    /// Semantic version marker for a serialized JSON schema.
    ///
    /// The string representation is the strict `major.minor.patch` form.
    ///
    /// @param major the major schema version
    /// @param minor the minor schema version
    /// @param patch the patch schema version
    /// @author Glavo
    @NotNullByDefault
    public record Version(int major, int minor, int patch) implements Comparable<Version> {
        /// @param major the major schema version
        /// @param minor the minor schema version
        /// @param patch the patch schema version
        public Version {
            if (major < 0) throw new IllegalArgumentException("Major version must be non-negative: " + major);
            if (minor < 0) throw new IllegalArgumentException("Minor version must be non-negative: " + minor);
            if (patch < 0) throw new IllegalArgumentException("Patch version must be non-negative: " + patch);
        }

        /// Parses a schema version string.
        ///
        /// @param version the version string in `major.minor` or `major.minor.patch` form
        /// @return the parsed schema version
        /// @throws IllegalArgumentException if the version string is invalid
        public static Version parse(String version) {
            int firstDot = version.indexOf('.');
            int secondDot = version.indexOf('.', firstDot + 1);
            if (firstDot <= 0
                    || firstDot == version.length() - 1
                    || (secondDot >= 0 && (secondDot <= firstDot + 1
                    || secondDot != version.lastIndexOf('.')
                    || secondDot == version.length() - 1))) {
                throw new IllegalArgumentException("Invalid JSON schema version: " + version);
            }

            try {
                int major = parsePart(version, 0, firstDot);
                if (secondDot >= 0) {
                    return new Version(
                            major,
                            parsePart(version, firstDot + 1, secondDot),
                            parsePart(version, secondDot + 1, version.length()));
                } else {
                    return new Version(
                            major,
                            parsePart(version, firstDot + 1, version.length()),
                            0);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid JSON schema version: " + version, e);
            }
        }

        /// Parses a decimal version part.
        private static int parsePart(String version, int start, int end) {
            for (int i = start; i < end; i++) {
                char ch = version.charAt(i);
                if (ch < '0' || ch > '9') {
                    throw new IllegalArgumentException("Invalid JSON schema version: " + version);
                }
            }

            return Integer.parseInt(version.substring(start, end));
        }

        /// Compares this version with another schema version.
        ///
        /// @param o the other version to compare to
        /// @return a negative integer, zero, or a positive integer as this version
        ///         is less than, equal to, or greater than the specified version
        @Override
        public int compareTo(Version o) {
            if (major != o.major) {
                return Integer.compare(major, o.major);
            } else if (minor != o.minor) {
                return Integer.compare(minor, o.minor);
            } else {
                return Integer.compare(patch, o.patch);
            }
        }

        /// Returns the canonical `major.minor.patch` string representation.
        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    /// Gson adapter for the JSON string representation of [JsonSchema].
    ///
    /// Null JSON values are preserved as null. Non-string values are rejected because
    /// schemas are intentionally serialized as raw strings.
    @NotNullByDefault
    public static final class Adapter extends TypeAdapter<@Nullable JsonSchema> {
        /// Writes the schema as a raw string, or JSON null when the value is null.
        @Override
        public void write(JsonWriter out, @Nullable JsonSchema value) throws IOException {
            if (value != null) {
                out.value(value.value);
            } else {
                out.nullValue();
            }
        }

        /// Reads a schema from a raw string or null JSON token.
        @Override
        public @Nullable JsonSchema read(JsonReader in) throws IOException {
            JsonElement element = JsonParser.parseReader(in);
            if (element.isJsonNull()) {
                return null;
            }

            return parseElement(element, "value");
        }
    }
}
