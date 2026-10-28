// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.pinger;

import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.server.resolver.ServerAddressResolveResult;
import org.jackhuang.hmcl.server.status.ServerPlayerInfo;
import org.jackhuang.hmcl.server.status.ServerStatus;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jackhuang.hmcl.server.status.ServerVersionInfo;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.StandardSocketOptions;

// https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping#1.6
// Minecraft Beta 1.8 to Minecraft 1.6
public final class LegacyServerStatusPinger implements ServerStatusPinger {
    static LegacyServerStatusPinger instance = new LegacyServerStatusPinger();

    private LegacyServerStatusPinger() {

    }

    @Override
    public @NotNull ServerStatusResult getStatus(@NotNull ServerAddressResolveResult.SuccessResult successResult) {
        try (Socket socket = new Socket()) {
            socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socket.connect(successResult.getConnectAddress(), 7_000);
            socket.setSoTimeout(7_000);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                long sendTime = System.currentTimeMillis();

                // only send these 3 bytes and all legacy servers(<=1.6) will respond correspondingly
                out.writeByte(0xfe); // packet id
                out.writeByte(0x01); // payload(always 1)
                out.writeByte(0xfa); // plugin message

//                writeString(out, "MC|PingHost"); // magic
//
//                byte[] remainBytes = toByteArray(remainOut -> {
//                    remainOut.writeByte(0x7f); // protocol version
//                    writeString(remainOut, successResult.getRawAddress().getHostAndPort().host()); // raw host
//                    remainOut.writeInt(successResult.getRawAddress().getHostAndPort().port()); // raw port
//                });
//
//                out.writeShort(remainBytes.length); // remain data length
//                out.write(remainBytes); // remain data

                int receivePacketId = in.readUnsignedByte();
                if (receivePacketId != 0xff) {
                    throw new IOException("Invalid packet id " + receivePacketId + ", expected 0xff(Kick Packet).");
                }

                String response = readString(in);
                long networkLatency = System.currentTimeMillis() - sendTime;
                return ServerStatusResult.success(successResult.getRawAddress(), this, networkLatency, parseResult(response));
            }
        } catch (Exception e) {
            return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, e);
        }
    }

    @Override
    public @NotNull String getName() {
        return "Legacy Server Status Pinger";
    }

    private @NotNull ServerStatus parseResult(@NotNull String input) throws IOException {
        ServerVersionInfo version;
        String motd;
        ServerPlayerInfo players;
        try {
            if (input.startsWith("§")) {
                String[] strings = input.split("\0");

                if (strings[0].equals("§1")) {
                    version = ServerVersionInfo.of(
                            parseIntOrDefault(strings[1], 0),
                            strings[2]
                    );
                    motd = strings[3];

                    players = ServerPlayerInfo.of(
                            parseIntOrDefault(strings[5], -1),
                            parseIntOrDefault(strings[4], -1)
                    );
                } else {
                    throw new IOException("Invalid header: " + strings[0]);
                }
            } else {
                String[] strings = input.split("§");
                motd = strings[0];

                version = ServerVersionInfo.of();
                players = ServerPlayerInfo.of(
                        parseIntOrDefault(strings[2], -1),
                        parseIntOrDefault(strings[1], -1)
                );
            }

            return new ServerStatus(
                    version,
                    players,
                    new JsonPrimitive(motd), null, false, null);
        } catch (Exception e) {
            throw new IOException("Failed to parse legacy server status: " + input, e);
        }
    }

    private int parseIntOrDefault(@NotNull String input, int defaultValue) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void writeString(final DataOutputStream out, final String str) throws IOException {
        out.writeShort(str.length());
        out.writeChars(str);
    }

    private String readString(final DataInputStream in) throws IOException {
        int length = in.readShort();

        char[] chars = new char[length];
        for (int index = 0; index < length; index++) {
            chars[index] = in.readChar();
        }

        return new String(chars);
    }

    private byte[] toByteArray(@NotNull DataWriter writer) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out)) {
            writer.write(dataOut);
            return out.toByteArray();
        }
    }
}
