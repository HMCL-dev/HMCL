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

import java.io.DataInputStream;
import java.io.IOException;

// https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping#1.6
// b1.8 to 1.6
public final class LegacyServerStatusPinger implements ServerStatusPinger {
    static LegacyServerStatusPinger instance = new LegacyServerStatusPinger();

    private LegacyServerStatusPinger() {

    }

    @Override
    public @NotNull ServerStatusResult getStatus(@NotNull ServerAddressResolveResult.SuccessResult successResult) {
        try (Connection connection = ServerStatusPinger.createConnection(successResult)) {
            long sendTime = System.currentTimeMillis();

            // only send these 3 bytes and all legacy servers(<=1.6) will respond correspondingly
            connection.getOut().writeByte(0xfe); // packet id
            connection.getOut().writeByte(0x01); // payload(always 1)
            connection.getOut().writeByte(0xfa); // plugin message

            int receivePacketId = connection.getIn().readUnsignedByte();
            if (receivePacketId != 0xff) {
                throw new IOException("Invalid packet id " + receivePacketId + ", expected 0xff(Kick Packet).");
            }

            String response = readString(connection.getIn());
            long networkLatency = System.currentTimeMillis() - sendTime;
            return ServerStatusResult.success(successResult.getRawAddress(), this, networkLatency, parseResult(response));
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

    private String readString(final DataInputStream in) throws IOException {
        int length = in.readShort();

        char[] chars = new char[length];
        for (int index = 0; index < length; index++) {
            chars[index] = in.readChar();
        }

        return new String(chars);
    }
}
