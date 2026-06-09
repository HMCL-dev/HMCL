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
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for user settings serialization and legacy migration.
@NotNullByDefault
public final class UserSettingsTest {
    /// Tests that new user settings are serialized with the current schema.
    @Test
    public void writesCurrentSchema() {
        UserSettings settings = new UserSettings();

        JsonObject serialized = JsonParser.parseString(settings.toJson()).getAsJsonObject();

        assertEquals(UserSettings.CURRENT_SCHEMA.url(), serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
    }

    /// Tests that legacy global config content can be read as current user settings.
    @Test
    public void readsLegacyGlobalConfigFormat() {
        String legacyUserConfig = """
                {
                  "agreementVersion": 1,
                  "terracottaAgreementVersion": 2,
                  "platformPromptVersion": 3,
                  "logRetention": 7,
                  "enableOfflineAccount": true,
                  "fontAntiAliasing": "gray",
                  "userJava": ["java-a"],
                  "disabledJava": ["java-b"]
                }
                """;
        UserSettings settings = Objects.requireNonNull(UserSettings.fromJson(legacyUserConfig));
        UserState state = Objects.requireNonNull(UserState.fromJson(legacyUserConfig));

        assertEquals(UserSettings.CURRENT_SCHEMA, settings.getSchema());
        assertEquals(UserState.CURRENT_SCHEMA, state.getSchema());
        assertEquals(1, state.agreementVersionProperty().get());
        assertEquals(2, state.terracottaAgreementVersionProperty().get());
        assertEquals(3, state.platformPromptVersionProperty().get());
        assertEquals(7, settings.logRetentionProperty().get());
        assertTrue(settings.enableOfflineAccountProperty().get());
        assertEquals("gray", settings.fontAntiAliasingProperty().get());
        assertTrue(settings.getUserJava().contains("java-a"));
        assertTrue(settings.getDisabledJava().contains("java-b"));

        JsonObject serialized = JsonParser.parseString(settings.toJson()).getAsJsonObject();
        assertEquals(UserSettings.CURRENT_SCHEMA.url(), serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
    }
}
