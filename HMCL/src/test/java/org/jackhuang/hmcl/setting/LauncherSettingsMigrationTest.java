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

import java.net.Proxy;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for legacy config migration into current launcher settings.
@NotNullByDefault
public final class LauncherSettingsMigrationTest {
    /// Tests migrating legacy language fields into the current launcher settings field.
    @Test
    public void migratesLegacyLocalizationToLanguage() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "localization": "zh_CN"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyLanguage(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertFalse(settings.has("localization"));
        assertEquals("zh-Hans", settings.get("language").getAsString());
        assertEquals("zh-Hans", launcherSettings.languageProperty().get().getName());
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
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));

        assertEquals("zh-Hant", settings.get("language").getAsString());
        assertEquals("zh-Hant", launcherSettings.languageProperty().get().getName());
    }

    /// Tests migrating the legacy common directory field into the current launcher settings field.
    @Test
    public void migratesLegacyCommonPathToCommonDirectory() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "commonpath": "/home/user/.minecraft"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyCommonDirectory(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertFalse(settings.has("commonpath"));
        assertEquals("/home/user/.minecraft", settings.get("commonDirectory").getAsString());
        assertEquals("/home/user/.minecraft", launcherSettings.commonDirectoryProperty().get());
        assertFalse(serialized.has("commonpath"));
        assertEquals("/home/user/.minecraft", serialized.get("commonDirectory").getAsString());
    }

    /// Tests migrating the legacy common directory type field into the current launcher settings field.
    @Test
    public void migratesLegacyCommonDirTypeToCommonDirectoryType() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "commonDirType": "CUSTOM"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyCommonDirectoryType(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertFalse(settings.has("commonDirType"));
        assertEquals("CUSTOM", settings.get("commonDirectoryType").getAsString());
        assertEquals(EnumCommonDirectory.CUSTOM, launcherSettings.commonDirectoryTypeProperty().get());
        assertFalse(serialized.has("commonDirType"));
        assertEquals("CUSTOM", serialized.get("commonDirectoryType").getAsString());
    }

    /// Tests migrating legacy enum ordinal fields into stable enum names.
    @Test
    public void migratesLegacyEnumOrdinals() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "backgroundType": 3,
                  "proxyType": 2
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyEnumOrdinals(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals("NETWORK", settings.get("backgroundType").getAsString());
        assertEquals("SOCKS", settings.get("proxyType").getAsString());
        assertEquals(EnumBackgroundImage.NETWORK, launcherSettings.backgroundImageTypeProperty().get());
        assertEquals(Proxy.Type.SOCKS, launcherSettings.proxyTypeProperty().get());
        assertEquals("NETWORK", serialized.get("backgroundType").getAsString());
        assertEquals("SOCKS", serialized.get("proxyType").getAsString());
    }

    /// Tests migrating legacy enum ordinal strings into stable enum names.
    @Test
    public void migratesLegacyEnumOrdinalStrings() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "backgroundType": "1",
                  "proxyType": "0"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyEnumOrdinals(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));

        assertEquals("CUSTOM", settings.get("backgroundType").getAsString());
        assertEquals("DIRECT", settings.get("proxyType").getAsString());
        assertEquals(EnumBackgroundImage.CUSTOM, launcherSettings.backgroundImageTypeProperty().get());
        assertEquals(Proxy.Type.DIRECT, launcherSettings.proxyTypeProperty().get());
    }

    /// Tests migrating legacy automatic download source fields into current download source fields.
    @Test
    public void migratesLegacyAutomaticDownloadSources() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "autoChooseDownloadType": true,
                  "versionListSource": "mirror",
                  "downloadType": "mojang"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyDownloadSources(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertFalse(settings.has("autoChooseDownloadType"));
        assertFalse(settings.has("downloadType"));
        assertEquals("MIRROR", settings.get("versionListSource").getAsString());
        assertEquals("MIRROR", settings.get("fileDownloadSource").getAsString());
        assertEquals(DownloadSource.MIRROR, launcherSettings.versionListSourceProperty().get());
        assertEquals(DownloadSource.MIRROR, launcherSettings.fileDownloadSourceProperty().get());
        assertFalse(serialized.has("autoChooseDownloadType"));
        assertFalse(serialized.has("downloadType"));
    }

    /// Tests migrating the legacy balanced automatic download source into the default source.
    @Test
    public void migratesLegacyBalancedDownloadSource() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "autoChooseDownloadType": true,
                  "versionListSource": "balanced"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyDownloadSources(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));

        assertEquals("DEFAULT", settings.get("versionListSource").getAsString());
        assertEquals("DEFAULT", settings.get("fileDownloadSource").getAsString());
        assertEquals(DownloadSource.DEFAULT, launcherSettings.versionListSourceProperty().get());
        assertEquals(DownloadSource.DEFAULT, launcherSettings.fileDownloadSourceProperty().get());
    }

    /// Tests migrating legacy direct download source fields into current download source fields.
    @Test
    public void migratesLegacyDirectDownloadSources() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "autoChooseDownloadType": false,
                  "versionListSource": "official",
                  "downloadType": "bmclapi"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyDownloadSources(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertFalse(settings.has("autoChooseDownloadType"));
        assertFalse(settings.has("downloadType"));
        assertEquals("MIRROR", settings.get("versionListSource").getAsString());
        assertEquals("MIRROR", settings.get("fileDownloadSource").getAsString());
        assertEquals(DownloadSource.MIRROR, launcherSettings.versionListSourceProperty().get());
        assertEquals(DownloadSource.MIRROR, launcherSettings.fileDownloadSourceProperty().get());
        assertFalse(serialized.has("autoChooseDownloadType"));
        assertFalse(serialized.has("downloadType"));
    }

    /// Tests migrating the legacy Mojang direct download source into the official source.
    @Test
    public void migratesLegacyMojangDownloadSource() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "autoChooseDownloadType": false,
                  "downloadType": "mojang"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.migrateLegacyDownloadSources(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));

        assertEquals("OFFICIAL", settings.get("versionListSource").getAsString());
        assertEquals("OFFICIAL", settings.get("fileDownloadSource").getAsString());
        assertEquals(DownloadSource.OFFICIAL, launcherSettings.versionListSourceProperty().get());
        assertEquals(DownloadSource.OFFICIAL, launcherSettings.fileDownloadSourceProperty().get());
    }

    /// Tests that launcher settings serialization preserves a patch-version schema and unknown fields.
    @Test
    public void preservesPatchSchemaAndUnknownFields() {
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(JsonParser.parseString("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/settings/1.0.1",
                  "futureField": true
                }
                """).getAsJsonObject()));

        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals("https://schemas.glavo.site/hmcl/settings/1.0.1",
                serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
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

        LauncherSettings launcherSettings = new LauncherSettings();
        GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();

        LegacyConfigMigrator.migrateLegacyAllowAutoAgent(
                launcherSettings,
                gameSettingsPresets,
                settings.remove("allowAutoAgent"));
        JsonObject serializedLauncherSettings = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();
        JsonObject serializedGameSettings = JsonParser.parseString(
                JsonUtils.GSON.toJson(gameSettingsPresets, GameSettingsPresets.class)
        ).getAsJsonObject();

        assertFalse(settings.has("allowAutoAgent"));
        assertFalse(serializedLauncherSettings.has("allowAutoAgent"));
        assertEquals(1, gameSettingsPresets.getPresets().size());

        GameSettings.Preset preset = gameSettingsPresets.getPresets().get(0);
        assertEquals(preset.idProperty().getValue(), launcherSettings.defaultGameSettingsPresetProperty().get());
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

        LauncherSettings launcherSettings = new LauncherSettings();
        GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();

        LegacyConfigMigrator.migrateLegacyDisableAutoGameOptions(
                launcherSettings,
                gameSettingsPresets,
                settings.remove("disableAutoGameOptions"));
        JsonObject serializedLauncherSettings = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();
        JsonObject serializedGameSettings = JsonParser.parseString(
                JsonUtils.GSON.toJson(gameSettingsPresets, GameSettingsPresets.class)
        ).getAsJsonObject();

        assertFalse(settings.has("disableAutoGameOptions"));
        assertFalse(serializedLauncherSettings.has("disableAutoGameOptions"));
        assertEquals(1, gameSettingsPresets.getPresets().size());

        GameSettings.Preset preset = gameSettingsPresets.getPresets().get(0);
        assertEquals(preset.idProperty().getValue(), launcherSettings.defaultGameSettingsPresetProperty().get());
        assertTrue(preset.disableAutoGameOptionsProperty().getValue());
        assertTrue(serializedGameSettings
                .getAsJsonArray("presets")
                .get(0)
                .getAsJsonObject()
                .get("disableAutoGameOptions")
                .getAsBoolean());
    }
}
