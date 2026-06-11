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
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for setting object identifiers.
@NotNullByDefault
public final class SettingIDTest {
    /// Tests parsing and canonical string serialization.
    @Test
    public void parsesCanonicalUuidString() {
        SettingID id = SettingID.parse("123e4567-e89b-12d3-a456-426614174000");

        assertEquals("123e4567-e89b-12d3-a456-426614174000", id.toString());
    }

    /// Tests JSON serialization through the standard HMCL Gson instance.
    @Test
    public void serializesAsUuidString() {
        SettingID id = SettingID.parse("123e4567-e89b-12d3-a456-426614174000");

        String serialized = LauncherSettings.SETTINGS_GSON.toJson(id, SettingID.class);
        SettingID deserialized = Objects.requireNonNull(
                LauncherSettings.SETTINGS_GSON.fromJson(serialized, SettingID.class));

        assertEquals("\"123e4567-e89b-12d3-a456-426614174000\"", serialized);
        assertEquals(id, deserialized);
    }

    /// Tests null JSON handling in the setting ID adapter.
    @Test
    public void readsJsonNullAsNull() {
        assertNull(LauncherSettings.SETTINGS_GSON.fromJson("null", SettingID.class));
    }

    /// Tests generated IDs are version 7 IDs and not nil.
    @Test
    public void generatesVersion7Ids() {
        SettingID id = SettingID.generate();

        assertNotEquals(SettingID.NIL, id);
        assertEquals(7, id.uuid().version());
    }

    /// Tests setting IDs work as JSON object map keys in launcher settings.
    @Test
    public void serializesLauncherSettingsMapKeysAsUuidStrings() {
        SettingID id = SettingID.parse("123e4567-e89b-12d3-a456-426614174000");
        LauncherSettings settings = new LauncherSettings();

        settings.setSelectedInstance(id, "1.20.1");
        JsonObject serialized = JsonParser.parseString(settings.toJson()).getAsJsonObject();
        LauncherSettings deserialized = Objects.requireNonNull(LauncherSettings.fromJson(serialized));

        assertEquals("1.20.1", serialized
                .getAsJsonObject(LauncherSettings.PROPERTY_SELECTED_INSTANCE)
                .get(id.toString())
                .getAsString());
        assertEquals("1.20.1", deserialized.getSelectedInstance(id));
    }
}
