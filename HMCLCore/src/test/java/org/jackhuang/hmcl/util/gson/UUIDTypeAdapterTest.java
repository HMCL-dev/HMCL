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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests UUID JSON adapters.
@NotNullByDefault
public final class UUIDTypeAdapterTest {
    /// Tests that the shared Gson writes ordinary UUID values as standard UUID strings.
    @Test
    public void jsonUtilsWritesStandardUUID() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertEquals("\"123e4567-e89b-12d3-a456-426614174000\"", JsonUtils.GSON.toJson(uuid, UUID.class));
        assertEquals(uuid, JsonUtils.GSON.fromJson("\"123e4567e89b12d3a456426614174000\"", UUID.class));
    }

    /// Tests that Yggdrasil profile DTOs keep Mojang/Yggdrasil UUID strings unhyphenated.
    @Test
    public void yggdrasilProfilesUseUnhyphenatedUUID() {
        Gson protocolGson = new GsonBuilder()
                .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE)
                .create();
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        JsonObject serialized = protocolGson.toJsonTree(new GameProfile(uuid, "Steve"), GameProfile.class).getAsJsonObject();
        assertEquals("123e4567e89b12d3a456426614174000", serialized.get("id").getAsString());

        GameProfile profile = protocolGson.fromJson(
                "{\"id\":\"123e4567e89b12d3a456426614174000\",\"name\":\"Steve\"}",
                GameProfile.class);
        assertEquals(uuid, profile.getId());

        CompleteGameProfile completeProfile = protocolGson.fromJson(
                "{\"id\":\"123e4567e89b12d3a456426614174000\",\"name\":\"Steve\",\"properties\":[]}",
                CompleteGameProfile.class);
        assertEquals(uuid, completeProfile.getId());
    }
}
