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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Tests for instance-specific game settings.
@NotNullByDefault
public final class GameSettingsInstanceTest {
    /// Tests that instance game settings are serialized with their schema.
    @Test
    public void storesSchema() {
        GameSettings.Instance instance = new GameSettings.Instance();

        JsonObject serialized = JsonParser.parseString(
                LauncherSettings.SETTINGS_GSON.toJson(instance, GameSettings.Instance.class)
        ).getAsJsonObject();

        assertEquals(GameSettings.Instance.CURRENT_SCHEMA.url(),
                serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
    }

    /// Tests that matching migration receipts prevent replaying legacy per-version settings migration.
    @Test
    public void skipsLegacyInstanceMigrationWhenReceiptMatches() throws IOException {
        Path tempDir = createInstanceSettingsTestDirectory("receipt");
        Path versionRoot = tempDir.resolve("version");
        Files.createDirectories(versionRoot);

        Path legacySetting = versionRoot.resolve("hmclversion.cfg");
        Path receipt = versionRoot.resolve(".hmcl").resolve("instance-game-settings.migration-receipt.json");
        Files.writeString(legacySetting, "{\"width\":854}");
        MigrationReceipt.save(receipt, legacySetting);

        assertNull(LegacyGameSettingsMigrator.migrateInstanceGameSettings(
                versionRoot,
                tempDir,
                null,
                receipt));
    }

    /// Tests that legacy Java default selection is migrated to automatic selection.
    @Test
    public void migratesLegacyDefaultJavaSelectionToAuto() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "java": "Default"
                }
                """).getAsJsonObject(), true);

        assertEquals(JavaVersionType.AUTO, instance.javaTypeProperty().getValue());
    }

    /// Tests that legacy Java selection ordinals keep their old meaning after removing DEFAULT.
    @Test
    public void migratesLegacyDefaultJavaSelectionOrdinalToAuto() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "javaVersionType": 0
                }
                """).getAsJsonObject(), true);

        assertEquals(JavaVersionType.AUTO, instance.javaTypeProperty().getValue());
    }

    /// Tests that legacy Java DEFAULT enum names are migrated to automatic selection.
    @Test
    public void migratesLegacyDefaultJavaSelectionNameToAuto() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "javaVersionType": "DEFAULT"
                }
                """).getAsJsonObject(), true);

        assertEquals(JavaVersionType.AUTO, instance.javaTypeProperty().getValue());
    }

    /// Tests that legacy detected Java selection is migrated to a detected Java reference.
    @Test
    public void migratesLegacyDetectedJavaSelection() throws IOException {
        Path tempDir = createInstanceSettingsTestDirectory("legacy-detected-java");
        Path javaBinary = tempDir.resolve("java.exe");
        Files.writeString(javaBinary, "");

        JsonObject source = new JsonObject();
        source.addProperty("javaVersionType", "DETECTED");
        source.addProperty("java", "17.0.11+9");
        source.addProperty("defaultJavaPath", javaBinary.toString());

        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, source, true);

        assertEquals(JavaVersionType.DETECTED, instance.javaTypeProperty().getValue());
        assertEquals("17.0.11+9", instance.detectedJavaProperty().getValue().version());
        assertEquals(GameSettings.DetectedJava.hashExistingPath(javaBinary), instance.detectedJavaProperty().getValue().pathHash());
    }

    /// Tests that legacy Java version selection is migrated to the custom Java version field.
    @Test
    public void migratesLegacyVersionJavaSelection() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "javaVersionType": "VERSION",
                  "java": "17"
                }
                """).getAsJsonObject(), true);

        assertEquals(JavaVersionType.VERSION, instance.javaTypeProperty().getValue());
        assertEquals("17", instance.customJavaVersionProperty().getValue());
    }

    /// Creates a temporary directory under Gradle's build directory for instance settings tests.
    private static Path createInstanceSettingsTestDirectory(String prefix) throws IOException {
        Path root = Path.of("build", "tmp", "instance-settings-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix + "-");
    }
}
