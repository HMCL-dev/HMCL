// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status.pinger;

import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.server.status.ServerStatus;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/// @see <a href="https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping#1.6">Server_List_Ping#1.6</a>
// Supported Version 1.6
public final class Legacy1_6ServerStatusPinger implements ServerStatusPinger {
    static final Legacy1_6ServerStatusPinger INSTANCE = new Legacy1_6ServerStatusPinger();
    private static final int PROTOCOL_NUMBER = 0x7F;

    private Legacy1_6ServerStatusPinger() {

    }

    private @NotNull ServerStatus buildServerStatus(@NotNull ServerStatusPinger.ResolvedServerAddress resolvedServerAddress, String input, long networkLatency) {
        String[] split = input.split("\u0000", 6);

        if (split[0].equals("§1")) {

            int protocolVersion = Integer.parseInt(split[1]);
            String version = split[2];
            String motd = split[3];
            int online = Integer.parseInt(split[4]);
            int players = Integer.parseInt(split[5]);

            return new ServerStatus(
                    this,
                    resolvedServerAddress.getOriginalServerAddress(),
                    resolvedServerAddress.getResolvedAddress(),
                    networkLatency,
                    new ServerStatus.ProtocolVersion(ServerStatus.ProtocolVersion.VersionType.LEGACY, version, protocolVersion),
                    new ServerStatus.PlayerList(players, online),
                    new JsonPrimitive(motd),
                    null,
                    null
            );
        }
        throw new IllegalArgumentException("Invalid header: " + split[0]);
    }

    @Override
    public @NotNull ServerStatusResult getStatus(@NotNull ResolvedServerAddress resolvedServerAddress) throws Exception {
        try (PingConnection connection = resolvedServerAddress.createConnection()) {
            long sendTime = System.currentTimeMillis();
            connection.getOut().writeByte(0xFE); // Packet ID
            connection.getOut().writeByte(0x01); // payload(always 1)
            connection.getOut().writeByte(0xFA); // plugin message

            writeString(connection.getOut(), "MC|PingHost"); // Magic

            byte[] remainBytes = buildBytes(writer -> {
                writer.writeByte(PROTOCOL_NUMBER); // Protocol Version.
                writeString(writer, resolvedServerAddress.getHostForIntentPacket());
                writer.writeInt(resolvedServerAddress.getPortForIntentPacket());
            });
            connection.getOut().writeShort(remainBytes.length);
            connection.getOut().write(remainBytes);

            int receivePacketId = connection.getIn().readUnsignedByte();
            if (receivePacketId != 0xff) {
                throw new IOException("Invalid packet id " + receivePacketId + ", expected 0xff(Kick Packet).");
            }

            String string = readString(connection.getIn());
            long networkLatency = System.currentTimeMillis() - sendTime;
            try {
                ServerStatus parseServerStatus = buildServerStatus(resolvedServerAddress, string, networkLatency);
                return ServerStatusResult.success(parseServerStatus);
            } catch (Exception e) {
                throw new StatusParseException("Failed to parse server status: " + string, e, false);
            }
        }
    }

    private void writeString(final DataOutputStream out, final String str) throws IOException {
        out.writeShort(str.length());
        out.writeChars(str);
    }

    private String readString(final DataInputStream in) throws IOException {
        int length = in.readShort();

        if (length < 0) throw new IOException("Invalid length: " + length);
        char[] chars = new char[length];
        for (int index = 0; index < length; index++) {
            chars[index] = in.readChar();
        }

        return new String(chars);
    }

    private byte[] buildBytes(@NotNull DataWriter dataWriter) throws IOException {
        try (ByteArrayOutputStream packetOut = new ByteArrayOutputStream();
             DataOutputStream dataPacketOut = new DataOutputStream(packetOut)) {
            dataWriter.write(dataPacketOut);
            dataPacketOut.flush();
            return packetOut.toByteArray();
        }
    }
}
