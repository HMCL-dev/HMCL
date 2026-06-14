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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached account metadata lists.
@NotNullByDefault
public final class AccountStoragesTest {
    /// Restores a system property to its previous value.
    ///
    /// @param name the system property name
    /// @param previous the previous system property value
    private static void restoreSystemProperty(String name, @Nullable String previous) {
        if (previous == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previous);
        }
    }

    /// Tests that account metadata serializes as an object containing an accounts list.
    @Test
    public void serializesAccountsAsObjectList() {
        AccountStorages accountStorages = AccountStorages.fromAccounts(List.of(Map.<Object, Object>of(
                "type", "offline",
                "username", "Steve"
        )));

        JsonObject serialized = JsonParser.parseString(
                JsonUtils.GSON.toJson(accountStorages, AccountStorages.class)
        ).getAsJsonObject();

        assertEquals(AccountStorages.CURRENT_SCHEMA.url(),
                serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
        assertTrue(serialized.has("accounts"));
        assertEquals(1, serialized.getAsJsonArray("accounts").size());
        assertEquals("offline", serialized.getAsJsonArray("accounts")
                .get(0)
                .getAsJsonObject()
                .get("type")
                .getAsString());
    }

    /// Tests wrapping legacy account list content in the current `accounts.json` model.
    @Test
    public void wrapsLegacyAccountListInCurrentModel() {
        AccountStorages storages = AccountStorages.fromAccounts(List.of(
                Map.of("type", "offline", "username", "Steve")
        ));

        JsonObject serialized = JsonParser.parseString(
                JsonUtils.GSON.toJson(storages, AccountStorages.class)).getAsJsonObject();

        assertEquals(AccountStorages.CURRENT_SCHEMA.url(), serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
        JsonArray accounts = serialized.getAsJsonArray("accounts");
        assertEquals(1, accounts.size());
        assertEquals("offline", accounts.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("Steve", accounts.get(0).getAsJsonObject().get("username").getAsString());
    }

    /// Tests extracting account metadata from a main config object.
    @Test
    public void extractsAccountsFromConfigJson() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "accounts": [
                    {
                      "type": "offline",
                      "username": "Alex"
                    }
                  ],
                  "selectedAccount": "Alex"
                }
                """).getAsJsonObject();

        AccountStorages accountStorages = LegacyConfigMigrator.extractAccountStorages(settings);
        assertNotNull(accountStorages);

        assertFalse(settings.has("accounts"));
        assertTrue(settings.has("selectedAccount"));
        assertEquals(1, accountStorages.getAccounts().size());
        assertEquals("offline", accountStorages.getAccounts().get(0).get("type"));
        assertEquals(AccountStorages.CURRENT_SCHEMA, accountStorages.getSchema());
    }

    /// Tests moving token fields from account metadata into the credential store.
    @Test
    public void extractsTokenFieldsIntoCredentials() {
        AccountCredentials credentials = new AccountCredentials();
        List<Map<Object, Object>> metadataAccounts = credentials.replaceFromAccountStorages(List.of(Map.of(
                "type", "microsoft",
                "uuid", "00000000-0000-0000-0000-000000000001",
                "displayName", "Steve",
                "accessToken", "access-token",
                "refreshToken", "refresh-token"
        )));
        Map<Object, Object> metadata = metadataAccounts.get(0);
        JsonObject identifier = Objects.requireNonNull(AccountCredentials.identifier(metadata));

        assertEquals("microsoft", metadata.get("type"));
        assertEquals("Steve", metadata.get("displayName"));
        assertFalse(metadata.containsKey("id"));
        assertFalse(metadata.containsKey("accessToken"));
        assertFalse(metadata.containsKey("refreshToken"));
        assertEquals("access-token", credentials.getCredentials().get(identifier).get("accessToken"));
        assertEquals("refresh-token", credentials.getCredentials().get(identifier).get("refreshToken"));
    }

    /// Tests restoring token fields from the credential store into account metadata.
    @Test
    public void mergesCredentialsIntoAccountMetadata() {
        AccountCredentials credentials = new AccountCredentials();
        List<Map<Object, Object>> metadataAccounts = credentials.replaceFromAccountStorages(List.of(Map.of(
                "type", "microsoft",
                "uuid", "00000000-0000-0000-0000-000000000001",
                "displayName", "Steve",
                "accessToken", "access-token",
                "refreshToken", "refresh-token"
        )));
        AccountStorages accountStorages = AccountStorages.fromAccounts(metadataAccounts);

        credentials.mergeInto(accountStorages);
        Map<Object, Object> account = accountStorages.getAccounts().get(0);

        assertEquals("access-token", account.get("accessToken"));
        assertEquals("refresh-token", account.get("refreshToken"));
    }

    /// Tests that account credentials serialize as one protected payload.
    @Test
    public void serializesCredentialsAsProtectedPayload() {
        @Nullable String previous = System.getProperty(AccountCredentials.PROTECTION_PROPERTY);
        System.clearProperty(AccountCredentials.PROTECTION_PROPERTY);
        try {
            AccountCredentials credentials = new AccountCredentials();
            credentials.replaceFromAccountStorages(List.of(Map.of(
                    "type", "microsoft",
                    "uuid", "00000000-0000-0000-0000-000000000001",
                    "displayName", "Steve",
                    "accessToken", "access-token",
                    "refreshToken", "refresh-token"
            )));

            JsonObject serialized = JsonParser.parseString(
                    LauncherSettings.SETTINGS_GSON.toJson(credentials, AccountCredentials.class)).getAsJsonObject();

            assertEquals(AccountCredentials.CURRENT_SCHEMA.url(),
                    serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
            assertEquals("hmcl-obfuscated-v1", serialized.get("protection").getAsString());
            assertTrue(serialized.has("nonce"));
            assertTrue(serialized.has("payload"));
            assertFalse(serialized.toString().contains("access-token"));
            assertFalse(serialized.toString().contains("refresh-token"));
        } finally {
            restoreSystemProperty(AccountCredentials.PROTECTION_PROPERTY, previous);
        }
    }

    /// Tests that account credentials can serialize as plain JSON payloads for development.
    @Test
    public void serializesCredentialsAsPlainPayloadWhenEnabled() {
        @Nullable String previous = System.getProperty(AccountCredentials.PROTECTION_PROPERTY);
        System.setProperty(AccountCredentials.PROTECTION_PROPERTY, "plain");
        try {
            AccountCredentials credentials = new AccountCredentials();
            List<Map<Object, Object>> metadataAccounts = credentials.replaceFromAccountStorages(List.of(Map.of(
                    "type", "microsoft",
                    "uuid", "00000000-0000-0000-0000-000000000001",
                    "displayName", "Steve",
                    "accessToken", "access-token",
                    "refreshToken", "refresh-token"
            )));
            JsonObject identifier = Objects.requireNonNull(AccountCredentials.identifier(metadataAccounts.get(0)));

            JsonObject serialized = JsonParser.parseString(
                    LauncherSettings.SETTINGS_GSON.toJson(credentials, AccountCredentials.class)).getAsJsonObject();
            JsonObject payload = serialized.getAsJsonObject("payload");
            JsonObject item = payload.getAsJsonArray("credentials").get(0).getAsJsonObject();
            JsonObject tokens = item.getAsJsonObject("tokens");
            JsonObject serializedIdentifier = item.getAsJsonObject("identifier");

            assertEquals(AccountCredentials.CURRENT_SCHEMA.url(),
                    serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
            assertEquals("plain", serialized.get("protection").getAsString());
            assertFalse(serialized.has("nonce"));
            assertEquals(identifier, serializedIdentifier);
            assertEquals("access-token", tokens.get("accessToken").getAsString());
            assertEquals("refresh-token", tokens.get("refreshToken").getAsString());

            AccountCredentials deserialized = Objects.requireNonNull(
                    LauncherSettings.SETTINGS_GSON.fromJson(serialized, AccountCredentials.class));

            assertEquals("access-token", deserialized.getCredentials().get(identifier).get("accessToken"));
            assertEquals("refresh-token", deserialized.getCredentials().get(identifier).get("refreshToken"));
        } finally {
            restoreSystemProperty(AccountCredentials.PROTECTION_PROPERTY, previous);
        }
    }

    /// Tests reading account credentials from one protected payload.
    @Test
    public void deserializesCredentialsFromProtectedPayload() {
        @Nullable String previous = System.getProperty(AccountCredentials.PROTECTION_PROPERTY);
        System.clearProperty(AccountCredentials.PROTECTION_PROPERTY);
        try {
            AccountCredentials credentials = new AccountCredentials();
            List<Map<Object, Object>> metadataAccounts = credentials.replaceFromAccountStorages(List.of(Map.of(
                    "type", "microsoft",
                    "uuid", "00000000-0000-0000-0000-000000000001",
                    "displayName", "Steve",
                    "accessToken", "access-token",
                    "refreshToken", "refresh-token"
            )));
            JsonObject identifier = Objects.requireNonNull(AccountCredentials.identifier(metadataAccounts.get(0)));
            JsonObject serialized = JsonParser.parseString(
                    LauncherSettings.SETTINGS_GSON.toJson(credentials, AccountCredentials.class)).getAsJsonObject();

            AccountCredentials deserialized = Objects.requireNonNull(
                    LauncherSettings.SETTINGS_GSON.fromJson(serialized, AccountCredentials.class));

            assertEquals("access-token", deserialized.getCredentials().get(identifier).get("accessToken"));
            assertEquals("refresh-token", deserialized.getCredentials().get(identifier).get("refreshToken"));
        } finally {
            restoreSystemProperty(AccountCredentials.PROTECTION_PROPERTY, previous);
        }
    }
}
