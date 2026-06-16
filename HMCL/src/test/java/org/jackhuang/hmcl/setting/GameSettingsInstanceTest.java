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
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /// Tests that directory settings are serialized with full `Directory` property names.
    @Test
    public void storesDirectoryPropertyNames() {
        GameSettings.Instance instance = new GameSettings.Instance();
        instance.runningDirectoryProperty().setValue("run");
        instance.useCustomNativesProperty().setValue(true);
        instance.nativesDirectoryProperty().setValue("natives");

        JsonObject serialized = JsonParser.parseString(
                LauncherSettings.SETTINGS_GSON.toJson(instance, GameSettings.Instance.class)
        ).getAsJsonObject();

        assertEquals("run", serialized.get("runningDirectory").getAsString());
        assertTrue(serialized.get("useCustomNatives").getAsBoolean());
        assertEquals("natives", serialized.get("nativesDirectory").getAsString());
        assertFalse(serialized.has("runningDir"));
        assertFalse(serialized.has("nativesDirectoryType"));
        assertFalse(serialized.has("nativesDirType"));
        assertFalse(serialized.has("nativesDir"));
    }

    /// Tests that legacy Java default selection is migrated to automatic selection.
    @Test
    public void migratesLegacyDefaultJavaSelectionToAuto() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "java": "Default"
                }
                """).getAsJsonObject(), false);

        assertEquals(JavaVersionType.AUTO, instance.javaTypeProperty().getValue());
    }

    /// Tests that legacy Java selection ordinals keep their old meaning after removing DEFAULT.
    @Test
    public void migratesLegacyDefaultJavaSelectionOrdinalToAuto() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "javaVersionType": 0
                }
                """).getAsJsonObject(), false);

        assertEquals(JavaVersionType.AUTO, instance.javaTypeProperty().getValue());
    }

    /// Tests that legacy Java DEFAULT enum names are migrated to automatic selection.
    @Test
    public void migratesLegacyDefaultJavaSelectionNameToAuto() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "javaVersionType": "DEFAULT"
                }
                """).getAsJsonObject(), false);

        assertEquals(JavaVersionType.AUTO, instance.javaTypeProperty().getValue());
    }

    /// Tests that legacy detected Java selection is migrated to a detected Java reference.
    @Test
    public void migratesLegacyDetectedJavaSelection() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDir = createInstanceSettingsTestDirectory(fileSystem, "legacy-detected-java");
            Path javaBinary = tempDir.resolve("java.exe");
            Files.writeString(javaBinary, "");

            JsonObject source = new JsonObject();
            source.addProperty("javaVersionType", "DETECTED");
            source.addProperty("java", "17.0.11+9");
            source.addProperty("defaultJavaPath", javaBinary.toString());

            GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, source, false, (version, javaBinaryPath) -> {
                assertEquals(javaBinary.toString(), javaBinaryPath);
                return new GameSettings.DetectedJava(version, GameSettings.DetectedJava.hashExistingPath(javaBinary));
            });

            assertEquals(JavaVersionType.DETECTED, instance.javaTypeProperty().getValue());
            assertEquals("17.0.11+9", instance.detectedJavaProperty().getValue().version());
            assertEquals(GameSettings.DetectedJava.hashExistingPath(javaBinary), instance.detectedJavaProperty().getValue().pathHash());
        }
    }

    /// Tests that legacy Java version selection is migrated to the custom Java version field.
    @Test
    public void migratesLegacyVersionJavaSelection() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "javaVersionType": "VERSION",
                  "java": "17"
                }
                """).getAsJsonObject(), false);

        assertEquals(JavaVersionType.VERSION, instance.javaTypeProperty().getValue());
        assertEquals("17", instance.customJavaVersionProperty().getValue());
    }

    /// Tests that legacy native directory fields migrate to the renamed game setting properties.
    @Test
    public void migratesLegacyNativeDirectoryFields() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "nativesDirType": "CUSTOM",
                  "nativesDir": "natives"
                }
                """).getAsJsonObject(), false);

        assertTrue(instance.useCustomNativesProperty().getValue());
        assertEquals("natives", instance.nativesDirectoryProperty().getValue());
        assertTrue(instance.getOverrideProperties().contains(GameSettings.PROPERTY_USE_CUSTOM_NATIVES));
        assertTrue(instance.getOverrideProperties().contains(GameSettings.PROPERTY_NATIVES_DIRECTORY));
    }

    /// Tests that legacy native library mode ordinals keep their old meaning.
    @Test
    public void migratesLegacyNativeDirectoryOrdinal() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "nativesDirType": 1,
                  "nativesDir": "natives"
                }
                """).getAsJsonObject(), false);

        assertTrue(instance.useCustomNativesProperty().getValue());
        assertEquals("natives", instance.nativesDirectoryProperty().getValue());
    }

    /// Tests that inheriting a legacy parent keeps copied fields unset on the instance itself.
    @Test
    public void preservesLegacyParentInheritanceWithoutCopyingValues() {
        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(null, JsonParser.parseString("""
                {
                  "javaVersionType": "VERSION",
                  "java": "17",
                  "maxMemory": 4096
                }
                """).getAsJsonObject(), true);

        assertEquals(JavaVersionType.AUTO, instance.javaTypeProperty().getValue());
        assertEquals("", instance.customJavaVersionProperty().getValue());
        assertEquals(GameSettings.SUGGESTED_MEMORY, instance.maxMemoryProperty().getValue());
        assertNull(instance.minMemoryProperty().getValue());
    }

    /// Tests that legacy global version-folder isolation is preserved for inherited instance settings.
    @Test
    public void preservesInheritedLegacyVersionFolderIsolation() {
        GameSettingsPresetID parentId =
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000");
        GameSettings.Preset parent = new GameSettings.Preset(parentId);
        parent.defaultIsolationTypeProperty().setValue(DefaultIsolationType.ALWAYS);

        GameSettings.Instance instance = LegacyGameSettingsMigrator.toInstance(
                parentId,
                parent,
                JsonParser.parseString("""
                        {
                          "usesGlobal": true
                        }
                        """).getAsJsonObject(),
                true,
                GameSettings.DetectedJava::ofLegacyPath);

        assertEquals(parentId, instance.parentProperty().getValue());
        assertTrue(instance.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY));
        assertEquals("", instance.runningDirectoryProperty().getValue());
    }

    /// Creates a temporary directory in an in-memory file system for instance settings tests.
    private static Path createInstanceSettingsTestDirectory(FileSystem fileSystem, String prefix) throws IOException {
        Path root = fileSystem.getPath("/instance-settings-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix + "-");
    }
}
