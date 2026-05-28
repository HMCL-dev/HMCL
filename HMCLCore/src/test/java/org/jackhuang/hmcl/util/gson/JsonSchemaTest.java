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
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for JSON schema URL parsing and compatibility checks.
@NotNullByDefault
public final class JsonSchemaTest {
    /// Tests reading schema URL strings.
    @Test
    public void readsSchema() {
        JsonObject object = new JsonObject();
        object.addProperty(JsonSchema.DEFAULT_MEMBER_NAME, schemaUrl("settings", "3.0"));

        JsonSchema schema = JsonSchema.readFromMember(object);

        assertEquals("settings", schema.id());
        assertEquals(new JsonSchema.Version(3, 0), schema.version());
        assertEquals(schemaUrl("settings", "3.0"), schema.url());
        assertEquals(schemaUrl("settings", "3.0"), schema.toString());
    }

    /// Tests serialization of schema URL strings.
    @Test
    public void serializesSchema() {
        JsonSchema schema = new JsonSchema("settings", new JsonSchema.Version(3, 0));

        JsonElement serialized = JsonParser.parseString(JsonUtils.GSON.toJson(schema));

        assertEquals(schema.url(), serialized.getAsString());
        assertEquals(schema, JsonUtils.GSON.fromJson(serialized, JsonSchema.class));
    }

    /// Tests schema URL compatibility check statuses.
    @Test
    public void checksSchema() {
        JsonSchema expected = new JsonSchema("settings", new JsonSchema.Version(3, 0));
        JsonObject object = new JsonObject();

        JsonSchema.CheckResult missing = JsonSchema.check(object, expected);
        assertTrue(missing.isMissing());

        object.addProperty(JsonSchema.DEFAULT_MEMBER_NAME, "hmcl.config/3.x");
        JsonSchema.CheckResult invalid = JsonSchema.check(object, expected);
        assertTrue(invalid.isInvalid());
        assertEquals("\"hmcl.config/3.x\"", invalid.invalidValue());

        object.addProperty(JsonSchema.DEFAULT_MEMBER_NAME, schemaUrl("settings", "3.x"));
        JsonSchema.CheckResult invalidVersion = JsonSchema.check(object, expected);
        assertTrue(invalidVersion.isInvalid());
        assertEquals("\"" + schemaUrl("settings", "3.x") + "\"", invalidVersion.invalidValue());

        object.addProperty(JsonSchema.DEFAULT_MEMBER_NAME, schemaUrl("game-settings", "1.0"));
        JsonSchema.CheckResult unexpected = JsonSchema.check(object, expected);
        assertTrue(unexpected.isUnexpectedId());
        assertEquals("game-settings", unexpected.actual().id());

        object.addProperty(JsonSchema.DEFAULT_MEMBER_NAME, schemaUrl("settings", "3.1"));
        JsonSchema.CheckResult newerMinor = JsonSchema.check(object, expected);
        assertTrue(newerMinor.isNewerThanExpected());
        assertFalse(newerMinor.hasNewerMajorVersion());

        object.addProperty(JsonSchema.DEFAULT_MEMBER_NAME, schemaUrl("settings", "4.0"));
        JsonSchema.CheckResult newerMajor = JsonSchema.check(object, expected);
        assertTrue(newerMajor.isNewerThanExpected());
        assertTrue(newerMajor.hasNewerMajorVersion());
    }

    /// Creates a schema URL string.
    private static String schemaUrl(String id, String version) {
        return "https://schemas.glavo.site/hmcl/" + id + "/" + version + "/" + id + "-" + version + ".schema.json";
    }
}
