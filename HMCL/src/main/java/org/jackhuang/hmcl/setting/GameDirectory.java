/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Objects;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;

/// Persistent configuration for a game directory.
@JsonAdapter(GameDirectory.Serializer.class)
@NotNullByDefault
public final class GameDirectory implements Observable {
    /// The stable game directory ID.
    private final GameDirectoryID id;

    /// Returns the stable game directory ID.
    public GameDirectoryID getId() {
        return id;
    }

    /// The game directory path.
    private final ObjectProperty<PortablePath> path;

    /// Returns the game directory path property.
    public ObjectProperty<PortablePath> pathProperty() {
        return path;
    }

    /// Returns the game directory path.
    public PortablePath getPath() {
        return path.get();
    }

    /// Sets the game directory path.
    public void setPath(PortablePath path) {
        this.path.set(Objects.requireNonNull(path));
    }

    /// The custom localized game directory name, or `null` when no name is stored.
    private final ObjectProperty<@Nullable LocalizedText> name;

    /// Returns the custom localized game directory name property.
    public ObjectProperty<@Nullable LocalizedText> nameProperty() {
        return name;
    }

    /// Returns the custom localized game directory name, or `null` when no name is stored.
    public @Nullable LocalizedText getName() {
        return name.get();
    }

    /// Sets the custom localized game directory name.
    public void setName(@Nullable LocalizedText name) {
        this.name.set(name);
    }

    /// The legacy game settings preset ID loaded from older game directory files, or `null` when absent.
    ///
    /// New migrations store this workspace-specific relationship in [GameSettingsPresets].
    private final ObjectProperty<@Nullable GameSettingsPresetID> legacyGameSettings;

    /// Returns the legacy game settings preset ID property retained for older files.
    public ObjectProperty<@Nullable GameSettingsPresetID> legacyGameSettingsProperty() {
        return legacyGameSettings;
    }

    /// Returns the legacy game settings preset ID loaded from older game directory files, or `null` when absent.
    public @Nullable GameSettingsPresetID getLegacyGameSettings() {
        return legacyGameSettings.get();
    }

    /// Sets the legacy game settings preset ID retained for older files.
    public void setLegacyGameSettings(@Nullable GameSettingsPresetID legacyGameSettings) {
        this.legacyGameSettings.set(legacyGameSettings);
    }

    /// Creates a game directory.
    public GameDirectory(GameDirectoryID id, @Nullable LocalizedText name, PortablePath path) {
        this(id, name, path, null);
    }

    /// Creates a game directory.
    public GameDirectory(
            GameDirectoryID id,
            @Nullable LocalizedText name,
            PortablePath path,
            @Nullable GameSettingsPresetID legacyGameSettings) {
        this.id = Objects.requireNonNull(id);
        this.name = new SimpleObjectProperty<>(this, "name", name);
        this.path = new SimpleObjectProperty<>(this, "path", Objects.requireNonNull(path));
        this.legacyGameSettings = new SimpleObjectProperty<>(this, "legacyGameSettings", legacyGameSettings);

        addPropertyChangedListener(onInvalidating(this::invalidate));
    }

    /// Returns a debug string containing the game directory path and display metadata.
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("path", getPath())
                .append("name", getName())
                .toString();
    }

    /// Registers a listener that invalidates this game directory when any stored property changes.
    private void addPropertyChangedListener(InvalidationListener listener) {
        name.addListener(listener);
        path.addListener(listener);
        legacyGameSettings.addListener(listener);
    }

    /// Helper that stores and dispatches invalidation listeners for this game directory.
    private final ObservableHelper observableHelper = new ObservableHelper(this);

    /// Adds an invalidation listener.
    @Override
    public void addListener(InvalidationListener listener) {
        observableHelper.addListener(listener);
    }

    /// Removes an invalidation listener.
    @Override
    public void removeListener(InvalidationListener listener) {
        observableHelper.removeListener(listener);
    }

    /// Notifies game directory observers on the JavaFX thread when the toolkit is available.
    private void invalidate() {
        try {
            Platform.runLater(observableHelper::invalidate);
        } catch (IllegalStateException e) {
            observableHelper.invalidate();
        }
    }

    /// Serializes and deserializes game directories.
    @NotNullByDefault
    public static final class Serializer implements JsonSerializer<GameDirectory>, JsonDeserializer<GameDirectory> {
        /// Serializes a game directory to JSON.
        @Override
        public JsonElement serialize(@Nullable GameDirectory src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null)
                return JsonNull.INSTANCE;

            JsonObject jsonObject = new JsonObject();
            jsonObject.add("id", context.serialize(src.getId(), GameDirectoryID.class));
            if (src.getName() != null) {
                JsonElement name = context.serialize(src.getName(), LocalizedText.class);
                if (name != null && !name.isJsonNull()) {
                    jsonObject.add("name", name);
                }
            }
            jsonObject.add("path", context.serialize(src.getPath(), PortablePath.class));
            if (src.getLegacyGameSettings() != null) {
                jsonObject.add("legacyGameSettings", context.serialize(src.getLegacyGameSettings(), GameSettingsPresetID.class));
            }

            return jsonObject;
        }

        /// Deserializes a game directory from JSON.
        @Override
        public @Nullable GameDirectory deserialize(@Nullable JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!(json instanceof JsonObject obj)) return null;
            GameDirectoryID id = context.deserialize(obj.get("id"), GameDirectoryID.class);
            if (id == null) {
                throw new JsonParseException("Game directory ID cannot be null");
            } else if (GameDirectoryID.NIL.equals(id)) {
                throw new JsonParseException("Game directory ID cannot be nil");
            }
            PortablePath path = context.deserialize(obj.get("path"), PortablePath.class);
            if (path == null) {
                throw new JsonParseException("Game directory path cannot be null");
            }
            @Nullable LocalizedText name = context.deserialize(obj.get("name"), LocalizedText.class);

            return new GameDirectory(id,
                    name,
                    path,
                    context.deserialize(obj.get("legacyGameSettings"), GameSettingsPresetID.class));
        }

    }
}
