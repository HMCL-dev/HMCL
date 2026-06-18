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

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Stores private account data in a protected payload.
@JsonAdapter(AccountPrivateData.Adapter.class)
@NotNullByDefault
@JsonSerializable
final class AccountPrivateData extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this account private data store.
    static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("account-private-data", new JsonSchema.Version(1, 0, 0));

    /// JVM system property selecting the account private data protection mode.
    static final String PROTECTION_PROPERTY = "hmcl.account.privateData.protection";

    /// Creates an empty account private data store.
    AccountPrivateData() {
        tracker.markDirty(schema);
        tracker.markDirty(privateData);
        register();
    }

    /// The schema used by this account private data store file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Private account data keyed by account ID.
    private final ObservableMap<AccountID, JsonObject> privateData =
            FXCollections.observableMap(new LinkedHashMap<>());

    /// Whether this account private data store may be saved back to its JSON file.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current on-disk file first.
    private transient boolean backupOnNextSave;

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this account private data store file.
    @Override
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this account private data store file.
    @Override
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Returns private account data keyed by account ID.
    ObservableMap<AccountID, JsonObject> getPrivateData() {
        return privateData;
    }

    /// Returns the protection mode selected for writing account private data.
    private static ProtectedPayload.ProtectionMode protectionMode() {
        return ProtectedPayload.ProtectionMode.fromConfiguredId(System.getProperty(PROTECTION_PROPERTY));
    }

    /// Returns whether this account private data store may be saved back to its JSON file.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this account private data store may be saved back to its JSON file.
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

    /// Finds the first private data entry matching an account ID.
    ///
    /// @param accountID the stable account ID
    /// @param privateDataStores private data stores searched in order
    /// @return the first matching private data object, or `null` if no store contains one
    static @Nullable JsonObject findPrivateData(
            AccountID accountID,
            List<AccountPrivateData> privateDataStores) {
        for (AccountPrivateData privateDataStore : privateDataStores) {
            @Nullable JsonObject accountPrivateData = privateDataStore.privateData.get(accountID);
            if (accountPrivateData != null) {
                return accountPrivateData;
            }
        }
        return null;
    }

    /// Returns whether this store contains private data for the account ID.
    ///
    /// @param accountID the stable account ID
    /// @return whether this store contains private data for the account ID
    boolean containsPrivateData(AccountID accountID) {
        return privateData.containsKey(accountID);
    }

    /// Removes private data for the account ID.
    ///
    /// @param accountID the stable account ID
    /// @return whether private data was removed
    boolean removePrivateData(AccountID accountID) {
        return privateData.remove(accountID) != null;
    }

    /// Stores private data for the account ID, or removes existing private data when the object is empty.
    ///
    /// @param accountID the stable account ID
    /// @param accountPrivateData the private account data
    void putPrivateData(AccountID accountID, JsonObject accountPrivateData) {
        if (accountPrivateData.isEmpty()) {
            privateData.remove(accountID);
        } else {
            privateData.put(accountID, accountPrivateData);
        }
    }

    /// Replaces this private data store with another store.
    ///
    /// @param other the private data store to copy from
    void replaceWith(AccountPrivateData other) {
        privateData.clear();
        for (Map.Entry<AccountID, JsonObject> entry : other.privateData.entrySet()) {
            putPrivateData(entry.getKey(), entry.getValue());
        }
    }

    /// JSON adapter for [AccountPrivateData].
    static final class Adapter implements JsonSerializer<AccountPrivateData>,
            com.google.gson.JsonDeserializer<AccountPrivateData> {
        /// Creates the JSON payload protected inside an account private data file.
        ///
        /// @param accountPrivateData the private data store to serialize
        /// @param context the JSON serialization context
        /// @return the plain payload before protection
        private static JsonArray createPayload(
                AccountPrivateData accountPrivateData,
                JsonSerializationContext context) {
            JsonArray payload = new JsonArray();
            for (Map.Entry<AccountID, JsonObject> entry : accountPrivateData.privateData.entrySet()) {
                JsonObject item = new JsonObject();
                item.add("accountID", context.serialize(entry.getKey(), AccountID.class));
                item.add("privateData", entry.getValue());
                payload.add(item);
            }
            return payload;
        }

        /// Reads an account ID from a private data entry.
        private static @Nullable AccountID readAccountID(
                @Nullable JsonElement accountIDElement,
                JsonDeserializationContext context) {
            if (accountIDElement == null || accountIDElement.isJsonNull()) {
                return null;
            }

            try {
                return context.deserialize(accountIDElement, AccountID.class);
            } catch (JsonParseException | IllegalArgumentException e) {
                return null;
            }
        }

        /// Reads the protected payload into the given private data store.
        ///
        /// @param accountPrivateData the private data store to update
        /// @param payload the revealed payload
        /// @param context the JSON deserialization context
        private static void readPayload(
                AccountPrivateData accountPrivateData,
                JsonArray payload,
                JsonDeserializationContext context) {
            for (JsonElement itemElement : payload) {
                if (!(itemElement instanceof JsonObject item)) {
                    continue;
                }

                @Nullable AccountID accountID = readAccountID(item.get("accountID"), context);
                if (accountID == null) {
                    continue;
                }

                JsonElement privateDataElement = item.get("privateData");
                if (privateDataElement == null || !privateDataElement.isJsonObject()) {
                    continue;
                }

                accountPrivateData.putPrivateData(accountID, privateDataElement.getAsJsonObject());
            }
        }

        /// Serializes the private data store as a protected payload envelope.
        @Override
        public JsonElement serialize(
                AccountPrivateData accountPrivateData,
                Type typeOfSrc,
                JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            result.addProperty(JsonSchema.PROPERTY_SCHEMA, accountPrivateData.getSchema().url());
            protectionMode().write(result, createPayload(accountPrivateData, context));
            accountPrivateData.unknownFields.forEach(result::add);
            return result;
        }

        /// Deserializes the private data store from a protected payload envelope.
        @Override
        public @Nullable AccountPrivateData deserialize(
                @Nullable JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (!(json instanceof JsonObject object)) {
                throw new JsonParseException("Account private data is not an object");
            }

            AccountPrivateData accountPrivateData = new AccountPrivateData();
            Map<String, JsonElement> values = new LinkedHashMap<>(object.asMap());
            JsonElement schema = values.remove(JsonSchema.PROPERTY_SCHEMA);
            if (schema != null && schema.isJsonPrimitive() && schema.getAsJsonPrimitive().isString()) {
                accountPrivateData.setSchema(new JsonSchema(schema.getAsString()));
            }
            values.remove(ProtectedPayload.PROPERTY_PROTECTION);
            values.remove(ProtectedPayload.PROPERTY_NONCE);
            values.remove(ProtectedPayload.PROPERTY_PAYLOAD);
            accountPrivateData.unknownFields.putAll(values);

            readPayload(accountPrivateData, ProtectedPayload.read(object, JsonArray.class), context);
            return accountPrivateData;
        }
    }
}
