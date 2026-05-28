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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/// Identifies the JSON schema used by a serialized file.
///
/// The JSON representation is a schema URL in the following fixed form:
///
/// `https://schemas.glavo.site/hmcl/<id>/<version>/<id>-<version>.schema.json`
///
/// Schema URLs use the following compatibility policy:
///
/// - When the schema ID differs from the expected schema, the file must be rejected.
/// - When the major version differs from the supported schema, the file must be rejected.
/// - When only the minor version is newer, the file may be read but must not be overwritten.
/// - When both major and minor versions match, the file may be read and saved normally.
///
/// @param id the stable schema identifier
/// @param version the schema version
/// @author Glavo
@JsonSerializable
@JsonAdapter(JsonSchema.Adapter.class)
@NotNullByDefault
public record JsonSchema(String id, Version version) {
    /// The default JSON member name used for schema URLs.
    public static final String DEFAULT_MEMBER_NAME = "$schema";

    /// The schema URL scheme.
    private static final String SCHEME = "https";

    /// The schema URL host.
    private static final String HOST = "schemas.glavo.site";

    /// The schema URL root path.
    private static final String ROOT_PATH = "hmcl";

    /// The schema file suffix.
    private static final String FILE_SUFFIX = ".schema.json";

    /// @param id the stable schema identifier
    /// @param version the schema version
    public JsonSchema {
        Objects.requireNonNull(id);
        Objects.requireNonNull(version);

        if (!isValidId(id)) {
            throw new IllegalArgumentException("Invalid JSON schema ID: " + id);
        }
    }

    /// Parses a schema URL.
    ///
    /// @param url the schema URL
    /// @return the parsed schema identifier
    /// @throws JsonParseException if the schema URL is invalid
    public static JsonSchema parseUrl(String url) throws JsonParseException {
        Objects.requireNonNull(url);

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new JsonParseException("Invalid JSON schema URL: " + url, e);
        }

        if (!SCHEME.equals(uri.getScheme())
                || !HOST.equals(uri.getHost())
                || uri.getPort() != -1
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new JsonParseException("Invalid JSON schema URL: " + url);
        }

        String[] segments = uri.getPath().split("/", -1);
        if (segments.length != 5 || !segments[0].isEmpty() || !ROOT_PATH.equals(segments[1])) {
            throw new JsonParseException("Invalid JSON schema URL path: " + url);
        }

        String id = segments[2];
        String versionString = segments[3];
        String fileName = segments[4];
        if (!isValidId(id)) {
            throw new JsonParseException("Invalid JSON schema ID: " + id);
        }

        Version version;
        try {
            version = Version.parse(versionString);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Invalid JSON schema version: " + versionString, e);
        }

        String canonicalVersion = version.toString();
        if (!versionString.equals(canonicalVersion)) {
            throw new JsonParseException("Non-canonical JSON schema version: " + versionString);
        }

        String expectedFileName = id + "-" + canonicalVersion + FILE_SUFFIX;
        if (!expectedFileName.equals(fileName)) {
            throw new JsonParseException("Invalid JSON schema file name: " + fileName);
        }

        return new JsonSchema(id, version);
    }

    /// Reads a schema URL from the default schema member of a container object.
    ///
    /// @param object the container object that contains the schema member
    /// @return the parsed schema identifier
    /// @throws JsonParseException if the schema member is missing or invalid
    public static JsonSchema readFromMember(JsonObject object) throws JsonParseException {
        return readFromMember(object, DEFAULT_MEMBER_NAME);
    }

    /// Reads a schema URL from a member of a container object.
    ///
    /// @param object the container object that contains the schema member
    /// @param memberName the JSON member name
    /// @return the parsed schema identifier
    /// @throws JsonParseException if the schema member is missing or invalid
    public static JsonSchema readFromMember(JsonObject object, String memberName) throws JsonParseException {
        Objects.requireNonNull(object);
        Objects.requireNonNull(memberName);

        return parseElement(object.get(memberName), "member `" + memberName + "`");
    }

    /// Reads and checks the default schema member of a JSON object.
    ///
    /// @param object the JSON object that contains the schema URL
    /// @param expected the schema supported by the current code
    /// @return the schema check result
    public static CheckResult check(JsonObject object, JsonSchema expected) {
        return check(object, DEFAULT_MEMBER_NAME, expected);
    }

    /// Reads and checks a schema URL JSON object member.
    ///
    /// @param object the JSON object that contains the schema URL
    /// @param memberName the JSON member name
    /// @param expected the schema supported by the current code
    /// @return the schema check result
    public static CheckResult check(JsonObject object, String memberName, JsonSchema expected) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(memberName);
        Objects.requireNonNull(expected);

        if (!object.has(memberName)) {
            return new CheckResult(null, expected, CheckResult.Status.MISSING, null);
        }

        try {
            JsonSchema actual = readFromMember(object, memberName);
            return new CheckResult(actual, expected, actual.id.equals(expected.id)
                    ? CheckResult.Status.VALID
                    : CheckResult.Status.UNEXPECTED_ID, null);
        } catch (JsonParseException e) {
            return new CheckResult(null, expected, CheckResult.Status.INVALID, String.valueOf(object.get(memberName)));
        }
    }

    /// Parses a schema URL from a JSON element.
    private static JsonSchema parseElement(@Nullable JsonElement element, String source) throws JsonParseException {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new JsonParseException("Invalid JSON schema " + source + ": " + element);
        }

        try {
            return parseUrl(primitive.getAsString());
        } catch (JsonParseException e) {
            throw new JsonParseException("Invalid JSON schema " + source + ": " + element, e);
        }
    }

    /// Returns the canonical schema URL.
    public String url() {
        return SCHEME + "://" + HOST + "/" + ROOT_PATH + "/" + id + "/" + version + "/" + id + "-" + version + FILE_SUFFIX;
    }

    /// Returns the canonical schema URL.
    @Override
    public String toString() {
        return url();
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

    /// Result of checking a serialized schema URL against the schema supported by the current code.
    ///
    /// @param actual the schema read from serialized data, or `null` when no valid schema was read
    /// @param expected the schema supported by the current code
    /// @param status the schema check status
    /// @param invalidValue the raw invalid JSON value text, or `null` when the member is valid or missing
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

            /// A schema member exists but cannot be parsed.
            INVALID,

            /// A schema member exists and was parsed, but its ID differs from the expected ID.
            UNEXPECTED_ID
        }

        /// Creates a schema check result.
        ///
        /// @param actual the schema read from serialized data, or `null` when no valid schema was read
        /// @param expected the schema supported by the current code
        /// @param status the schema check status
        /// @param invalidValue the raw invalid JSON value text, or `null` when the member is valid or missing
        public CheckResult {
            Objects.requireNonNull(expected);
            Objects.requireNonNull(status);
            if (status == Status.VALID || status == Status.UNEXPECTED_ID) {
                Objects.requireNonNull(actual);
            } else if (actual != null) {
                throw new IllegalArgumentException("Only parsed JSON schema checks may have an actual schema");
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

        /// Returns whether the serialized data contains an unparseable schema member.
        public boolean isInvalid() {
            return status == Status.INVALID;
        }

        /// Returns whether the serialized data uses an unexpected schema ID.
        public boolean isUnexpectedId() {
            return status == Status.UNEXPECTED_ID;
        }

        /// Returns whether the serialized schema is newer than the supported schema.
        public boolean isNewerThanExpected() {
            return status == Status.VALID && actual != null && actual.version.compareTo(expected.version) > 0;
        }

        /// Returns whether the serialized schema has a newer major version than the supported schema.
        public boolean hasNewerMajorVersion() {
            return status == Status.VALID && actual != null && actual.version.major() > expected.version.major();
        }
    }

    /// Gson adapter for the JSON string representation of [JsonSchema].
    ///
    /// Null JSON values are preserved as null. Non-string values are rejected because
    /// schemas are intentionally serialized as canonical URLs.
    @NotNullByDefault
    public static final class Adapter extends TypeAdapter<@Nullable JsonSchema> {
        /// Writes the schema as a URL string, or JSON null when the value is null.
        @Override
        public void write(JsonWriter out, @Nullable JsonSchema value) throws IOException {
            if (value != null) {
                out.value(value.url());
            } else {
                out.nullValue();
            }
        }

        /// Reads a schema from a URL string or null JSON token.
        @Override
        public @Nullable JsonSchema read(JsonReader in) throws IOException {
            JsonElement element = JsonParser.parseReader(in);
            if (element.isJsonNull()) {
                return null;
            }

            return parseElement(element, "value");
        }
    }

    /// Semantic version marker for a serialized JSON schema.
    ///
    /// The string representation is the strict `major.minor` form.
    ///
    /// @param major the major schema version
    /// @param minor the minor schema version
    /// @author Glavo
    @NotNullByDefault
    public record Version(int major, int minor) implements Comparable<Version> {
        /// @param major the major schema version
        /// @param minor the minor schema version
        public Version {
            if (major < 0) throw new IllegalArgumentException("Major version must be non-negative: " + major);
            if (minor < 0) throw new IllegalArgumentException("Minor version must be non-negative: " + minor);
        }

        /// Parses a schema version string.
        ///
        /// @param version the version string in `major.minor` form
        /// @return the parsed schema version
        /// @throws IllegalArgumentException if the version string is invalid
        public static Version parse(String version) {
            int dot = version.indexOf('.');
            if (dot <= 0 || dot != version.lastIndexOf('.') || dot == version.length() - 1) {
                throw new IllegalArgumentException("Invalid JSON schema version: " + version);
            }

            try {
                return new Version(parsePart(version, 0, dot), parsePart(version, dot + 1, version.length()));
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
            return major != o.major
                    ? Integer.compare(major, o.major)
                    : Integer.compare(minor, o.minor);
        }

        /// Returns the canonical `major.minor` string representation.
        @Override
        public String toString() {
            return major + "." + minor;
        }
    }
}
