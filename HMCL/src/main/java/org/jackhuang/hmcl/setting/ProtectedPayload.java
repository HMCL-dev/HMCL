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
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/// Stores a JSON payload using HMCL's portable protection envelope.
///
/// This class is intentionally a weak, portable protection layer. The key is embedded in the launcher so the
/// protected payload can move between machines, but it should not be treated as device-bound secret storage.
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
            public void write(JsonObject envelope, JsonObject payload) {
                envelope.addProperty(PROPERTY_PROTECTION, id());
                envelope.add(PROPERTY_PAYLOAD, payload.deepCopy());
            }

            /// Reads the payload from the given envelope.
            @Override
            public JsonObject read(JsonObject envelope) {
                if (envelope.get(PROPERTY_PAYLOAD) instanceof JsonObject payload)
                    return payload.deepCopy();
                else
                    throw new JsonParseException("Missing protected payload member: " + PROPERTY_PAYLOAD);
            }
        },

        /// Stores the payload as a portable obfuscated envelope.
        OBFUSCATED_V1("hmcl-obfuscated-v1") {
            /// Writes the payload into the given envelope.
            @Override
            public void write(JsonObject envelope, JsonObject payload) {
                String actualPayload;
                try {
                    byte[] payloadBytes = JsonUtils.UGLY_GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                    var buffer = new ByteArrayOutputStream(payloadBytes.length);
                    try (var compressStream = new XZOutputStream(buffer, new LZMA2Options())) {
                        compressStream.write(payloadBytes);
                    }
                    actualPayload = Base64.getEncoder().encodeToString(buffer.toByteArray());
                } catch (IOException e) {
                    throw new JsonParseException("Failed to protect JSON payload", e);
                }

                envelope.addProperty(PROPERTY_PROTECTION, id());
                envelope.addProperty(PROPERTY_PAYLOAD, actualPayload);
            }

            /// Reads the payload from the given envelope.
            @Override
            public JsonObject read(JsonObject envelope) {
                try {
                    String encodedPayload = JsonUtils.getString(envelope, PROPERTY_PAYLOAD);
                    if (encodedPayload == null) {
                        throw new JsonParseException("Missing protected payload member: " + PROPERTY_PAYLOAD);
                    }

                    byte[] compressedBytes = Base64.getDecoder().decode(encodedPayload);
                    try (var decompressStream = new XZInputStream(new ByteArrayInputStream(compressedBytes));
                         var reader = new InputStreamReader(decompressStream, StandardCharsets.UTF_8)) {
                        return JsonUtils.GSON.fromJson(reader, JsonObject.class);
                    }
                } catch (IllegalArgumentException | IOException e) {
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
        /// @throws JsonParseException if the payload cannot be protected
        public abstract void write(JsonObject envelope, JsonObject payload);

        /// Reads the payload from the given envelope.
        ///
        /// @param envelope the envelope object to read from
        /// @return the revealed JSON payload
        /// @throws JsonParseException if the envelope is malformed or cannot be revealed
        public abstract JsonObject read(JsonObject envelope);

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
    /// @return the revealed JSON payload
    /// @throws JsonParseException if the envelope is malformed or cannot be revealed
    static JsonElement read(JsonObject envelope) throws JsonParseException {
        return ProtectionMode.fromEnvelope(envelope).read(envelope);
    }

}
