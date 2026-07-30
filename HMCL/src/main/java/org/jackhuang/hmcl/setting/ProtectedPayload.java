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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/// Stores a JSON payload using HMCL's portable protection envelope.
///
/// This class is responsible for wrapping payloads with a protection marker and revealing them according to that
/// marker. Protection modes define whether and how a payload is stored.
@NotNullByDefault
final class ProtectedPayload {

    /// The JSON member containing the protection marker.
    static final String PROPERTY_PROTECTION = "protection";

    /// The JSON member containing the envelope payload.
    static final String PROPERTY_PAYLOAD = "payload";

    /// The JSON member containing the Base64-encoded encryption nonce.
    static final String PROPERTY_NONCE = "nonce";

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

        /// Stores the payload as a portable weakly encrypted envelope.
        ///
        /// The payload is encrypted with a built-in application key, Base64-encoded, and split into padded strings. This
        /// avoids storing private data as directly readable JSON, but it should not be treated as device-bound secret
        /// storage. The nonce is stored separately, while the payload lanes store `ciphertext || authentication tag`.
        OBFUSCATED_V1("hmcl-obfuscated-v1") {
            /// The number of lanes used by the obfuscated payload.
            private static final int OBFUSCATED_LANE_COUNT = 4;

            /// The number of padding elements stored before each lane.
            private static final int LANE_PADDING_COUNT = 63;

            /// The number of elements written in an obfuscated payload array.
            private static final int WRITTEN_OBFUSCATED_PAYLOAD_SIZE = 256;

            /// The JCA transformation used for payload encryption.
            private static final String CIPHER_TRANSFORMATION = "ChaCha20-Poly1305";

            /// The ChaCha20-Poly1305 nonce size in bytes.
            private static final int NONCE_SIZE = 12;

            /// The random source used to create payload nonces.
            private static final SecureRandom SECURE_RANDOM = new SecureRandom();

            /// The built-in application key used by this weak portable protection mode.
            static final SecretKeySpec PROTECTION_KEY = new SecretKeySpec(new byte[]{
                    (byte) 0x3c, (byte) 0xd8, (byte) 0xa2, (byte) 0x22,
                    (byte) 0x11, (byte) 0xd2, (byte) 0x8d, (byte) 0x89,
                    (byte) 0xb4, (byte) 0xf7, (byte) 0xd9, (byte) 0xb0,
                    (byte) 0x65, (byte) 0xbc, (byte) 0x14, (byte) 0x8a,
                    (byte) 0x6e, (byte) 0xb0, (byte) 0xa9, (byte) 0x4d,
                    (byte) 0xeb, (byte) 0x93, (byte) 0x99, (byte) 0x6f,
                    (byte) 0x84, (byte) 0x07, (byte) 0x5a, (byte) 0x9e,
                    (byte) 0xbd, (byte) 0xc8, (byte) 0xd1, (byte) 0xeb
            }, "ChaCha20");

            /// Encrypts the plain payload bytes.
            ///
            /// @param payload the plain payload bytes
            /// @param nonce the encryption nonce
            /// @return the encrypted payload bytes with the authentication tag appended
            /// @throws JsonParseException if the cipher is not available
            private byte[] encryptPayload(byte[] payload, byte[] nonce) {
                try {
                    Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
                    cipher.init(Cipher.ENCRYPT_MODE, PROTECTION_KEY, new IvParameterSpec(nonce));
                    return cipher.doFinal(payload);
                } catch (GeneralSecurityException e) {
                    throw new JsonParseException("Failed to protect JSON payload", e);
                }
            }

            /// Decrypts the protected payload bytes.
            ///
            /// @param payload the encrypted payload bytes with the authentication tag appended
            /// @param nonce the encryption nonce
            /// @return the plain payload bytes
            /// @throws JsonParseException if the payload cannot be decrypted
            private byte[] decryptPayload(byte[] payload, byte[] nonce) {
                try {
                    Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
                    cipher.init(Cipher.DECRYPT_MODE, PROTECTION_KEY, new IvParameterSpec(nonce));
                    return cipher.doFinal(payload);
                } catch (GeneralSecurityException e) {
                    throw new JsonParseException("Failed to reveal protected JSON payload", e);
                }
            }

            /// Returns the payload array index storing one lane in an effective payload window.
            ///
            /// @param laneIndex the lane index
            /// @param payloadSize the effective payload window size
            /// @return the payload array index
            private static int lanePayloadIndex(int laneIndex, int payloadSize) {
                int segmentSize = payloadSize / OBFUSCATED_LANE_COUNT;
                return (laneIndex + 1) * segmentSize - 1;
            }

            /// Splits a Base64 payload into padded lanes.
            ///
            /// @param payload the Base64 payload to split
            /// @return the padded payload lanes
            private static JsonArray splitObfuscatedPayload(String payload) {
                int laneLength = payload.length() / OBFUSCATED_LANE_COUNT;
                JsonArray result = new JsonArray(WRITTEN_OBFUSCATED_PAYLOAD_SIZE);
                for (int laneIndex = 0; laneIndex < OBFUSCATED_LANE_COUNT; laneIndex++) {
                    int start = laneIndex * laneLength;
                    String lane = payload.substring(start, start + laneLength);
                    for (int i = 0; i < LANE_PADDING_COUNT; i++) {
                        result.add(JsonNull.INSTANCE);
                    }
                    result.add(lane);
                }
                return result;
            }

            /// Joins Base64 payload lanes from the envelope.
            ///
            /// @param envelope the envelope object to read from
            /// @return the restored Base64 payload
            /// @throws JsonParseException if the payload lanes are missing or malformed
            private static String joinObfuscatedPayload(JsonObject envelope) {
                if (!(envelope.get(PROPERTY_PAYLOAD) instanceof JsonArray lanes)
                        || lanes.size() < OBFUSCATED_LANE_COUNT) {
                    throw new JsonParseException("Missing payload or payload array is too small");
                }

                int effectivePayloadSize = Integer.highestOneBit(lanes.size());
                String[] laneTexts = new String[OBFUSCATED_LANE_COUNT];
                int totalLength = 0;
                for (int i = 0; i < OBFUSCATED_LANE_COUNT; i++) {
                    int payloadIndex = lanePayloadIndex(i, effectivePayloadSize);
                    JsonElement lane = lanes.get(payloadIndex);
                    if (!lane.isJsonPrimitive() || !lane.getAsJsonPrimitive().isString()) {
                        throw new JsonParseException("Protected payload lane is not a string");
                    }

                    laneTexts[i] = lane.getAsString();
                    totalLength += laneTexts[i].length();
                }

                StringBuilder result = new StringBuilder(totalLength);
                for (String laneText : laneTexts) {
                    result.append(laneText);
                }
                return result.toString();
            }

            /// Writes the payload into the given envelope.
            @Override
            protected void writePayload(JsonObject envelope, JsonElement payload) {
                byte[] nonce = new byte[NONCE_SIZE];
                SECURE_RANDOM.nextBytes(nonce);
                writePayload(envelope, payload, nonce);
            }

            /// Writes the payload into the given envelope with a caller-provided nonce.
            @Override
            protected void writePayload(JsonObject envelope, JsonElement payload, byte[] nonce) {
                if (nonce.length != NONCE_SIZE) {
                    throw new JsonParseException("Protected payload nonce has invalid length");
                }

                String payloadText = JsonUtils.UGLY_GSON.toJson(payload);
                byte[] payloadBytes = payloadText.getBytes(StandardCharsets.UTF_8);
                byte[] encryptedPayload = encryptPayload(payloadBytes, nonce);
                String actualPayload = Base64.getEncoder().encodeToString(encryptedPayload);

                envelope.addProperty(PROPERTY_PROTECTION, id());
                envelope.add(PROPERTY_PAYLOAD, splitObfuscatedPayload(actualPayload));
                envelope.addProperty(PROPERTY_NONCE, Base64.getEncoder().encodeToString(nonce));
            }

            /// Reads the payload from the given envelope.
            @Override
            protected JsonElement readPayload(JsonObject envelope) {
                try {
                    String encodedNonce = JsonUtils.getString(envelope, PROPERTY_NONCE);
                    if (encodedNonce == null) {
                        throw new JsonParseException("Missing protected payload member: nonce");
                    }

                    byte[] nonce = Base64.getDecoder().decode(encodedNonce);
                    if (nonce.length != NONCE_SIZE) {
                        throw new JsonParseException("Protected payload nonce has invalid length");
                    }

                    String encodedPayload = joinObfuscatedPayload(envelope);
                    byte[] encryptedPayload = Base64.getDecoder().decode(encodedPayload);
                    byte[] payloadBytes = decryptPayload(encryptedPayload, nonce);
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
        /// @param payload the plain JSON payload
        /// @throws JsonParseException if the payload cannot be protected
        protected abstract void writePayload(JsonObject envelope, JsonElement payload);

        /// Writes the payload into the given envelope with a caller-provided nonce.
        ///
        /// @param envelope the envelope object to write into
        /// @param payload the plain JSON payload
        /// @param nonce the nonce to use for modes that require one
        /// @throws JsonParseException if the payload cannot be protected
        protected void writePayload(JsonObject envelope, JsonElement payload, byte[] nonce) {
            writePayload(envelope, payload);
        }

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
