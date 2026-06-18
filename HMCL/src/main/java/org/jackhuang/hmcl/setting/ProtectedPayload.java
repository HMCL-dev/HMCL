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
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/// Stores a JSON payload using HMCL's portable protection envelope.
///
/// This class is responsible for wrapping payloads with a protection marker and revealing them according to that
/// marker. Protection modes define whether and how a payload is transformed.
@NotNullByDefault
final class ProtectedPayload {

    /// The JSON member containing the protection marker.
    static final String PROPERTY_PROTECTION = "protection";

    /// The JSON member containing the envelope payload.
    static final String PROPERTY_PAYLOAD = "payload";

    /// Prevents instantiation.
    private ProtectedPayload() {
    }

    /// Selects how a protected payload is stored in its JSON envelope.
    @NotNullByDefault
    enum ProtectionMode {
        /// Stores the payload as plain JSON for development and diagnostics.
        PLAIN("plain") {
            /// Writes the payload into the given envelope.
            @Override
            protected void writePayload(JsonObject envelope, JsonElement payload) {
                envelope.addProperty(PROPERTY_PROTECTION, id());
                envelope.add(PROPERTY_PAYLOAD, payload);
            }

            /// Reads the payload from the given envelope.
            @Override
            protected JsonElement readPayload(JsonObject envelope) {
                JsonElement payload = envelope.get(PROPERTY_PAYLOAD);
                if (payload == null) {
                    throw new JsonParseException("Missing payload");
                }
                return payload;
            }
        },

        /// Stores the payload as a portable weakly obfuscated envelope.
        ///
        /// The payload is Base64-encoded, split into interleaved strings, and transformed with lane-specific character
        /// mappings. This avoids storing private data as directly readable JSON, but it should not be treated as
        /// device-bound secret storage.
        OBFUSCATED_V1("hmcl-obfuscated-v1") {
            /// The number of interleaved lanes used by the obfuscated payload.
            private static final int OBFUSCATED_LANE_COUNT = 4;

            /// The number of null placeholders stored before each lane.
            private static final int LANE_PADDING_COUNT = 63;

            /// The total number of elements stored in an obfuscated payload array.
            private static final int OBFUSCATED_PAYLOAD_SIZE =
                    OBFUSCATED_LANE_COUNT * (LANE_PADDING_COUNT + 1);

            /// The character alphabet produced by standard Base64 encoding.
            private static final String BASE64_ALPHABET =
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

            /// Lane-specific multipliers used by the fixed Base64 character mapping.
            private static final int @Unmodifiable [] LANE_MULTIPLIERS = {7, 11, 17, 19};

            /// Modular inverses of [LANE_MULTIPLIERS] modulo `BASE64_ALPHABET.length()`.
            private static final int @Unmodifiable [] LANE_INVERSE_MULTIPLIERS = {28, 6, 23, 24};

            /// Lane-specific offsets used by the fixed Base64 character mapping.
            private static final int @Unmodifiable [] LANE_OFFSETS = {13, 29, 41, 53};

            /// Returns the alphabet index of one Base64 character.
            ///
            /// @param character the Base64 character to inspect
            /// @return the alphabet index
            /// @throws JsonParseException if the character is outside the supported alphabet
            private static int base64AlphabetIndex(char character) {
                int index = BASE64_ALPHABET.indexOf(character);
                if (index < 0) {
                    throw new JsonParseException("Protected payload lane contains an unsupported character");
                }
                return index;
            }

            /// Encodes one Base64 character with its lane-specific fixed mapping.
            ///
            /// @param character the Base64 character to encode
            /// @param laneIndex the lane index
            /// @return the encoded character
            /// @throws JsonParseException if the character is outside the supported alphabet
            private static char encodeCharacter(char character, int laneIndex) {
                int alphabetLength = BASE64_ALPHABET.length();
                int transformedIndex =
                        (base64AlphabetIndex(character) * LANE_MULTIPLIERS[laneIndex] + LANE_OFFSETS[laneIndex])
                                % alphabetLength;
                return BASE64_ALPHABET.charAt(transformedIndex);
            }

            /// Decodes one Base64 character with its lane-specific inverse mapping.
            ///
            /// @param character the encoded character to decode
            /// @param laneIndex the lane index
            /// @return the decoded Base64 character
            /// @throws JsonParseException if the character is outside the supported alphabet
            private static char decodeCharacter(char character, int laneIndex) {
                int alphabetLength = BASE64_ALPHABET.length();
                int transformedIndex = Math.floorMod(
                        (base64AlphabetIndex(character) - LANE_OFFSETS[laneIndex])
                                * LANE_INVERSE_MULTIPLIERS[laneIndex],
                        alphabetLength);
                return BASE64_ALPHABET.charAt(transformedIndex);
            }

            /// Returns the payload array index storing one lane.
            ///
            /// @param laneIndex the lane index
            /// @return the payload array index
            private static int lanePayloadIndex(int laneIndex) {
                return laneIndex * (LANE_PADDING_COUNT + 1) + LANE_PADDING_COUNT;
            }

            /// Checks that one payload array index contains a null placeholder.
            ///
            /// @param payload the payload array
            /// @param index the payload array index
            /// @throws JsonParseException if the element is not a null placeholder
            private static void requireNullPlaceholder(JsonArray payload, int index) {
                if (!payload.get(index).isJsonNull()) {
                    throw new JsonParseException("Protected payload placeholder is not null");
                }
            }

            /// Splits a Base64 payload into interleaved lanes.
            ///
            /// @param payload the Base64 payload to split
            /// @return the interleaved payload lanes
            private static JsonArray splitObfuscatedPayload(String payload) {
                StringBuilder[] lanes = new StringBuilder[OBFUSCATED_LANE_COUNT];
                for (int i = 0; i < lanes.length; i++) {
                    lanes[i] = new StringBuilder((payload.length() + OBFUSCATED_LANE_COUNT - 1) / OBFUSCATED_LANE_COUNT);
                }

                for (int i = 0; i < payload.length(); i++) {
                    int laneIndex = i % OBFUSCATED_LANE_COUNT;
                    lanes[laneIndex].append(encodeCharacter(payload.charAt(i), laneIndex));
                }

                JsonArray result = new JsonArray();
                for (StringBuilder lane : lanes) {
                    for (int i = 0; i < LANE_PADDING_COUNT; i++) {
                        result.add(JsonNull.INSTANCE);
                    }
                    result.add(lane.toString());
                }
                return result;
            }

            /// Joins interleaved Base64 payload lanes from the envelope.
            ///
            /// @param envelope the envelope object to read from
            /// @return the restored Base64 payload
            /// @throws JsonParseException if the payload lanes are missing or malformed
            private static String joinObfuscatedPayload(JsonObject envelope) {
                if (!(envelope.get(PROPERTY_PAYLOAD) instanceof JsonArray lanes)
                        || lanes.size() != OBFUSCATED_PAYLOAD_SIZE) {
                    throw new JsonParseException("Missing payload or payload is not a padded 4-lane array");
                }

                String[] laneTexts = new String[OBFUSCATED_LANE_COUNT];
                int totalLength = 0;
                for (int i = 0; i < OBFUSCATED_LANE_COUNT; i++) {
                    int payloadIndex = lanePayloadIndex(i);
                    for (int placeholderIndex = payloadIndex - LANE_PADDING_COUNT;
                         placeholderIndex < payloadIndex;
                         placeholderIndex++) {
                        requireNullPlaceholder(lanes, placeholderIndex);
                    }

                    JsonElement lane = lanes.get(payloadIndex);
                    if (!lane.isJsonPrimitive() || !lane.getAsJsonPrimitive().isString()) {
                        throw new JsonParseException("Protected payload lane is not a string");
                    }

                    laneTexts[i] = lane.getAsString();
                    totalLength += laneTexts[i].length();
                    if (i > 0 && laneTexts[i].length() != laneTexts[0].length()) {
                        throw new JsonParseException("Protected payload lanes are malformed");
                    }
                }

                StringBuilder result = new StringBuilder(totalLength);
                for (int position = 0; result.length() < totalLength; position++) {
                    for (int laneIndex = 0; laneIndex < laneTexts.length; laneIndex++) {
                        String lane = laneTexts[laneIndex];
                        if (position < lane.length()) {
                            result.append(decodeCharacter(lane.charAt(position), laneIndex));
                        }
                    }
                }
                return result.toString();
            }

            /// Writes the payload into the given envelope.
            @Override
            protected void writePayload(JsonObject envelope, JsonElement payload) {
                String payloadText = JsonUtils.UGLY_GSON.toJson(payload);
                byte[] payloadBytes = payloadText.getBytes(StandardCharsets.UTF_8);
                String actualPayload = Base64.getEncoder().encodeToString(payloadBytes);

                envelope.addProperty(PROPERTY_PROTECTION, id());
                envelope.add(PROPERTY_PAYLOAD, splitObfuscatedPayload(actualPayload));
            }

            /// Reads the payload from the given envelope.
            @Override
            protected JsonElement readPayload(JsonObject envelope) {
                try {
                    String encodedPayload = joinObfuscatedPayload(envelope);
                    byte[] payloadBytes = Base64.getDecoder().decode(encodedPayload);
                    return JsonParser.parseString(new String(payloadBytes, StandardCharsets.UTF_8));
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException("Failed to reveal protected JSON payload", e);
                }
            }
        };

        /// The serialized protection marker.
        private final String id;

        /// Creates a protection mode.
        ///
        /// @param id the serialized protection marker
        ProtectionMode(String id) {
            this.id = id;
        }

        /// Returns the serialized protection marker.
        ///
        /// @return the serialized protection marker
        String id() {
            return id;
        }

        /// Writes the payload into the given envelope.
        ///
        /// @param envelope the envelope object to write into
        /// @param payload  the plain JSON payload
        /// @param <T> the JSON payload type
        /// @throws JsonParseException if the payload cannot be protected
        public final <T extends JsonElement> void write(JsonObject envelope, T payload) {
            writePayload(envelope, Objects.requireNonNull(payload));
        }

        /// Writes the payload into the given envelope.
        ///
        /// @param envelope the envelope object to write into
        /// @param payload the plain JSON payload
        /// @throws JsonParseException if the payload cannot be protected
        protected abstract void writePayload(JsonObject envelope, JsonElement payload);

        /// Reads the payload from the given envelope.
        ///
        /// @param envelope the envelope object to read from
        /// @return the revealed JSON payload
        /// @throws JsonParseException if the envelope is malformed or cannot be revealed
        protected abstract JsonElement readPayload(JsonObject envelope);

        /// Reads the payload from the given envelope and checks its JSON element type.
        ///
        /// @param envelope the envelope object to read from
        /// @param payloadType the expected JSON element type
        /// @return the revealed JSON payload
        /// @param <T> the expected JSON element type
        /// @throws JsonParseException if the envelope is malformed, cannot be revealed, or has the wrong payload type
        public final <T extends JsonElement> T read(JsonObject envelope, Class<T> payloadType) {
            Objects.requireNonNull(payloadType);
            JsonElement payload = readPayload(envelope);
            if (!payloadType.isInstance(payload)) {
                throw new JsonParseException("Protected payload is not a " + payloadType.getSimpleName());
            }
            return payloadType.cast(payload);
        }

        /// Returns the write mode selected by a configuration value.
        ///
        /// Unknown values intentionally fall back to obfuscation so opt-in plain storage cannot be enabled by typos.
        ///
        /// @param id the configured protection marker
        /// @return the selected write mode
        static ProtectionMode fromConfiguredId(@Nullable String id) {
            for (ProtectionMode mode : values()) {
                if (mode.id.equals(id)) {
                    return mode;
                }
            }
            return OBFUSCATED_V1;
        }

        /// Reads the protection mode from an envelope.
        ///
        /// @param envelope the envelope object to inspect
        /// @return the protection mode declared by the envelope
        /// @throws JsonParseException if the declared protection mode is unsupported
        static ProtectionMode fromEnvelope(JsonObject envelope) {
            String protection = JsonUtils.getString(envelope, PROPERTY_PROTECTION);
            if (protection == null) {
                throw new JsonParseException("Missing protected payload member: protection");
            }

            for (ProtectionMode mode : values()) {
                if (mode.id.equals(protection)) {
                    return mode;
                }
            }
            throw new JsonParseException("Unsupported protected payload: " + protection);
        }
    }

    /// Reads and reveals a protected JSON payload from an envelope object.
    ///
    /// @param envelope the envelope object to read from
    /// @param payloadType the expected JSON element type
    /// @return the revealed JSON payload
    /// @param <T> the expected JSON element type
    /// @throws JsonParseException if the envelope is malformed or cannot be revealed
    static <T extends JsonElement> T read(JsonObject envelope, Class<T> payloadType) throws JsonParseException {
        return ProtectionMode.fromEnvelope(envelope).read(envelope, payloadType);
    }

}
