// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Stores per-workspace authlib-injector authentication servers.
///
/// The JSON representation is saved as `config/authlib-injector-servers.json`
/// under the current HMCL directory.
///
/// @author Glavo
@JsonAdapter(AuthlibInjectorServerList.Adapter.class)
@NotNullByDefault
@JsonSerializable
public final class AuthlibInjectorServerList extends ObservableSetting implements JsonSchemaSetting {
    /// The LittleSkin Yggdrasil API endpoint bundled into newly created server lists.
    public static final String LITTLE_SKIN_URL = "https://littleskin.cn/api/yggdrasil/";

    /// The JSON schema supported by this authlib-injector server list.
    public static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("authlib-injector-servers", new JsonSchema.Version(1, 0, 0));

    /// Creates an empty authlib-injector server list.
    public AuthlibInjectorServerList() {
        tracker.markDirty(schema);
        register();
    }

    /// Creates the default authlib-injector server list for a newly created file.
    public static AuthlibInjectorServerList createDefault() {
        AuthlibInjectorServerList result = new AuthlibInjectorServerList();
        result.addLittleSkinIfAbsent();
        return result;
    }

    /// Adds the bundled LittleSkin server when it is not already present.
    public void addLittleSkinIfAbsent() {
        if (getServers().stream().noneMatch(server -> LITTLE_SKIN_URL.equals(server.getUrl()))) {
            getServers().add(new AuthlibInjectorServer(LITTLE_SKIN_URL));
        }
    }

    /// The schema used by this authlib-injector server list file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this authlib-injector server list file.
    @Override
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this authlib-injector server list file.
    @Override
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this server list may be saved back to `config/authlib-injector-servers.json`.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current `config/authlib-injector-servers.json` first.
    private transient boolean backupOnNextSave;

    /// Returns whether this server list may be saved back to `config/authlib-injector-servers.json`.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this server list may be saved back to `config/authlib-injector-servers.json`.
    @Override
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /// Returns whether the next successful save should back up the current `config/authlib-injector-servers.json` first.
    @Override
    public boolean isBackupOnNextSave() {
        return backupOnNextSave;
    }

    /// Sets whether the next successful save should back up the current `config/authlib-injector-servers.json` first.
    @Override
    public void setBackupOnNextSave(boolean backupOnNextSave) {
        this.backupOnNextSave = backupOnNextSave;
    }

    /// Authlib-injector authentication servers available for account login.
    @SerializedName("servers")
    private final ObservableList<AuthlibInjectorServer> servers =
            FXCollections.observableArrayList();

    /// Returns authlib-injector authentication servers available for account login.
    public ObservableList<AuthlibInjectorServer> getServers() {
        return servers;
    }

    /// JSON adapter for [AuthlibInjectorServerList].
    public static final class Adapter extends ObservableSetting.Adapter<AuthlibInjectorServerList> {
        /// Creates an empty authlib-injector server list for deserialization.
        @Override
        protected AuthlibInjectorServerList createInstance() {
            return new AuthlibInjectorServerList();
        }
    }
}
