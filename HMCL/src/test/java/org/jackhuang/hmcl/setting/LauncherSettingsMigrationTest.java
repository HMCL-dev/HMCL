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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for legacy config migration into current launcher settings.
@NotNullByDefault
public final class LauncherSettingsMigrationTest {
    /// Returns the migrated account ID generated for a legacy offline profile name.
    private static String offlineAccountID(String profileName) {
        return new AccountID(UUIDs.generateV5(
                LegacyConfigMigrator.LEGACY_ACCOUNT_ID_NAMESPACE,
                profileName + ":" + profileName)).toString();
    }

    @Test
    public void testNameSpaces() {
        assertEquals(LegacyConfigMigrator.LEGACY_ACCOUNT_ID_NAMESPACE,
                UUIDs.generateV5(UUIDs.NAMESPACE_URL, "hmcl:legacy-account"));

        assertEquals(LegacyConfigMigrator.LEGACY_GAME_SETTINGS_ID_NAMESPACE,
                UUIDs.generateV5(UUIDs.NAMESPACE_URL, "hmcl:legacy-game-settings"));

        assertEquals(LegacyConfigMigrator.LEGACY_PROFILE_ID_NAMESPACE,
                UUIDs.generateV5(UUIDs.NAMESPACE_URL, "hmcl:legacy-profile"));
    }

    /// Tests ignoring config files with numeric versions outside the legacy format range.
    @Test
    public void ignoresUnsupportedLegacyConfigVersion() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path root = fileSystem.getPath("/launcher-settings-migration-tests");
            Files.createDirectories(root);
            Path config = Files.createTempFile(root, "unsupported-legacy-config-", ".json");
            Files.writeString(config, """
                    {
                      "_version": 3,
                      "language": "en"
                    }
                    """);

            assertNull(LegacyConfigMigrator.migrateLegacyConfig(config));
        }
    }

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

    /// Tests serializing log font settings with log-specific names.
    @Test
    public void serializesLogFontSettings() {
        LauncherSettings launcherSettings = new LauncherSettings();
        launcherSettings.logFontFamilyProperty().set("Fira Code");
        launcherSettings.logFontSizeProperty().set(13.5);
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertFalse(serialized.has("fontFamily"));
        assertFalse(serialized.has("fontSize"));
        assertEquals("Fira Code", serialized.get("logFontFamily").getAsString());
        assertEquals(13.5, serialized.get("logFontSize").getAsDouble(), 1e-9);
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

        LegacyConfigMigrator.migrateLegacyBackgroundImageType(settings);
        LegacyConfigMigrator.migrateLegacyProxyType(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals("NETWORK", settings.get("backgroundType").getAsString());
        assertEquals("SOCKS", settings.get("proxyType").getAsString());
        assertEquals(BackgroundType.NETWORK, launcherSettings.backgroundTypeProperty().get());
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

        LegacyConfigMigrator.migrateLegacyBackgroundImageType(settings);
        LegacyConfigMigrator.migrateLegacyProxyType(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));

        assertEquals("CUSTOM", settings.get("backgroundType").getAsString());
        assertEquals("DIRECT", settings.get("proxyType").getAsString());
        assertEquals(BackgroundType.CUSTOM, launcherSettings.backgroundTypeProperty().get());
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

    /// Tests migrating the legacy selected account string into a structured account reference.
    @Test
    public void migratesLegacySelectedAccountToReference() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "accounts": [
                    {
                      "type": "offline",
                      "username": "Alex"
                    }
                  ],
                  "selectedAccount": "Alex:Alex"
                }
                """).getAsJsonObject();

        AccountStorages accountStorages = Objects.requireNonNull(LegacyConfigMigrator.extractAccountStorages(settings));
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedAccount(settings, accountStorages));
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));

        assertEquals(offlineAccountID("Alex"),
                Objects.requireNonNull(launcherSettings.selectedAccountProperty().get()).toString());
    }

    /// Tests migrating the legacy selected account marker into a structured account reference.
    @Test
    public void migratesLegacySelectedAccountMarkerToReference() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "accounts": [
                    {
                      "type": "offline",
                      "username": "Steve"
                    },
                    {
                      "type": "offline",
                      "username": "Alex",
                      "selected": true
                    }
                  ]
                }
                """).getAsJsonObject();

        AccountStorages accountStorages = Objects.requireNonNull(LegacyConfigMigrator.extractAccountStorages(settings));
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedAccount(settings, accountStorages));

        assertEquals(offlineAccountID("Alex"), settings.get("selectedAccount").getAsString());
        assertFalse(accountStorages.getAccounts().get(1).containsKey("selected"));
    }

    /// Tests migrating legacy selected Microsoft account identifiers with hyphenated UUIDs.
    @Test
    public void migratesLegacySelectedMicrosoftAccountToReference() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "accounts": [
                    {
                      "type": "microsoft",
                      "uuid": "123456781234123412341234567890ab",
                      "userid": "user-id"
                    }
                  ],
                  "selectedAccount": "microsoft:12345678-1234-1234-1234-1234567890ab"
                }
                """).getAsJsonObject();

        AccountStorages accountStorages = Objects.requireNonNull(LegacyConfigMigrator.extractAccountStorages(settings));
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedAccount(settings, accountStorages));

        assertEquals(accountStorages.getAccounts().get(0).get("accountID"),
                settings.get("selectedAccount").getAsString());
    }

    /// Tests serializing selected account references as account ID strings.
    @Test
    public void serializesSelectedAccountReferenceAsAccountID() {
        LauncherSettings launcherSettings = new LauncherSettings();
        launcherSettings.selectedAccountProperty().set(
                AccountID.parse("account:12345678-1234-1234-1234-1234567890ab"));

        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals("account:12345678-1234-1234-1234-1234567890ab",
                serialized.get("selectedAccount").getAsString());
    }

    /// Tests that launcher settings serialization preserves a patch-version schema and unknown fields.
    @Test
    public void preservesPatchSchemaAndUnknownFields() {
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(JsonParser.parseString("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/launcher-settings/1.0.1",
                  "futureField": true
                }
                """).getAsJsonObject()));

        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals("https://schemas.glavo.site/hmcl/launcher-settings/1.0.1",
                serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
        assertTrue(serialized.get("futureField").getAsBoolean());
    }

    /// Tests migrating legacy custom-decorated window bounds into content bounds.
    @Test
    public void migratesLegacyWindowContentBounds() {
        JsonObject state = JsonParser.parseString("""
                {
                  "x": 0.1,
                  "y": 0.2,
                  "width": 1280,
                  "height": 720
                }
                """).getAsJsonObject();

        LauncherState launcherState = LegacyConfigMigrator.extractLauncherState(state, 1000, 2000);

        assertEquals(0.108, launcherState.getX(), 1e-9);
        assertEquals(0.204, launcherState.getY(), 1e-9);
        assertEquals(1264, launcherState.getWidth(), 1e-9);
        assertEquals(704, launcherState.getHeight(), 1e-9);
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
        assertEquals(1, preset.autoNameNumberProperty().getValue());
        assertTrue(preset.allowAutoAgentProperty().getValue());
        assertEquals(1, serializedGameSettings
                .getAsJsonArray("presets")
                .get(0)
                .getAsJsonObject()
                .get("autoNameNumber")
                .getAsInt());
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
        assertEquals(1, preset.autoNameNumberProperty().getValue());
        assertTrue(preset.disableAutoGameOptionsProperty().getValue());
        assertEquals(1, serializedGameSettings
                .getAsJsonArray("presets")
                .get(0)
                .getAsJsonObject()
                .get("autoNameNumber")
                .getAsInt());
        assertTrue(serializedGameSettings
                .getAsJsonArray("presets")
                .get(0)
                .getAsJsonObject()
                .get("disableAutoGameOptions")
                .getAsBoolean());
    }
}
