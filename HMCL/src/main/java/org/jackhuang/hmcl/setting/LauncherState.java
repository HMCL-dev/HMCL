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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Stores per-workspace launcher runtime state independently from the main settings file.
///
/// The JSON representation is saved as `state/launcher-state.json` under the current HMCL directory.
///
/// @author Glavo
@JsonAdapter(LauncherState.Adapter.class)
@NotNullByDefault
@JsonSerializable
public final class LauncherState extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this launcher state store.
    public static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("launcher-state", new JsonSchema.Version(1, 0, 0));

    /// Creates an empty launcher state store.
    public LauncherState() {
        tracker.markDirty(schema);
        register();
    }

    /// The schema used by this launcher state file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this launcher state file.
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this launcher state file.
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this launcher state may be saved back to `state/launcher-state.json`.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current `state/launcher-state.json` first.
    private transient boolean backupOnNextSave;

    /// Returns whether this launcher state may be saved back to `state/launcher-state.json`.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this launcher state may be saved back to `state/launcher-state.json`.
    @Override
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /// Returns whether the next successful save should back up the current `state/launcher-state.json` first.
    @Override
    public boolean isBackupOnNextSave() {
        return backupOnNextSave;
    }

    /// Sets whether the next successful save should back up the current `state/launcher-state.json` first.
    @Override
    public void setBackupOnNextSave(boolean backupOnNextSave) {
        this.backupOnNextSave = backupOnNextSave;
    }

    /// The normalized launcher window content X position.
    @SerializedName("x")
    private final DoubleProperty x = new SimpleDoubleProperty();

    /// Returns the normalized launcher window content X position property.
    public DoubleProperty xProperty() {
        return x;
    }

    /// Returns the normalized launcher window content X position.
    public double getX() {
        return x.get();
    }

    /// Sets the normalized launcher window content X position.
    public void setX(double x) {
        this.x.set(x);
    }

    /// The normalized launcher window content Y position.
    @SerializedName("y")
    private final DoubleProperty y = new SimpleDoubleProperty();

    /// Returns the normalized launcher window content Y position property.
    public DoubleProperty yProperty() {
        return y;
    }

    /// Returns the normalized launcher window content Y position.
    public double getY() {
        return y.get();
    }

    /// Sets the normalized launcher window content Y position.
    public void setY(double y) {
        this.y.set(y);
    }

    /// The launcher window content width.
    @SerializedName("width")
    private final DoubleProperty width = new SimpleDoubleProperty();

    /// Returns the launcher window content width property.
    public DoubleProperty widthProperty() {
        return width;
    }

    /// Returns the launcher window content width.
    public double getWidth() {
        return width.get();
    }

    /// Sets the launcher window content width.
    public void setWidth(double width) {
        this.width.set(width);
    }

    /// The launcher window content height.
    @SerializedName("height")
    private final DoubleProperty height = new SimpleDoubleProperty();

    /// Returns the launcher window content height property.
    public DoubleProperty heightProperty() {
        return height;
    }

    /// Returns the launcher window content height.
    public double getHeight() {
        return height.get();
    }

    /// Sets the launcher window content height.
    public void setHeight(double height) {
        this.height.set(height);
    }

    /// The latest update version that has already prompted the user.
    @SerializedName("promptedVersion")
    private final StringProperty promptedVersion = new SimpleStringProperty();

    /// Returns the latest prompted update version.
    public @Nullable String getPromptedVersion() {
        return promptedVersion.get();
    }

    /// Returns the latest prompted update version property.
    public StringProperty promptedVersionProperty() {
        return promptedVersion;
    }

    /// Sets the latest prompted update version.
    public void setPromptedVersion(@Nullable String promptedVersion) {
        this.promptedVersion.set(promptedVersion);
    }

    /// Tip markers that prevent repeated prompts.
    @SerializedName("shownTips")
    private final ObservableMap<String, Object> shownTips = FXCollections.observableHashMap();

    /// Returns tip markers that prevent repeated prompts.
    public ObservableMap<String, Object> getShownTips() {
        return shownTips;
    }

    /// JSON adapter for [LauncherState].
    public static final class Adapter extends ObservableSetting.Adapter<LauncherState> {
        /// Creates an empty launcher state store for deserialization.
        @Override
        protected LauncherState createInstance() {
            return new LauncherState();
        }
    }
}
