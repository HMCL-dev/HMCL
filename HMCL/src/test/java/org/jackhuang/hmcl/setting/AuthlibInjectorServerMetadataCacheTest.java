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
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for authlib-injector server metadata cache persistence.
@NotNullByDefault
public final class AuthlibInjectorServerMetadataCacheTest {
    /// A normalized authlib-injector test server URL.
    private static final String SERVER_URL = "https://example.com/api/yggdrasil/";

    /// A representative authlib-injector metadata response.
    private static final String METADATA_RESPONSE = """
            {
              "meta": {
                "serverName": "Example",
                "links": {
                  "homepage": "https://example.com/"
                },
                "feature.non_email_login": true
              }
            }
            """;

    /// Another metadata response used to verify overwrite behavior.
    private static final String UPDATED_METADATA_RESPONSE = """
            {
              "meta": {
                "serverName": "Updated"
              }
            }
            """;

    /// Tests storing metadata from one server and restoring it into another server object.
    @Test
    public void storesAndRestoresMetadata() {
        AuthlibInjectorServer source = new AuthlibInjectorServer(SERVER_URL);
        source.restoreMetadataCache(METADATA_RESPONSE, 123L);
        AuthlibInjectorServerMetadataCache cache = new AuthlibInjectorServerMetadataCache();

        cache.store(source);
        AuthlibInjectorServer restored = new AuthlibInjectorServer(SERVER_URL);
        cache.restore(restored);

        assertEquals(METADATA_RESPONSE, restored.getMetadataResponse().orElseThrow());
        assertEquals(123L, restored.getMetadataTimestamp());
        assertEquals("Example", restored.getName());
        assertEquals("https://example.com/", restored.getLinks().get("homepage"));
        assertTrue(restored.isNonEmailLogin());
    }

    /// Tests the array JSON shape used by the metadata cache file.
    @Test
    public void serializesAsServerMetadataArray() {
        AuthlibInjectorServer source = new AuthlibInjectorServer(SERVER_URL);
        source.restoreMetadataCache(METADATA_RESPONSE, 123L);
        AuthlibInjectorServerMetadataCache cache = new AuthlibInjectorServerMetadataCache();
        cache.store(source);

        JsonObject serialized = JsonParser.parseString(
                LauncherSettings.SETTINGS_GSON.toJson(cache, AuthlibInjectorServerMetadataCache.class))
                .getAsJsonObject();
        JsonObject serializedServer = serialized.getAsJsonArray("servers").get(0).getAsJsonObject();

        assertEquals(AuthlibInjectorServerMetadataCache.CURRENT_SCHEMA.toString(),
                serialized.get(JsonSchema.PROPERTY_SCHEMA).getAsString());
        assertEquals(SERVER_URL, serializedServer.get("url").getAsString());
        assertEquals(METADATA_RESPONSE, serializedServer.get("metadataResponse").getAsString());
        assertEquals(123L, serializedServer.get("metadataTimestamp").getAsLong());

        AuthlibInjectorServerMetadataCache deserialized = Objects.requireNonNull(
                LauncherSettings.SETTINGS_GSON.fromJson(serialized, AuthlibInjectorServerMetadataCache.class));
        AuthlibInjectorServer restored = new AuthlibInjectorServer(SERVER_URL);
        deserialized.restore(restored);

        assertEquals("Example", restored.getName());
    }

    /// Tests that fresh metadata on a newly added server replaces stale cached metadata.
    @Test
    public void keepsFreshServerMetadataWhenInitializingNewServer() {
        AuthlibInjectorServerMetadataCache cache = new AuthlibInjectorServerMetadataCache();
        cache.getServers().add(new AuthlibInjectorServerMetadataCache.Entry(SERVER_URL, METADATA_RESPONSE, 123L));
        AuthlibInjectorServer server = new AuthlibInjectorServer(SERVER_URL);
        server.restoreMetadataCache(UPDATED_METADATA_RESPONSE, 456L);

        cache.initialize(server, true);
        AuthlibInjectorServer restored = new AuthlibInjectorServer(SERVER_URL);
        cache.restore(restored);

        assertEquals("Updated", server.getName());
        assertEquals(UPDATED_METADATA_RESPONSE, restored.getMetadataResponse().orElseThrow());
        assertEquals(456L, restored.getMetadataTimestamp());
        assertEquals("Updated", restored.getName());
    }

    /// Tests that malformed cache entries are dropped instead of being applied to servers.
    @Test
    public void dropsMalformedMetadataEntry() {
        AuthlibInjectorServerMetadataCache cache = new AuthlibInjectorServerMetadataCache();
        cache.getServers().add(new AuthlibInjectorServerMetadataCache.Entry(SERVER_URL, "{", 123L));

        cache.restore(new AuthlibInjectorServer(SERVER_URL));

        assertTrue(cache.getServers().stream().noneMatch(entry -> SERVER_URL.equals(entry.getUrl())));
    }
}
