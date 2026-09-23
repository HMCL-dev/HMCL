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
package org.jackhuang.hmcl.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;

public final class ServerStatusGetter {
    private ServerStatusGetter() {

    }

    public static ServerStatus getStatus(String serverIp) throws IOException {
        return ServerStatusGetter.getStatus(ServerAddress.parseAddress(serverIp));
    }

    private static ServerStatus getStatus(ServerAddress address) throws IOException {
        ServerAddress resolvedAddress = ServerDnsSrvRedirector.lookup(address);

        try (Socket socket = new Socket()) {
            socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socket.connect(resolvedAddress.toInetSocketAddress(), 7_000);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                sendHandshakeStatusPacket(out, address.host(), address.port());
                sendStatusRequestPacket(out);
                var status = readStatusResponsePacket(in);

                long sendTime = System.currentTimeMillis();
                sendPingRequestPacket(out);
                var pong = readPongResponsePacket(in);
                long networkLatency = System.currentTimeMillis() - sendTime;

                JsonObject rootStatus = JsonParser.parseString(status).getAsJsonObject();

                JsonObject versionJsonObject = rootStatus.getAsJsonObject("version");
                int protocol = versionJsonObject.get("protocol").getAsInt();
                String protocolName = versionJsonObject.get("name").getAsString();

                JsonObject playersJsonObject = rootStatus.getAsJsonObject("players");
                int playerMax = playersJsonObject.get("max").getAsInt();
                int playerOnline = playersJsonObject.get("online").getAsInt();

                String favicon = null;
                if (rootStatus.get("favicon") != null) {
                    String fetchedFavicon = rootStatus.get("favicon").getAsString();
                    if (fetchedFavicon.startsWith("data:image/png;base64,")) {
                        favicon = fetchedFavicon.substring("data:image/png;base64,".length());
                    }
                }

                return new ServerStatus(
                        networkLatency,
                        protocol,
                        protocolName,
                        playerMax,
                        playerOnline,
                        favicon
                );
            }
        }
    }

    private static long readPongResponsePacket(DataInputStream in) throws IOException {
        byte[] packetData = new byte[readVarInt(in)];
        in.readFully(packetData);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(packetData);
             DataInputStream packetIn = new DataInputStream(bais)) {
            int packetId = readVarInt(packetIn);
            if (packetId != 0x01)
                throw new IOException("Invalid packet id " + packetId + ", expected 0x01(Pong Response).");
            return readLong(packetIn);
        }
    }

    private static String readStatusResponsePacket(DataInputStream in) throws IOException {
        byte[] packetData = new byte[readVarInt(in)];
        in.readFully(packetData);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(packetData);
             DataInputStream packetIn = new DataInputStream(bais)) {
            int packetId = readVarInt(packetIn);
            if (packetId != 0x00)
                throw new IOException("Invalid packet id " + packetId + ", expected 0x00(Status Response).");

            return readVarString(packetIn); // Status Json
        }
    }

    private static void sendStatusRequestPacket(DataOutputStream sendTarget) throws IOException {
        sendPacket(sendTarget, out -> {
            out.writeByte(0x00); // Packet ID
        });
    }

    private static void sendPacket(DataOutputStream target, PacketWriter packetWriter) throws IOException {
        try (ByteArrayOutputStream packetOut = new ByteArrayOutputStream();
             DataOutputStream dataPacketOut = new DataOutputStream(packetOut)) {
            packetWriter.write(dataPacketOut);

            writeVarInt(target, packetOut.size()); // Packet Size
            target.write(packetOut.toByteArray()); // Packet Contents
        }
    }

    private static void sendHandshakeStatusPacket(DataOutputStream sendTarget, String address, int port) throws IOException {
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

    private static void sendPingRequestPacket(DataOutputStream sendTarget) throws IOException {
        sendPacket(sendTarget, out -> {
            out.write(0x01); // packet id
            long time = System.currentTimeMillis();

            writeLong(out, time); // time
        });
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);

            value >>>= 7;
        }

        out.write(value);
    }

    private static String readVarString(DataInputStream in) throws IOException {
        byte[] bytes = new byte[readVarInt(in)];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readVarInt(InputStream in) throws IOException {
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

    private static void writeLong(OutputStream out, long value) throws IOException {
        for (int i = 7; i >= 0; i--) {
            out.write((int) ((value >>> (i * 8)) & 0xFF));
        }
    }

    private static void writeVarString(OutputStream out, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static long readLong(InputStream in) throws IOException {
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

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
