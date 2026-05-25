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

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jackhuang.hmcl.util.gson.SchemaVersion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Stores reusable game setting presets independently from the main config file.
///
/// The JSON representation is saved as `game-setting-presets.json` under the current HMCL
/// directory. The main `settings.json` file may still contain the same fields when migrating from
/// older development builds, so [#fromEmbeddedConfig(JsonObject, JsonDeserializationContext)] reads
/// only the legacy embedded fields and ignores unrelated config data.
///
/// @author Glavo
@JsonAdapter(GameSettingPresets.Adapter.class)
@NotNullByDefault
public final class GameSettingPresets extends ObservableSetting {
    /// The schema version supported by this preset store.
    public static final SchemaVersion CURRENT_SCHEMA_VERSION = new SchemaVersion(1, 0);

    /// Creates an empty preset store.
    public GameSettingPresets() {
        tracker.markDirty(schemaVersion);
        register();
    }

    /// Reads a preset store from a JSON object.
    public static @Nullable GameSettingPresets fromJson(JsonObject json) throws JsonParseException {
        return Config.CONFIG_GSON.fromJson(json, GameSettingPresets.class);
    }

    /// Reads a preset store embedded in an older main config JSON object.
    static @Nullable GameSettingPresets fromEmbeddedConfig(
            JsonObject json,
            JsonDeserializationContext context) throws JsonParseException {
        if (!json.has("gameSettings") && !json.has("defaultGameSetting")) {
            return null;
        }

        JsonObject embedded = new JsonObject();
        if (json.has("gameSettings")) {
            embedded.add("gameSettings", json.get("gameSettings"));
        }
        if (json.has("defaultGameSetting")) {
            embedded.add("defaultGameSetting", json.get("defaultGameSetting"));
        }
        return context.deserialize(embedded, GameSettingPresets.class);
    }

    /// Serializes this preset store to JSON.
    public String toJson() {
        return Config.CONFIG_GSON.toJson(this);
    }

    /// Copies another preset store into this instance.
    void copyFrom(GameSettingPresets source) {
        if (source == this) {
            return;
        }

        gameSettings.setAll(source.getGameSettings());
        setDefaultGameSetting(source.getDefaultGameSetting());
    }

    /// The schema version used by this preset store file.
    @SerializedName("schemaVersion")
    private final ObjectProperty<SchemaVersion> schemaVersion = new SimpleObjectProperty<>(CURRENT_SCHEMA_VERSION);

    /// Returns the schema version property.
    public ObjectProperty<SchemaVersion> schemaVersionProperty() {
        return schemaVersion;
    }

    /// Returns the schema version used by this preset store file.
    public SchemaVersion getSchemaVersion() {
        return schemaVersion.get();
    }

    /// Sets the schema version used by this preset store file.
    public void setSchemaVersion(SchemaVersion schemaVersion) {
        this.schemaVersion.set(Objects.requireNonNull(schemaVersion));
    }

    /// Reusable game setting presets.
    @SerializedName("gameSettings")
    private final ObservableList<GameSetting.Preset> gameSettings =
            FXCollections.observableArrayList(setting -> new Observable[] { setting });

    /// Returns the reusable game setting presets.
    public ObservableList<GameSetting.Preset> getGameSettings() {
        return gameSettings;
    }

    /// The default preset ID.
    @SerializedName("defaultGameSetting")
    private final ObjectProperty<@Nullable UUID> defaultGameSetting = new SimpleObjectProperty<>(this, "defaultGameSetting");

    /// Returns the default preset ID property.
    public ObjectProperty<@Nullable UUID> defaultGameSettingProperty() {
        return defaultGameSetting;
    }

    /// Returns the default preset ID.
    public @Nullable UUID getDefaultGameSetting() {
        return defaultGameSetting.get();
    }

    /// Sets the default preset ID.
    public void setDefaultGameSetting(@Nullable UUID defaultGameSetting) {
        this.defaultGameSetting.set(defaultGameSetting);
    }

    /// Returns the preset with the given ID.
    public GameSetting.@Nullable Preset getGameSetting(@Nullable UUID id) {
        if (id == null) {
            return null;
        }

        for (GameSetting.Preset setting : gameSettings) {
            if (id.equals(setting.idProperty().getValue())) {
                return setting;
            }
        }
        return null;
    }

    /// Returns the default preset, creating one when needed.
    public GameSetting.Preset getDefaultGameSettingOrCreate() {
        GameSetting.Preset setting = getGameSetting(getDefaultGameSetting());
        if (setting != null) {
            return setting;
        }

        if (!gameSettings.isEmpty()) {
            setting = gameSettings.get(0);
            setDefaultGameSetting(setting.idProperty().getValue());
            return setting;
        }

        setting = new GameSetting.Preset();
        setting.nameProperty().setValue(i18n("message.default"));
        gameSettings.add(setting);
        setDefaultGameSetting(setting.idProperty().getValue());
        return setting;
    }

    /// JSON adapter for [GameSettingPresets].
    public static final class Adapter extends ObservableSetting.Adapter<GameSettingPresets> {
        /// Creates an empty preset store for deserialization.
        @Override
        protected GameSettingPresets createInstance() {
            return new GameSettingPresets();
        }
    }
}
