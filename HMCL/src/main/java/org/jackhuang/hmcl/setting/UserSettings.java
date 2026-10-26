// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// Stores launcher settings shared by all workspaces for the current user.
@NotNullByDefault
@JsonAdapter(UserSettings.Adapter.class)
@JsonSerializable
public final class UserSettings extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this user settings store.
    public static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("user-settings", new JsonSchema.Version(1, 0, 0));

    /// Deserializes user settings from JSON.
    ///
    /// @param json the JSON content to parse
    /// @return the parsed settings, or {@code null} when the JSON value is {@code null}
    public static @Nullable UserSettings fromJson(String json) throws JsonParseException {
        return JsonUtils.fromJson(JsonUtils.GSON, json, UserSettings.class);
    }

    /// Creates empty user settings with default values.
    public UserSettings() {
        tracker.markDirty(schema);
        register();
    }

    /// Serializes these settings to JSON.
    public String toJson() {
        return JsonUtils.GSON.toJson(this);
    }

    /// The schema used by this user settings file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this user settings file.
    @Override
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this user settings file.
    @Override
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this user settings store may be saved back to `config/user-settings.json`.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current `config/user-settings.json` first.
    private transient boolean backupOnNextSave;

    /// Returns whether this user settings store may be saved back to `config/user-settings.json`.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this user settings store may be saved back to `config/user-settings.json`.
    @Override
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /// Returns whether the next successful save should back up the current `config/user-settings.json` first.
    @Override
    public boolean isBackupOnNextSave() {
        return backupOnNextSave;
    }

    /// Sets whether the next successful save should back up the current `config/user-settings.json` first.
    @Override
    public void setBackupOnNextSave(boolean backupOnNextSave) {
        this.backupOnNextSave = backupOnNextSave;
    }

    /// The number of launcher log files to retain.
    @SerializedName("logRetention")
    private final IntegerProperty logRetention = new SimpleIntegerProperty(20);

    /// Returns the log retention property.
    public IntegerProperty logRetentionProperty() {
        return logRetention;
    }

    /// Whether offline accounts are enabled for this user.
    @SerializedName("enableOfflineAccount")
    private final BooleanProperty enableOfflineAccount = new SimpleBooleanProperty(false);

    /// Returns the offline account enablement property.
    public BooleanProperty enableOfflineAccountProperty() {
        return enableOfflineAccount;
    }

    /// The JavaFX font antialiasing mode override.
    @SerializedName("fontAntiAliasing")
    private final StringProperty fontAntiAliasing = new SimpleStringProperty();

    /// Returns the JavaFX font antialiasing mode override property.
    public StringProperty fontAntiAliasingProperty() {
        return fontAntiAliasing;
    }

    /// User-added Java executable paths.
    @SerializedName("userJava")
    private final ObservableSet<String> userJava = FXCollections.observableSet(new LinkedHashSet<>());

    /// Returns user-added Java executable paths.
    public ObservableSet<String> getUserJava() {
        return userJava;
    }

    /// Disabled Java executable paths.
    @SerializedName("disabledJava")
    private final ObservableSet<String> disabledJava = FXCollections.observableSet(new LinkedHashSet<>());

    /// Returns disabled Java executable paths.
    public ObservableSet<String> getDisabledJava() {
        return disabledJava;
    }

    /// Gson adapter for observable user settings.
    static final class Adapter extends ObservableSetting.Adapter<UserSettings> {
        /// Creates an empty user settings instance during deserialization.
        @Override
        protected UserSettings createInstance() {
            return new UserSettings();
        }
    }
}
