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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached account storage lists.
@NotNullByDefault
public final class AccountStoragesTest {
    /// Tests that account storages serialize as an object containing an accounts list.
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
                serialized.get(JsonSchema.DEFAULT_MEMBER_NAME).getAsString());
        assertTrue(serialized.has("accounts"));
        assertEquals(1, serialized.getAsJsonArray("accounts").size());
        assertEquals("offline", serialized.getAsJsonArray("accounts")
                .get(0)
                .getAsJsonObject()
                .get("type")
                .getAsString());
    }

    /// Tests wrapping legacy account list content in the current `game-accounts.json` model.
    @Test
    public void wrapsLegacyAccountListInCurrentModel() {
        AccountStorages storages = AccountStorages.fromAccounts(List.of(
                Map.of("type", "offline", "username", "Steve")
        ));

        JsonObject serialized = JsonParser.parseString(
                JsonUtils.GSON.toJson(storages, AccountStorages.class)).getAsJsonObject();

        assertEquals(AccountStorages.CURRENT_SCHEMA.url(), serialized.get(JsonSchema.DEFAULT_MEMBER_NAME).getAsString());
        JsonArray accounts = serialized.getAsJsonArray("accounts");
        assertEquals(1, accounts.size());
        assertEquals("offline", accounts.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("Steve", accounts.get(0).getAsJsonObject().get("username").getAsString());
    }

    /// Tests extracting account storages from a main config object.
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
}
