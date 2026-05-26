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
public final class JsonFileFormatTest {
    /// Tests reading file format objects.
    @Test
    public void readsFileFormat() {
        JsonObject object = new JsonObject();
        object.add(JsonFileFormat.DEFAULT_MEMBER_NAME, createFormatObject("hmcl.settings", "3.0"));

        JsonFileFormat format = JsonFileFormat.readFromMember(object);

        assertEquals("hmcl.settings", format.id());
        assertEquals(new JsonFileFormat.Version(3, 0), format.version());
        assertEquals("hmcl.settings/3.0", format.toString());
    }

    /// Tests serialization of file format objects.
    @Test
    public void serializesFileFormat() {
        JsonFileFormat format = new JsonFileFormat("hmcl.settings", new JsonFileFormat.Version(3, 0));

        JsonObject serialized = JsonParser.parseString(JsonUtils.GSON.toJson(format)).getAsJsonObject();

        assertEquals("hmcl.settings", serialized.get("id").getAsString());
        assertEquals("3.0", serialized.get("version").getAsString());
        assertEquals(format, JsonUtils.GSON.fromJson(serialized, JsonFileFormat.class));
    }

    /// Tests file format compatibility check statuses.
    @Test
    public void checksFileFormat() {
        JsonFileFormat expected = new JsonFileFormat("hmcl.settings", new JsonFileFormat.Version(3, 0));
        JsonObject object = new JsonObject();

        JsonFileFormat.CheckResult missing = JsonFileFormat.check(object, expected);
        assertTrue(missing.isMissing());

        object.addProperty(JsonFileFormat.DEFAULT_MEMBER_NAME, "hmcl.config/3.x");
        JsonFileFormat.CheckResult invalid = JsonFileFormat.check(object, expected);
        assertTrue(invalid.isInvalid());
        assertEquals("\"hmcl.config/3.x\"", invalid.invalidValue());

        object.add(JsonFileFormat.DEFAULT_MEMBER_NAME, createFormatObject("hmcl.settings", "3.x"));
        JsonFileFormat.CheckResult invalidVersion = JsonFileFormat.check(object, expected);
        assertTrue(invalidVersion.isInvalid());
        assertEquals("{\"id\":\"hmcl.settings\",\"version\":\"3.x\"}", invalidVersion.invalidValue());

        object.add(JsonFileFormat.DEFAULT_MEMBER_NAME, createFormatObject("hmcl.game-settings", "1.0"));
        JsonFileFormat.CheckResult unexpected = JsonFileFormat.check(object, expected);
        assertTrue(unexpected.isUnexpectedId());
        assertEquals("hmcl.game-settings", unexpected.actual().id());

        object.add(JsonFileFormat.DEFAULT_MEMBER_NAME, createFormatObject("hmcl.settings", "3.1"));
        JsonFileFormat.CheckResult newerMinor = JsonFileFormat.check(object, expected);
        assertTrue(newerMinor.isNewerThanExpected());
        assertFalse(newerMinor.hasNewerMajorVersion());

        object.add(JsonFileFormat.DEFAULT_MEMBER_NAME, createFormatObject("hmcl.settings", "4.0"));
        JsonFileFormat.CheckResult newerMajor = JsonFileFormat.check(object, expected);
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
