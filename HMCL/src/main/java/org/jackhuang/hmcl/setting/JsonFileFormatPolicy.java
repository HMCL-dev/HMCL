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
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Applies the common compatibility policy for JSON files with a [JsonFileFormat] marker.
///
/// @author Glavo
@NotNullByDefault
final class JsonFileFormatPolicy {
    /// Prevents instantiation.
    private JsonFileFormatPolicy() {
    }

    /// Checks the format marker of a JSON file.
    ///
    /// @param location the file location used in logs
    /// @param displayName the human-readable file type name used in logs
    /// @param object the JSON object that contains the format marker
    /// @param expected the file format supported by the current code
    /// @return the compatibility result
    static Result check(Path location, String displayName, JsonObject object, JsonFileFormat expected) {
        JsonFileFormat.CheckResult format = JsonFileFormat.check(object, expected);
        if (format.isMissing()) {
            LOG.warning("Missing format in " + displayName + ": " + location);
            return Result.UNREADABLE;
        } else if (format.isInvalid()) {
            LOG.warning("Invalid format in " + displayName + ": "
                    + location + ", Actual: " + format.invalidValue());
            return Result.UNREADABLE;
        } else if (format.isUnexpectedId()) {
            LOG.warning("Unexpected " + displayName + " format. Expected: "
                    + expected + ", Actual: " + format.actual());
            return Result.UNREADABLE;
        } else if (format.isNewerThanExpected()) {
            LOG.warning("Unsupported " + displayName + " format. Expected: "
                    + expected + ", Actual: " + format.actual());
            return format.hasNewerMajorVersion() ? Result.UNREADABLE : Result.READ_ONLY;
        } else {
            return Result.READ_WRITE;
        }
    }

    /// Result of checking whether a JSON file can be read and safely saved.
    ///
    /// @param readable whether the file may be deserialized
    /// @param allowSave whether the file may be overwritten
    record Result(boolean readable, boolean allowSave) {
        /// Result used when a file is compatible and may be saved.
        private static final Result READ_WRITE = new Result(true, true);

        /// Result used when a file is readable but must not be overwritten.
        private static final Result READ_ONLY = new Result(true, false);

        /// Result used when a file must not be read or overwritten.
        private static final Result UNREADABLE = new Result(false, false);
    }
}
