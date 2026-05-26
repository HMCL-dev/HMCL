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
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jackhuang.hmcl.util.gson.FileFormat;
import org.jackhuang.hmcl.util.gson.FormatVersion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Stores reusable game settings presets independently from the main config file.
///
/// The JSON representation is saved as `game-settings-presets.json` under the current HMCL
/// directory.
///
/// @author Glavo
@JsonAdapter(GameSettingsPresets.Adapter.class)
@NotNullByDefault
@JsonSerializable
public final class GameSettingsPresets extends ObservableSetting {
    /// The file format supported by this game settings preset store.
    public static final FileFormat CURRENT_FORMAT = new FileFormat("hmcl.game-settings-presets", new FormatVersion(1, 0));

    /// Creates an empty game settings preset store.
    public GameSettingsPresets() {
        tracker.markDirty(format);
        register();
    }

    /// Reads a game settings preset store from a JSON object.
    public static @Nullable GameSettingsPresets fromJson(JsonObject json) throws JsonParseException {
        return Config.CONFIG_GSON.<@Nullable GameSettingsPresets>fromJson(json, GameSettingsPresets.class);
    }

    /// Serializes this game settings preset store to JSON.
    public String toJson() {
        return Config.CONFIG_GSON.toJson(this);
    }

    /// Copies another preset store into this instance.
    void copyFrom(GameSettingsPresets source) {
        if (source == this) {
            return;
        }

        gameSettings.setAll(source.getGameSettings());
        setDefaultGameSettings(source.getDefaultGameSettings());
    }

    /// The format used by this game settings preset store file.
    @SerializedName("format")
    private final ObjectProperty<FileFormat> format = new SimpleObjectProperty<>(CURRENT_FORMAT);

    /// Returns the format property.
    public ObjectProperty<FileFormat> formatProperty() {
        return format;
    }

    /// Returns the format used by this game settings preset store file.
    public FileFormat getFormat() {
        return format.get();
    }

    /// Sets the format used by this game settings preset store file.
    public void setFormat(FileFormat format) {
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

    /// The default preset ID.
    @SerializedName("defaultGameSettings")
    private final ObjectProperty<@Nullable UUID> defaultGameSettings = new SimpleObjectProperty<>(this, "defaultGameSettings");

    /// Returns the default preset ID property.
    public ObjectProperty<@Nullable UUID> defaultGameSettingsProperty() {
        return defaultGameSettings;
    }

    /// Returns the default preset ID.
    public @Nullable UUID getDefaultGameSettings() {
        return defaultGameSettings.get();
    }

    /// Sets the default preset ID.
    public void setDefaultGameSettings(@Nullable UUID defaultGameSettings) {
        this.defaultGameSettings.set(defaultGameSettings);
    }

    /// Returns the preset with the given ID.
    public GameSettings.@Nullable Preset getGameSettings(@Nullable UUID id) {
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

    /// Returns the default preset, creating one when needed.
    public GameSettings.Preset getDefaultGameSettingsOrCreate() {
        GameSettings.Preset setting = getGameSettings(getDefaultGameSettings());
        if (setting != null) {
            return setting;
        }

        if (!gameSettings.isEmpty()) {
            setting = gameSettings.get(0);
            setDefaultGameSettings(setting.idProperty().getValue());
            return setting;
        }

        setting = new GameSettings.Preset();
        setting.nameProperty().setValue(i18n("message.default"));
        gameSettings.add(setting);
        setDefaultGameSettings(setting.idProperty().getValue());
        return setting;
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
