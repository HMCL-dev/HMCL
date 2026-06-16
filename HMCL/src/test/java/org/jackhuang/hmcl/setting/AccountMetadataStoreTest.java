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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftSession;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
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

    /// Returns the reflected synchronous account metadata save method.
    ///
    /// @param accountPrivateDataStoreClass the private data store record class
    /// @return the reflected save method
    private static Method saveAccountMetadataStoreSyncMethod(Class<?> accountPrivateDataStoreClass)
            throws ReflectiveOperationException {
        Method method = SettingsManager.class.getDeclaredMethod(
                "saveAccountMetadataStoreSync",
                AccountMetadataStore.class,
                JsonSettingFile.class,
                accountPrivateDataStoreClass,
                List.class);
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
        AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(List.of(Map.<Object, Object>of(
                "type", "offline",
                "accountID", accountID(1),
                "profileName", "Steve",
                "profileID", "5627dd98e6be3c21b8a8e92344183641"
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
            AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(List.of(Map.<Object, Object>of(
                    "type", "microsoft",
                    "accountID", accountID(1),
                    "profileID", "123456781234123412341234567890ab",
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
            JsonSettingFile<AccountPrivateData> privateDataFile = new JsonSettingFile<>(
                    privateDataPath,
                    "test account private data",
                    AccountPrivateData.class,
                    AccountPrivateData.CURRENT_SCHEMA,
                    AccountPrivateData::new);
            Object privateDataStore = accountPrivateDataStore(new AccountPrivateData(), privateDataFile);
            Method saveMethod = saveAccountMetadataStoreSyncMethod(privateDataStore.getClass());

            InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                    () -> saveMethod.invoke(
                            null,
                            accountMetadata,
                            metadataFile,
                            privateDataStore,
                            List.of(privateDataStore)));

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
            AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(List.of(Map.<Object, Object>of(
                    "type", "microsoft",
                    "accountID", accountID(1),
                    "profileID", "123456781234123412341234567890ab",
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

    /// Tests wrapping legacy account list content in the current `accounts.json` model.
    @Test
    public void wrapsLegacyAccountListInCurrentModel() {
        AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(List.of(
                Map.of(
                        "type", "offline",
                        "accountID", accountID(1),
                        "profileName", "Steve",
                        "profileID", "5627dd98e6be3c21b8a8e92344183641")
        ));

        JsonObject serialized = JsonParser.parseString(
                JsonUtils.GSON.toJson(accountMetadata, AccountMetadataStore.class)).getAsJsonObject();

        assertEquals(AccountMetadataStore.CURRENT_SCHEMA.url(), serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
        JsonArray accounts = serialized.getAsJsonArray("accounts");
        assertEquals(1, accounts.size());
        assertEquals("offline", accounts.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(accountID(1), accounts.get(0).getAsJsonObject().get("accountID").getAsString());
        assertEquals("Steve", accounts.get(0).getAsJsonObject().get("profileName").getAsString());
        assertEquals("5627dd98e6be3c21b8a8e92344183641",
                accounts.get(0).getAsJsonObject().get("profileID").getAsString());
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

        AccountMetadataStore accountMetadata = LegacyConfigMigrator.extractAccountMetadataStore(settings);
        assertNotNull(accountMetadata);

        assertFalse(settings.has("accounts"));
        assertTrue(settings.has("selectedAccount"));
        assertEquals(1, accountMetadata.getAccounts().size());
        assertEquals("offline", accountMetadata.getAccounts().get(0).get("type"));
        assertEquals("Alex", accountMetadata.getAccounts().get(0).get("profileName"));
        assertEquals(UUIDTypeAdapter.fromUUID(OfflineAccountFactory.getUUIDFromUserName("Alex")),
                accountMetadata.getAccounts().get(0).get("profileID"));
        assertEquals(legacyAccountID(false, "Alex:Alex"),
                accountMetadata.getAccounts().get(0).get("accountID"));
        assertEquals(AccountMetadataStore.CURRENT_SCHEMA, accountMetadata.getSchema());
    }

    /// Tests normalizing legacy account list fields before storing migrated shared accounts.
    @Test
    public void migratesLegacyAccountListFields() {
        AccountMetadataStore accountMetadata = LegacyConfigMigrator.migrateLegacyAccountMetadataStore(List.of(
                Map.<Object, Object>of(
                        "type", "offline",
                        "username", "Alex"),
                Map.<Object, Object>of(
                        "type", "microsoft",
                        "uuid", "00000000000000000000000000000001",
                        "displayName", "Steve",
                        "accessToken", "access-token"),
                Map.<Object, Object>of(
                        "type", "yggdrasil",
                        "username", "steve@example.com",
                        "uuid", "00000000000000000000000000000002",
                        "displayName", "Steve"))
        );

        Map<Object, Object> offlineAccount = accountMetadata.getAccounts().get(0);
        assertEquals("Alex", offlineAccount.get("profileName"));
        assertEquals(UUIDTypeAdapter.fromUUID(OfflineAccountFactory.getUUIDFromUserName("Alex")),
                offlineAccount.get("profileID"));
        assertEquals(legacyAccountID(false, "Alex:Alex"), offlineAccount.get("accountID"));
        assertFalse(offlineAccount.containsKey("username"));
        assertFalse(offlineAccount.containsKey("uuid"));

        Map<Object, Object> microsoftAccount = accountMetadata.getAccounts().get(1);
        assertEquals("00000000000000000000000000000001", microsoftAccount.get("profileID"));
        assertEquals("Steve", microsoftAccount.get("profileName"));
        assertNotNull(microsoftAccount.get("accountID"));
        assertFalse(microsoftAccount.containsKey("uuid"));
        assertFalse(microsoftAccount.containsKey("displayName"));

        Map<Object, Object> yggdrasilAccount = accountMetadata.getAccounts().get(2);
        assertEquals("steve@example.com", yggdrasilAccount.get("loginName"));
        assertEquals("00000000000000000000000000000002", yggdrasilAccount.get("profileID"));
        assertEquals("Steve", yggdrasilAccount.get("profileName"));
        assertNotNull(yggdrasilAccount.get("accountID"));
        assertFalse(yggdrasilAccount.containsKey("username"));
        assertFalse(yggdrasilAccount.containsKey("uuid"));
        assertFalse(yggdrasilAccount.containsKey("displayName"));

        AccountPrivateData.ExtractedPrivateData extracted =
                AccountPrivateData.extractFromAccountRecords(accountMetadata.getAccounts());
        assertEquals(3, extracted.accountIDs().size());
        AccountID microsoftAccountID = Objects.requireNonNull(Account.getAccountID(microsoftAccount));
        assertEquals(microsoftAccount.get("accountID"), microsoftAccountID.toString());
        assertEquals("access-token", extracted.privateData().get(microsoftAccountID).get("accessToken"));
    }

    /// Tests replacing duplicate account IDs across local and shared account metadata stores.
    @Test
    public void deduplicatesAccountIDsAcrossMetadataStores() {
        String duplicateAccountID = new AccountID(OfflineAccountFactory.getUUIDFromUserName("Alex")).toString();
        AccountMetadataStore localAccounts = AccountMetadataStore.fromRecords(List.of(Map.of(
                "type", "offline",
                "accountID", duplicateAccountID,
                "profileName", "Alex",
                "profileID", UUIDTypeAdapter.fromUUID(OfflineAccountFactory.getUUIDFromUserName("Alex"))
        )));
        AccountMetadataStore userAccounts = AccountMetadataStore.fromRecords(List.of(Map.of(
                "type", "offline",
                "accountID", duplicateAccountID,
                "profileName", "Alex",
                "profileID", UUIDTypeAdapter.fromUUID(OfflineAccountFactory.getUUIDFromUserName("Alex"))
        )));

        Set<String> usedAccountIDs = new HashSet<>();
        LegacyConfigMigrator.assignAccountIDs(localAccounts, usedAccountIDs, false);
        LegacyConfigMigrator.assignAccountIDs(userAccounts, usedAccountIDs, true);

        assertEquals(duplicateAccountID, localAccounts.getAccounts().get(0).get("accountID"));
        assertEquals(legacyAccountID(true, "Alex:Alex"), userAccounts.getAccounts().get(0).get("accountID"));
        assertDoesNotThrow(() -> AccountID.parse((String) userAccounts.getAccounts().get(0).get("accountID")));
    }

    /// Tests using the legacy global selected-account prefix for shared account migration IDs.
    @Test
    public void migratesLegacyUserAccountsWithGlobalSeed() {
        AccountMetadataStore accountMetadata = LegacyConfigMigrator.migrateLegacyAccountMetadataStore(List.of(
                Map.<Object, Object>of(
                        "type", "offline",
                        "username", "Alex")
        ), true);

        assertEquals(legacyAccountID(true, "Alex:Alex"),
                accountMetadata.getAccounts().get(0).get("accountID"));
    }

    /// Tests moving private fields from account metadata into the private data store.
    @Test
    public void extractsPrivateFieldsIntoPrivateData() {
        AccountPrivateData privateData = new AccountPrivateData();
        List<Map<Object, Object>> metadataAccounts = privateData.replaceFromAccountRecords(List.of(Map.of(
                "type", "microsoft",
                "accountID", accountID(1),
                "profileID", "00000000000000000000000000000001",
                "profileName", "Steve",
                "accessToken", "access-token",
                "refreshToken", "refresh-token",
                "tokenType", "Bearer",
                "notAfter", 1234L,
                "userid", "user-id"
        )));
        Map<Object, Object> metadata = metadataAccounts.get(0);
        AccountID accountID = Objects.requireNonNull(Account.getAccountID(metadata));

        assertEquals("microsoft", metadata.get("type"));
        assertEquals("00000000000000000000000000000001", metadata.get("profileID"));
        assertFalse(metadata.containsKey("profileName"));
        assertFalse(metadata.containsKey("id"));
        assertFalse(metadata.containsKey("accessToken"));
        assertFalse(metadata.containsKey("refreshToken"));
        assertFalse(metadata.containsKey("tokenType"));
        assertFalse(metadata.containsKey("notAfter"));
        assertFalse(metadata.containsKey("userid"));
        assertEquals("access-token", privateData.getPrivateData().get(accountID).get("accessToken"));
        assertEquals("refresh-token", privateData.getPrivateData().get(accountID).get("refreshToken"));
        assertEquals("Steve", privateData.getPrivateData().get(accountID).get("profileName"));
        assertEquals("Bearer", privateData.getPrivateData().get(accountID).get("tokenType"));
        assertEquals(1234L, privateData.getPrivateData().get(accountID).get("notAfter"));
        assertEquals("user-id", privateData.getPrivateData().get(accountID).get("userid"));
    }

    /// Tests moving Yggdrasil private fields from account metadata into the private data store.
    @Test
    public void extractsYggdrasilPrivateFieldsIntoPrivateData() {
        AccountPrivateData privateData = new AccountPrivateData();
        List<Map<Object, Object>> metadataAccounts = privateData.replaceFromAccountRecords(List.of(Map.of(
                "type", "yggdrasil",
                "accountID", accountID(1),
                "loginName", "steve@example.com",
                "profileID", "00000000000000000000000000000001",
                "profileName", "Steve",
                "accessToken", "access-token",
                "clientToken", "client-token",
                "userProperties", Map.of("preferredLanguage", "en_US")
        )));
        Map<Object, Object> metadata = metadataAccounts.get(0);
        AccountID accountID = Objects.requireNonNull(Account.getAccountID(metadata));

        assertFalse(metadata.containsKey("accessToken"));
        assertFalse(metadata.containsKey("clientToken"));
        assertFalse(metadata.containsKey("profileName"));
        assertFalse(metadata.containsKey("userProperties"));
        assertEquals("access-token", privateData.getPrivateData().get(accountID).get("accessToken"));
        assertEquals("client-token", privateData.getPrivateData().get(accountID).get("clientToken"));
        assertEquals("Steve", privateData.getPrivateData().get(accountID).get("profileName"));
        assertEquals(
                Map.of("preferredLanguage", "en_US"),
                privateData.getPrivateData().get(accountID).get("userProperties"));
    }

    /// Tests moving authlib-injector profile cache fields into the private data store.
    @Test
    public void extractsAuthlibInjectorProfilePropertiesIntoPrivateData() {
        AccountPrivateData privateData = new AccountPrivateData();
        List<Map<Object, Object>> metadataAccounts = privateData.replaceFromAccountRecords(List.of(Map.of(
                "type", "authlibInjector",
                "accountID", accountID(1),
                "serverBaseURL", "https://example.invalid/api/yggdrasil",
                "loginName", "steve@example.com",
                "profileID", "00000000000000000000000000000001",
                "profileName", "Steve",
                "profileProperties", Map.of("textures", "texture-data")
        )));
        Map<Object, Object> metadata = metadataAccounts.get(0);
        AccountID accountID = Objects.requireNonNull(Account.getAccountID(metadata));

        assertFalse(metadata.containsKey("profileProperties"));
        assertFalse(metadata.containsKey("profileName"));
        assertEquals(Map.of("textures", "texture-data"),
                privateData.getPrivateData().get(accountID).get("profileProperties"));
        assertEquals("Steve", privateData.getPrivateData().get(accountID).get("profileName"));
    }

    /// Tests loading online account sessions that must refresh because their profile name is missing.
    @Test
    public void loadsOnlineSessionsWithoutProfileNameAsRefreshRequired() {
        MicrosoftSession microsoftSession = MicrosoftSession.fromStorage(Map.of(
                "profileID", "00000000000000000000000000000001",
                "tokenType", "Bearer",
                "accessToken", "access-token",
                "refreshToken", "refresh-token",
                "userid", "user-id"
        ));
        assertFalse(microsoftSession.hasProfileName());

        YggdrasilSession yggdrasilSession = YggdrasilSession.fromStorage(Map.of(
                "profileID", "00000000000000000000000000000001",
                "accessToken", "access-token",
                "clientToken", "client-token"
        ));
        assertFalse(yggdrasilSession.hasProfileName());
    }

    /// Tests restoring private fields from the private data store into account metadata.
    @Test
    public void mergesPrivateDataIntoAccountMetadata() {
        AccountPrivateData privateData = new AccountPrivateData();
        List<Map<Object, Object>> metadataAccounts = privateData.replaceFromAccountRecords(List.of(Map.of(
                "type", "microsoft",
                "accountID", accountID(1),
                "profileID", "00000000000000000000000000000001",
                "profileName", "Steve",
                "accessToken", "access-token",
                "refreshToken", "refresh-token"
        )));
        AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(metadataAccounts);

        AccountPrivateData.mergeInto(accountMetadata, List.of(privateData));
        Map<Object, Object> account = accountMetadata.getAccounts().get(0);

        assertEquals("access-token", account.get("accessToken"));
        assertEquals("refresh-token", account.get("refreshToken"));
    }

    /// Tests restoring private fields from the first matching private data store.
    @Test
    public void mergesPrivateDataFromMultipleStoresInOrder() {
        AccountPrivateData preferredPrivateData = new AccountPrivateData();
        preferredPrivateData.replaceFromAccountRecords(List.of(Map.of(
                "type", "microsoft",
                "accountID", accountID(1),
                "profileID", "00000000000000000000000000000001",
                "profileName", "Steve",
                "accessToken", "preferred-token"
        )));

        AccountPrivateData fallbackPrivateData = new AccountPrivateData();
        List<Map<Object, Object>> metadataAccounts = fallbackPrivateData.replaceFromAccountRecords(List.of(Map.of(
                "type", "microsoft",
                "accountID", accountID(1),
                "profileID", "00000000000000000000000000000001",
                "profileName", "Steve",
                "accessToken", "fallback-token"
        )));

        AccountMetadataStore accountMetadata = AccountMetadataStore.fromRecords(metadataAccounts);
        AccountPrivateData.mergeInto(accountMetadata, List.of(new AccountPrivateData(), fallbackPrivateData));

        Map<Object, Object> account = accountMetadata.getAccounts().get(0);
        assertEquals("fallback-token", account.get("accessToken"));

        account.remove("accessToken");
        AccountPrivateData.mergeInto(accountMetadata, List.of(preferredPrivateData, fallbackPrivateData));

        assertEquals("preferred-token", account.get("accessToken"));
    }

    /// Tests that account private data serializes as one protected payload.
    @Test
    public void serializesPrivateDataAsProtectedPayload() {
        @Nullable String previous = System.getProperty(AccountPrivateData.PROTECTION_PROPERTY);
        System.clearProperty(AccountPrivateData.PROTECTION_PROPERTY);
        try {
            AccountPrivateData privateData = new AccountPrivateData();
            privateData.replaceFromAccountRecords(List.of(Map.of(
                    "type", "microsoft",
                    "accountID", accountID(1),
                    "profileID", "00000000000000000000000000000001",
                    "profileName", "Steve",
                    "accessToken", "access-token",
                    "refreshToken", "refresh-token"
            )));

            JsonObject serialized = JsonParser.parseString(
                    LauncherSettings.SETTINGS_GSON.toJson(privateData, AccountPrivateData.class)).getAsJsonObject();

            assertEquals(AccountPrivateData.CURRENT_SCHEMA.url(),
                    serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
            assertEquals("hmcl-obfuscated-v1", serialized.get("protection").getAsString());
            assertTrue(serialized.has("payload"));
            assertFalse(serialized.toString().contains("access-token"));
            assertFalse(serialized.toString().contains("refresh-token"));
        } finally {
            restoreSystemProperty(AccountPrivateData.PROTECTION_PROPERTY, previous);
        }
    }

    /// Tests that account private data can serialize as plain JSON payloads for development.
    @Test
    public void serializesPrivateDataAsPlainPayloadWhenEnabled() {
        @Nullable String previous = System.getProperty(AccountPrivateData.PROTECTION_PROPERTY);
        System.setProperty(AccountPrivateData.PROTECTION_PROPERTY, "plain");
        try {
            AccountPrivateData privateData = new AccountPrivateData();
            List<Map<Object, Object>> metadataAccounts = privateData.replaceFromAccountRecords(List.of(Map.of(
                    "type", "microsoft",
                    "accountID", accountID(1),
                    "profileID", "00000000000000000000000000000001",
                    "profileName", "Steve",
                    "accessToken", "access-token",
                    "refreshToken", "refresh-token"
            )));
            AccountID accountID = Objects.requireNonNull(Account.getAccountID(metadataAccounts.get(0)));

            JsonObject serialized = JsonParser.parseString(
                    LauncherSettings.SETTINGS_GSON.toJson(privateData, AccountPrivateData.class)).getAsJsonObject();
            JsonObject payload = serialized.getAsJsonObject("payload");
            JsonObject item = payload.getAsJsonArray("entries").get(0).getAsJsonObject();
            JsonObject entryPrivateData = item.getAsJsonObject("privateData");
            JsonElement serializedAccountID = item.get("accountID");

            assertEquals(AccountPrivateData.CURRENT_SCHEMA.url(),
                    serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
            assertEquals("plain", serialized.get("protection").getAsString());
            assertEquals(accountID.toString(), serializedAccountID.getAsString());
            assertEquals("access-token", entryPrivateData.get("accessToken").getAsString());
            assertEquals("refresh-token", entryPrivateData.get("refreshToken").getAsString());
            assertEquals("Steve", entryPrivateData.get("profileName").getAsString());

            AccountPrivateData deserialized = Objects.requireNonNull(
                    LauncherSettings.SETTINGS_GSON.fromJson(serialized, AccountPrivateData.class));

            assertEquals("access-token", deserialized.getPrivateData().get(accountID).get("accessToken"));
            assertEquals("refresh-token", deserialized.getPrivateData().get(accountID).get("refreshToken"));
        } finally {
            restoreSystemProperty(AccountPrivateData.PROTECTION_PROPERTY, previous);
        }
    }

    /// Tests reading account private data from one protected payload.
    @Test
    public void deserializesPrivateDataFromProtectedPayload() {
        @Nullable String previous = System.getProperty(AccountPrivateData.PROTECTION_PROPERTY);
        System.clearProperty(AccountPrivateData.PROTECTION_PROPERTY);
        try {
            AccountPrivateData privateData = new AccountPrivateData();
            List<Map<Object, Object>> metadataAccounts = privateData.replaceFromAccountRecords(List.of(Map.of(
                    "type", "microsoft",
                    "accountID", accountID(1),
                    "profileID", "00000000000000000000000000000001",
                    "profileName", "Steve",
                    "accessToken", "access-token",
                    "refreshToken", "refresh-token"
            )));
            AccountID accountID = Objects.requireNonNull(Account.getAccountID(metadataAccounts.get(0)));
            JsonObject serialized = JsonParser.parseString(
                    LauncherSettings.SETTINGS_GSON.toJson(privateData, AccountPrivateData.class)).getAsJsonObject();

            AccountPrivateData deserialized = Objects.requireNonNull(
                    LauncherSettings.SETTINGS_GSON.fromJson(serialized, AccountPrivateData.class));

            assertEquals("access-token", deserialized.getPrivateData().get(accountID).get("accessToken"));
            assertEquals("refresh-token", deserialized.getPrivateData().get(accountID).get("refreshToken"));
            assertEquals("Steve", deserialized.getPrivateData().get(accountID).get("profileName"));
        } finally {
            restoreSystemProperty(AccountPrivateData.PROTECTION_PROPERTY, previous);
        }
    }
}
