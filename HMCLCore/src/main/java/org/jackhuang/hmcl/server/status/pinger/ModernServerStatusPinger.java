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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// @see <a href="https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping">Java Edition protocol/Server List Ping</a>
/// @see <a href="https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Minecraft_Forge_Handshake">JMinecraft Forge Handshake</a>
// Supported Version 1.7+
public final class ModernServerStatusPinger implements ServerStatusPinger {
    static final ModernServerStatusPinger INSTANCE = new ModernServerStatusPinger();
    private static final int PROTOCOL_NUMBER = 777; // 26.3 protocol number

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
                parseServerModList(statusJsonObject.get("modinfo"))
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

    private ServerStatus.@Nullable ServerModList parseServerModList(JsonElement modinfoElement) {
        if (modinfoElement == null || !modinfoElement.isJsonObject()) {
            return null;
        }

        JsonObject asJsonObject = modinfoElement.getAsJsonObject();
        List<ServerStatus.ServerModList.Mod> modList = new ArrayList<>();
        if (asJsonObject.get("modList") instanceof JsonArray jArray) {
            for (JsonElement element : jArray) {
                if (element instanceof JsonObject jsonObject) {
                    modList.add(new ServerStatus.ServerModList.Mod(
                            jsonObject.get("modid").getAsString(),
                            jsonObject.get("version").getAsString()
                    ));
                }
            }
        }

        return new ServerStatus.ServerModList(asJsonObject.get("type").getAsString(), modList);
    }

    private @NotNull ServerStatus.ProtocolVersion parseProtocolVersion(@Nullable JsonElement protocolVersionElement) {
        if (protocolVersionElement == null || !protocolVersionElement.isJsonObject()) {
            return ServerStatus.ProtocolVersion.UNKNOWN;
        }
        JsonObject asJsonObject = protocolVersionElement.getAsJsonObject();
        int protocolNumber = asJsonObject.getAsJsonPrimitive("protocol").getAsInt();
        String specifiedProtocolName = asJsonObject.getAsJsonPrimitive("name").getAsString();

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
        int playerMax = asJsonObject.getAsJsonPrimitive("max").getAsInt();
        int playerOnline = asJsonObject.getAsJsonPrimitive("online").getAsInt();

        if (asJsonObject.get("sample") instanceof JsonArray sampleJsonArray) {
            List<ServerStatus.PlayerList.Sample> samples = new ArrayList<>();
            for (JsonElement element : sampleJsonArray) {
                if (element instanceof JsonObject elementAsJsonObject) {
                    samples.add(new ServerStatus.PlayerList.Sample(
                            elementAsJsonObject.get("name").getAsString(),
                            UUID.fromString(elementAsJsonObject.get("id").getAsString())
                    ));
                }
            }

            return new ServerStatus.PlayerList(playerMax, playerOnline, samples);
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
}
