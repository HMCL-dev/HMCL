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
import com.google.gson.JsonParser;
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

    /// Asserts that one fixed JSON payload keeps the same obfuscated lane output and can be decoded from it.
    ///
    /// @param payloadJson the fixed compact JSON payload
    /// @param lane0 the first transformed lane
    /// @param lane1 the second transformed lane
    /// @param lane2 the third transformed lane
    /// @param lane3 the fourth transformed lane
    private static void assertStableObfuscatedPayloadFormat(
            String payloadJson, String lane0, String lane1, String lane2, String lane3) {
        JsonElement fixedPayload = JsonParser.parseString(payloadJson);
        JsonObject envelope = new JsonObject();

        ProtectedPayload.ProtectionMode.OBFUSCATED_V1.write(envelope, fixedPayload);
        JsonArray lanes = envelope.getAsJsonArray(ProtectedPayload.PROPERTY_PAYLOAD);

        assertEquals(lane0, lanes.get(63).getAsString());
        assertEquals(lane1, lanes.get(127).getAsString());
        assertEquals(lane2, lanes.get(191).getAsString());
        assertEquals(lane3, lanes.get(255).getAsString());

        JsonObject fixedEnvelope = new JsonObject();
        fixedEnvelope.addProperty(
                ProtectedPayload.PROPERTY_PROTECTION,
                ProtectedPayload.ProtectionMode.OBFUSCATED_V1.id());
        JsonArray compactLanes = new JsonArray(4);
        compactLanes.add(lane0);
        compactLanes.add(lane1);
        compactLanes.add(lane2);
        compactLanes.add(lane3);
        fixedEnvelope.add(ProtectedPayload.PROPERTY_PAYLOAD, compactLanes);

        JsonElement decodedPayload = ProtectedPayload.read(fixedEnvelope, JsonElement.class);
        assertEquals(payloadJson, JsonUtils.UGLY_GSON.toJson(decodedPayload));
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

        assertEquals(256, lanes.size());
        for (int i = 0; i < lanes.size(); i++) {
            JsonElement lane = lanes.get(i);
            if ((i + 1) % 64 == 0) {
                assertTrue(lane.isJsonPrimitive());
                assertTrue(lane.getAsJsonPrimitive().isString());
            }
        }
        String directBase64 = Base64.getEncoder()
                .encodeToString(JsonUtils.UGLY_GSON.toJson(entries).getBytes(StandardCharsets.UTF_8));
        assertNotEquals(directBase64, joinTransformedLanes(lanes));

        lanes.set(0, new JsonPrimitive("ignored"));
        JsonObject ignoredPadding = new JsonObject();
        ignoredPadding.addProperty("ignored", true);
        lanes.set(1, ignoredPadding);

        JsonArray payload = ProtectedPayload.read(envelope, JsonArray.class);
        assertEquals("value", payload.get(0).getAsJsonObject().get("name").getAsString());

        JsonArray compactLanes = new JsonArray();
        for (int i = 0; i < 4; i++) {
            compactLanes.add(lanes.get((i + 1) * 64 - 1));
        }
        envelope.add(ProtectedPayload.PROPERTY_PAYLOAD, compactLanes);
        payload = ProtectedPayload.read(envelope, JsonArray.class);
        assertEquals("value", payload.get(0).getAsJsonObject().get("name").getAsString());

        compactLanes.add(new JsonPrimitive("ignored trailing payload"));
        payload = ProtectedPayload.read(envelope, JsonArray.class);
        assertEquals("value", payload.get(0).getAsJsonObject().get("name").getAsString());
    }

    /// Tests that obfuscated payload encoding and decoding stay stable for fixed data.
    @Test
    public void preservesObfuscatedPayloadFormat() {
        assertStableObfuscatedPayloadFormat(
                "[{\"name\":\"alpha\",\"value\":1},{\"name\":\"beta\",\"enabled\":true,\"items\":[\"x\",\"y\"]}]",
                "lH6EOEVVuZH6EVZHHEOZVOlEcj",
                "x5g5eN5LYp5g5ez5eYDze77NgA",
                "J9v9RM949J9v=s=9IXI=Iv=M=P",
                "x/ysexsxqx/ypxpxWBp0/yMxTi");
        assertStableObfuscatedPayloadFormat(
                "{\"accessToken\":\"abc123+/=\",\"refreshToken\":\"refresh-456\",\"selected\":true,\"version\":2}",
                "czOHHEzg1E6OHHE6OnEO66uVEOHu",
                "7mxmN5F7gDDmmNDDm+NmLLDLDDmY",
                "=CCav9sJv==Rav==A4MIC1F4LCS=",
                "eprpyxckspprpypp/nxsBxcsp0xq");
        assertStableObfuscatedPayloadFormat(
                "\"plain-secret-token\"",
                "EzZzVHH",
                "DLWxzmN",
                "6UC=gav",
                "sRppBpi");
        assertStableObfuscatedPayloadFormat(
                "[]",
                "l",
                "b",
                "P",
                "i");
        assertStableObfuscatedPayloadFormat(
                "{\"empty\":{},\"list\":[1,2,3],\"flag\":false}",
                "cHccEOuZgEzuHj",
                "7Wgx5xu+b5L5pK",
                "=6vPd1JvPL+LCY",
                "pBys0xJsssxepi");
    }

    /// Tests that typed reads reject mismatched JSON payload types.
    @Test
    public void rejectsMismatchedPayloadType() {
        JsonObject envelope = new JsonObject();

        ProtectedPayload.ProtectionMode.PLAIN.write(envelope, new JsonPrimitive("payload"));

        assertThrows(JsonParseException.class, () -> ProtectedPayload.read(envelope, JsonArray.class));
    }
}
