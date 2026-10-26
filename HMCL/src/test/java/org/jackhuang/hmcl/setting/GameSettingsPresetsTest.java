// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached game settings presets.
@NotNullByDefault
public final class GameSettingsPresetsTest {
    /// Tests that the default preset selection belongs to LauncherSettings.
    @Test
    public void storesDefaultGameSettingsPresetInConfig() {
        GameSettingsPresetID id =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");
        LauncherSettings config = new LauncherSettings();

        config.defaultGameSettingsPresetProperty().set(id);
        JsonObject serialized = JsonParser.parseString(config.toJson()).getAsJsonObject();

        assertEquals(id.toString(), serialized.get(LauncherSettings.PROPERTY_DEFAULT_GAME_SETTINGS_PRESET).getAsString());
    }

    /// Tests that presets must be deserialized with a non-nil ID.
    @Test
    public void rejectsNilPresetId() {
        assertThrows(JsonParseException.class, () -> JsonUtils.GSON.fromJson("""
                {
                  "id": "game-settings-preset:00000000-0000-0000-0000-000000000000"
                }
                """, GameSettings.Preset.class));

        assertThrows(JsonParseException.class,
                () -> JsonUtils.GSON.fromJson("{}", GameSettings.Preset.class));
    }

    /// Tests that automatic preset name numbers are stored separately from custom names.
    @Test
    public void storesAutomaticPresetNameNumber() {
        GameSettings.Preset preset = new GameSettings.Preset(
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000"));

        preset.autoNameNumberProperty().setValue(3);
        JsonObject serialized = JsonParser.parseString(JsonUtils.GSON.toJson(preset, GameSettings.Preset.class))
                .getAsJsonObject();

        assertFalse(serialized.has("name"));
        assertEquals(3, serialized.get("autoNameNumber").getAsInt());
    }

    /// Tests that custom preset names are stored as strings.
    @Test
    public void storesCustomPresetNameAsString() {
        GameSettings.Preset preset = new GameSettings.Preset(
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000"));

        preset.nameProperty().setValue(LocalizedText.plain("Custom"));
        JsonObject serialized = JsonParser.parseString(JsonUtils.GSON.toJson(preset, GameSettings.Preset.class))
                .getAsJsonObject();

        assertEquals("Custom", serialized.get("name").getAsString());
        assertFalse(serialized.has("autoNameNumber"));
    }

    /// Tests that custom preset names and automatic name numbers are read independently.
    @Test
    public void readsCustomNameAndAutomaticNameNumber() {
        GameSettings.Preset automatic = JsonUtils.GSON.fromJson("""
                {
                  "id": "game-settings-preset:123e4567-e89b-12d3-a456-426614174000",
                  "autoNameNumber": 4
                }
                """, GameSettings.Preset.class);
        GameSettings.Preset custom = JsonUtils.GSON.fromJson("""
                {
                  "id": "game-settings-preset:123e4567-e89b-12d3-a456-426614174001",
                  "name": "Custom"
                }
                """, GameSettings.Preset.class);

        assertEquals(4, automatic.autoNameNumberProperty().getValue());
        assertNull(automatic.nameProperty().getValue());
        assertNull(custom.autoNameNumberProperty().getValue());
        assertEquals("Custom", Objects.requireNonNull(custom.nameProperty().getValue()).getText(List.of(Locale.ENGLISH)));
    }

    /// Tests that legacy profile-level game settings migrate to IDs separate from profile IDs.
    @Test
    public void migratesLegacyProfileGlobalSettingsToSeparatePresetId() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "configurations": {
                    "Dev": {
                      "gameDir": ".minecraft",
                      "global": {
                        "maxMemory": 2048
                      }
                    }
                  }
                }
                """).getAsJsonObject();
        JsonObject configurations = settings.getAsJsonObject("configurations").deepCopy();
        GameDirectories gameDirectories = Objects.requireNonNull(LegacyConfigMigrator.extractGameDirectoriesFromConfigJson(settings));
        GameSettingsPresets presets = new GameSettingsPresets();

        LegacyConfigMigrator.migrateLegacyPresetSettings(gameDirectories, presets, configurations);

        assertEquals(1, presets.getPresets().size());
        GameDirectory gameDirectory = gameDirectories.getGameDirectories().get(0);
        GameSettings.Preset preset = presets.getPresets().get(0);
        assertEquals(gameDirectory.getLegacyGameSettings(), preset.idProperty().getValue());
        assertNotEquals(gameDirectory.getId(), preset.idProperty().getValue());
        assertEquals(2048, preset.maxMemoryProperty().getValue());
    }
}
