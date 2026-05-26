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

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for file format parsing and compatibility checks.
@NotNullByDefault
public final class FileFormatTest {
    /// Tests strict parsing of file format strings.
    @Test
    public void parsesFileFormat() {
        FileFormat format = FileFormat.parse("hmcl.config/3.0");

        assertEquals("hmcl.config", format.id());
        assertEquals(new FormatVersion(3, 0), format.version());
        assertEquals("hmcl.config/3.0", format.toString());
    }

    /// Tests invalid file format strings.
    @Test
    public void rejectsInvalidFileFormat() {
        assertThrows(IllegalArgumentException.class, () -> FileFormat.parse("hmcl.config"));
        assertThrows(IllegalArgumentException.class, () -> FileFormat.parse("hmcl.config/3"));
        assertThrows(IllegalArgumentException.class, () -> FileFormat.parse("hmcl.config/3.x"));
        assertThrows(IllegalArgumentException.class, () -> FileFormat.parse("hmcl.config/3.0/extra"));
    }

    /// Tests file format compatibility check statuses.
    @Test
    public void checksFileFormat() {
        FileFormat expected = new FileFormat("hmcl.config", new FormatVersion(3, 0));
        JsonObject object = new JsonObject();

        FileFormat.CheckResult missing = FileFormat.check(object, expected);
        assertTrue(missing.isMissing());

        object.addProperty("format", "hmcl.config/3.x");
        FileFormat.CheckResult invalid = FileFormat.check(object, expected);
        assertTrue(invalid.isInvalid());
        assertEquals("\"hmcl.config/3.x\"", invalid.invalidValue());

        object.addProperty("format", "hmcl.game-settings-presets/1.0");
        FileFormat.CheckResult unexpected = FileFormat.check(object, expected);
        assertTrue(unexpected.isUnexpectedId());
        assertEquals("hmcl.game-settings-presets", unexpected.actual().id());

        object.addProperty("format", "hmcl.config/3.1");
        FileFormat.CheckResult newerMinor = FileFormat.check(object, expected);
        assertTrue(newerMinor.isNewerThanExpected());
        assertFalse(newerMinor.hasNewerMajorVersion());

        object.addProperty("format", "hmcl.config/4.0");
        FileFormat.CheckResult newerMajor = FileFormat.check(object, expected);
        assertTrue(newerMajor.isNewerThanExpected());
        assertTrue(newerMajor.hasNewerMajorVersion());
    }
}
