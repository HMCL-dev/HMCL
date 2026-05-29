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
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for legacy config migration into current settings.
@NotNullByDefault
public final class ConfigMigrationTest {
    /// Tests migrating legacy language fields into the current main config field.
    @Test
    public void migratesLegacyLocalizationToLanguage() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "localization": "zh_CN"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyLanguage(settings);
        Config config = Objects.requireNonNull(Config.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(config.toJson()).getAsJsonObject();

        assertFalse(settings.has("localization"));
        assertEquals("zh-Hans", settings.get("language").getAsString());
        assertEquals("zh-Hans", config.languageProperty().get().getName());
        assertFalse(serialized.has("localization"));
        assertEquals("zh-Hans", serialized.get("language").getAsString());
    }

    /// Tests that legacy Traditional Chinese language values are migrated before locale deserialization.
    @Test
    public void migratesLegacyTraditionalChineseLanguage() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "localization": "zh"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyLanguage(settings);
        Config config = Objects.requireNonNull(Config.fromJson(settings));

        assertEquals("zh-Hant", settings.get("language").getAsString());
        assertEquals("zh-Hant", config.languageProperty().get().getName());
    }

    /// Tests that config serialization preserves a patch-version schema and unknown fields.
    @Test
    public void preservesPatchSchemaAndUnknownFields() {
        Config config = Objects.requireNonNull(Config.fromJson(JsonParser.parseString("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/settings/1.0.1",
                  "futureField": true
                }
                """).getAsJsonObject()));

        JsonObject serialized = JsonParser.parseString(config.toJson()).getAsJsonObject();

        assertEquals("https://schemas.glavo.site/hmcl/settings/1.0.1",
                serialized.get(JsonSchema.DEFAULT_MEMBER_NAME).getAsString());
        assertTrue(serialized.get("futureField").getAsBoolean());
    }

    /// Tests migrating the legacy workspace-wide automatic Java agent permission into game settings.
    @Test
    public void migratesLegacyAllowAutoAgentToGameSettings() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "allowAutoAgent": true
                }
                """).getAsJsonObject();

        Config config = new Config();
        GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();

        LegacyConfigMigrator.migrateLegacyAllowAutoAgent(
                config,
                gameSettingsPresets,
                settings.remove("allowAutoAgent"));
        JsonObject serializedConfig = JsonParser.parseString(config.toJson()).getAsJsonObject();
        JsonObject serializedGameSettings = JsonParser.parseString(
                JsonUtils.GSON.toJson(gameSettingsPresets, GameSettingsPresets.class)
        ).getAsJsonObject();

        assertFalse(settings.has("allowAutoAgent"));
        assertFalse(serializedConfig.has("allowAutoAgent"));
        assertEquals(1, gameSettingsPresets.getPresets().size());

        GameSettings.Preset preset = gameSettingsPresets.getPresets().get(0);
        assertEquals(preset.idProperty().getValue(), config.defaultGameSettingsPresetProperty().get());
        assertTrue(preset.allowAutoAgentProperty().getValue());
        assertTrue(serializedGameSettings
                .getAsJsonArray("presets")
                .get(0)
                .getAsJsonObject()
                .get("allowAutoAgent")
                .getAsBoolean());
    }

    /// Tests migrating the legacy workspace-wide automatic game options switch into game settings.
    @Test
    public void migratesLegacyDisableAutoGameOptionsToGameSettings() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "disableAutoGameOptions": true
                }
                """).getAsJsonObject();

        Config config = new Config();
        GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();

        LegacyConfigMigrator.migrateLegacyDisableAutoGameOptions(
                config,
                gameSettingsPresets,
                settings.remove("disableAutoGameOptions"));
        JsonObject serializedConfig = JsonParser.parseString(config.toJson()).getAsJsonObject();
        JsonObject serializedGameSettings = JsonParser.parseString(
                JsonUtils.GSON.toJson(gameSettingsPresets, GameSettingsPresets.class)
        ).getAsJsonObject();

        assertFalse(settings.has("disableAutoGameOptions"));
        assertFalse(serializedConfig.has("disableAutoGameOptions"));
        assertEquals(1, gameSettingsPresets.getPresets().size());

        GameSettings.Preset preset = gameSettingsPresets.getPresets().get(0);
        assertEquals(preset.idProperty().getValue(), config.defaultGameSettingsPresetProperty().get());
        assertTrue(preset.disableAutoGameOptionsProperty().getValue());
        assertTrue(serializedGameSettings
                .getAsJsonArray("presets")
                .get(0)
                .getAsJsonObject()
                .get("disableAutoGameOptions")
                .getAsBoolean());
    }
}
