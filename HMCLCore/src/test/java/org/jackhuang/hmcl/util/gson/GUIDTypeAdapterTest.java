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
package org.jackhuang.hmcl.util.gson;

import com.github.f4b6a3.uuid.alt.GUID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for [GUIDTypeAdapter].
@NotNullByDefault
public final class GUIDTypeAdapterTest {
    /// Gson instance with only the GUID adapter under test registered.
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GUID.class, GUIDTypeAdapter.INSTANCE)
            .create();

    /// Tests that GUID wraps a UUID value without changing its bits.
    @Test
    public void wrapsUUID() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        GUID guid = new GUID(uuid);

        assertEquals(uuid, guid.toUUID());
        assertEquals(uuid.version(), guid.version());
        assertEquals(uuid.toString(), guid.toString());
        assertEquals(guid, new GUID(uuid.toString()));
    }

    /// Tests GUID serialization as a canonical UUID string.
    @Test
    public void serializesAsUUIDString() {
        GUID guid = new GUID("123e4567-e89b-12d3-a456-426614174000");

        assertEquals("\"123e4567-e89b-12d3-a456-426614174000\"", GSON.toJson(guid, GUID.class));
        assertEquals(guid, GSON.fromJson("\"123e4567-e89b-12d3-a456-426614174000\"", GUID.class));
        assertEquals(guid, GSON.fromJson("\"123e4567e89b12d3a456426614174000\"", GUID.class));
        assertNull(GSON.fromJson("null", GUID.class));
    }

    /// Tests that the shared Gson instance registers the GUID adapter.
    @Test
    public void sharedGsonRegistersAdapter() {
        GUID guid = new GUID("123e4567-e89b-12d3-a456-426614174000");

        assertEquals("\"123e4567-e89b-12d3-a456-426614174000\"", JsonUtils.GSON.toJson(guid, GUID.class));
        assertEquals(guid, JsonUtils.GSON.fromJson("\"123e4567-e89b-12d3-a456-426614174000\"", GUID.class));
    }
}
