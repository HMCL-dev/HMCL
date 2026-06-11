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
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Stores per-workspace authlib-injector authentication servers.
///
/// The JSON representation is saved as `authlib-injector-servers.json` under the current HMCL directory.
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

    /// Whether this server list may be saved back to `authlib-injector-servers.json`.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current `authlib-injector-servers.json` first.
    private transient boolean backupOnNextSave;

    /// Returns whether this server list may be saved back to `authlib-injector-servers.json`.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this server list may be saved back to `authlib-injector-servers.json`.
    @Override
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /// Returns whether the next successful save should back up the current `authlib-injector-servers.json` first.
    @Override
    public boolean isBackupOnNextSave() {
        return backupOnNextSave;
    }

    /// Sets whether the next successful save should back up the current `authlib-injector-servers.json` first.
    @Override
    public void setBackupOnNextSave(boolean backupOnNextSave) {
        this.backupOnNextSave = backupOnNextSave;
    }

    /// Authlib-injector authentication servers available for account login.
    @SerializedName("servers")
    private final ObservableList<AuthlibInjectorServer> servers =
            FXCollections.observableArrayList(server -> new Observable[]{server});

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
