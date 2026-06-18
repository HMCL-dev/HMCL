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
                joinPayloadLanes(lanes),
                joinPayloadLanes(secondEnvelope.getAsJsonArray(ProtectedPayload.PROPERTY_PAYLOAD)));
    }

    /// Tests that obfuscated payload encoding and decoding stay stable for fixed data.
    @Test
    public void preservesObfuscatedPayloadFormat() {
        assertDecodesStableObfuscatedPayloadFormat(
                "[{\"name\":\"alpha\",\"value\":1},{\"name\":\"beta\",\"enabled\":true,\"items\":[\"x\",\"y\"]}]",
                "AAECAwQFBgcICQoLb7DMUDzzpXJNPWg7Ngl",
                "VWmXCtP39wCuWthXBv7OtEp40MHQ0FbaWEl",
                "WpXmEAnTz5CibOv6O6Wer8DrI4jXzuy9MTC",
                "Y4Dm7qLSib6Rx6FAgv9HDTFy3cLIu+WcmKs");
        assertDecodesStableObfuscatedPayloadFormat(
                "{\"accessToken\":\"abc123+/=\",\"refreshToken\":\"refresh-456\",\"selected\":true,\"version\":2}",
                "AAECAwQFBgcICQoLT+mPXT77syMjcGIyKEMOWi",
                "iCoa2jhmWbsQaQsbrqGo08Jj5aWL+WCBaxUDEA",
                "lS/+FSuHqaz4CbO7UfU9nHr33ZULaNgJzPOFEX",
                "LCSDC1BrUO6GvMlafz4hpWVjifRoOwY9SpYw==");
        assertDecodesStableObfuscatedPayloadFormat(
                "\"plain-secret-token\"",
                "AAECAwQFBgcICQoL",
                "FruCXzTw7SMSfHsy",
                "MkxAFyKFrL4hYh6O",
                "yLITJti5GVewJKau");
        assertDecodesStableObfuscatedPayloadFormat(
                "[]",
                "AAECAwQFBg",
                "cICQoLb5bz",
                "59UboWWpnr",
                "Oc/SK1tHGt");
        assertDecodesStableObfuscatedPayloadFormat(
                "{\"empty\":{},\"list\":[1,2,3],\"flag\":false}",
                "AAECAwQFBgcICQoLT+mLUy3",
                "quXJNZHR7ZA1dCz3C+MegmX",
                "yYv3mQsa7jHZh7bzBvW6eWG",
                "/tzD9z9PN9vZxp8HQ1de2M=");
    }

    /// Tests that tampered encrypted payloads are rejected.
    @Test
    public void rejectsTamperedObfuscatedPayload() {
        JsonObject envelope = compactObfuscatedEnvelope(
                "AAECAwQFBg",
                "cICQoLb5bz",
                "59UboWWpnr",
                "Oc/SK1tHG9");

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
