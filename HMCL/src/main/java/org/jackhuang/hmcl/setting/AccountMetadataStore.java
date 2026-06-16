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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Stores account metadata records in a detached JSON file.
///
/// The JSON representation is saved under the `config` directory and stores account metadata in the `accounts` list.
/// Private account data is persisted separately in [AccountPrivateData].
@JsonAdapter(AccountMetadataStore.Adapter.class)
@NotNullByDefault
@JsonSerializable
final class AccountMetadataStore extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this account metadata store.
    static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("accounts", new JsonSchema.Version(1, 0, 0));

    /// Creates an empty account metadata store.
    AccountMetadataStore() {
        tracker.markDirty(schema);
        tracker.markDirty(accounts);
        register();
    }

    /// Creates an account metadata store from already serialized account records.
    ///
    /// @param records the serialized account records
    /// @return an account metadata store containing the given records
    static AccountMetadataStore fromRecords(List<Map<Object, Object>> records) {
        AccountMetadataStore result = new AccountMetadataStore();
        result.getAccounts().setAll(records);
        return result;
    }

    /// Creates a copy of this account metadata store with replacement account records.
    ///
    /// @param records the replacement account records
    /// @return a copy preserving this store's schema and unknown top-level members
    AccountMetadataStore copyWithRecords(List<Map<Object, Object>> records) {
        AccountMetadataStore result = new AccountMetadataStore();
        result.setSchema(getSchema());
        result.getAccounts().setAll(records);
        result.unknownFields.putAll(unknownFields);
        return result;
    }

    /// The schema used by this account metadata store file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this account metadata store file.
    @Override
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this account metadata store file.
    @Override
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this account metadata store may be saved back to its JSON file.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current on-disk file first.
    private transient boolean backupOnNextSave;

    /// Returns whether this account metadata store may be saved back to its JSON file.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this account metadata store may be saved back to its JSON file.
    @Override
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /// Returns whether the next successful save should back up the current on-disk file first.
    @Override
    public boolean isBackupOnNextSave() {
        return backupOnNextSave;
    }

    /// Sets whether the next successful save should back up the current on-disk file first.
    @Override
    public void setBackupOnNextSave(boolean backupOnNextSave) {
        this.backupOnNextSave = backupOnNextSave;
    }

    /// Serialized account records.
    @SerializedName("accounts")
    private final ObservableList<Map<Object, Object>> accounts = FXCollections.observableArrayList();

    /// Returns serialized account records.
    public ObservableList<Map<Object, Object>> getAccounts() {
        return accounts;
    }

    /// JSON adapter for [AccountMetadataStore].
    static final class Adapter extends ObservableSetting.Adapter<AccountMetadataStore> {
        /// Creates an empty account metadata store for deserialization.
        @Override
        protected AccountMetadataStore createInstance() {
            return new AccountMetadataStore();
        }
    }
}
