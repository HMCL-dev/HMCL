// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.pinger;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.server.resolver.ServerAddressResolveResult;
import org.jackhuang.hmcl.server.status.ServerPlayerInfo;
import org.jackhuang.hmcl.server.status.ServerStatus;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jackhuang.hmcl.server.status.ServerVersionInfo;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// Classic Multiplayer Test Version.
public final class ClassicServerStatusPinger implements ServerStatusPinger {
    static ClassicServerStatusPinger instance = new ClassicServerStatusPinger();

    private ClassicServerStatusPinger() {

    }

    private static String generateFakeName() {
        return UUID.randomUUID().toString().replace("-", "").substring(
                ThreadLocalRandom.current().nextInt(3, 17)
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T readClassicData(final DataInputStream in, Class<T> type) throws IOException {
        if (type == Long.TYPE || type == Long.class) {
            return (T) (Object) in.readLong();
        } else if (type == Integer.TYPE || type == Integer.class) {
            return (T) (Object) in.readInt();
        } else if (type == Short.TYPE || type == Short.class) {
            return (T) (Object) in.readShort();
        } else if (type == Byte.TYPE || type == Byte.class) {
            return (T) (Object) in.readByte();
        } else if (type == Double.TYPE || type == Double.class) {
            return (T) (Object) in.readDouble();
        } else if (type == Float.TYPE || type == Float.class) {
            return (T) (Object) in.readFloat();
        } else if (type == String.class) {
            byte[] input = new byte[64];
            in.readFully(input);
            return (T) new String(input, StandardCharsets.UTF_8).trim();
        } else if (type == byte[].class) {
            byte[] input = new byte[1024];
            in.readFully(input);
            return (T) input;
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    private static void writeClassicData(final DataOutputStream out, Object param) throws IOException {
        if (param instanceof Long value) {
            out.writeLong(value);
        } else if (param instanceof Integer value) {
            out.writeInt(value);
        } else if (param instanceof Short value) {
            out.writeShort(value);
        } else if (param instanceof Byte value) {
            out.writeByte(value);
        } else if (param instanceof Double value) {
            out.writeDouble(value);
        } else if (param instanceof Float value) {
            out.writeFloat(value);
        } else if (param instanceof String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 64) {
                throw new IllegalArgumentException("String is too large");
            }
            if (bytes.length == 64) {
                out.write(bytes);
            } else {
                byte[] writeBytes = new byte[64];
                Arrays.fill(writeBytes, (byte) 32);
                System.arraycopy(bytes, 0, writeBytes, 0, bytes.length);
                out.write(writeBytes);
            }
        } else if (param instanceof byte[] value) {
            if (value.length < 1024) {
                out.write(Arrays.copyOf(value, 1024));
            } else if (value.length == 1024) {
                out.write(value);
            } else {
                throw new IllegalArgumentException("Invalid byte array length");
            }
        } else {
            throw new IllegalArgumentException("Unknown parameter type: " + param.getClass());
        }
    }

    @Override
    public @NotNull ServerStatusResult getStatus(@NotNull ServerAddressResolveResult.SuccessResult successResult) {
        IOException statusError = new IOException("Failed to get server status");
        for (ClassicVersion version : ClassicVersion.values()) {
            try (Socket socket = new Socket()) {
                socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
                socket.connect(successResult.getConnectAddress(), 7_000);
                socket.setSoTimeout(7_000);

                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    long sendTime = System.currentTimeMillis();

                    version.writePlayerIdentificationPacket(out);
                    return version.processResponse(successResult, in, sendTime);
                }
            } catch (Exception e) {
                statusError.addSuppressed(e);
            }
        }
        return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, statusError);
    }

    @Override
    public @NotNull String getName() {
        return "Classic Server Status Pinger";
    }

    enum ClassicVersion {
        // reference: https://minecraft.wiki/w/Classic_server_protocol#Client_%E2%86%92_server_packets
        // Untested: Can't find a version to test.
        PROTOCOL_0X07 {
            @Override
            byte getProtocolVersion() {
                return 0x07;
            }

            @Override
            void writePlayerIdentificationPacket(DataOutputStream out) throws IOException {
                super.writePlayerIdentificationPacket(out);
                writeClassicData(out, (byte) 0); // Not sure if it's int or byte
            }
        },

        // Tested Versions:
        //  * Minecraft Classic Server 1.8.2
        PROTOCOL_0X06 {
            @Override
            byte getProtocolVersion() {
                return 0x06;
            }

            @Override
            void writePlayerIdentificationPacket(DataOutputStream out) throws IOException {
                super.writePlayerIdentificationPacket(out);
                writeClassicData(out, (byte) 0); // Not sure if it's int or byte
            }
        },

        // Tested Versions:
        //  * Minecraft Classic Server 1.6
        PROTOCOL_0X05 {
            @Override
            byte getProtocolVersion() {
                return 0x05;
            }
        },

        // Tested Versions:
        //  * Minecraft Classic Server 1.3
        //  * Minecraft Classic Server 1.4
        //  * Minecraft Classic Server 1.4.1
        PROTOCOL_0X04 {
            @Override
            byte getProtocolVersion() {
                return 0x04;
            }
        },

        // Tested Versions:
        //  * Minecraft Classic Server 1.2
        PROTOCOL_0X03 {
            @Override
            byte getProtocolVersion() {
                return 0x03;
            }
        },

        // reference: https://github.com/Lenni0451/MCPing/blob/a7b4fb83822407166f1b66652733520d0137a677/src/main/java/net/lenni0451/mcping/pings/impl/ClassicPing.java#L80
        // Untested: Can't find a version to test.
        PROTOCOL_HEADLESS {
            @Override
            byte getProtocolVersion() {
                // Never called.
                return -0x01;
            }

            @Override
            ServerVersionInfo getVersionInfo() {
                return ServerVersionInfo.of();
            }

            @Override
            void writePlayerIdentificationPacket(DataOutputStream out) throws IOException {
                out.writeByte(0x00); // packet id
                writeClassicData(out, generateFakeName());
            }
        };

        abstract byte getProtocolVersion();

        ServerVersionInfo getVersionInfo() {
            return ServerVersionInfo.of(getProtocolVersion());
        }

        void writePlayerIdentificationPacket(DataOutputStream out) throws IOException {
            out.writeByte(0x00); // packet id

            writeClassicData(out, getProtocolVersion());
            writeClassicData(out, generateFakeName());
            writeClassicData(out, "--");
        }

        @NotNull ServerStatusResult.SuccessResult processResponse(
                @NotNull ServerAddressResolveResult.SuccessResult successResult,
                DataInputStream in,
                long packSendTime
        ) throws IOException {
            byte packetID = in.readByte();
            if (packetID == 0x0E) {
                String kickMessage = readClassicData(in, String.class);
                throw new IllegalArgumentException("Invalid response: " + kickMessage);
            }

            if (packetID != 0x00) {
                throw new IllegalArgumentException("Invalid server identification packet");
            }
            readClassicData(in, Byte.TYPE); // skip protocol number.

            ServerVersionInfo versionInfo = getVersionInfo();
            JsonArray description = new JsonArray();
            description.add(new JsonPrimitive(readClassicData(in, String.class)));
            description.add(new JsonPrimitive("\n"));
            description.add(new JsonPrimitive(readClassicData(in, String.class)));

            long networkLatency = System.currentTimeMillis() - packSendTime;
            return ServerStatusResult.SuccessResult.success(
                    successResult.getRawAddress(), ClassicServerStatusPinger.instance, networkLatency, new ServerStatus(
                            versionInfo, ServerPlayerInfo.of(), description, null, false, null
                    )
            );
        }

    }
}
