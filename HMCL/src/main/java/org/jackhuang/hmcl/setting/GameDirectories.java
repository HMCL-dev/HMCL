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
import org.jackhuang.hmcl.util.GUID;
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/// Stores per-workspace game directories independently from the main config file.
///
/// The JSON representation is saved as `game-directories.json` under the current HMCL
/// directory.
///
/// @author Glavo
@JsonAdapter(GameDirectories.Adapter.class)
@NotNullByDefault
@JsonSerializable
public final class GameDirectories extends ObservableSetting implements FormattedJsonSetting {
    /// The file format supported by this game directory store.
    public static final JsonFileFormat CURRENT_FORMAT =
            new JsonFileFormat("hmcl.game-directories", new JsonFileFormat.Version(1, 0));

    /// Creates an empty game directory store.
    public GameDirectories() {
        tracker.markDirty(format);
        register();
    }

    /// Extracts game directory data from a settings JSON object and removes the legacy members.
    ///
    /// This supports migrating the in-development `profiles` list and the old `configurations`
    /// map into `game-directories.json`.
    ///
    /// @param json the settings JSON object
    /// @return the extracted game directory store, or `null` when the object contains no game directory data
    static @Nullable GameDirectories extractFromConfigJson(JsonObject json) {
        Objects.requireNonNull(json);

        @Nullable JsonElement profilesElement = json.remove("profiles");
        @Nullable JsonElement configurationsElement = json.remove("configurations");

        @Nullable JsonArray profiles = null;
        if (profilesElement instanceof JsonArray profileArray) {
            profiles = migrateProfileArray(profileArray);
        } else if (configurationsElement instanceof JsonObject configurations) {
            profiles = migrateConfigurationMap(configurations);
        }

        if (profiles == null) {
            return null;
        }

        JsonObject object = new JsonObject();
        object.add(JsonFileFormat.DEFAULT_MEMBER_NAME, JsonUtils.GSON.toJsonTree(CURRENT_FORMAT, JsonFileFormat.class));
        object.add("gameDirectories", profiles);

        return JsonUtils.GSON.fromJson(object, GameDirectories.class);
    }

    /// The format used by this game directory store file.
    @SerializedName(JsonFileFormat.DEFAULT_MEMBER_NAME)
    private final ObjectProperty<JsonFileFormat> format = new SimpleObjectProperty<>(CURRENT_FORMAT);

    /// Returns the format property.
    public ObjectProperty<JsonFileFormat> formatProperty() {
        return format;
    }

    /// Returns the format used by this game directory store file.
    public JsonFileFormat getFormat() {
        return format.get();
    }

    /// Sets the format used by this game directory store file.
    public void setFormat(JsonFileFormat format) {
        this.format.set(Objects.requireNonNull(format));
    }

    /// Per-workspace game directories.
    @SerializedName("gameDirectories")
    private final ObservableList<Profile> gameDirectories =
            FXCollections.observableArrayList(profile -> new Observable[] { profile });

    /// Returns the per-workspace game directories.
    public ObservableList<Profile> getGameDirectories() {
        return gameDirectories;
    }

    /// Converts a current profile array into game directory JSON.
    private static JsonArray migrateProfileArray(JsonArray profiles) {
        JsonArray result = new JsonArray();
        for (JsonElement element : profiles) {
            if (!(element instanceof JsonObject profile)) {
                continue;
            }

            JsonObject migrated = profile.deepCopy();
            if (!migrated.has("id")) {
                @Nullable GUID id = readLegacyProfileId(migrated);
                if (id == null) {
                    @Nullable String name = readString(migrated.get("name"));
                    if (name != null) {
                        id = LegacyGameSettingsMigrator.getLegacyProfileId(name);
                    }
                }
                if (id != null) {
                    migrated.addProperty("id", id.toString());
                }
            }
            migrated.remove("legacyGameSettingsParent");
            result.add(migrated);
        }
        return result;
    }

    /// Converts a legacy profile map into game directory JSON.
    private static JsonArray migrateConfigurationMap(JsonObject configurations) {
        JsonArray result = new JsonArray();
        for (Map.Entry<String, JsonElement> entry : configurations.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject profile)) {
                continue;
            }

            JsonObject migrated = profile.deepCopy();
            @Nullable GUID id = readLegacyProfileId(migrated);
            migrated.addProperty("name", entry.getKey());
            migrated.addProperty("id", Objects.requireNonNullElseGet(
                    id,
                    () -> LegacyGameSettingsMigrator.getLegacyProfileId(entry.getKey())
            ).toString());
            migrated.remove("legacyGameSettingsParent");
            result.add(migrated);
        }
        return result;
    }

    /// Reads the legacy profile-parent preset ID from a profile JSON object.
    private static @Nullable GUID readLegacyProfileId(JsonObject profile) {
        if (!(profile.get("legacyGameSettingsParent") instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return null;
        }

        try {
            return GUID.fromUUID(UUIDTypeAdapter.fromString(primitive.getAsString()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// Reads a string JSON value.
    private static @Nullable String readString(@Nullable JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isString()
                ? primitive.getAsString()
                : null;
    }

    /// JSON adapter for [GameDirectories].
    public static final class Adapter extends ObservableSetting.Adapter<GameDirectories> {
        /// Creates an empty game directory store for deserialization.
        @Override
        protected GameDirectories createInstance() {
            return new GameDirectories();
        }

        /// Deserializes game directories and drops the workspace-level selected directory.
        @Override
        public @Nullable GameDirectories deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            @Nullable GameDirectories result = super.deserialize(json, typeOfT, context);
            if (result != null) {
                result.unknownFields.remove(Config.SELECTED_GAME_DIRECTORY_MEMBER_NAME);
            }
            return result;
        }
    }
}
