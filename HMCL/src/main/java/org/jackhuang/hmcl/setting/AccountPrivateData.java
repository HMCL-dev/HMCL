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
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /// Private account storage fields moved out of the metadata store.
    private static final @Unmodifiable Set<String> PRIVATE_DATA_FIELDS = Set.of(
            "accessToken",
            "refreshToken",
            "clientToken",
            "profileProperties",
            "profileName",
            "userProperties",
            "tokenType",
            "notAfter",
            "userid");

    /// Creates an empty account private data store.
    AccountPrivateData() {
        tracker.markDirty(schema);
        tracker.markDirty(privateData);
        register();
    }

    /// The schema used by this account private data store file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Private account data keyed by account identifier.
    private final ObservableMap<AccountID, Map<Object, Object>> privateData =
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

    /// Returns private account data keyed by account identifier.
    ObservableMap<AccountID, Map<Object, Object>> getPrivateData() {
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

    /// Merges private data from the given private data stores into account storages.
    ///
    /// Private data stores are searched in order, so callers can prefer the file paired with the account metadata
    /// file while still accepting private data from other stores.
    ///
    /// @param accountStorages account storages to update in place
    /// @param privateDataStores private data stores searched for matching private fields
    static void mergeInto(AccountStorages accountStorages, List<AccountPrivateData> privateDataStores) {
        for (Map<Object, Object> account : accountStorages.getAccounts()) {
            @Nullable AccountID identifier = Account.identifier(account);
            if (identifier == null) {
                continue;
            }

            @Nullable Map<Object, Object> accountPrivateData = findPrivateData(identifier, privateDataStores);
            if (accountPrivateData != null) {
                account.putAll(accountPrivateData);
            }
        }
    }

    /// Finds the first private data entry matching an account identifier.
    ///
    /// @param identifier the stable account identifier
    /// @param privateDataStores private data stores searched in order
    /// @return the first matching private data map, or `null` if no store contains one
    private static @Nullable Map<Object, Object> findPrivateData(
            AccountID identifier,
            List<AccountPrivateData> privateDataStores) {
        for (AccountPrivateData privateDataStore : privateDataStores) {
            @Nullable Map<Object, Object> accountPrivateData = privateDataStore.privateData.get(identifier);
            if (accountPrivateData != null) {
                return accountPrivateData;
            }
        }
        return null;
    }

    /// Replaces this private data store from full account storages and returns metadata-only account entries.
    ///
    /// @param accountStorages full account storages containing both metadata and private data
    /// @return metadata-only account storages
    List<Map<Object, Object>> replaceFromAccountStorages(List<Map<Object, Object>> accountStorages) {
        ExtractedPrivateData extracted = extractFromAccountStorages(accountStorages);
        privateData.clear();
        privateData.putAll(extracted.privateData());
        return extracted.metadataAccounts();
    }

    /// Extracts private data from full account storages.
    ///
    /// @param accountStorages full account storages containing both metadata and private data
    /// @return extracted metadata, account identifiers, and private data
    static ExtractedPrivateData extractFromAccountStorages(List<Map<Object, Object>> accountStorages) {
        List<Map<Object, Object>> metadataAccounts = new ArrayList<>(accountStorages.size());
        List<AccountID> identifiers = new ArrayList<>(accountStorages.size());
        Map<AccountID, Map<Object, Object>> extractedPrivateData = new LinkedHashMap<>();

        for (Map<Object, Object> account : accountStorages) {
            Map<Object, Object> metadata = new LinkedHashMap<>(account);
            @Nullable String type = JsonUtils.getString(metadata, "type");
            @Nullable AccountID identifier = Account.identifier(metadata);

            Map<Object, Object> accountPrivateData = new LinkedHashMap<>();
            if (identifier != null) {
                identifiers.add(identifier);
                for (String field : PRIVATE_DATA_FIELDS) {
                    if (!isPrivateDataField(type, field)) {
                        continue;
                    }
                    @Nullable Object value = metadata.remove(field);
                    if (value != null) {
                        accountPrivateData.put(field, value);
                    }
                }
            }

            if (identifier != null && !accountPrivateData.isEmpty()) {
                extractedPrivateData.put(identifier, accountPrivateData);
            }
            metadataAccounts.add(metadata);
        }

        return new ExtractedPrivateData(metadataAccounts, identifiers, extractedPrivateData);
    }

    /// Returns whether the field should be stored in private data for this account type.
    private static boolean isPrivateDataField(@Nullable String type, String field) {
        return !"profileName".equals(field) || !"offline".equals(type);
    }

    /// Returns whether this store contains private data for the account identifier.
    ///
    /// @param identifier the stable account identifier
    /// @return whether this store contains private data for the identifier
    boolean containsPrivateData(AccountID identifier) {
        return privateData.containsKey(identifier);
    }

    /// Removes private data for the account identifier.
    ///
    /// @param identifier the stable account identifier
    /// @return whether private data was removed
    boolean removePrivateData(AccountID identifier) {
        return privateData.remove(identifier) != null;
    }

    /// Stores private data for the account identifier.
    ///
    /// @param identifier the stable account identifier
    /// @param accountPrivateData the private account data
    void putPrivateData(AccountID identifier, Map<Object, Object> accountPrivateData) {
        privateData.put(identifier, new LinkedHashMap<>(accountPrivateData));
    }

    /// Replaces this private data store with another store.
    ///
    /// @param other the private data store to copy from
    void replaceWith(AccountPrivateData other) {
        privateData.clear();
        for (Map.Entry<AccountID, Map<Object, Object>> entry : other.privateData.entrySet()) {
            privateData.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
    }

    /// Private data extracted from full account storages.
    ///
    /// @param metadataAccounts account entries with private fields removed
    /// @param identifiers identifiers for all account entries that can be matched to private data
    /// @param privateData extracted private data by account identifier
    record ExtractedPrivateData(
            List<Map<Object, Object>> metadataAccounts,
            List<AccountID> identifiers,
            Map<AccountID, Map<Object, Object>> privateData) {
    }

    /// JSON adapter for [AccountPrivateData].
    static final class Adapter implements JsonSerializer<AccountPrivateData>,
            com.google.gson.JsonDeserializer<AccountPrivateData> {
        /// Creates the JSON payload protected inside an account private data file.
        ///
        /// @param accountPrivateData the private data store to serialize
        /// @param context the JSON serialization context
        /// @return the plain payload before protection
        private static JsonObject createPayload(
                AccountPrivateData accountPrivateData,
                JsonSerializationContext context) {
            JsonObject payload = new JsonObject();
            JsonArray entries = new JsonArray();
            for (Map.Entry<AccountID, Map<Object, Object>> entry : accountPrivateData.privateData.entrySet()) {
                JsonObject item = new JsonObject();
                item.add("identifier", context.serialize(entry.getKey(), AccountID.class));
                item.add("privateData", context.serialize(entry.getValue()));
                entries.add(item);
            }
            payload.add("entries", entries);
            return payload;
        }

        /// Reads an account identifier from a private data entry.
        private static @Nullable AccountID readIdentifier(
                @Nullable JsonElement identifierElement,
                JsonDeserializationContext context) {
            if (identifierElement == null || identifierElement.isJsonNull()) {
                return null;
            }

            try {
                return context.deserialize(identifierElement, AccountID.class);
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
                JsonElement payload,
                JsonDeserializationContext context) {
            if (!(payload instanceof JsonObject object)) {
                throw new JsonParseException("Account private data payload is not an object");
            }

            JsonElement entriesElement = object.get("entries");
            if (!(entriesElement instanceof JsonArray entries)) {
                return;
            }

            for (JsonElement itemElement : entries) {
                if (!(itemElement instanceof JsonObject item)) {
                    continue;
                }

                @Nullable AccountID identifier = readIdentifier(item.get("identifier"), context);
                if (identifier == null) {
                    continue;
                }

                JsonElement privateDataElement = item.get("privateData");
                if (privateDataElement == null || !privateDataElement.isJsonObject()) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                @Nullable Map<Object, Object> entryPrivateData = context.deserialize(privateDataElement, Map.class);
                if (entryPrivateData != null) {
                    accountPrivateData.privateData.put(identifier, entryPrivateData);
                }
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
            values.remove(ProtectedPayload.PROPERTY_PAYLOAD);
            accountPrivateData.unknownFields.putAll(values);

            readPayload(accountPrivateData, ProtectedPayload.read(object), context);
            return accountPrivateData;
        }
    }
}
