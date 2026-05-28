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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for detached authlib-injector server lists.
@NotNullByDefault
public final class AuthlibInjectorServerListTest {
    /// Tests that newly created server lists contain LittleSkin by default.
    @Test
    public void defaultListContainsLittleSkin() {
        AuthlibInjectorServerList list = AuthlibInjectorServerList.createDefault();

        assertEquals(1, list.getServers().size());
        assertEquals(AuthlibInjectorServerList.LITTLE_SKIN_URL, list.getServers().get(0).getUrl());
    }

    /// Tests extracting authlib-injector servers from an upstream/main config object.
    @Test
    public void extractsAuthlibInjectorServersFromLegacyConfigJson() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "authlibInjectorServers": [
                    {
                      "url": "https://example.com/api/yggdrasil/"
                    }
                  ],
                  "addedLittleSkin": true,
                  "localization": "en"
                }
                """).getAsJsonObject();

        AuthlibInjectorServerList list = LegacyConfigMigrator.extractAuthlibInjectorServers(settings);

        assertFalse(settings.has("authlibInjectorServers"));
        assertFalse(settings.has("addedLittleSkin"));
        assertTrue(settings.has("localization"));

        assertEquals(1, list.getServers().size());
        assertEquals("https://example.com/api/yggdrasil/", list.getServers().get(0).getUrl());
        assertEquals(AuthlibInjectorServerList.CURRENT_SCHEMA, list.getSchema());
    }

    /// Tests that legacy configs without `addedLittleSkin` receive LittleSkin during migration.
    @Test
    public void addsLittleSkinWhenLegacyFlagIsMissing() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "authlibInjectorServers": [
                    {
                      "url": "https://example.com/api/yggdrasil/"
                    }
                  ]
                }
                """).getAsJsonObject();

        AuthlibInjectorServerList list = LegacyConfigMigrator.extractAuthlibInjectorServers(settings);

        assertEquals(2, list.getServers().size());
        assertEquals("https://example.com/api/yggdrasil/", list.getServers().get(0).getUrl());
        assertEquals(AuthlibInjectorServerList.LITTLE_SKIN_URL, list.getServers().get(1).getUrl());
    }

    /// Tests that legacy configs with `addedLittleSkin=false` do not add a duplicate LittleSkin entry.
    @Test
    public void addsLittleSkinOnlyOnceWhenLegacyFlagIsFalse() {
        JsonObject settings = JsonParser.parseString("""
                {
                  "authlibInjectorServers": [
                    {
                      "url": "https://littleskin.cn/api/yggdrasil/"
                    }
                  ],
                  "addedLittleSkin": false
                }
                """).getAsJsonObject();

        AuthlibInjectorServerList list = LegacyConfigMigrator.extractAuthlibInjectorServers(settings);

        assertEquals(1, list.getServers().size());
        assertEquals(AuthlibInjectorServerList.LITTLE_SKIN_URL, list.getServers().get(0).getUrl());
    }
}
