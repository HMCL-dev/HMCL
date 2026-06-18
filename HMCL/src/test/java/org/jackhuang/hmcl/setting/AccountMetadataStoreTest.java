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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached account metadata stores.
@NotNullByDefault
public final class AccountMetadataStoreTest {
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

    /// Creates a JSON object from alternating string keys and values.
    ///
    /// @param entries alternating keys and values
    /// @return a JSON object containing the entries
    private static JsonObject jsonObject(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("JSON object entries must be key-value pairs");
        }

        JsonObject object = new JsonObject();
        for (int i = 0; i < entries.length; i += 2) {
            object.add((String) entries[i], JsonUtils.GSON.toJsonTree(entries[i + 1]));
        }
        return object;
    }

    /// Returns a deterministic account ID for tests.
    ///
    /// @param value the numeric suffix
    /// @return an account ID string with the given numeric suffix
    private static String accountID(int value) {
        return "account:00000000-0000-0000-0000-" + String.format("%012d", value);
    }

    /// Returns the migrated account ID generated from a legacy account reference.
    ///
    /// @param userStorage whether the legacy account belongs to the shared user account file
    /// @param legacyIdentifier the legacy account identifier
    /// @return the migrated account ID
    private static String legacyAccountID(boolean userStorage, String legacyIdentifier) {
        return new AccountID(UUIDs.generateV5(
                LegacyConfigMigrator.LEGACY_ACCOUNT_ID_NAMESPACE,
                userStorage ? "$GLOBAL:" + legacyIdentifier : legacyIdentifier)).toString();
    }

    /// Creates a reflected account private data store for testing private save ordering.
    ///
    /// @param privateData the account private data store
    /// @param file the JSON setting file helper
    /// @return the reflected private data store record
    private static Object accountPrivateDataStore(
            AccountPrivateData privateData,
            JsonSettingFile<AccountPrivateData> file) throws ReflectiveOperationException {
        Class<?> type = Class.forName("org.jackhuang.hmcl.setting.SettingsManager$AccountPrivateDataStore");
        Constructor<?> constructor = type.getDeclaredConstructor(AccountPrivateData.class, JsonSettingFile.class);
        constructor.setAccessible(true);
        return constructor.newInstance(privateData, file);
    }

    /// Returns the reflected account metadata save method.
    private static Method saveAccountMetadataStoreMethod()
            throws ReflectiveOperationException {
        Method method = SettingsManager.class.getDeclaredMethod(
                "saveAccountMetadataStore",
                AccountMetadataStore.class,
                JsonSettingFile.class,
                List.class,
                boolean.class,
                boolean.class);
        method.setAccessible(true);
        return method;
    }

    /// Returns the reflected synchronous forced account metadata overwrite method.
    private static Method backupAndOverwriteAccountMetadataStoreMethod() throws ReflectiveOperationException {
        Method method = SettingsManager.class.getDeclaredMethod(
                "backupAndOverwriteAccountMetadataStore",
                AccountMetadataStore.class,
                JsonSettingFile.class,
                AccountPrivateData.class,
                JsonSettingFile.class);
        method.setAccessible(true);
        return method;
    }

    /// Tests that account metadata serializes as an object containing an accounts list.
    @Test
    public void serializesAccountsAsObjectList() {
        AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(List.of(jsonObject(
                "type", "offline",
                "accountID", accountID(1),
                "profileName", "Steve",
                "profileID", "5627dd98-e6be-3c21-b8a8-e92344183641"
        )));

        JsonObject serialized = JsonParser.parseString(
                JsonUtils.GSON.toJson(accountMetadata, AccountMetadataStore.class)
        ).getAsJsonObject();

        assertEquals(AccountMetadataStore.CURRENT_SCHEMA.url(),
                serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
        assertTrue(serialized.has("accounts"));
        assertEquals(1, serialized.getAsJsonArray("accounts").size());
        assertEquals("offline", serialized.getAsJsonArray("accounts")
                .get(0)
                .getAsJsonObject()
                .get("type")
                .getAsString());
        assertEquals(accountID(1), serialized.getAsJsonArray("accounts")
                .get(0)
                .getAsJsonObject()
                .get("accountID")
                .getAsString());
    }

    /// Tests that private data save failures stop metadata from being saved.
    @Test
    public void doesNotSaveMetadataWhenPrivateDataSyncSaveFails()
            throws ReflectiveOperationException, IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDirectory = fileSystem.getPath("/work");
            Files.createDirectories(tempDirectory);
            Path metadataPath = tempDirectory.resolve("accounts.json");
            Path privateDataParent = tempDirectory.resolve("private-parent");
            Path privateDataPath = privateDataParent.resolve("account-private-data.json");
            Files.writeString(privateDataParent, "not a directory");
            AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(List.of(jsonObject(
                    "type", "microsoft",
                    "accountID", accountID(1),
                    "profileID", "12345678-1234-1234-1234-1234567890ab"
            )));
            AccountPrivateData privateData = new AccountPrivateData();
            privateData.putPrivateData(
                    AccountID.parse(accountID(1)),
                    jsonObject(
                            "profileName", "Steve",
                            "accessToken", "access-token",
                            "refreshToken", "refresh-token"));
            JsonSettingFile<AccountMetadataStore> metadataFile = new JsonSettingFile<>(
                    metadataPath,
                    "test account metadata",
                    AccountMetadataStore.class,
                    AccountMetadataStore.CURRENT_SCHEMA,
                    AccountMetadataStore::new);
            JsonSettingFile<AccountPrivateData> privateDataFile = new JsonSettingFile<>(
                    privateDataPath,
                    "test account private data",
                    AccountPrivateData.class,
                    AccountPrivateData.CURRENT_SCHEMA,
                    AccountPrivateData::new);
            Object privateDataStore = accountPrivateDataStore(privateData, privateDataFile);
            Method saveMethod = saveAccountMetadataStoreMethod();

            InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                    () -> saveMethod.invoke(
                            null,
                            accountMetadata,
                            metadataFile,
                            List.of(privateDataStore),
                            true,
                            true));

            assertInstanceOf(IOException.class, exception.getCause());
            assertFalse(Files.exists(metadataPath));
            assertTrue(Files.isRegularFile(privateDataParent));
            assertFalse(Files.exists(privateDataPath));
        }
    }

    /// Tests that forced overwrite does not update account metadata when private data cannot be written.
    @Test
    public void doesNotOverwriteMetadataWhenForcedPrivateDataOverwriteFails()
            throws ReflectiveOperationException, IOException {
        try (FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix())) {
            Path tempDirectory = fileSystem.getPath("/work");
            Files.createDirectories(tempDirectory);
            Path metadataPath = tempDirectory.resolve("accounts.json");
            String previousMetadata = "{\"old\":true}";
            Files.writeString(metadataPath, previousMetadata);
            Path privateDataParent = tempDirectory.resolve("private-parent");
            Path privateDataPath = privateDataParent.resolve("account-private-data.json");
            Files.writeString(privateDataParent, "not a directory");
            AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(List.of(jsonObject(
                    "type", "microsoft",
                    "accountID", accountID(1),
                    "profileID", "12345678-1234-1234-1234-1234567890ab",
                    "profileName", "Steve",
                    "accessToken", "access-token",
                    "refreshToken", "refresh-token"
            )));
            JsonSettingFile<AccountMetadataStore> metadataFile = new JsonSettingFile<>(
                    metadataPath,
                    "test account metadata",
                    AccountMetadataStore.class,
                    AccountMetadataStore.CURRENT_SCHEMA,
                    AccountMetadataStore::new);
            AccountPrivateData privateData = new AccountPrivateData();
            JsonSettingFile<AccountPrivateData> privateDataFile = new JsonSettingFile<>(
                    privateDataPath,
                    "test account private data",
                    AccountPrivateData.class,
                    AccountPrivateData.CURRENT_SCHEMA,
                    AccountPrivateData::new);
            Method overwriteMethod = backupAndOverwriteAccountMetadataStoreMethod();

            InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                    () -> overwriteMethod.invoke(
                            null,
                            accountMetadata,
                            metadataFile,
                            privateData,
                            privateDataFile));

            assertInstanceOf(IOException.class, exception.getCause());
            assertEquals(previousMetadata, Files.readString(metadataPath));
            assertTrue(Files.isRegularFile(privateDataParent));
            assertFalse(Files.exists(privateDataPath));
            assertTrue(privateData.getPrivateData().isEmpty());
        }
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
                  "selectedAccount": "Alex:Alex"
                }
                """).getAsJsonObject();

        LegacyConfigMigrator.AccountMigrationResult migratedAccounts = LegacyConfigMigrator.extractAccounts(settings);
        assertNotNull(migratedAccounts);
        AccountMetadataStore accountMetadata = migratedAccounts.metadata();

        assertFalse(settings.has("accounts"));
        assertTrue(settings.has("selectedAccount"));
        assertEquals(1, accountMetadata.getAccounts().size());
        assertEquals("offline", JsonUtils.getString(accountMetadata.getAccounts().get(0), "type"));
        assertEquals("Alex", JsonUtils.getString(accountMetadata.getAccounts().get(0), "profileName"));
        assertEquals(OfflineAccountFactory.getUUIDFromUserName("Alex").toString(),
                JsonUtils.getString(accountMetadata.getAccounts().get(0), "profileID"));
        assertEquals(legacyAccountID(false, "Alex:Alex"),
                JsonUtils.getString(accountMetadata.getAccounts().get(0), "accountID"));
        assertEquals(AccountMetadataStore.CURRENT_SCHEMA, accountMetadata.getSchema());
    }

    /// Tests normalizing legacy account list fields before storing migrated shared accounts.
    @Test
    public void migratesLegacyAccountListFields() {
        LegacyConfigMigrator.AccountMigrationResult migratedAccounts = LegacyConfigMigrator.migrateLegacyAccounts(List.of(
                jsonObject(
                        "type", "offline",
                        "username", "Alex"),
                jsonObject(
                        "type", "microsoft",
                        "uuid", "00000000000000000000000000000001",
                        "displayName", "Steve",
                        "accessToken", "access-token"),
                jsonObject(
                        "type", "yggdrasil",
                        "username", "steve@example.com",
                        "uuid", "00000000000000000000000000000002",
                        "displayName", "Steve"))
        );
        AccountMetadataStore accountMetadata = migratedAccounts.metadata();
        AccountPrivateData privateData = migratedAccounts.privateData();

        JsonObject offlineAccount = accountMetadata.getAccounts().get(0);
        assertEquals("Alex", JsonUtils.getString(offlineAccount, "profileName"));
        assertEquals(OfflineAccountFactory.getUUIDFromUserName("Alex").toString(),
                JsonUtils.getString(offlineAccount, "profileID"));
        assertEquals(legacyAccountID(false, "Alex:Alex"), JsonUtils.getString(offlineAccount, "accountID"));
        assertFalse(offlineAccount.has("username"));
        assertFalse(offlineAccount.has("uuid"));
        AccountID offlineAccountID = Objects.requireNonNull(Account.getAccountID(offlineAccount));
        assertNull(AccountPrivateData.findPrivateData(offlineAccountID, List.of(privateData)));

        JsonObject microsoftAccount = accountMetadata.getAccounts().get(1);
        assertEquals("00000000-0000-0000-0000-000000000001", JsonUtils.getString(microsoftAccount, "profileID"));
        assertEquals(legacyAccountID(false, "microsoft:00000000-0000-0000-0000-000000000001"),
                JsonUtils.getString(microsoftAccount, "accountID"));
        assertFalse(microsoftAccount.has("uuid"));
        assertFalse(microsoftAccount.has("displayName"));
        assertFalse(microsoftAccount.has("profileName"));
        assertFalse(microsoftAccount.has("accessToken"));

        JsonObject yggdrasilAccount = accountMetadata.getAccounts().get(2);
        assertEquals("steve@example.com", JsonUtils.getString(yggdrasilAccount, "loginName"));
        assertEquals("00000000-0000-0000-0000-000000000002", JsonUtils.getString(yggdrasilAccount, "profileID"));
        assertEquals(legacyAccountID(false, "steve@example.com:00000000-0000-0000-0000-000000000002"),
                JsonUtils.getString(yggdrasilAccount, "accountID"));
        assertFalse(yggdrasilAccount.has("username"));
        assertFalse(yggdrasilAccount.has("uuid"));
        assertFalse(yggdrasilAccount.has("displayName"));
        assertFalse(yggdrasilAccount.has("profileName"));

        AccountID microsoftAccountID = Objects.requireNonNull(Account.getAccountID(microsoftAccount));
        assertEquals(JsonUtils.getString(microsoftAccount, "accountID"), microsoftAccountID.toString());
        JsonObject microsoftPrivateData = privateData.getPrivateData().get(microsoftAccountID);
        assertEquals("Steve", JsonUtils.getString(microsoftPrivateData, "profileName"));
        assertEquals("access-token", JsonUtils.getString(microsoftPrivateData, "accessToken"));

        AccountID yggdrasilAccountID = Objects.requireNonNull(Account.getAccountID(yggdrasilAccount));
        JsonObject yggdrasilPrivateData = privateData.getPrivateData().get(yggdrasilAccountID);
        assertEquals("Steve", JsonUtils.getString(yggdrasilPrivateData, "profileName"));
    }

    /// Tests replacing duplicate account IDs across local and shared account metadata stores.
    @Test
    public void deduplicatesAccountIDsAcrossMetadataStores() {
        String duplicateAccountID = new AccountID(OfflineAccountFactory.getUUIDFromUserName("Alex")).toString();
        AccountMetadataStore localAccounts = AccountMetadataStore.fromRecords(List.of(jsonObject(
                "type", "offline",
                "accountID", duplicateAccountID,
                "profileName", "Alex",
                "profileID", OfflineAccountFactory.getUUIDFromUserName("Alex").toString()
        )));
        AccountMetadataStore userAccounts = AccountMetadataStore.fromRecords(List.of(jsonObject(
                "type", "offline",
                "accountID", duplicateAccountID,
                "profileName", "Alex",
                "profileID", OfflineAccountFactory.getUUIDFromUserName("Alex").toString()
        )));

        Set<String> usedAccountIDs = new HashSet<>();
        LegacyConfigMigrator.assignAccountIDs(localAccounts, usedAccountIDs, false);
        LegacyConfigMigrator.assignAccountIDs(userAccounts, usedAccountIDs, true);

        assertEquals(duplicateAccountID, JsonUtils.getString(localAccounts.getAccounts().get(0), "accountID"));
        assertEquals(legacyAccountID(true, "Alex:Alex"),
                JsonUtils.getString(userAccounts.getAccounts().get(0), "accountID"));
        assertDoesNotThrow(() -> AccountID.parse(JsonUtils.getString(userAccounts.getAccounts().get(0), "accountID")));
    }

    /// Tests using the legacy global selected-account prefix for shared account migration IDs.
    @Test
    public void migratesLegacyUserAccountsWithGlobalSeed() {
        AccountMetadataStore accountMetadata = LegacyConfigMigrator.migrateLegacyAccounts(List.of(
                jsonObject(
                        "type", "offline",
                        "username", "Alex")
        ), true).metadata();

        assertEquals(legacyAccountID(true, "Alex:Alex"),
                JsonUtils.getString(accountMetadata.getAccounts().get(0), "accountID"));
    }

    /// Tests moving online account private fields from account metadata into the private data store.
    @Test
    public void migratesOnlineAccountPrivateFieldsIntoPrivateData() {
        LegacyConfigMigrator.AccountMigrationResult migratedAccounts = LegacyConfigMigrator.migrateLegacyAccounts(List.of(
                jsonObject(
                        "type", "microsoft",
                        "uuid", "00000000000000000000000000000001",
                        "displayName", "Steve",
                        "accessToken", "microsoft-access-token",
                        "refreshToken", "microsoft-refresh-token",
                        "tokenType", "Bearer",
                        "notAfter", 1234L,
                        "userid", "user-id"),
                jsonObject(
                        "type", "yggdrasil",
                        "username", "steve@example.com",
                        "uuid", "00000000000000000000000000000002",
                        "displayName", "Steve",
                        "accessToken", "yggdrasil-access-token",
                        "clientToken", "client-token",
                        "userProperties", Map.of("preferredLanguage", "en_US")),
                jsonObject(
                        "type", "authlibInjector",
                        "serverBaseURL", "https://example.invalid/api/yggdrasil",
                        "username", "steve@example.com",
                        "uuid", "00000000000000000000000000000003",
                        "displayName", "Steve",
                        "profileProperties", Map.of("textures", "texture-data"))
        ));
        AccountPrivateData privateData = migratedAccounts.privateData();

        JsonObject microsoftMetadata = migratedAccounts.metadata().getAccounts().get(0);
        AccountID microsoftAccountID = Objects.requireNonNull(Account.getAccountID(microsoftMetadata));
        assertFalse(microsoftMetadata.has("profileName"));
        assertFalse(microsoftMetadata.has("accessToken"));
        assertFalse(microsoftMetadata.has("refreshToken"));
        assertFalse(microsoftMetadata.has("tokenType"));
        assertFalse(microsoftMetadata.has("notAfter"));
        assertFalse(microsoftMetadata.has("userid"));
        JsonObject microsoftPrivateData = privateData.getPrivateData().get(microsoftAccountID);
        assertEquals("microsoft-access-token", JsonUtils.getString(microsoftPrivateData, "accessToken"));
        assertEquals("microsoft-refresh-token", JsonUtils.getString(microsoftPrivateData, "refreshToken"));
        assertEquals("Steve", JsonUtils.getString(microsoftPrivateData, "profileName"));
        assertEquals("Bearer", JsonUtils.getString(microsoftPrivateData, "tokenType"));
        assertEquals(1234L, microsoftPrivateData.get("notAfter").getAsLong());
        assertEquals("user-id", JsonUtils.getString(microsoftPrivateData, "userid"));

        JsonObject yggdrasilMetadata = migratedAccounts.metadata().getAccounts().get(1);
        AccountID yggdrasilAccountID = Objects.requireNonNull(Account.getAccountID(yggdrasilMetadata));
        assertFalse(yggdrasilMetadata.has("profileName"));
        assertFalse(yggdrasilMetadata.has("accessToken"));
        assertFalse(yggdrasilMetadata.has("clientToken"));
        assertFalse(yggdrasilMetadata.has("userProperties"));
        JsonObject yggdrasilPrivateData = privateData.getPrivateData().get(yggdrasilAccountID);
        assertEquals("yggdrasil-access-token", JsonUtils.getString(yggdrasilPrivateData, "accessToken"));
        assertEquals("client-token", JsonUtils.getString(yggdrasilPrivateData, "clientToken"));
        assertEquals("Steve", JsonUtils.getString(yggdrasilPrivateData, "profileName"));
        assertEquals("en_US",
                JsonUtils.getString(yggdrasilPrivateData.getAsJsonObject("userProperties"), "preferredLanguage"));

        JsonObject authlibInjectorMetadata = migratedAccounts.metadata().getAccounts().get(2);
        AccountID authlibInjectorAccountID = Objects.requireNonNull(Account.getAccountID(authlibInjectorMetadata));
        assertFalse(authlibInjectorMetadata.has("profileName"));
        assertFalse(authlibInjectorMetadata.has("profileProperties"));
        JsonObject authlibInjectorPrivateData = privateData.getPrivateData().get(authlibInjectorAccountID);
        assertEquals("Steve", JsonUtils.getString(authlibInjectorPrivateData, "profileName"));
        assertEquals("texture-data",
                JsonUtils.getString(authlibInjectorPrivateData.getAsJsonObject("profileProperties"), "textures"));
    }

    /// Tests finding private fields from the first matching private data store.
    @Test
    public void findsPrivateDataFromMultipleStoresInOrder() {
        AccountPrivateData preferredPrivateData = new AccountPrivateData();
        AccountID accountID = AccountID.parse(accountID(1));
        preferredPrivateData.putPrivateData(accountID, jsonObject(
                "accessToken", "preferred-token",
                "refreshToken", "preferred-refresh-token"
        ));

        AccountPrivateData fallbackPrivateData = new AccountPrivateData();
        fallbackPrivateData.putPrivateData(accountID, jsonObject(
                "accessToken", "fallback-token"
        ));
        JsonObject fallback =
                Objects.requireNonNull(AccountPrivateData.findPrivateData(
                        accountID,
                        List.of(new AccountPrivateData(), fallbackPrivateData)));

        assertEquals("fallback-token", JsonUtils.getString(fallback, "accessToken"));

        JsonObject preferred =
                Objects.requireNonNull(AccountPrivateData.findPrivateData(
                        accountID,
                        List.of(preferredPrivateData, fallbackPrivateData)));

        assertEquals("preferred-token", JsonUtils.getString(preferred, "accessToken"));
        assertEquals("preferred-refresh-token", JsonUtils.getString(preferred, "refreshToken"));
    }

    /// Tests that account private data serializes as one protected payload.
    @Test
    public void serializesPrivateDataAsProtectedPayload() {
        @Nullable String previous = System.getProperty(AccountPrivateData.PROTECTION_PROPERTY);
        System.clearProperty(AccountPrivateData.PROTECTION_PROPERTY);
        try {
            AccountPrivateData privateData = new AccountPrivateData();
            privateData.putPrivateData(AccountID.parse(accountID(1)), jsonObject(
                    "profileName", "Steve",
                    "accessToken", "access-token",
                    "refreshToken", "refresh-token"
            ));

            JsonObject serialized = JsonParser.parseString(
                    LauncherSettings.SETTINGS_GSON.toJson(privateData, AccountPrivateData.class)).getAsJsonObject();

            assertEquals(AccountPrivateData.CURRENT_SCHEMA.url(),
                    serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
            assertEquals("hmcl-obfuscated-v1", serialized.get("protection").getAsString());
            assertEquals(256, serialized.getAsJsonArray("payload").size());
            assertFalse(serialized.toString().contains("access-token"));
            assertFalse(serialized.toString().contains("refresh-token"));

            AccountPrivateData deserialized = Objects.requireNonNull(
                    LauncherSettings.SETTINGS_GSON.fromJson(serialized, AccountPrivateData.class));
            JsonObject deserializedPrivateData = deserialized.getPrivateData().get(AccountID.parse(accountID(1)));
            assertEquals("access-token", JsonUtils.getString(deserializedPrivateData, "accessToken"));
            assertEquals("refresh-token", JsonUtils.getString(deserializedPrivateData, "refreshToken"));
            assertEquals("Steve", JsonUtils.getString(deserializedPrivateData, "profileName"));
        } finally {
            restoreSystemProperty(AccountPrivateData.PROTECTION_PROPERTY, previous);
        }
    }

}
