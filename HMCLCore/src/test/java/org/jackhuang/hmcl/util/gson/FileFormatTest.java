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
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for file format parsing and compatibility checks.
@NotNullByDefault
public final class FileFormatTest {
    /// Tests reading file format objects.
    @Test
    public void readsFileFormat() {
        JsonObject object = new JsonObject();
        object.add("format", createFormatObject("hmcl.config", "3.0"));

        FileFormat format = FileFormat.readFrom(object);

        assertEquals("hmcl.config", format.id());
        assertEquals(new FormatVersion(3, 0), format.version());
        assertEquals("hmcl.config/3.0", format.toString());
    }

    /// Tests serialization of file format objects.
    @Test
    public void serializesFileFormat() {
        FileFormat format = new FileFormat("hmcl.config", new FormatVersion(3, 0));

        JsonObject serialized = JsonParser.parseString(JsonUtils.GSON.toJson(format)).getAsJsonObject();

        assertEquals("hmcl.config", serialized.get("id").getAsString());
        assertEquals("3.0", serialized.get("version").getAsString());
        assertEquals(format, JsonUtils.GSON.fromJson(serialized, FileFormat.class));
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

        object.add("format", createFormatObject("hmcl.config", "3.x"));
        FileFormat.CheckResult invalidVersion = FileFormat.check(object, expected);
        assertTrue(invalidVersion.isInvalid());
        assertEquals("{\"id\":\"hmcl.config\",\"version\":\"3.x\"}", invalidVersion.invalidValue());

        object.add("format", createFormatObject("hmcl.game-settings-presets", "1.0"));
        FileFormat.CheckResult unexpected = FileFormat.check(object, expected);
        assertTrue(unexpected.isUnexpectedId());
        assertEquals("hmcl.game-settings-presets", unexpected.actual().id());

        object.add("format", createFormatObject("hmcl.config", "3.1"));
        FileFormat.CheckResult newerMinor = FileFormat.check(object, expected);
        assertTrue(newerMinor.isNewerThanExpected());
        assertFalse(newerMinor.hasNewerMajorVersion());

        object.add("format", createFormatObject("hmcl.config", "4.0"));
        FileFormat.CheckResult newerMajor = FileFormat.check(object, expected);
        assertTrue(newerMajor.isNewerThanExpected());
        assertTrue(newerMajor.hasNewerMajorVersion());
    }

    /// Creates a file format JSON object.
    private static JsonObject createFormatObject(String id, String version) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("version", version);
        return object;
    }
}
