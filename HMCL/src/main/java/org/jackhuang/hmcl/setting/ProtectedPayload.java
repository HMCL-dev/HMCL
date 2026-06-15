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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/// Stores a JSON payload using HMCL's portable protection envelope.
///
/// This class is intentionally a weak, portable protection layer. The key is embedded in the launcher so the
/// protected payload can move between machines, but it should not be treated as device-bound secret storage.
@NotNullByDefault
final class ProtectedPayload {
    /// The protection marker for obfuscated envelope objects.
    static final String PROTECTION_OBFUSCATED = "hmcl-obfuscated-v1";

    /// The protection marker for plain envelope objects.
    static final String PROTECTION_PLAIN = "plain";

    /// The JSON member containing the protection marker.
    static final String PROPERTY_PROTECTION = "protection";

    /// The JSON member containing the random nonce.
    static final String PROPERTY_NONCE = "nonce";

    /// The JSON member containing the envelope payload.
    static final String PROPERTY_PAYLOAD = "payload";

    /// The portable obfuscation key embedded in the launcher.
    private static final SecretKeySpec KEY = new SecretKeySpec(
            DigestUtils.digest("SHA-256", "org.jackhuang.hmcl.protected-payload.v1"
                    .getBytes(StandardCharsets.UTF_8)),
            "AES");

    /// Secure random source used for GCM nonces.
    private static final SecureRandom RANDOM = new SecureRandom();

    /// Base64 encoder used for JSON-safe binary data.
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /// Base64 decoder used for JSON-safe binary data.
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /// The byte length of AES-GCM nonces.
    private static final int NONCE_LENGTH = 12;

    /// The AES-GCM authentication tag length in bits.
    private static final int TAG_LENGTH = 128;

    /// Prevents instantiation.
    private ProtectedPayload() {
    }

    /// Selects how a protected payload is stored in its JSON envelope.
    enum ProtectionMode {
        /// Stores the payload as a portable obfuscated AES-GCM envelope.
        OBFUSCATED_V1(PROTECTION_OBFUSCATED) {
            /// Writes the payload into the given envelope.
            @Override
            void write(JsonObject envelope, JsonElement payload) {
                writeObfuscated(envelope, payload);
            }

            /// Reads the payload from the given envelope.
            @Override
            JsonElement read(JsonObject envelope) {
                return readObfuscated(envelope);
            }
        },

        /// Stores the payload as plain JSON for development and diagnostics.
        PLAIN(PROTECTION_PLAIN) {
            /// Writes the payload into the given envelope.
            @Override
            void write(JsonObject envelope, JsonElement payload) {
                envelope.addProperty(PROPERTY_PROTECTION, id());
                envelope.add(PROPERTY_PAYLOAD, payload.deepCopy());
            }

            /// Reads the payload from the given envelope.
            @Override
            JsonElement read(JsonObject envelope) {
                JsonElement payload = envelope.get(PROPERTY_PAYLOAD);
                if (payload == null) {
                    throw new JsonParseException("Missing protected payload member: " + PROPERTY_PAYLOAD);
                }
                return payload.deepCopy();
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
        abstract void write(JsonObject envelope, JsonElement payload);

        /// Reads the payload from the given envelope.
        ///
        /// @param envelope the envelope object to read from
        /// @return the revealed JSON payload
        /// @throws JsonParseException if the envelope is malformed or cannot be revealed
        abstract JsonElement read(JsonObject envelope);

        /// Returns the write mode selected by a configuration value.
        ///
        /// Unknown values intentionally fall back to obfuscation so opt-in plain storage cannot be enabled by typos.
        ///
        /// @param id the configured protection marker
        /// @return the selected write mode
        static ProtectionMode fromConfiguredId(@Nullable String id) {
            return PROTECTION_PLAIN.equals(id) ? PLAIN : OBFUSCATED_V1;
        }

        /// Reads the protection mode from an envelope.
        ///
        /// @param envelope the envelope object to inspect
        /// @return the protection mode declared by the envelope
        /// @throws JsonParseException if the declared protection mode is unsupported
        static ProtectionMode fromEnvelope(JsonObject envelope) {
            String protection = readString(envelope, PROPERTY_PROTECTION);
            for (ProtectionMode mode : values()) {
                if (mode.id.equals(protection)) {
                    return mode;
                }
            }
            throw new JsonParseException("Unsupported protected payload: " + protection);
        }
    }

    /// Writes a JSON payload into an envelope object.
    ///
    /// @param envelope the envelope object to write into
    /// @param payload the plain JSON payload
    /// @param protectionMode the protection mode used for writing the payload
    /// @throws JsonParseException if the payload cannot be protected
    static void write(JsonObject envelope, JsonElement payload, ProtectionMode protectionMode) {
        protectionMode.write(envelope, payload);
    }

    /// Writes a JSON payload as an obfuscated envelope.
    ///
    /// @param envelope the envelope object to write into
    /// @param payload the plain JSON payload
    /// @throws JsonParseException if the payload cannot be protected
    private static void writeObfuscated(JsonObject envelope, JsonElement payload) {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(TAG_LENGTH, nonce));
            cipher.updateAAD(PROTECTION_OBFUSCATED.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));

            envelope.addProperty(PROPERTY_PROTECTION, PROTECTION_OBFUSCATED);
            envelope.addProperty(PROPERTY_NONCE, ENCODER.encodeToString(nonce));
            envelope.addProperty(PROPERTY_PAYLOAD, ENCODER.encodeToString(encrypted));
        } catch (GeneralSecurityException e) {
            throw new JsonParseException("Failed to protect JSON payload", e);
        }
    }

    /// Reads and reveals a protected JSON payload from an envelope object.
    ///
    /// @param envelope the envelope object to read from
    /// @return the revealed JSON payload
    /// @throws JsonParseException if the envelope is malformed or cannot be revealed
    static JsonElement read(JsonObject envelope) throws JsonParseException {
        return ProtectionMode.fromEnvelope(envelope).read(envelope);
    }

    /// Reads and reveals an obfuscated JSON payload from an envelope object.
    ///
    /// @param envelope the envelope object to read from
    /// @return the revealed JSON payload
    /// @throws JsonParseException if the envelope is malformed or cannot be revealed
    private static JsonElement readObfuscated(JsonObject envelope) throws JsonParseException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, KEY,
                    new GCMParameterSpec(TAG_LENGTH, DECODER.decode(readString(envelope, PROPERTY_NONCE))));
            cipher.updateAAD(PROTECTION_OBFUSCATED.getBytes(StandardCharsets.UTF_8));
            byte[] decrypted = cipher.doFinal(DECODER.decode(readString(envelope, PROPERTY_PAYLOAD)));
            return JsonParser.parseString(new String(decrypted, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new JsonParseException("Failed to reveal protected JSON payload", e);
        }
    }

    /// Reads a required string member from a JSON object.
    ///
    /// @param object the JSON object to read
    /// @param name the member name
    /// @return the string value
    /// @throws JsonParseException if the member is missing or is not a string
    private static String readString(JsonObject object, String name) throws JsonParseException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Missing protected payload member: " + name);
        }
        return value.getAsString();
    }
}
