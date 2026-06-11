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
        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("settings", "3.0.1"));

        JsonSchema schema = JsonSchema.readFromMember(object);

        assertTrue(schema.isParsed());
        assertEquals("settings", schema.id());
        assertEquals(new JsonSchema.Version(3, 0, 1), schema.version());
        assertEquals(schemaUrl("settings", "3.0.1"), schema.url());
        assertEquals(schemaUrl("settings", "3.0.1"), schema.toString());
    }

    /// Tests reading schema URLs with an omitted patch number.
    @Test
    public void readsPatchlessSchema() {
        JsonObject object = new JsonObject();
        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("settings", "3.0"));

        JsonSchema schema = JsonSchema.readFromMember(object);

        assertTrue(schema.isParsed());
        assertEquals("settings", schema.id());
        assertEquals(new JsonSchema.Version(3, 0, 0), schema.version());
        assertEquals(schemaUrl("settings", "3.0"), schema.url());
    }

    /// Tests reading schema strings that are not HMCL schema URLs.
    @Test
    public void readsUnparseableSchemaString() {
        JsonObject object = new JsonObject();
        object.addProperty(JsonSchema.PROPERTY_SCHEMA, "https://json-schema.org/draft/2020-12/schema");

        JsonSchema schema = JsonSchema.readFromMember(object);

        assertFalse(schema.isParsed());
        assertNull(schema.id());
        assertNull(schema.version());
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.url());
    }

    /// Tests serialization of schema URL strings.
    @Test
    public void serializesSchema() {
        JsonSchema schema = new JsonSchema("settings", new JsonSchema.Version(3, 0, 0));

        JsonElement serialized = JsonParser.parseString(JsonUtils.GSON.toJson(schema));

        assertEquals(schemaUrl("settings", "3.0.0"), serialized.getAsString());
        assertEquals(schema, JsonUtils.GSON.fromJson(serialized, JsonSchema.class));
    }

    /// Tests serialization of arbitrary schema strings.
    @Test
    public void serializesUnparseableSchemaString() {
        JsonSchema schema = new JsonSchema("custom-schema");

        JsonElement serialized = JsonParser.parseString(JsonUtils.GSON.toJson(schema));

        assertEquals("custom-schema", serialized.getAsString());
        assertEquals(schema, JsonUtils.GSON.fromJson(serialized, JsonSchema.class));
    }

    /// Creates a schema URL string.
    private static String schemaUrl(String id, String version) {
        return "https://schemas.glavo.site/hmcl/" + id + "/" + version;
    }
}
