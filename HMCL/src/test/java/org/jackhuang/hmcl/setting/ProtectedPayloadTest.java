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
    /// Stable Base64 nonce used by fixed encrypted payload vectors.
    private static final String FIXED_NONCE = "AAECAwQFBgcICQoL";

    /// Joins padded payload lanes in storage order.
    ///
    /// @param lanes the padded payload lanes
    /// @return the direct concatenation of the lane strings
    private static String joinPayloadLanes(JsonArray lanes) {
        int laneCount = 4;
        int lanePaddingCount = 63;
        int laneStride = lanePaddingCount + 1;
        int totalLength = 0;
        for (int i = 0; i < laneCount; i++) {
            totalLength += lanes.get(i * laneStride + lanePaddingCount).getAsString().length();
        }

        StringBuilder result = new StringBuilder(totalLength);
        for (int i = 0; i < laneCount; i++) {
            result.append(lanes.get(i * laneStride + lanePaddingCount).getAsString());
        }
        return result.toString();
    }

    /// Creates a compact obfuscated envelope from four payload lanes.
    ///
    /// @param lane0 the first encrypted lane
    /// @param lane1 the second encrypted lane
    /// @param lane2 the third encrypted lane
    /// @param lane3 the fourth encrypted lane
    /// @return the compact obfuscated envelope
    private static JsonObject compactObfuscatedEnvelope(String lane0, String lane1, String lane2, String lane3) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty(
                ProtectedPayload.PROPERTY_PROTECTION,
                ProtectedPayload.ProtectionMode.OBFUSCATED_V1.id());
        envelope.addProperty(ProtectedPayload.PROPERTY_NONCE, FIXED_NONCE);
        JsonArray lanes = new JsonArray(4);
        lanes.add(lane0);
        lanes.add(lane1);
        lanes.add(lane2);
        lanes.add(lane3);
        envelope.add(ProtectedPayload.PROPERTY_PAYLOAD, lanes);
        return envelope;
    }

    /// Asserts that one fixed encrypted payload can be decoded.
    ///
    /// @param payloadJson the fixed compact JSON payload
    /// @param lane0 the first encrypted lane
    /// @param lane1 the second encrypted lane
    /// @param lane2 the third encrypted lane
    /// @param lane3 the fourth encrypted lane
    private static void assertDecodesStableObfuscatedPayloadFormat(
            String payloadJson, String lane0, String lane1, String lane2, String lane3) {
        JsonElement decodedPayload = ProtectedPayload.read(
                compactObfuscatedEnvelope(lane0, lane1, lane2, lane3),
                JsonElement.class);
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

        assertTrue(envelope.get(ProtectedPayload.PROPERTY_NONCE).isJsonPrimitive());
        assertTrue(envelope.get(ProtectedPayload.PROPERTY_NONCE).getAsJsonPrimitive().isString());
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
        assertNotEquals(directBase64, joinPayloadLanes(lanes));

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

        JsonObject secondEnvelope = new JsonObject();
        ProtectedPayload.ProtectionMode.OBFUSCATED_V1.write(secondEnvelope, entries);
        assertNotEquals(
                envelope.get(ProtectedPayload.PROPERTY_NONCE).getAsString(),
                secondEnvelope.get(ProtectedPayload.PROPERTY_NONCE).getAsString());
        assertNotEquals(
                joinPayloadLanes(lanes),
                joinPayloadLanes(secondEnvelope.getAsJsonArray(ProtectedPayload.PROPERTY_PAYLOAD)));
    }

    /// Tests that obfuscated payload encoding and decoding stay stable for fixed data.
    @Test
    public void preservesObfuscatedPayloadFormat() {
        assertDecodesStableObfuscatedPayloadFormat(
                "[{\"name\":\"alpha\",\"value\":1},{\"name\":\"beta\",\"enabled\":true,\"items\":[\"x\",\"y\"]}]",
                "b7DMUDzzpXJNPWg7NglVWmXCtP39wCu",
                "WthXBv7OtEp40MHQ0FbaWElWpXmEAnT",
                "z5CibOv6O6Wer8DrI4jXzuy9MTCY4Dm",
                "7qLSib6Rx6FAgv9HDTFy3cLIu+WcmKs");
        assertDecodesStableObfuscatedPayloadFormat(
                "{\"accessToken\":\"abc123+/=\",\"refreshToken\":\"refresh-456\",\"selected\":true,\"version\":2}",
                "T+mPXT77syMjcGIyKEMOWiiCoa2jhmWbsQ",
                "aQsbrqGo08Jj5aWL+WCBaxUDEAlS/+FSuH",
                "qaz4CbO7UfU9nHr33ZULaNgJzPOFEXLCSD",
                "C1BrUO6GvMlafz4hpWVjifRoOwY9SpYw==");
        assertDecodesStableObfuscatedPayloadFormat(
                "\"plain-secret-token\"",
                "FruCXzTw7SMS",
                "fHsyMkxAFyKF",
                "rL4hYh6OyLIT",
                "Jti5GVewJKau");
        assertDecodesStableObfuscatedPayloadFormat(
                "[]",
                "b5bz59",
                "UboWWp",
                "nrOc/S",
                "K1tHGt");
        assertDecodesStableObfuscatedPayloadFormat(
                "{\"empty\":{},\"list\":[1,2,3],\"flag\":false}",
                "T+mLUy3quXJNZHR7ZA1",
                "dCz3C+MegmXyYv3mQsa",
                "7jHZh7bzBvW6eWG/tzD",
                "9z9PN9vZxp8HQ1de2M=");
    }

    /// Tests that tampered encrypted payloads are rejected.
    @Test
    public void rejectsTamperedObfuscatedPayload() {
        JsonObject envelope = compactObfuscatedEnvelope(
                "b5bz59",
                "UboWWp",
                "nrOc/S",
                "K1tHG9");

        assertThrows(JsonParseException.class, () -> ProtectedPayload.read(envelope, JsonArray.class));
    }

    /// Tests that typed reads reject mismatched JSON payload types.
    @Test
    public void rejectsMismatchedPayloadType() {
        JsonObject envelope = new JsonObject();

        ProtectedPayload.ProtectionMode.PLAIN.write(envelope, new JsonPrimitive("payload"));

        assertThrows(JsonParseException.class, () -> ProtectedPayload.read(envelope, JsonArray.class));
    }
}
