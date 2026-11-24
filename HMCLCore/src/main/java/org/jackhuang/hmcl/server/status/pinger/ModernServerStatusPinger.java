// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status.pinger;

import com.google.gson.*;
import org.jackhuang.hmcl.server.status.ServerStatus;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @see <a href="https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping">Java Edition protocol/Server List Ping</a>
/// @see <a href="https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Minecraft_Forge_Handshake#Changes_to_Server_List_Ping">JMinecraft Forge Handshake</a>
/// @see <a href="https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Minecraft_Forge_Handshake#Changes_to_Server_List_Ping_2">JMinecraft Forge Handshake</a>
// Supported Version 1.7+
public final class ModernServerStatusPinger implements ServerStatusPinger {
    static final ModernServerStatusPinger INSTANCE = new ModernServerStatusPinger();
    private static final int PROTOCOL_NUMBER = 777; // 26.3 protocol number
    private static final String FORGE = "Forge";
    private static final String NEO_FORGED = "NeoForged";

    private ModernServerStatusPinger() {

    }

    @Override
    public long delayToPing() {
        return 0;
    }

    @Override
    public @NotNull ServerStatusResult getStatus(@NotNull ResolvedServerAddress resolvedServerAddress) throws Exception {
        try (PingConnection connection = resolvedServerAddress.createConnection()) {
            // Handshaking
            buildAndSendPacket(connection.getOut(), out -> {
                out.write(0x00); // Packet ID
                writeVarInt(out, PROTOCOL_NUMBER); // Protocol Version
                writeVarString(out, resolvedServerAddress.getHostForIntentPacket()); // Server Address
                out.writeShort(resolvedServerAddress.getPortForIntentPacket()); // Server Port
                writeVarInt(out, 1); // Intent (1 for Status)
            });

            // Status
            buildAndSendPacket(connection.getOut(), out -> {
                out.writeByte(0x00); // Packet ID
            });

            String statusJsonString = readPacket(connection.getIn(), 0x00, "Status Response", this::readVarString);

            long sendTime = System.currentTimeMillis();
            buildAndSendPacket(connection.getOut(), out -> {
                out.write(0x01); // Packet ID
                writeLong(out, sendTime); // Timestamp
            });

            readPacket(connection.getIn(), 0x01, "Pong Response", this::readLong);
            long networkLatency = System.currentTimeMillis() - sendTime;

            try {
                JsonObject rootStatus = JsonParser.parseString(statusJsonString).getAsJsonObject();

                ServerStatus parseServerStatus = buildServerStatus(resolvedServerAddress, rootStatus, networkLatency);
                return ServerStatusResult.success(parseServerStatus);
            } catch (Exception e) {
                throw new StatusParseException("Failed to parse server status: " + statusJsonString, e, true);
            }
        }
    }

    private @NotNull ServerStatus buildServerStatus(@NotNull ResolvedServerAddress resolvedServerAddress, @NotNull JsonObject statusJsonObject, long networkLatency) {
        return new ServerStatus(
                this,
                resolvedServerAddress.getOriginalServerAddress(),
                resolvedServerAddress.getResolvedAddress(),
                networkLatency,
                parseProtocolVersion(statusJsonObject.get("version")),
                parsePlayerList(statusJsonObject.get("players")),
                Objects.requireNonNullElse(statusJsonObject.get("description"), JsonNull.INSTANCE),
                parseSererFavicon(statusJsonObject.get("favicon")),
                parseServerModType(statusJsonObject)
        );
    }

    private @Nullable String parseSererFavicon(JsonElement faviconElement) {
        if (faviconElement == null || !faviconElement.isJsonPrimitive()) {
            return null;
        }
        String fetchedFavicon = faviconElement.getAsJsonPrimitive().getAsString();
        if (fetchedFavicon.startsWith("data:image/png;base64,")) {
            return fetchedFavicon.substring("data:image/png;base64,".length());
        } else {
            return null;
        }
    }

    private ServerStatus.@Nullable ServerModType parseServerModType(@NotNull JsonObject statusJsonObject) {
        if (statusJsonObject.get("modinfo") instanceof JsonObject modinfoObject) {
            Set<ServerStatus.ServerModType.Mod> modList = new LinkedHashSet<>();
            if (modinfoObject.get("modList") instanceof JsonArray jArray) {
                for (JsonElement element : jArray) {
                    if (element instanceof JsonObject jsonObject
                            && jsonObject.get("modid") instanceof JsonPrimitive modidPrimitive
                            && jsonObject.get("version") instanceof JsonPrimitive versionPrimitive
                    ) {
                        modList.add(new ServerStatus.ServerModType.Mod(
                                modidPrimitive.getAsString(),
                                versionPrimitive.getAsString()
                        ));
                    }
                }
            }
            String type = null;
            if (modinfoObject.get("type") instanceof JsonPrimitive primitive) {
                type = primitive.getAsString();
            }

            return new ServerStatus.ServerModType(type, modList);
        }

        boolean containsIsModdedElement = false;
        if (statusJsonObject.get("isModded") instanceof JsonPrimitive isModdedPrimitive) {
            containsIsModdedElement = isModdedPrimitive.getAsBoolean();
        }

        if (statusJsonObject.get("forgeData") instanceof JsonObject forgeDataObject) {
            Set<ServerStatus.ServerModType.Mod> modList = new LinkedHashSet<>();
            if (forgeDataObject.get("mods") instanceof JsonArray jArray) {
                for (JsonElement element : jArray) {
                    if (element instanceof JsonObject jsonObject
                            && jsonObject.get("modId") instanceof JsonPrimitive modIdPrimitive
                            && jsonObject.get("modmarker") instanceof JsonPrimitive modmarkerPrimitive
                    ) {
                        modList.add(new ServerStatus.ServerModType.Mod(
                                modIdPrimitive.getAsString(),
                                modmarkerPrimitive.getAsString()
                        ));
                    }
                }
            }

            if (forgeDataObject.get("d") instanceof JsonPrimitive dPrimitive) {
                modList.addAll(decodeForgeDataD(dPrimitive.getAsString()));
            }

            return new ServerStatus.ServerModType(containsIsModdedElement ? NEO_FORGED : FORGE, modList);
        }

        return null;
    }

    private @NotNull ServerStatus.ProtocolVersion parseProtocolVersion(@Nullable JsonElement protocolVersionElement) {
        if (protocolVersionElement == null || !protocolVersionElement.isJsonObject()) {
            return ServerStatus.ProtocolVersion.UNKNOWN;
        }
        JsonObject asJsonObject = protocolVersionElement.getAsJsonObject();
        int protocolNumber = -1;
        if (asJsonObject.get("protocol") instanceof JsonPrimitive primitive) {
            try {
                protocolNumber = primitive.getAsInt();
            } catch (NumberFormatException e) {
                LOG.error("Invalid protocol number: " + primitive);
            }
        }
        String specifiedProtocolName = null;
        if (asJsonObject.get("name") instanceof JsonPrimitive primitive) {
            specifiedProtocolName = primitive.getAsString();
        }

        if (protocolNumber >= 0 && protocolNumber < 0x40000000) {
            return new ServerStatus.ProtocolVersion(ServerStatus.ProtocolVersion.VersionType.RELEASE, specifiedProtocolName, protocolNumber);
        } else if (protocolNumber >= 0x40000000) {
            return new ServerStatus.ProtocolVersion(ServerStatus.ProtocolVersion.VersionType.SNAPSHOT, specifiedProtocolName, protocolNumber - 0x40000000);
        } else {
            return new ServerStatus.ProtocolVersion(ServerStatus.ProtocolVersion.VersionType.UNKNOWN, specifiedProtocolName, protocolNumber);
        }
    }

    private @NotNull ServerStatus.PlayerList parsePlayerList(@Nullable JsonElement playerListElement) {
        if (playerListElement == null || !playerListElement.isJsonObject()) {
            return ServerStatus.PlayerList.UNKNOWN;
        }

        JsonObject asJsonObject = playerListElement.getAsJsonObject();
        boolean numbered = false;
        int playerMax = -1;
        int playerOnline = -1;

        if (asJsonObject.get("max") instanceof JsonPrimitive maxPrimitive && asJsonObject.get("online") instanceof JsonPrimitive onlinePrimitive) {
            numbered = true;
            try {
                playerMax = maxPrimitive.getAsInt();
            } catch (NumberFormatException e) {
                numbered = false;
                LOG.error("Invalid max number: " + maxPrimitive);
            }
            try {
                playerOnline = onlinePrimitive.getAsInt();
            } catch (NumberFormatException e) {
                numbered = false;
                LOG.error("Invalid online number: " + onlinePrimitive);
            }
        }

        if (asJsonObject.get("sample") instanceof JsonArray sampleJsonArray) {
            List<ServerStatus.PlayerList.Sample> samples = new ArrayList<>();
            for (JsonElement element : sampleJsonArray) {
                if (element instanceof JsonObject elementAsJsonObject) {
                    if (elementAsJsonObject.get("name") instanceof JsonPrimitive namePrimitive) {
                        if (elementAsJsonObject.get("id") instanceof JsonPrimitive idPrimitive) {
                            try {
                                samples.add(new ServerStatus.PlayerList.Sample(
                                        namePrimitive.getAsString(),
                                        UUID.fromString(idPrimitive.getAsString())
                                ));
                            } catch (IllegalArgumentException e) {
                                LOG.error("Invalid sample id: " + idPrimitive);
                            }
                        }
                    }
                }
            }

            return new ServerStatus.PlayerList(playerMax, playerOnline, numbered, samples);
        }
        return new ServerStatus.PlayerList(playerMax, playerOnline);
    }

    private <T> T readPacket(@NotNull DataInputStream in, int expectPacketID, String packetDescription, DataReader<T> reader) throws IOException {
        int length = readVarInt(in);
        if (length > 2097151) throw new IOException("Packet length too large: " + length);
        if (length < 0) throw new IOException("Packet length is negative: " + length);
        byte[] packetData = new byte[length];
        in.readFully(packetData);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(packetData);
             DataInputStream packetIn = new DataInputStream(bais)) {
            int packetId = readVarInt(packetIn);
            if (packetId != expectPacketID) {
                throw new IOException("Invalid packet id " + packetId + ", expected " + expectPacketID + "(" + packetDescription + ").");
            }

            return reader.read(packetIn);
        }
    }

    private void buildAndSendPacket(@NotNull DataOutputStream target, DataWriter dataWriter) throws IOException {
        try (ByteArrayOutputStream packetOut = new ByteArrayOutputStream();
             DataOutputStream dataPacketOut = new DataOutputStream(packetOut)) {
            dataWriter.write(dataPacketOut);

            writeVarInt(target, packetOut.size()); // Packet Size
            target.write(packetOut.toByteArray()); // Packet Contents
        }
    }

    private void writeVarInt(@NotNull OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);

            value >>>= 7;
        }

        out.write(value);
    }

    private String readVarString(@NotNull DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length > 32767) throw new IOException("Packet string length too large: " + length);
        if (length < 0) throw new IOException("Packet string length is negative: " + length);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int readVarInt(@NotNull InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        int currentByte;

        while (position < 32) {
            currentByte = in.read();
            if (currentByte == -1) {
                throw new EOFException("");
            }

            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0)
                return value;
            position += 7;
        }

        throw new IOException("VarInt too big");
    }

    private void writeLong(@NotNull OutputStream out, long value) throws IOException {
        for (int i = 7; i >= 0; i--) {
            out.write((int) ((value >>> (i * 8)) & 0xFF));
        }
    }

    private void writeVarString(@NotNull OutputStream out, @NotNull String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private long readLong(@NotNull InputStream in) throws IOException {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int b = in.read();
            if (b == -1) {
                throw new EOFException("");
            }
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    private static int read15(String s, int[] pos) {
        return pos[0] >= s.length() ? 0 : s.charAt(pos[0]++) & 0x7FFF;
    }

    private static byte[] decodeOptimizedFromForge(String s) {
        int[] pos = {0};
        int length = read15(s, pos) | (read15(s, pos) << 15);
        byte[] out = new byte[length];
        int outIndex = 0, bitBuffer = 0, bitCount = 0;

        while (outIndex < length) {
            if (pos[0] >= s.length()) break;
            bitBuffer |= read15(s, pos) << bitCount;
            bitCount += 15;
            while (bitCount >= 8 && outIndex < length) {
                out[outIndex++] = (byte) (bitBuffer & 0xFF);
                bitBuffer >>>= 8;
                bitCount -= 8;
            }
        }
        return out;
    }

    private String readUtf(@NotNull DataInputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private @NotNull Set<ServerStatus.ServerModType.Mod> decodeForgeDataD(@NotNull String dInput) {
        Set<ServerStatus.ServerModType.Mod> mods = new LinkedHashSet<>();
        if (dInput.length() < 2) return mods;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(decodeOptimizedFromForge(dInput)))){

            in.readBoolean();                      // truncated
            int modCount = in.readUnsignedShort(); // modCount

            for (int i = 0; i < modCount; i++) {
                int flags = readVarInt(in);
                int channelCount = flags >>> 1;
                boolean isServerOnly = (flags & 1) != 0;

                String modid = readUtf(in);
                String marker = isServerOnly ? "" : readUtf(in);

                for (int j = 0; j < channelCount; j++) {
                    readUtf(in);              // channel name
                    readUtf(in);              // channel version
                    in.readBoolean();         // required
                }

                mods.add(new ServerStatus.ServerModType.Mod(modid, marker));
            }
        } catch (Exception e) {
            LOG.error("Error while decoding forge data d.", e);
        }
        return mods;
    }
}
