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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for protected JSON payload envelopes.
@NotNullByDefault
public final class ProtectedPayloadTest {
    /// Joins interleaved payload lanes without reversing lane-specific character mappings.
    ///
    /// @param lanes the padded transformed payload lanes
    /// @return the direct interleaving of the lane strings
    private static String joinTransformedLanes(JsonArray lanes) {
        int laneCount = 4;
        int lanePaddingCount = 63;
        int laneStride = lanePaddingCount + 1;
        String[] laneTexts = new String[laneCount];
        int totalLength = 0;
        for (int i = 0; i < laneCount; i++) {
            laneTexts[i] = lanes.get(i * laneStride + lanePaddingCount).getAsString();
            totalLength += laneTexts[i].length();
        }

        StringBuilder result = new StringBuilder(totalLength);
        for (int position = 0; result.length() < totalLength; position++) {
            for (String laneText : laneTexts) {
                if (position < laneText.length()) {
                    result.append(laneText.charAt(position));
                }
            }
        }
        return result.toString();
    }

    /// Tests that plain envelopes can store non-object JSON payloads.
    @Test
    public void storesPlainPrimitivePayload() {
        JsonObject envelope = new JsonObject();

        ProtectedPayload.ProtectionMode.PLAIN.write(envelope, new JsonPrimitive("payload"));
        JsonPrimitive payload = ProtectedPayload.read(envelope, JsonPrimitive.class);

        assertEquals("payload", payload.getAsString());
    }

    /// Tests that obfuscated envelopes can store array JSON payloads.
    @Test
    public void storesObfuscatedArrayPayload() {
        JsonArray entries = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("name", "value");
        entries.add(entry);
        JsonObject envelope = new JsonObject();

        ProtectedPayload.ProtectionMode.OBFUSCATED_V1.write(envelope, entries);
        JsonArray lanes = envelope.getAsJsonArray(ProtectedPayload.PROPERTY_PAYLOAD);
        JsonArray payload = ProtectedPayload.read(envelope, JsonArray.class);

        assertEquals(256, lanes.size());
        for (int i = 0; i < lanes.size(); i++) {
            JsonElement lane = lanes.get(i);
            if ((i + 1) % 64 == 0) {
                assertTrue(lane.isJsonPrimitive());
                assertTrue(lane.getAsJsonPrimitive().isString());
            } else {
                assertTrue(lane.isJsonNull());
            }
        }
        String directBase64 = Base64.getEncoder()
                .encodeToString(JsonUtils.UGLY_GSON.toJson(entries).getBytes(StandardCharsets.UTF_8));
        assertNotEquals(directBase64, joinTransformedLanes(lanes));
        assertEquals("value", payload.get(0).getAsJsonObject().get("name").getAsString());
    }

    /// Tests that typed reads reject mismatched JSON payload types.
    @Test
    public void rejectsMismatchedPayloadType() {
        JsonObject envelope = new JsonObject();

        ProtectedPayload.ProtectionMode.PLAIN.write(envelope, new JsonPrimitive("payload"));

        assertThrows(JsonParseException.class, () -> ProtectedPayload.read(envelope, JsonArray.class));
    }
}
