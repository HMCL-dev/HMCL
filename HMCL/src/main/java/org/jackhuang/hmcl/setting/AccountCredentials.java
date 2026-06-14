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
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
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

    /// Returns whether credentials should be written as plain JSON payloads.
    static boolean isPlainProtectionEnabled() {
        return ProtectedPayload.PROTECTION_PLAIN.equals(System.getProperty(PROTECTION_PROPERTY));
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

    /// Merges stored credentials into the given account storages.
    ///
    /// @param accountStorages account storages to update in place
    void mergeInto(AccountStorages accountStorages) {
        for (Map<Object, Object> account : accountStorages.getAccounts()) {
            @Nullable JsonObject identifier = identifier(account);
            if (identifier == null) {
                continue;
            }

            @Nullable Map<Object, Object> accountCredentials = credentials.get(identifier);
            if (accountCredentials != null) {
                account.putAll(accountCredentials);
            }
        }
    }

    /// Replaces this credential store from full account storages and returns metadata-only account entries.
    ///
    /// @param accountStorages full account storages containing both metadata and credentials
    /// @return metadata-only account storages
    List<Map<Object, Object>> replaceFromAccountStorages(List<Map<Object, Object>> accountStorages) {
        List<Map<Object, Object>> metadataAccounts = new ArrayList<>(accountStorages.size());
        Map<JsonObject, Map<Object, Object>> updatedCredentials = new LinkedHashMap<>();

        for (Map<Object, Object> account : accountStorages) {
            Map<Object, Object> metadata = new LinkedHashMap<>(account);
            @Nullable JsonObject identifier = identifier(metadata);

            Map<Object, Object> accountCredentials = new LinkedHashMap<>();
            if (identifier != null) {
                for (String field : CREDENTIAL_FIELDS) {
                    @Nullable Object value = metadata.remove(field);
                    if (value != null) {
                        accountCredentials.put(field, value);
                    }
                }
            }

            if (identifier != null && !accountCredentials.isEmpty()) {
                updatedCredentials.put(identifier, accountCredentials);
            }
            metadataAccounts.add(metadata);
        }

        credentials.clear();
        credentials.putAll(updatedCredentials);
        return metadataAccounts;
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

    /// Returns a copy of an account storage map with sensitive fields redacted for logging.
    ///
    /// @param storage the account storage map to redact
    /// @return a redacted copy of the given account storage map
    static Map<Object, Object> redact(Map<?, ?> storage) {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : storage.entrySet()) {
            Object key = entry.getKey();
            if (key instanceof String field && CREDENTIAL_FIELDS.contains(field) && entry.getValue() != null) {
                result.put(key, "<redacted>");
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    /// Returns the stable account identifier for the given account storage.
    ///
    /// @param storage the account storage map
    /// @return the stable account identifier, or `null` if the account cannot be identified
    static @Nullable JsonObject identifier(Map<?, ?> storage) {
        @Nullable String type = JsonUtils.getString(storage, "type");
        if (type == null) {
            return null;
        }

        JsonObject identifier = new JsonObject();
        identifier.addProperty("type", type);
        switch (type) {
            case "offline" -> {
                if (!addIdentifierProperty(identifier, storage, "username")) {
                    return null;
                }
            }
            case "microsoft" -> {
                if (!addIdentifierProperty(identifier, storage, "uuid")) {
                    return null;
                }
            }
            case "authlibInjector" -> {
                if (!addIdentifierProperty(identifier, storage, "serverBaseURL")
                        || !addIdentifierProperty(identifier, storage, "username")
                        || !addIdentifierProperty(identifier, storage, "uuid")) {
                    return null;
                }
            }
            default -> {
                return null;
            }
        }
        return identifier;
    }

    /// Adds a string member from an account storage map to an identifier object.
    ///
    /// @param identifier the identifier object to update
    /// @param storage the account storage map
    /// @param key the member key
    /// @return whether the member exists and was added
    private static boolean addIdentifierProperty(JsonObject identifier, Map<?, ?> storage, String key) {
        @Nullable String value = JsonUtils.getString(storage, key);
        if (value == null) {
            return false;
        }
        identifier.addProperty(key, value);
        return true;
    }

    /// JSON adapter for [AccountCredentials].
    static final class Adapter implements com.google.gson.JsonSerializer<AccountCredentials>,
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
            ProtectedPayload.write(result, createPayload(accountCredentials, context), isPlainProtectionEnabled());
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
            values.remove(ProtectedPayload.PROPERTY_NONCE);
            values.remove(ProtectedPayload.PROPERTY_PAYLOAD);
            accountCredentials.unknownFields.putAll(values);

            readPayload(accountCredentials, ProtectedPayload.read(object), context);
            return accountCredentials;
        }
    }
}
