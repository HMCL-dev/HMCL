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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.util.GUID;
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
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
public final class GameSettingsPresets extends ObservableSetting implements FormattedJsonSetting {
    /// The file format supported by this game settings preset store.
    public static final JsonFileFormat CURRENT_FORMAT =
            new JsonFileFormat("hmcl.game-settings", new JsonFileFormat.Version(1, 0));

    /// Creates an empty game settings preset store.
    public GameSettingsPresets() {
        tracker.markDirty(format);
        register();
    }

    /// Copies another preset store into this instance.
    void copyFrom(GameSettingsPresets source) {
        if (source == this) {
            return;
        }

        gameSettings.setAll(source.getGameSettings());
    }

    /// The format used by this game settings preset store file.
    @SerializedName(JsonFileFormat.DEFAULT_MEMBER_NAME)
    private final ObjectProperty<JsonFileFormat> format = new SimpleObjectProperty<>(CURRENT_FORMAT);

    /// Returns the format property.
    public ObjectProperty<JsonFileFormat> formatProperty() {
        return format;
    }

    /// Returns the format used by this game settings preset store file.
    public JsonFileFormat getFormat() {
        return format.get();
    }

    /// Sets the format used by this game settings preset store file.
    public void setFormat(JsonFileFormat format) {
        this.format.set(Objects.requireNonNull(format));
    }

    /// Reusable game setting presets.
    @SerializedName("gameSettings")
    private final ObservableList<GameSettings.Preset> gameSettings =
            FXCollections.observableArrayList(setting -> new Observable[] { setting });

    /// Returns the reusable game setting presets.
    public ObservableList<GameSettings.Preset> getGameSettings() {
        return gameSettings;
    }

    /// Returns the preset with the given ID.
    public GameSettings.@Nullable Preset getGameSettings(@Nullable GUID id) {
        if (id == null) {
            return null;
        }

        for (GameSettings.Preset setting : gameSettings) {
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

        /// Deserializes presets and drops the workspace-level default preset selection.
        @Override
        public @Nullable GameSettingsPresets deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            @Nullable GameSettingsPresets result = super.deserialize(json, typeOfT, context);
            if (result != null) {
                result.unknownFields.remove(Config.DEFAULT_GAME_SETTINGS_MEMBER_NAME);
            }
            return result;
        }
    }
}
