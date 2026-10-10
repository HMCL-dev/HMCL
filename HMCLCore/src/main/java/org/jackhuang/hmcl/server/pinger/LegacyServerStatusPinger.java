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
package org.jackhuang.hmcl.server.pinger;

import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.server.ServerStatus;
import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.server.resolver.ServerAddressResolveResult;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.util.List;

// https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping#1.6
// Minecraft Beta 1.8 to Minecraft 1.6
final class LegacyServerStatusPinger implements ServerStatusPinger {
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
                return ServerStatusResult.success(parseResult(networkLatency, response));
            }
        } catch (Exception e) {
            return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, e);
        }
    }

    private @NotNull ServerStatus parseResult(long networkLatency, @NotNull String input) throws IOException {
        ServerStatus.Version version;
        String motd;
        ServerStatus.Players players;
        try {
            if (input.startsWith("§")) {
                String[] strings = input.split("\0");

                if (strings[0].equals("§1")) {
                    version = new ServerStatus.Version(
                            strings[2],
                            parseIntOrDefault(strings[1], 0)
                    );
                    motd = strings[3];

                    players = new ServerStatus.Players(
                            parseIntOrDefault(strings[5], -1),
                            parseIntOrDefault(strings[4], -1),
                            List.of()
                    );
                } else {
                    throw new IOException("Invalid header: " + strings[0]);
                }
            } else {
                String[] strings = input.split("§");
                motd = strings[0];

                version = null;
                players = new ServerStatus.Players(
                        parseIntOrDefault(strings[2], -1),
                        parseIntOrDefault(strings[1], -1),
                        List.of()
                );
            }

            return new ServerStatus(
                    networkLatency,
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
