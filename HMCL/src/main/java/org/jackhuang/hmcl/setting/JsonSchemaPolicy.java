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
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Applies the common compatibility policy for JSON files with a [JsonSchema] marker.
///
/// @author Glavo
@NotNullByDefault
public final class JsonSchemaPolicy {
    /// Prevents instantiation.
    private JsonSchemaPolicy() {
    }

    /// Checks the schema marker of a JSON file.
    ///
    /// @param location the file location used in logs
    /// @param displayName the human-readable file type name used in logs
    /// @param object the JSON object that contains the schema marker
    /// @param expected the JSON schema supported by the current code
    /// @return the compatibility result
    public static Result check(Path location, String displayName, JsonObject object, JsonSchema expected) {
        if (!expected.isParsed()) {
            throw new IllegalArgumentException("Expected JSON schema must be parseable: " + expected);
        }

        if (!object.has(JsonSchema.PROPERTY_SCHEMA)) {
            LOG.warning("Missing schema in " + displayName + ": " + location);
            return Result.UNREADABLE;
        }

        JsonSchema actual;
        try {
            actual = JsonSchema.readFromMember(object);
        } catch (JsonParseException e) {
            LOG.warning("Invalid schema in " + displayName + ": "
                    + location + ", Actual: " + object.get(JsonSchema.PROPERTY_SCHEMA));
            return Result.UNREADABLE;
        }

        @Nullable JsonSchema.Parsed actualParsed = actual.parsed();
        @Nullable JsonSchema.Parsed expectedParsed = expected.parsed();
        if (actualParsed == null) {
            LOG.warning("Unparseable schema in " + displayName + ": "
                    + location + ", Actual: " + actual);
            return Result.UNREADABLE;
        }

        if (!actualParsed.id().equals(expectedParsed.id())) {
            LOG.warning("Unexpected " + displayName + " schema. Expected: "
                    + expected + ", Actual: " + actual);
            return Result.UNREADABLE;
        }

        if (actualParsed.version().major() != expectedParsed.version().major()) {
            LOG.warning("Unsupported " + displayName + " schema. Expected: "
                    + expected + ", Actual: " + actual);
            return Result.UNREADABLE;
        }

        if (actualParsed.version().minor() > expectedParsed.version().minor()) {
            LOG.warning("Unsupported " + displayName + " schema. Expected: "
                    + expected + ", Actual: " + actual);
            return Result.READ_ONLY_PRESERVE_SCHEMA;
        }

        if (actualParsed.version().minor() == expectedParsed.version().minor()) {
            return Result.READ_WRITE_PRESERVE_SCHEMA;
        }

        return Result.READ_WRITE;
    }

    /// Result of checking whether a JSON file can be read and safely saved.
    ///
    /// @param readable whether the file may be deserialized
    /// @param allowSave whether the file may be overwritten
    /// @param preserveSchema whether saving should keep the original schema value
    public record Result(boolean readable, boolean allowSave, boolean preserveSchema) {
        /// Result used when a file is compatible, may be saved, and should be upgraded to the expected schema.
        private static final Result READ_WRITE = new Result(true, true, false);

        /// Result used when a file is compatible, may be saved, and should preserve the original schema and unknown members.
        private static final Result READ_WRITE_PRESERVE_SCHEMA = new Result(true, true, true);

        /// Result used when a file is readable but must not be overwritten.
        private static final Result READ_ONLY_PRESERVE_SCHEMA = new Result(true, false, true);

        /// Result used when a file must not be read or overwritten.
        private static final Result UNREADABLE = new Result(false, false, false);
    }
}
