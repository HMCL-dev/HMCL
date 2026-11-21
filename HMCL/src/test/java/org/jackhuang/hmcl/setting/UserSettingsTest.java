// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for user settings serialization and legacy migration.
@NotNullByDefault
public final class UserSettingsTest {

    @Test
    public void redirectLegacyLogRetention() {
        var settings = UserSettings.fromJson(JsonUtils.GSON.toJson(Map.of(
                "$schema", UserSettings.CURRENT_SCHEMA.toString(),
                "logRetention", UserSettings.LOG_RETENTION_LEGACY_DEFAULT
        )));
        assertNotNull(settings);
        var property = settings.logRetentionProperty();

        assertEquals(UserSettings.LOG_RETENTION_DEFAULT, property.get());

        property.set(UserSettings.LOG_RETENTION_LEGACY_DEFAULT);
        assertEquals(UserSettings.LOG_RETENTION_DEFAULT, property.get());
        assertEquals(UserSettings.LOG_RETENTION_DEFAULT, property.getValue());

        // default value written because it's "dirty"
        var json = JsonUtils.GSON.fromJson(settings.toJson(), JsonObject.class);
        assertEquals(UserSettings.LOG_RETENTION_DEFAULT, json.get("logRetention").getAsInt());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 123, -1, -100})
    public void notRedirectValidLogRetention(int value) {
        var settings = new UserSettings();

        var property = settings.logRetentionProperty();
        property.set(value);
        assertEquals(value, property.get());

        var json = settings.toJson();
        var parsed = JsonParser.parseString(json).getAsJsonObject();
        if (value == UserSettings.LOG_RETENTION_DEFAULT) {
            assertNull(parsed.get("logRetention")); // default value not written
        } else {
            assertEquals(value, parsed.get("logRetention").getAsInt());
        }

        var reloaded = UserSettings.fromJson(settings.toJson());
        assertNotNull(reloaded);
        assertEquals(value, reloaded.logRetentionProperty().get());
    }

    /// Tests that legacy global config content is split without preserving unowned fields.
    @Test
    public void migratesLegacyGlobalConfigWithoutPreservingUnownedFields() throws IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path legacyConfig = fileSystem.getPath("/config.json");
            Files.writeString(legacyConfig, """
                    {
                      "agreementVersion": 1,
                      "terracottaAgreementVersion": 2,
                      "platformPromptVersion": 3,
                      "logRetention": 7,
                      "enableOfflineAccount": true,
                      "fontAntiAliasing": "gray",
                      "userJava": ["java-a"],
                      "disabledJava": ["java-b"],
                      "selectedAccount": "Alex:Alex",
                      "accounts": [],
                      "configurations": {},
                      "futureLauncherField": true
                    }
                    """);

            LegacyConfigMigrator.UserSettingsMigrationResult migration =
                    Objects.requireNonNull(LegacyConfigMigrator.migrateLegacyUserSettings(legacyConfig));
            UserSettings settings = migration.userSettings();
            UserState state = migration.userState();

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

            JsonObject serializedSettings = JsonParser.parseString(settings.toJson()).getAsJsonObject();
            assertEquals(UserSettings.CURRENT_SCHEMA.url(),
                    serializedSettings.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
            assertFalse(serializedSettings.has("agreementVersion"));
            assertFalse(serializedSettings.has("terracottaAgreementVersion"));
            assertFalse(serializedSettings.has("platformPromptVersion"));
            assertFalse(serializedSettings.has("selectedAccount"));
            assertFalse(serializedSettings.has("accounts"));
            assertFalse(serializedSettings.has("configurations"));
            assertFalse(serializedSettings.has("futureLauncherField"));

            JsonObject serializedState = JsonParser.parseString(state.toJson()).getAsJsonObject();
            assertEquals(UserState.CURRENT_SCHEMA.url(), serializedState.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
            assertFalse(serializedState.has("logRetention"));
            assertFalse(serializedState.has("enableOfflineAccount"));
            assertFalse(serializedState.has("fontAntiAliasing"));
            assertFalse(serializedState.has("userJava"));
            assertFalse(serializedState.has("disabledJava"));
            assertFalse(serializedState.has("selectedAccount"));
            assertFalse(serializedState.has("accounts"));
            assertFalse(serializedState.has("configurations"));
            assertFalse(serializedState.has("futureLauncherField"));
        }
    }
}
