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
/// - When the current code does not support the major version, the file must be rejected.
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
    /// The JSON property name used for schema strings.
    public static final String PROPERTY_SCHEMA = "$schema";

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
        return readFromMember(object, PROPERTY_SCHEMA);
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

    /// Checks whether a JSON object schema marker is compatible with the expected schema.
    ///
    /// This method only inspects the `$schema` member and returns structured compatibility
    /// information. It does not log, mutate the input object, or apply any caller-specific
    /// recovery policy.
    ///
    /// @param object the JSON object that contains the schema marker
    /// @param expected the JSON schema supported by the current code
    /// @return the compatibility result, including the resolved status and the parsed
    ///         schema value when one is available
    public static CompatibilityResult check(JsonObject object, JsonSchema expected) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(expected);
        if (!expected.isParsed()) {
            throw new IllegalArgumentException("Expected JSON schema must be parseable: " + expected);
        }

        if (!object.has(PROPERTY_SCHEMA)) {
            return CompatibilityResult.missing();
        }

        JsonSchema actual;
        try {
            actual = readFromMember(object);
        } catch (JsonParseException e) {
            return CompatibilityResult.invalid(String.valueOf(object.get(PROPERTY_SCHEMA)));
        }

        @Nullable Parsed actualParsed = actual.parsed;
        @Nullable Parsed expectedParsed = expected.parsed();
        if (actualParsed == null) {
            return CompatibilityResult.unparseable(actual);
        }

        if (!actualParsed.id().equals(expectedParsed.id())) {
            return CompatibilityResult.unexpectedId(actual);
        }

        if (actualParsed.version().major() != expectedParsed.version().major()) {
            return CompatibilityResult.unsupportedMajor(actual);
        }

        if (actualParsed.version().minor() > expectedParsed.version().minor()) {
            return CompatibilityResult.readOnlyPreserveSchema(actual);
        }

        if (actualParsed.version().minor() == expectedParsed.version().minor()) {
            return CompatibilityResult.readWritePreserveSchema(actual);
        }

        return CompatibilityResult.readWrite(actual);
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

    /// Result of checking whether a JSON file can be read and safely saved.
    ///
    /// @param actual the parsed schema marker read from the file, or `null` when the
    ///               marker is missing or invalid as JSON
    /// @param status the compatibility status derived from comparing the actual and
    ///               expected schema markers
    /// @param invalidValue the raw invalid `$schema` value text when the marker exists
    ///                     but is not a JSON string, otherwise `null`
    public record CompatibilityResult(
            @Nullable JsonSchema actual,
            Status status,
            @Nullable String invalidValue) {
        /// Detailed compatibility status.
        ///
        /// Each status carries the read/save behavior expected by callers:
        ///
        /// - `READ_WRITE`: the file matches an older compatible schema and may be rewritten.
        /// - `READ_WRITE_PRESERVE_SCHEMA`: the file may be rewritten, but the original schema
        ///   marker should be preserved.
        /// - `READ_ONLY_PRESERVE_SCHEMA`: the file may be read, but it must not be overwritten.
        /// - other values: the file must be rejected.
        public enum Status {
            READ_WRITE(true, true, false),
            READ_WRITE_PRESERVE_SCHEMA(true, true, true),
            READ_ONLY_PRESERVE_SCHEMA(true, false, true),
            MISSING(false, false, false),
            INVALID(false, false, false),
            UNPARSEABLE(false, false, false),
            UNEXPECTED_ID(false, false, false),
            UNSUPPORTED_MAJOR(false, false, false);

            private final boolean readable;
            private final boolean allowSave;
            private final boolean preserveSchema;

            Status(boolean readable, boolean allowSave, boolean preserveSchema) {
                this.readable = readable;
                this.allowSave = allowSave;
                this.preserveSchema = preserveSchema;
            }

            /// Returns whether a file with this status may be deserialized.
            public boolean readable() {
                return readable;
            }

            /// Returns whether a file with this status may be overwritten.
            public boolean allowSave() {
                return allowSave;
            }

            /// Returns whether saving should preserve the original `$schema` marker.
            public boolean preserveSchema() {
                return preserveSchema;
            }
        }

        /// Creates a readable and writable compatibility result.
        public static CompatibilityResult readWrite(JsonSchema actual) {
            return new CompatibilityResult(actual, Status.READ_WRITE, null);
        }

        /// Creates a readable and writable compatibility result that preserves the original schema string.
        public static CompatibilityResult readWritePreserveSchema(JsonSchema actual) {
            return new CompatibilityResult(actual, Status.READ_WRITE_PRESERVE_SCHEMA, null);
        }

        /// Creates a read-only compatibility result that preserves the original schema string.
        public static CompatibilityResult readOnlyPreserveSchema(JsonSchema actual) {
            return new CompatibilityResult(actual, Status.READ_ONLY_PRESERVE_SCHEMA, null);
        }

        /// Creates a missing-schema compatibility result.
        public static CompatibilityResult missing() {
            return new CompatibilityResult(null, Status.MISSING, null);
        }

        /// Creates an invalid-schema compatibility result.
        public static CompatibilityResult invalid(String invalidValue) {
            return new CompatibilityResult(null, Status.INVALID, invalidValue);
        }

        /// Creates an unparseable-schema compatibility result.
        public static CompatibilityResult unparseable(JsonSchema actual) {
            return new CompatibilityResult(actual, Status.UNPARSEABLE, null);
        }

        /// Creates an unexpected-schema-id compatibility result.
        public static CompatibilityResult unexpectedId(JsonSchema actual) {
            return new CompatibilityResult(actual, Status.UNEXPECTED_ID, null);
        }

        /// Creates an unsupported-major-version compatibility result.
        public static CompatibilityResult unsupportedMajor(JsonSchema actual) {
            return new CompatibilityResult(actual, Status.UNSUPPORTED_MAJOR, null);
        }

        /// Returns whether the file may be deserialized.
        public boolean readable() {
            return status.readable();
        }

        /// Returns whether the file may be overwritten.
        public boolean allowSave() {
            return status.allowSave();
        }

        /// Returns whether saving should preserve the original schema string.
        public boolean preserveSchema() {
            return status.preserveSchema();
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
