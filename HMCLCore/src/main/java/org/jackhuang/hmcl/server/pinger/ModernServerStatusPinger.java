// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.pinger;

import com.google.gson.*;
import org.jackhuang.hmcl.server.resolver.ServerAddressResolveResult;
import org.jackhuang.hmcl.server.status.ServerPlayerInfo;
import org.jackhuang.hmcl.server.status.ServerStatus;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jackhuang.hmcl.server.status.ServerVersionInfo;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

// https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping
// https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Minecraft_Forge_Handshake
// 1.7+
public final class ModernServerStatusPinger implements ServerStatusPinger {
    static ModernServerStatusPinger instance = new ModernServerStatusPinger();

    private ModernServerStatusPinger() {

    }

    public @NotNull ServerStatusResult getStatus(@NotNull ServerAddressResolveResult.SuccessResult successResult) {
        try (Connection connection = ServerStatusPinger.createConnection(successResult)) {
            sendHandshakeStatusPacket(connection.getOut(),
                    successResult.getPackerHandshakeAddress(), successResult.getPackerHandshakePort()
            );
            sendStatusRequestPacket(connection.getOut());
            var status = readStatusResponsePacket(connection.getIn());

            long sendTime = System.currentTimeMillis();
            sendPingRequestPacket(connection.getOut());
            var pong = readPongResponsePacket(connection.getIn());
            long networkLatency = System.currentTimeMillis() - sendTime;

            JsonObject rootStatus = JsonParser.parseString(status).getAsJsonObject();

            ServerStatus parseServerStatus = parseServerStatus(rootStatus);
            return ServerStatusResult.success(successResult.getRawAddress(), this, networkLatency, parseServerStatus);
        } catch (Exception e) {
            return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, e);
        }
    }

    @Override
    public @NotNull String getName() {
        return "Modern Server Status Pinger";
    }

    private ServerStatus parseServerStatus(@NotNull JsonObject rootStatus) {
        JsonObject versionJsonObject = rootStatus.getAsJsonObject("version");

        ServerVersionInfo version = ServerVersionInfo.of(versionJsonObject.get("protocol").getAsInt(), versionJsonObject.get("name").getAsString());

        JsonObject playersJsonObject = rootStatus.getAsJsonObject("players");

        ServerPlayerInfo players;
        JsonElement playersSampleJsonElement = playersJsonObject.get("sample");
        if (playersSampleJsonElement instanceof JsonArray jArray) {
            ArrayList<ServerPlayerInfo.FullServerPlayerInfo.Sample> samples = new ArrayList<>();
            players = ServerPlayerInfo.of(playersJsonObject.get("max").getAsInt(), playersJsonObject.get("online").getAsInt(), samples);

            for (JsonElement element : jArray) {
                JsonObject jsonObject = (JsonObject) element;
                samples.add(new ServerPlayerInfo.FullServerPlayerInfo.Sample(
                        jsonObject.get("name").getAsString(),
                        UUID.fromString(jsonObject.get("id").getAsString())
                ));
            }
        } else {
            players = ServerPlayerInfo.of(playersJsonObject.get("max").getAsInt(), playersJsonObject.get("online").getAsInt());
        }

        String favicon;
        if (rootStatus.get("favicon") != null) {
            String fetchedFavicon = rootStatus.get("favicon").getAsString();
            if (fetchedFavicon.startsWith("data:image/png;base64,")) {
                favicon = fetchedFavicon.substring("data:image/png;base64,".length());
            } else {
                favicon = null;
            }
        } else {
            favicon = null;
        }

        boolean enforcesSecureChat;
        if (rootStatus.get("enforcesSecureChat") != null) {
            enforcesSecureChat = rootStatus.get("enforcesSecureChat").getAsBoolean();
        } else {
            enforcesSecureChat = false;
        }

        ServerStatus.ServerModInfo modInfo;
        if (rootStatus.get("modinfo") != null) {
            JsonObject modinfoJsonObject = rootStatus.get("modinfo").getAsJsonObject();
            ArrayList<ServerStatus.ServerModInfo.Mod> modList = new ArrayList<>();
            modInfo = new ServerStatus.ServerModInfo(
                    modinfoJsonObject.get("type").getAsString(),
                    modList
            );
            if (modinfoJsonObject.get("modList") instanceof JsonArray jArray) {
                for (JsonElement element : jArray) {
                    JsonObject jsonObject = (JsonObject) element;
                    modList.add(new ServerStatus.ServerModInfo.Mod(
                            jsonObject.get("modid").getAsString(),
                            jsonObject.get("version").getAsString()
                    ));
                }
            }
        } else {
            modInfo = null;
        }

        return new ServerStatus(
                version,
                players,
                Objects.requireNonNullElse(rootStatus.get("description"), JsonNull.INSTANCE),
                favicon,
                enforcesSecureChat,
                modInfo
        );
    }

    private long readPongResponsePacket(DataInputStream in) throws IOException {
        return readPacket(in, packetIn -> {
            int packetId = readVarInt(packetIn);
            if (packetId != 0x01)
                throw new IOException("Invalid packet id " + packetId + ", expected 0x01(Pong Response).");
            return readLong(packetIn);
        });
    }

    private <T> T readPacket(DataInputStream in, DataReader<T> reader) throws IOException {
        int length = readVarInt(in);
        if (length > 2097151) throw new IOException("Packet length too large: " + length);
        byte[] packetData = new byte[length];
        in.readFully(packetData);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(packetData);
             DataInputStream packetIn = new DataInputStream(bais)) {
            return reader.read(packetIn);
        }
    }

    private String readStatusResponsePacket(DataInputStream in) throws IOException {
        return readPacket(in, packetIn -> {
            int packetId = readVarInt(packetIn);
            if (packetId != 0x00)
                throw new IOException("Invalid packet id " + packetId + ", expected 0x00(Status Response).");

            return readVarString(packetIn); // Status Json
        });
    }

    private void sendStatusRequestPacket(DataOutputStream sendTarget) throws IOException {
        sendPacket(sendTarget, out -> {
            out.writeByte(0x00); // Packet ID
        });
    }

    private void sendPacket(DataOutputStream target, DataWriter dataWriter) throws IOException {
        try (ByteArrayOutputStream packetOut = new ByteArrayOutputStream();
             DataOutputStream dataPacketOut = new DataOutputStream(packetOut)) {
            dataWriter.write(dataPacketOut);

            writeVarInt(target, packetOut.size()); // Packet Size
            target.write(packetOut.toByteArray()); // Packet Contents
        }
    }

    private void sendHandshakeStatusPacket(DataOutputStream sendTarget, String address, int port) throws IOException {
        sendPacket(sendTarget, out -> {
            // Packet ID
            out.write(0x00);

            // Protocol Version
            writeVarInt(out, 777);

            // Server Host
            writeVarString(out, address);

            // Server Port
            out.writeShort(port);

            // Next State Status Request
            writeVarInt(out, 1);
        });
    }

    private void sendPingRequestPacket(DataOutputStream sendTarget) throws IOException {
        sendPacket(sendTarget, out -> {
            out.write(0x01); // packet id
            long time = System.currentTimeMillis();

            writeLong(out, time); // time
        });
    }

    private void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);

            value >>>= 7;
        }

        out.write(value);
    }

    private String readVarString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length > 32768) throw new IOException("Packet string length too large: " + length);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int readVarInt(InputStream in) throws IOException {
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

    private void writeLong(OutputStream out, long value) throws IOException {
        for (int i = 7; i >= 0; i--) {
            out.write((int) ((value >>> (i * 8)) & 0xFF));
        }
    }

    private void writeVarString(OutputStream out, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private long readLong(InputStream in) throws IOException {
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
