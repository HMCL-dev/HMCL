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

import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Stores launcher state shared by all workspaces for the current user.
@NotNullByDefault
@JsonAdapter(UserState.Adapter.class)
@JsonSerializable
public final class UserState extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this user state store.
    public static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("user-state", new JsonSchema.Version(1, 0, 0));

    /// Deserializes user state from JSON.
    ///
    /// @param json the JSON content to parse
    /// @return the parsed state, or {@code null} when the JSON value is {@code null}
    public static @Nullable UserState fromJson(String json) throws JsonParseException {
        return JsonUtils.fromJson(JsonUtils.GSON, json, UserState.class);
    }

    /// Creates empty user state with default values.
    public UserState() {
        tracker.markDirty(schema);
        register();
    }

    /// Serializes this state to JSON.
    public String toJson() {
        return JsonUtils.GSON.toJson(this);
    }

    /// The schema used by this user state file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this user state file.
    @Override
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this user state file.
    @Override
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this user state store may be saved back to `user-state.json`.
    private transient boolean saveable = true;

    /// Returns whether this user state store may be saved back to `user-state.json`.
    @Override
    public boolean isSaveable() {
        return saveable;
    }

    /// Sets whether this user state store may be saved back to `user-state.json`.
    @Override
    public void setSaveable(boolean saveable) {
        this.saveable = saveable;
    }

    /// The accepted launcher agreement version.
    @SerializedName("agreementVersion")
    private final IntegerProperty agreementVersion = new SimpleIntegerProperty();

    /// Returns the accepted launcher agreement version property.
    public IntegerProperty agreementVersionProperty() {
        return agreementVersion;
    }

    /// The accepted Terracotta agreement version.
    @SerializedName("terracottaAgreementVersion")
    private final IntegerProperty terracottaAgreementVersion = new SimpleIntegerProperty();

    /// Returns the accepted Terracotta agreement version property.
    public IntegerProperty terracottaAgreementVersionProperty() {
        return terracottaAgreementVersion;
    }

    /// The platform prompt version shown to the user.
    @SerializedName("platformPromptVersion")
    private final IntegerProperty platformPromptVersion = new SimpleIntegerProperty();

    /// Returns the platform prompt version property.
    public IntegerProperty platformPromptVersionProperty() {
        return platformPromptVersion;
    }

    /// Gson adapter for observable user state.
    static final class Adapter extends ObservableSetting.Adapter<UserState> {
        /// Creates an empty user state instance during deserialization.
        @Override
        protected UserState createInstance() {
            return new UserState();
        }
    }
}
