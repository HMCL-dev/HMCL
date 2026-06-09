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

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Stores reusable game settings presets independently from the main config file.
///
/// The JSON representation is saved as `game-settings.json` under the current HMCL
/// directory.
///
/// @author Glavo
@JsonAdapter(GameSettingsPresets.Adapter.class)
@NotNullByDefault
@JsonSerializable
public final class GameSettingsPresets extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this game settings preset store.
    public static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("game-settings", new JsonSchema.Version(1, 0, 0));

    /// Creates an empty game settings preset store.
    public GameSettingsPresets() {
        tracker.markDirty(schema);
        register();
    }

    /// Copies another preset store into this instance.
    void copyFrom(GameSettingsPresets source) {
        if (source == this) {
            return;
        }

        presets.setAll(source.getPresets());
    }

    /// The schema used by this game settings preset store file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this game settings preset store file.
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this game settings preset store file.
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this preset store may be saved back to `game-settings.json`.
    private transient boolean saveable = true;

    /// Returns whether this preset store may be saved back to `game-settings.json`.
    @Override
    public boolean isSaveable() {
        return saveable;
    }

    /// Sets whether this preset store may be saved back to `game-settings.json`.
    @Override
    public void setSaveable(boolean saveable) {
        this.saveable = saveable;
    }

    /// Reusable game setting presets.
    @SerializedName("presets")
    private final ObservableList<GameSettings.Preset> presets =
            FXCollections.observableArrayList(setting -> new Observable[] { setting });

    /// Returns the reusable game setting presets.
    public ObservableList<GameSettings.Preset> getPresets() {
        return presets;
    }

    /// Creates a preset ID that does not collide with existing presets.
    public SettingId newPresetId() {
        SettingId id;
        do {
            id = SettingId.generate();
        } while (getPreset(id) != null);
        return id;
    }

    /// Returns the preset with the given ID.
    public GameSettings.@Nullable Preset getPreset(@Nullable SettingId id) {
        if (id == null) {
            return null;
        }

        for (GameSettings.Preset setting : presets) {
            if (id.equals(setting.idProperty().getValue())) {
                return setting;
            }
        }
        return null;
    }

    /// JSON adapter for [GameSettingsPresets].
    public static final class Adapter extends ObservableSetting.Adapter<GameSettingsPresets> {
        /// Creates an empty preset store for deserialization.
        @Override
        protected GameSettingsPresets createInstance() {
            return new GameSettingsPresets();
        }
    }
}
