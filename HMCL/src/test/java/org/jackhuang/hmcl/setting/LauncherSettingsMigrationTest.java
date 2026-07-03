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
import org.jackhuang.hmcl.theme.BuiltinBackground;
import org.jackhuang.hmcl.theme.NetworkBackgroundImageCachePolicy;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

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

    /// Tests migrating 3.15.1 appearance fields into current launcher settings.
    @Test
    public void migratesLegacyAppearanceSettings() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path root = fileSystem.getPath("/launcher-settings-migration-tests");
            Files.createDirectories(root);
            Path config = Files.createTempFile(root, "appearance-", ".json");
            Files.writeString(config, """
                    {
                      "_version": 2,
                      "themeBrightness": "dark",
                      "theme": "#336699",
                      "titleTransparent": true,
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
            assertFalse(serialized.has("themeBrightness"));
            assertFalse(serialized.has("theme"));
            assertFalse(serialized.has("titleTransparent"));
            assertEquals("dark", launcherSettings.themeBrightnessModeProperty().get());
            assertEquals(ThemeColor.of("#336699"), launcherSettings.customThemeColorProperty().get());
            assertEquals(ThemeColorType.CUSTOM, launcherSettings.themeColorTypeProperty().get());
            assertTrue(launcherSettings.titleBarTransparentProperty().get());
            assertEquals("/pictures/background.png", launcherSettings.customBackgroundImagePathProperty().get());
            assertEquals("https://example.com/background.png",
                    launcherSettings.networkBackgroundImageUrlProperty().get());
            assertEquals(NetworkBackgroundImageCachePolicy.DISABLED,
                    launcherSettings.networkBackgroundImageCachePolicyProperty().get());
            assertTrue(launcherSettings.getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND));
            assertTrue(launcherSettings.getThemeAppearanceOverrides().contains(
                    LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE));
            assertTrue(launcherSettings.getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_COLOR));
            assertTrue(launcherSettings.getThemeAppearanceOverrides().contains(
                    LauncherSettings.THEME_APPEARANCE_TITLE_BAR_TRANSPARENT));
            assertFalse(launcherSettings.getThemeAppearanceOverrides().contains("networkBackgroundImageCachePolicy"));
            assertEquals("/pictures/background.png", serialized.get("customBackgroundImagePath").getAsString());
            assertEquals("https://example.com/background.png",
                    serialized.get("networkBackgroundImageUrl").getAsString());
            assertEquals(NetworkBackgroundImageCachePolicy.DISABLED.name(),
                    serialized.get("networkBackgroundImageCachePolicy").getAsString());
            assertEquals("#336699", serialized.get("customThemeColor").getAsString());
            assertEquals(ThemeColorType.CUSTOM.name(), serialized.get("themeColorType").getAsString());
            assertEquals("dark", serialized.get("themeBrightnessMode").getAsString());
            assertTrue(serialized.get("titleBarTransparent").getAsBoolean());
            assertEquals("#336699", serialized.get("customBackgroundPaint").getAsString());
        }
    }

    /// Tests migrating the removed classic background type to the built-in wallpaper ID setting.
    @Test
    public void migratesLegacyClassicBackgroundType() {
        JsonObject settings = new JsonObject();
        settings.addProperty("backgroundType", "CLASSIC");

        LegacyConfigMigrator.migrateLegacyBackgroundImageType(settings);
        LauncherSettings launcherSettings = Objects.requireNonNull(LauncherSettings.fromJson(settings));
        JsonObject serialized = JsonParser.parseString(launcherSettings.toJson()).getAsJsonObject();

        assertEquals(BackgroundType.BUILTIN.name(), settings.get("backgroundType").getAsString());
        assertEquals(BuiltinBackground.WALLPAPER_2016_02_25.id(), settings.get("builtinBackgroundId").getAsString());
        assertEquals(BackgroundType.BUILTIN, launcherSettings.backgroundTypeProperty().get());
        assertEquals(BuiltinBackground.WALLPAPER_2016_02_25.id(), launcherSettings.builtinBackgroundIdProperty().get());
        assertEquals(BackgroundType.BUILTIN.name(), serialized.get("backgroundType").getAsString());
        assertEquals(BuiltinBackground.WALLPAPER_2016_02_25.id(), serialized.get("builtinBackgroundId").getAsString());
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
}
