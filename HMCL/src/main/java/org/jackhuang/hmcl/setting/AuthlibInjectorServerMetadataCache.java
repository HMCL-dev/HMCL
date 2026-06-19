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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Stores derived authlib-injector server metadata separately from the user-managed server list.
///
/// The JSON representation is saved as `cache/authlib-injector-server-metadata.json`
/// under the current HMCL directory.
@JsonAdapter(AuthlibInjectorServerMetadataCache.Adapter.class)
@NotNullByDefault
@JsonSerializable
final class AuthlibInjectorServerMetadataCache extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this metadata cache.
    static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("authlib-injector-server-metadata", new JsonSchema.Version(1, 0, 0));

    /// Creates an empty metadata cache.
    AuthlibInjectorServerMetadataCache() {
        tracker.markDirty(schema);
        register();
    }

    /// The schema used by this metadata cache file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this metadata cache file.
    @Override
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this metadata cache file.
    @Override
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this metadata cache may be saved back to `cache/authlib-injector-server-metadata.json`.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current metadata cache file first.
    private transient boolean backupOnNextSave;

    /// Returns whether this metadata cache may be saved back to `cache/authlib-injector-server-metadata.json`.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this metadata cache may be saved back to `cache/authlib-injector-server-metadata.json`.
    @Override
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /// Returns whether the next successful save should back up the current metadata cache file first.
    @Override
    public boolean isBackupOnNextSave() {
        return backupOnNextSave;
    }

    /// Sets whether the next successful save should back up the current metadata cache file first.
    @Override
    public void setBackupOnNextSave(boolean backupOnNextSave) {
        this.backupOnNextSave = backupOnNextSave;
    }

    /// Authlib-injector metadata entries.
    @SerializedName("servers")
    private final ObservableList<Entry> servers = FXCollections.observableArrayList();

    /// Returns authlib-injector metadata entries.
    ObservableList<Entry> getServers() {
        return servers;
    }

    /// Initializes metadata for a server that is being connected to this cache.
    ///
    /// @param server the server to initialize
    /// @param storeExistingMetadata whether metadata already present on the server should replace cached metadata
    void initialize(AuthlibInjectorServer server, boolean storeExistingMetadata) {
        if (storeExistingMetadata && server.getMetadataResponse().isPresent()) {
            store(server);
        } else {
            restore(server);
        }
    }

    /// Restores cached metadata into the given server when a valid cache entry exists.
    void restore(AuthlibInjectorServer server) {
        int index = indexOf(server.getUrl());
        if (index < 0) {
            return;
        }

        Entry entry = servers.get(index);
        String metadataResponse = entry.getMetadataResponse();
        if (metadataResponse.isEmpty()) {
            servers.remove(index);
            return;
        }

        try {
            server.restoreMetadataCache(metadataResponse, entry.getMetadataTimestamp());
        } catch (JsonParseException e) {
            LOG.warning("Ignoring malformed authlib-injector server metadata cache: " + server.getUrl(), e);
            servers.remove(index);
        }
    }

    /// Stores the current metadata response from the given server when it has one.
    void store(AuthlibInjectorServer server) {
        server.getMetadataResponse().ifPresent(metadataResponse -> {
            Entry entry = new Entry(server.getUrl(), metadataResponse, server.getMetadataTimestamp());
            int index = indexOf(server.getUrl());
            if (index >= 0) {
                servers.set(index, entry);
            } else {
                servers.add(entry);
            }
        });
    }

    /// Removes cached metadata for the given server.
    void remove(AuthlibInjectorServer server) {
        int index = indexOf(server.getUrl());
        if (index >= 0) {
            servers.remove(index);
        }
    }

    /// Returns the index of the cache entry for the given normalized server URL.
    private int indexOf(String url) {
        for (int i = 0; i < servers.size(); i++) {
            if (url.equals(servers.get(i).getUrl())) {
                return i;
            }
        }
        return -1;
    }

    /// Metadata cached for one authlib-injector server.
    @NotNullByDefault
    @JsonSerializable
    static final class Entry {
        /// Normalized authlib-injector server URL.
        @SerializedName("url")
        private @Nullable String url = "";

        /// Raw metadata response returned by the authlib-injector endpoint.
        @SerializedName("metadataResponse")
        private @Nullable String metadataResponse = "";

        /// Wall-clock time when the metadata response was fetched.
        @SerializedName("metadataTimestamp")
        private long metadataTimestamp;

        /// Creates an empty metadata entry for deserialization.
        private Entry() {
        }

        /// Creates a metadata entry.
        ///
        /// @param url normalized authlib-injector server URL
        /// @param metadataResponse raw metadata response returned by the authlib-injector endpoint
        /// @param metadataTimestamp wall-clock time when the metadata response was fetched
        Entry(String url, String metadataResponse, long metadataTimestamp) {
            this.url = Objects.requireNonNull(url);
            this.metadataResponse = Objects.requireNonNull(metadataResponse);
            this.metadataTimestamp = metadataTimestamp;
        }

        /// Returns the normalized authlib-injector server URL.
        String getUrl() {
            return Objects.requireNonNullElse(url, "");
        }

        /// Returns the raw metadata response, or an empty string when the cache entry is incomplete.
        String getMetadataResponse() {
            return Objects.requireNonNullElse(metadataResponse, "");
        }

        /// Returns the wall-clock time when the metadata response was fetched.
        long getMetadataTimestamp() {
            return metadataTimestamp;
        }
    }

    /// JSON adapter for [AuthlibInjectorServerMetadataCache].
    @NotNullByDefault
    static final class Adapter extends ObservableSetting.Adapter<AuthlibInjectorServerMetadataCache> {
        /// Creates an empty metadata cache for deserialization.
        @Override
        protected AuthlibInjectorServerMetadataCache createInstance() {
            return new AuthlibInjectorServerMetadataCache();
        }
    }
}
