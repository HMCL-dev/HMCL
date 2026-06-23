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
import javafx.scene.paint.Color;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.theme.BackgroundLoadPolicy;
import org.jackhuang.hmcl.theme.NetworkBackgroundImageCachePolicy;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
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
        return accountIDFromLegacyIdentifier(profileName + ":" + profileName);
    }

    /// Returns the migrated account ID generated from one legacy selected-account identifier.
    private static String accountIDFromLegacyIdentifier(String legacyIdentifier) {
        return new AccountID(UUIDs.generateV5(
                LegacyConfigMigrator.LEGACY_ACCOUNT_ID_NAMESPACE,
                legacyIdentifier)).toString();
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

    /// Tests migrating the HMCL 2.x selected offline account username into the current account ID.
    @Test
    public void migratesLegacyHMCL2SelectedOfflineAccount() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path root = fileSystem.getPath("/launcher-settings-migration-tests");
            Files.createDirectories(root);
            Path config = Files.createTempFile(root, "hmcl2-selected-account-", ".json");
            Files.writeString(config, """
                    {
                      "_version": 0,
                      "auth": {
                        "offline": {
                          "IAuthenticator_UserName": "Alex",
                          "uuidMap": {
                            "Alex": "00000000000000000000000000000001"
                          }
                        }
                      }
                    }
                    """);

            LegacyConfigMigrator.LegacyConfigMigration migration =
                    Objects.requireNonNull(LegacyConfigMigrator.migrateLegacyConfig(config));

            LegacyConfigMigrator.AccountMigrationResult accountMigration =
                    Objects.requireNonNull(migration.detachedSettings().accountMigration());
            assertFalse(accountMigration.metadata().getAccounts().get(0).has("selected"));
            assertEquals(offlineAccountID("Alex"),
                    Objects.requireNonNull(migration.launcherSettings().selectedAccountProperty().get()).toString());
        }
    }

    /// Tests migrating legacy language fields into the current launcher settings field.
    @ParameterizedTest
    @CsvSource({
            "zh_CN, zh-Hans",
            "zh, zh-Hant"
    })
    public void migratesLegacyLocalizationToLanguage(String legacyLanguage, String expectedLanguage) {
        JsonObject settings = new JsonObject();
        settings.addProperty("localization", legacyLanguage);

        LegacyConfigMigrator.migrateLegacyLanguage(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertFalse(settings.has("localization"));
        assertEquals(expectedLanguage, settings.get("language").getAsString());
        assertEquals(expectedLanguage, launcherSettings.languageProperty().get().getName());
        assertFalse(serialized.has("localization"));
        assertEquals(expectedLanguage, serialized.get("language").getAsString());
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

    /// Tests serializing background fallback and loading controls.
    @Test
    public void serializesBackgroundLoadingControls() {
        LauncherSettings launcherSettings = new LauncherSettings();
        assertEquals(BackgroundLoadPolicy.WAIT_FOR_BACKGROUND, launcherSettings.backgroundLoadPolicyProperty().get());

        launcherSettings.backgroundFallbackTypeProperty().set(BackgroundType.PAINT);
        launcherSettings.backgroundFallbackPaintProperty().set(Color.web("#123456"));
        launcherSettings.backgroundLoadPolicyProperty().set(BackgroundLoadPolicy.SHOW_FALLBACK_WHILE_LOADING);
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals(BackgroundType.PAINT.name(), serialized.get("backgroundFallbackType").getAsString());
        assertEquals("#123456", serialized.get("backgroundFallbackPaint").getAsString());
        assertEquals(
                BackgroundLoadPolicy.SHOW_FALLBACK_WHILE_LOADING.name(),
                serialized.get("backgroundLoadPolicy").getAsString());

        LauncherSettings deserialized = Objects.requireNonNull(LauncherSettings.fromJson(serialized));
        assertEquals(BackgroundType.PAINT, deserialized.backgroundFallbackTypeProperty().get());
        assertEquals(Color.web("#123456"), deserialized.backgroundFallbackPaintProperty().get());
        assertEquals(
                BackgroundLoadPolicy.SHOW_FALLBACK_WHILE_LOADING,
                deserialized.backgroundLoadPolicyProperty().get());
    }

    /// Tests migrating the legacy theme color field into the custom theme color field.
    @Test
    public void migratesLegacyThemeToCustomThemeColor() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path root = fileSystem.getPath("/launcher-settings-migration-tests");
            Files.createDirectories(root);
            Path config = Files.createTempFile(root, "theme-color-", ".json");
            Files.writeString(config, """
                    {
                      "_version": 2,
                      "theme": "#336699"
                    }
                    """);

            LegacyConfigMigrator.LegacyConfigMigration migration =
                    Objects.requireNonNull(LegacyConfigMigrator.migrateLegacyConfig(config));
            LauncherSettings launcherSettings = migration.launcherSettings();
            JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

            assertFalse(serialized.has("theme"));
            assertFalse(serialized.has("themeColor"));
            assertEquals(ThemeColor.of("#336699"), launcherSettings.customThemeColorProperty().get());
            assertEquals("#336699", serialized.get("customThemeColor").getAsString());
        }
    }

    /// Tests migrating legacy background source fields.
    @Test
    public void migratesLegacyBackgroundSourceFields() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path root = fileSystem.getPath("/launcher-settings-migration-tests");
            Files.createDirectories(root);
            Path config = Files.createTempFile(root, "background-image-", ".json");
            Files.writeString(config, """
                    {
                      "_version": 2,
                      "backgroundImage": "/pictures/background.png",
                      "backgroundImageUrl": "https://example.com/background.png",
                      "backgroundPaint": "#336699"
                    }
                    """);

            LegacyConfigMigrator.LegacyConfigMigration migration =
                    Objects.requireNonNull(LegacyConfigMigrator.migrateLegacyConfig(config));
            LauncherSettings launcherSettings = migration.launcherSettings();
            JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

            assertFalse(serialized.has("backgroundImage"));
            assertFalse(serialized.has("backgroundImageUrl"));
            assertFalse(serialized.has("backgroundPaint"));
            assertEquals("/pictures/background.png", launcherSettings.customBackgroundImagePathProperty().get());
            assertEquals("https://example.com/background.png", launcherSettings.networkBackgroundImageUrlProperty().get());
            assertEquals(NetworkBackgroundImageCachePolicy.DISABLED,
                    launcherSettings.networkBackgroundImageCachePolicyProperty().get());
            assertEquals("/pictures/background.png", serialized.get("customBackgroundImagePath").getAsString());
            assertEquals("https://example.com/background.png", serialized.get("networkBackgroundImageUrl").getAsString());
            assertEquals(NetworkBackgroundImageCachePolicy.DISABLED.name(),
                    serialized.get("networkBackgroundImageCachePolicy").getAsString());
            assertEquals("#336699", serialized.get("customBackgroundPaint").getAsString());
        }
    }

    /// Tests migrating legacy enum ordinal fields into stable enum names.
    @ParameterizedTest
    @CsvSource({
            "3, 2, true, true, NETWORK, SOCKS",
            "1, DIRECT, false, true, CUSTOM, DIRECT",
            "1, 1, true, false, CUSTOM, SYSTEM"
    })
    public void migratesLegacyEnumOrdinals(
            String backgroundType,
            String proxyType,
            boolean numericValue,
            boolean hasProxy,
            String expectedBackgroundType,
            String expectedProxyType) {
        JsonObject settings = new JsonObject();
        settings.addProperty("hasProxy", hasProxy);
        if (numericValue) {
            settings.addProperty("backgroundType", Integer.parseInt(backgroundType));
            settings.addProperty("proxyType", Integer.parseInt(proxyType));
        } else {
            settings.addProperty("backgroundType", backgroundType);
            settings.addProperty("proxyType", proxyType);
        }

        LegacyConfigMigrator.migrateLegacyBackgroundImageType(settings);
        LegacyConfigMigrator.migrateLegacyProxyType(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals(expectedBackgroundType, settings.get("backgroundType").getAsString());
        assertEquals(expectedProxyType, settings.get("proxyType").getAsString());
        assertFalse(settings.has("hasProxy"));
        assertEquals(BackgroundType.valueOf(expectedBackgroundType), launcherSettings.backgroundTypeProperty().get());
        assertEquals(ProxyType.valueOf(expectedProxyType), launcherSettings.proxyTypeProperty().get());
        assertEquals(expectedBackgroundType, serialized.get("backgroundType").getAsString());
        assertEquals(expectedProxyType, serialized.get("proxyType").getAsString());
        assertFalse(serialized.has("hasProxy"));
    }

    /// Tests migrating the removed classic background type to the named built-in background setting.
    @ParameterizedTest
    @CsvSource({
            "CLASSIC, false",
            "2, true"
    })
    public void migratesLegacyClassicBackgroundType(String backgroundType, boolean numericValue) {
        JsonObject settings = new JsonObject();
        if (numericValue) {
            settings.addProperty("backgroundType", Integer.parseInt(backgroundType));
        } else {
            settings.addProperty("backgroundType", backgroundType);
        }

        LegacyConfigMigrator.migrateLegacyBackgroundImageType(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals(BackgroundType.BUILTIN.name(), settings.get("backgroundType").getAsString());
        assertEquals(BackgroundType.BUILTIN_CLASSIC, settings.get("builtinBackgroundName").getAsString());
        assertEquals(BackgroundType.BUILTIN, launcherSettings.backgroundTypeProperty().get());
        assertEquals(BackgroundType.BUILTIN_CLASSIC, launcherSettings.builtinBackgroundNameProperty().get());
        assertEquals(BackgroundType.BUILTIN.name(), serialized.get("backgroundType").getAsString());
        assertEquals(BackgroundType.BUILTIN_CLASSIC, serialized.get("builtinBackgroundName").getAsString());
    }

    /// Tests migrating legacy download source combinations into current download source fields.
    @ParameterizedTest
    @CsvSource(value = {
            "true, mirror, mojang, MIRROR, MIRROR",
            "true, balanced, null, DEFAULT, DEFAULT",
            "false, official, bmclapi, MIRROR, MIRROR",
            "false, null, mojang, OFFICIAL, OFFICIAL",
            "false, null, null, OFFICIAL, OFFICIAL"
    }, nullValues = "null")
    public void migratesLegacyDownloadSources(
            boolean autoChooseDownloadType,
            String versionListSource,
            String downloadType,
            String expectedVersionListSource,
            String expectedFileDownloadSource) {
        JsonObject settings = new JsonObject();
        settings.addProperty("autoChooseDownloadType", autoChooseDownloadType);
        if (versionListSource != null) {
            settings.addProperty("versionListSource", versionListSource);
        }
        if (downloadType != null) {
            settings.addProperty("downloadType", downloadType);
        }

        LegacyConfigMigrator.migrateLegacyDownloadSources(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertFalse(settings.has("autoChooseDownloadType"));
        assertFalse(settings.has("downloadType"));
        assertEquals(expectedVersionListSource, settings.get("versionListSource").getAsString());
        assertEquals(expectedFileDownloadSource, settings.get("fileDownloadSource").getAsString());
        assertEquals(DownloadSource.valueOf(expectedVersionListSource), launcherSettings.versionListSourceProperty().get());
        assertEquals(DownloadSource.valueOf(expectedFileDownloadSource), launcherSettings.fileDownloadSourceProperty().get());
        assertFalse(serialized.has("autoChooseDownloadType"));
        assertFalse(serialized.has("downloadType"));
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

        AccountMetadataStore accountMetadata =
                Objects.requireNonNull(LegacyConfigMigrator.extractAccounts(settings)).metadata();
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedAccount(settings));
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

        AccountMetadataStore accountMetadata =
                Objects.requireNonNull(LegacyConfigMigrator.extractAccounts(settings)).metadata();

        assertEquals("Alex:Alex", settings.get("selectedAccount").getAsString());
        assertFalse(accountMetadata.getAccounts().get(1).has("selected"));
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedAccount(settings));
        assertEquals(offlineAccountID("Alex"), settings.get("selectedAccount").getAsString());
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

        AccountMetadataStore accountMetadata =
                Objects.requireNonNull(LegacyConfigMigrator.extractAccounts(settings)).metadata();
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedAccount(settings));

        assertEquals(accountMetadata.getAccounts().get(0).get("accountID").getAsString(),
                settings.get("selectedAccount").getAsString());
    }

    /// Tests migrating a `$GLOBAL:` selected-account string by directly deriving the account ID from it.
    @Test
    public void migratesLegacyGlobalSelectedAccountStringDirectly() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "accounts": [
                    {
                      "type": "offline",
                      "username": "Alex"
                    }
                  ],
                  "selectedAccount": "$GLOBAL:Alex:Alex"
                }
                """).getAsJsonObject();

        AccountMetadataStore accountMetadata =
                Objects.requireNonNull(LegacyConfigMigrator.extractAccounts(settings)).metadata();
        assertTrue(LegacyConfigMigrator.migrateLegacySelectedAccount(settings));

        assertEquals(accountIDFromLegacyIdentifier("$GLOBAL:Alex:Alex"),
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

        LauncherState launcherState = LegacyConfigMigrator.extractLauncherState(state);

        assertEquals(0.1, launcherState.getX(), 1e-9);
        assertEquals(0.2, launcherState.getY(), 1e-9);
        assertEquals(1264, launcherState.getWidth(), 1e-9);
        assertEquals(704, launcherState.getHeight(), 1e-9);
    }

    /// Tests migrating legacy workspace-wide switches into the default game settings preset.
    @ParameterizedTest
    @CsvSource({
            "allowAutoAgent, allowAutoAgent",
            "disableAutoGameOptions, disableAutoGameOptions"
    })
    public void migratesLegacyWorkspaceSwitchesToGameSettings(String legacyProperty, String gameSettingsProperty) {
        JsonObject settings = new JsonObject();
        settings.addProperty(legacyProperty, true);

        LauncherSettings launcherSettings = new LauncherSettings();
        GameSettingsPresets gameSettingsPresets = new GameSettingsPresets();

        if ("allowAutoAgent".equals(legacyProperty)) {
            LegacyConfigMigrator.migrateLegacyAllowAutoAgent(
                    launcherSettings,
                    gameSettingsPresets,
                    settings.remove(legacyProperty));
        } else {
            LegacyConfigMigrator.migrateLegacyDisableAutoGameOptions(
                    launcherSettings,
                    gameSettingsPresets,
                    settings.remove(legacyProperty));
        }
        JsonObject serializedLauncherSettings = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();
        JsonObject serializedGameSettings = JsonParser.parseString(
                JsonUtils.GSON.toJson(gameSettingsPresets, GameSettingsPresets.class)
        ).getAsJsonObject();

        assertFalse(settings.has(legacyProperty));
        assertFalse(serializedLauncherSettings.has(legacyProperty));
        assertEquals(1, gameSettingsPresets.getPresets().size());

        GameSettings.Preset preset = gameSettingsPresets.getPresets().get(0);
        assertEquals(preset.idProperty().getValue(), launcherSettings.defaultGameSettingsPresetProperty().get());
        assertEquals(1, preset.autoNameNumberProperty().getValue());
        if ("allowAutoAgent".equals(gameSettingsProperty)) {
            assertTrue(preset.allowAutoAgentProperty().getValue());
        } else {
            assertTrue(preset.disableAutoGameOptionsProperty().getValue());
        }
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
                .get(gameSettingsProperty)
                .getAsBoolean());
    }
}
