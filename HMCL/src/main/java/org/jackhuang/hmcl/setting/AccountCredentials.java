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
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
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

/// Stores account token credentials in a protected payload.
@JsonAdapter(AccountCredentials.Adapter.class)
@NotNullByDefault
@JsonSerializable
final class AccountCredentials extends ObservableSetting implements JsonSchemaSetting {
    /// The JSON schema supported by this account credential store.
    static final JsonSchema CURRENT_SCHEMA =
            new JsonSchema("account-credentials", new JsonSchema.Version(1, 0, 0));

    /// JVM system property selecting the account credential protection mode.
    static final String PROTECTION_PROPERTY = "hmcl.credentials.account.protection";

    /// Sensitive account storage fields moved into the credential store.
    private static final @Unmodifiable Set<String> CREDENTIAL_FIELDS =
            Set.of("accessToken", "refreshToken", "clientToken");

    /// Creates an empty account credential store.
    AccountCredentials() {
        tracker.markDirty(schema);
        tracker.markDirty(credentials);
        register();
    }

    /// The schema used by this account credential store file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Account token credentials keyed by account identifier.
    private final ObservableMap<JsonObject, Map<Object, Object>> credentials =
            FXCollections.observableMap(new LinkedHashMap<>());

    /// Whether this account credential store may be saved back to its JSON file.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current on-disk file first.
    private transient boolean backupOnNextSave;

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this account credential store file.
    @Override
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this account credential store file.
    @Override
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Returns account token credentials keyed by account identifier.
    ObservableMap<JsonObject, Map<Object, Object>> getCredentials() {
        return credentials;
    }

    /// Returns the protection mode selected for writing account credentials.
    private static ProtectedPayload.ProtectionMode protectionMode() {
        return ProtectedPayload.ProtectionMode.fromConfiguredId(System.getProperty(PROTECTION_PROPERTY));
    }

    /// Returns whether this account credential store may be saved back to its JSON file.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this account credential store may be saved back to its JSON file.
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

    /// Merges credentials from the given credential stores into account storages.
    ///
    /// Credential stores are searched in order, so callers can prefer the credential file paired with the account
    /// metadata file while still accepting credentials from other stores.
    ///
    /// @param accountStorages account storages to update in place
    /// @param credentialStores credential stores searched for matching token fields
    static void mergeInto(AccountStorages accountStorages, List<AccountCredentials> credentialStores) {
        for (Map<Object, Object> account : accountStorages.getAccounts()) {
            @Nullable JsonObject identifier = Account.identifier(account);
            if (identifier == null) {
                continue;
            }

            @Nullable Map<Object, Object> accountCredentials = findCredentials(identifier, credentialStores);
            if (accountCredentials != null) {
                account.putAll(accountCredentials);
            }
        }
    }

    /// Finds the first credential entry matching an account identifier.
    ///
    /// @param identifier the stable account identifier
    /// @param credentialStores credential stores searched in order
    /// @return the first matching token map, or `null` if no store contains one
    private static @Nullable Map<Object, Object> findCredentials(
            JsonObject identifier,
            List<AccountCredentials> credentialStores) {
        for (AccountCredentials credentialStore : credentialStores) {
            @Nullable Map<Object, Object> accountCredentials = credentialStore.credentials.get(identifier);
            if (accountCredentials != null) {
                return accountCredentials;
            }
        }
        return null;
    }

    /// Replaces this credential store from full account storages and returns metadata-only account entries.
    ///
    /// @param accountStorages full account storages containing both metadata and credentials
    /// @return metadata-only account storages
    List<Map<Object, Object>> replaceFromAccountStorages(List<Map<Object, Object>> accountStorages) {
        ExtractedCredentials extracted = extractFromAccountStorages(accountStorages);
        credentials.clear();
        credentials.putAll(extracted.credentials());
        return extracted.metadataAccounts();
    }

    /// Extracts token credentials from full account storages.
    ///
    /// @param accountStorages full account storages containing both metadata and credentials
    /// @return extracted metadata, account identifiers, and token credentials
    static ExtractedCredentials extractFromAccountStorages(List<Map<Object, Object>> accountStorages) {
        List<Map<Object, Object>> metadataAccounts = new ArrayList<>(accountStorages.size());
        List<JsonObject> identifiers = new ArrayList<>(accountStorages.size());
        Map<JsonObject, Map<Object, Object>> extractedCredentials = new LinkedHashMap<>();

        for (Map<Object, Object> account : accountStorages) {
            Map<Object, Object> metadata = new LinkedHashMap<>(account);
            @Nullable JsonObject identifier = Account.identifier(metadata);

            Map<Object, Object> accountCredentials = new LinkedHashMap<>();
            if (identifier != null) {
                identifiers.add(identifier);
                for (String field : CREDENTIAL_FIELDS) {
                    @Nullable Object value = metadata.remove(field);
                    if (value != null) {
                        accountCredentials.put(field, value);
                    }
                }
            }

            if (identifier != null && !accountCredentials.isEmpty()) {
                extractedCredentials.put(identifier, accountCredentials);
            }
            metadataAccounts.add(metadata);
        }

        return new ExtractedCredentials(metadataAccounts, identifiers, extractedCredentials);
    }

    /// Returns whether this store contains credentials for the account identifier.
    ///
    /// @param identifier the stable account identifier
    /// @return whether this store contains credentials for the identifier
    boolean containsCredentials(JsonObject identifier) {
        return credentials.containsKey(identifier);
    }

    /// Removes credentials for the account identifier.
    ///
    /// @param identifier the stable account identifier
    /// @return whether credentials were removed
    boolean removeCredentials(JsonObject identifier) {
        return credentials.remove(identifier) != null;
    }

    /// Stores token credentials for the account identifier.
    ///
    /// @param identifier the stable account identifier
    /// @param accountCredentials the token credentials
    void putCredentials(JsonObject identifier, Map<Object, Object> accountCredentials) {
        credentials.put(identifier.deepCopy(), new LinkedHashMap<>(accountCredentials));
    }

    /// Replaces this credential store with another store.
    ///
    /// @param other the credential store to copy from
    void replaceWith(AccountCredentials other) {
        credentials.clear();
        for (Map.Entry<JsonObject, Map<Object, Object>> entry : other.credentials.entrySet()) {
            credentials.put(entry.getKey().deepCopy(), new LinkedHashMap<>(entry.getValue()));
        }
    }

    /// Token credentials extracted from full account storages.
    ///
    /// @param metadataAccounts account entries with token fields removed
    /// @param identifiers identifiers for all account entries that can be matched to credentials
    /// @param credentials extracted token credentials by account identifier
    record ExtractedCredentials(
            List<Map<Object, Object>> metadataAccounts,
            List<JsonObject> identifiers,
            Map<JsonObject, Map<Object, Object>> credentials) {
    }

    /// JSON adapter for [AccountCredentials].
    static final class Adapter implements JsonSerializer<AccountCredentials>,
            com.google.gson.JsonDeserializer<AccountCredentials> {
        /// Creates the JSON payload protected inside an account credential file.
        ///
        /// @param accountCredentials the credential store to serialize
        /// @param context the JSON serialization context
        /// @return the plain payload before protection
        private static JsonObject createPayload(
                AccountCredentials accountCredentials,
                JsonSerializationContext context) {
            JsonObject payload = new JsonObject();
            JsonArray credentialsArray = new JsonArray();
            for (Map.Entry<JsonObject, Map<Object, Object>> entry : accountCredentials.credentials.entrySet()) {
                JsonObject item = new JsonObject();
                item.add("identifier", entry.getKey().deepCopy());
                item.add("tokens", context.serialize(entry.getValue()));
                credentialsArray.add(item);
            }
            payload.add("credentials", credentialsArray);
            return payload;
        }

        /// Reads the protected credential payload into the given credential store.
        ///
        /// @param accountCredentials the credential store to update
        /// @param payload the revealed payload
        /// @param context the JSON deserialization context
        private static void readPayload(
                AccountCredentials accountCredentials,
                JsonElement payload,
                JsonDeserializationContext context) {
            if (!(payload instanceof JsonObject object)) {
                throw new JsonParseException("Account credential payload is not an object");
            }

            JsonElement credentialsElement = object.get("credentials");
            if (!(credentialsElement instanceof JsonArray credentialsArray)) {
                return;
            }

            for (JsonElement itemElement : credentialsArray) {
                if (!(itemElement instanceof JsonObject item)) {
                    continue;
                }

                JsonElement identifierElement = item.get("identifier");
                if (!(identifierElement instanceof JsonObject identifier)) {
                    continue;
                }

                JsonElement tokensElement = item.get("tokens");
                if (tokensElement == null || !tokensElement.isJsonObject()) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                @Nullable Map<Object, Object> tokens = context.deserialize(tokensElement, Map.class);
                if (tokens != null) {
                    accountCredentials.credentials.put(identifier.deepCopy(), tokens);
                }
            }
        }

        /// Serializes the credential store as a protected payload envelope.
        @Override
        public JsonElement serialize(
                AccountCredentials accountCredentials,
                Type typeOfSrc,
                JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            result.addProperty(JsonSchema.PROPERTY_SCHEMA, accountCredentials.getSchema().url());
            protectionMode().write(result, createPayload(accountCredentials, context));
            accountCredentials.unknownFields.forEach(result::add);
            return result;
        }

        /// Deserializes the credential store from a protected payload envelope.
        @Override
        public @Nullable AccountCredentials deserialize(
                @Nullable JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (!(json instanceof JsonObject object)) {
                throw new JsonParseException("Account credentials are not an object");
            }

            AccountCredentials accountCredentials = new AccountCredentials();
            Map<String, JsonElement> values = new LinkedHashMap<>(object.asMap());
            JsonElement schema = values.remove(JsonSchema.PROPERTY_SCHEMA);
            if (schema != null && schema.isJsonPrimitive() && schema.getAsJsonPrimitive().isString()) {
                accountCredentials.setSchema(new JsonSchema(schema.getAsString()));
            }
            values.remove(ProtectedPayload.PROPERTY_PROTECTION);
            values.remove(ProtectedPayload.PROPERTY_PAYLOAD);
            accountCredentials.unknownFields.putAll(values);

            readPayload(accountCredentials, ProtectedPayload.read(object), context);
            return accountCredentials;
        }
    }
}
