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

import java.util.Objects;

/// Stores game directories independently from the main config file.
///
/// The JSON representation is saved as `game-directories.json` under either the current
/// HMCL directory or the user HMCL directory.
///
/// @author Glavo
@JsonAdapter(GameDirectories.Adapter.class)
@NotNullByDefault
@JsonSerializable
public final class GameDirectories extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this game directory store.
    public static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("game-directories", new JsonSchema.Version(1, 0, 0));

    /// Creates an empty game directory store.
    public GameDirectories() {
        tracker.markDirty(schema);
        register();
    }

    /// The schema used by this game directory store file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this game directory store file.
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this game directory store file.
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this game directory store may be saved back to its JSON file.
    private transient boolean savable = true;

    /// Returns whether this game directory store may be saved back to its JSON file.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this game directory store may be saved back to its JSON file.
    @Override
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /// Game directories stored in this file.
    @SerializedName("directories")
    private final ObservableList<Profile> gameDirectories =
            FXCollections.observableArrayList(profile -> new Observable[] { profile });

    /// Whether this store represents `HMCL_USER_HOME/user-game-directories.json`.
    private transient boolean userFile;

    /// Returns the game directories stored in this file.
    public ObservableList<Profile> getGameDirectories() {
        return gameDirectories;
    }

    /// Sets whether this store represents `HMCL_USER_HOME/user-game-directories.json`.
    void setUserFile(boolean userFile) {
        this.userFile = userFile;
        for (Profile profile : gameDirectories) {
            profile.setUserGameDirectory(userFile);
        }
    }

    /// Returns whether this store represents `HMCL_USER_HOME/user-game-directories.json`.
    boolean isUserFile() {
        return userFile;
    }

    /// JSON adapter for [GameDirectories].
    public static final class Adapter extends ObservableSetting.Adapter<GameDirectories> {
        /// Creates an empty game directory store for deserialization.
        @Override
        protected GameDirectories createInstance() {
            return new GameDirectories();
        }
    }
}
