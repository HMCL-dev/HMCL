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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ServerStatusGetter {
    private ServerStatusGetter() {

    }

    private static InetSocketAddress parseAddress(String ipStr) throws IOException {
        if (ipStr == null || ipStr.isEmpty()) throw new IOException("server ip is empty.");

        String host = ipStr;
        int port = 25565;

        int lastIndexOf = ipStr.lastIndexOf(":");
        if (lastIndexOf != -1) {
            String portArea = ipStr.substring(lastIndexOf + 1);
            try {
                int parsedPort = Integer.parseInt(portArea);
                if (parsedPort > 0 && parsedPort <= 65535) {
                    host = ipStr.substring(0, lastIndexOf);
                    port = parsedPort;
                }
            } catch (Exception ignore) {
            }
        }
        return new InetSocketAddress(host, port);
    }

    public static ServerStatus getStatus(String serverIp) throws IOException {
        return ServerStatusGetter.getStatus(parseAddress(serverIp));
    }

    public static ServerStatus getStatus(InetSocketAddress address) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(address);
            socket.setSoTimeout(10_000);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                sendHandshakeStatusPacket(out, address.getHostName(), address.getPort());
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


                // todo check
                String favicon = rootStatus.get("favicon").getAsString().substring("data:image/png;base64,".length());

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
            byte[] jsonBytes = new byte[readVarInt(packetIn)];
            packetIn.readFully(jsonBytes);
            return new String(jsonBytes, StandardCharsets.UTF_8);
        }
    }

    private static void sendStatusRequestPacket(DataOutputStream sendTarget) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(0x00);                             // Packet ID

            writeVarInt(sendTarget, output.size()); // Packet Size
            sendTarget.write(output.toByteArray()); // Packet Contents
        }
    }

    private static void sendHandshakeStatusPacket(DataOutputStream sendTarget, String address, int port) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(0x00);                             // Packet ID
            writeVarInt(output, -1);                     // Protocol Version

            byte[] addressBytes = address.getBytes(StandardCharsets.UTF_8);
            writeVarInt(output, addressBytes.length);
            output.write(addressBytes);

            // Server Port
            output.write((port >>> 8) & 0xFF);
            output.write(port & 0xFF);

            // Next State Status Request
            writeVarInt(output, 1);

            writeVarInt(sendTarget, output.size()); // Packet Size
            sendTarget.write(output.toByteArray()); // Packet Contents
        }
    }

    private static void sendPingRequestPacket(DataOutputStream sendTarget) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(0x01); // packet id
            long time = System.currentTimeMillis();

            writeLong(output, time); // time

            writeVarInt(sendTarget, output.size());
            sendTarget.write(output.toByteArray());
        }
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);

            value >>>= 7;
        }

        out.write(value);
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
}
