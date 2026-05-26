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
package org.jackhuang.hmcl.util;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for GUID UUID storage and JSON serialization.
@NotNullByDefault
public final class GUIDTest {
    /// Tests that GUID stores the backing UUID and exposes its version.
    @Test
    public void storesUUID() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        GUID guid = GUID.fromUUID(uuid);

        assertSame(uuid, guid.uuid());
        assertEquals(uuid.version(), guid.version());
        assertEquals(uuid.toString(), guid.toString());
        assertEquals(guid, GUID.fromString(uuid.toString()));
    }

    /// Tests default Gson serialization as a 36-character UUID string.
    @Test
    public void serializesAsUUIDString() {
        Gson gson = new Gson();
        GUID guid = GUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertEquals("\"123e4567-e89b-12d3-a456-426614174000\"", gson.toJson(guid));
        assertEquals(guid, gson.fromJson("\"123e4567-e89b-12d3-a456-426614174000\"", GUID.class));
        assertNull(gson.fromJson("null", GUID.class));
    }
}
