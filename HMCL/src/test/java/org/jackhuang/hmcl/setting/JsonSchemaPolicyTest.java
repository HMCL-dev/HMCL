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
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Tests for JSON schema compatibility policy decisions.
@NotNullByDefault
public final class JsonSchemaPolicyTest {
    /// Tests policy results for supported and unsupported schema markers.
    @Test
    public void checksSchemaCompatibility() {
        JsonSchema expected = new JsonSchema("settings", new JsonSchema.Version(3, 0, 1));
        Path location = Path.of("settings.json");
        JsonObject object = new JsonObject();

        assertEquals(new JsonSchemaPolicy.Result(false, false, false),
                JsonSchemaPolicy.check(location, "settings file", object, expected));

        object.addProperty(JsonSchema.PROPERTY_SCHEMA, "hmcl.config/3.x");
        assertEquals(new JsonSchemaPolicy.Result(false, false, false),
                JsonSchemaPolicy.check(location, "settings file", object, expected));

        object.add(JsonSchema.PROPERTY_SCHEMA, new JsonObject());
        assertEquals(new JsonSchemaPolicy.Result(false, false, false),
                JsonSchemaPolicy.check(location, "settings file", object, expected));

        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("settings", "3.x"));
        assertEquals(new JsonSchemaPolicy.Result(false, false, false),
                JsonSchemaPolicy.check(location, "settings file", object, expected));

        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("game-settings", "1.0.0"));
        assertEquals(new JsonSchemaPolicy.Result(false, false, false),
                JsonSchemaPolicy.check(location, "settings file", object, expected));

        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("settings", "4.0.0"));
        assertEquals(new JsonSchemaPolicy.Result(false, false, false),
                JsonSchemaPolicy.check(location, "settings file", object, expected));

        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("settings", "3.1.0"));
        assertEquals(new JsonSchemaPolicy.Result(true, false, true),
                JsonSchemaPolicy.check(location, "settings file", object, expected));

        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("settings", "3.0"));
        assertEquals(new JsonSchemaPolicy.Result(true, true, true),
                JsonSchemaPolicy.check(location, "settings file", object, expected));

        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("settings", "3.0.2"));
        JsonSchemaPolicy.Result newerPatch =
                JsonSchemaPolicy.check(location, "settings file", object, expected);
        assertEquals(new JsonSchemaPolicy.Result(true, true, true), newerPatch);

        JsonSchema expectedLater = new JsonSchema("settings", new JsonSchema.Version(3, 1, 1));
        object.addProperty(JsonSchema.PROPERTY_SCHEMA, schemaUrl("settings", "3.0.9"));
        JsonSchemaPolicy.Result olderMinor =
                JsonSchemaPolicy.check(location, "settings file", object, expectedLater);
        assertEquals(new JsonSchemaPolicy.Result(true, true, false), olderMinor);
        assertFalse(olderMinor.preserveSchema());
    }

    /// Creates a schema URL string.
    private static String schemaUrl(String id, String version) {
        return "https://schemas.glavo.site/hmcl/" + id + "/" + version;
    }
}
