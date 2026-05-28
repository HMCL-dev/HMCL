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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Objects;

/// Stores per-workspace launcher runtime state independently from the main settings file.
///
/// The JSON representation is saved as `state.json` under the current HMCL directory.
///
/// @author Glavo
@JsonAdapter(LauncherState.Adapter.class)
@NotNullByDefault
@JsonSerializable
public final class LauncherState extends ObservableSetting implements FormattedJsonSetting {
    /// The file format supported by this launcher state store.
    public static final JsonFileFormat CURRENT_FORMAT =
            new JsonFileFormat("hmcl.state", new JsonFileFormat.Version(1, 0));

    /// Creates an empty launcher state store.
    public LauncherState() {
        tracker.markDirty(format);
        register();
    }

    /// The format used by this launcher state file.
    @SerializedName(JsonFileFormat.DEFAULT_MEMBER_NAME)
    private final ObjectProperty<JsonFileFormat> format = new SimpleObjectProperty<>(CURRENT_FORMAT);

    /// Returns the format property.
    public ObjectProperty<JsonFileFormat> formatProperty() {
        return format;
    }

    /// Returns the format used by this launcher state file.
    public JsonFileFormat getFormat() {
        return format.get();
    }

    /// Sets the format used by this launcher state file.
    public void setFormat(JsonFileFormat format) {
        this.format.set(Objects.requireNonNull(format));
    }

    /// The normalized launcher window X position.
    @SerializedName("x")
    private final DoubleProperty x = new SimpleDoubleProperty();

    /// Returns the normalized launcher window X position property.
    public DoubleProperty xProperty() {
        return x;
    }

    /// Returns the normalized launcher window X position.
    public double getX() {
        return x.get();
    }

    /// Sets the normalized launcher window X position.
    public void setX(double x) {
        this.x.set(x);
    }

    /// The normalized launcher window Y position.
    @SerializedName("y")
    private final DoubleProperty y = new SimpleDoubleProperty();

    /// Returns the normalized launcher window Y position property.
    public DoubleProperty yProperty() {
        return y;
    }

    /// Returns the normalized launcher window Y position.
    public double getY() {
        return y.get();
    }

    /// Sets the normalized launcher window Y position.
    public void setY(double y) {
        this.y.set(y);
    }

    /// The launcher window width.
    @SerializedName("width")
    private final DoubleProperty width = new SimpleDoubleProperty();

    /// Returns the launcher window width property.
    public DoubleProperty widthProperty() {
        return width;
    }

    /// Returns the launcher window width.
    public double getWidth() {
        return width.get();
    }

    /// Sets the launcher window width.
    public void setWidth(double width) {
        this.width.set(width);
    }

    /// The launcher window height.
    @SerializedName("height")
    private final DoubleProperty height = new SimpleDoubleProperty();

    /// Returns the launcher window height property.
    public DoubleProperty heightProperty() {
        return height;
    }

    /// Returns the launcher window height.
    public double getHeight() {
        return height.get();
    }

    /// Sets the launcher window height.
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

        /// Deserializes launcher state and drops the previous main settings file format marker.
        @Override
        public @Nullable LauncherState deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            @Nullable LauncherState result = super.deserialize(json, typeOfT, context);
            if (result != null) {
                result.unknownFields.remove(JsonFileFormat.DEFAULT_MEMBER_NAME);
            }
            return result;
        }
    }
}
