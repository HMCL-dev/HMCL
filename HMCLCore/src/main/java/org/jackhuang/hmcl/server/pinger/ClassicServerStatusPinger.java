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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

// reference: https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Protocol_version_numbers#Classic
// c0.0.2a to c0.30
public final class ClassicServerStatusPinger implements ServerStatusPinger {
    static ClassicServerStatusPinger instance = new ClassicServerStatusPinger();

    private ClassicServerStatusPinger() {

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
        IOException statusError = new IOException("Failed to get server status(classic).");
        for (ClassicVersions version : ClassicVersions.values()) {
            try (Connection connection = ServerStatusPinger.createConnection(successResult)) {

                long sendTime = System.currentTimeMillis();

                version.absentPlayerIdentificationCodec.write(connection.getOut(), version);
                return version.processResponse(successResult, connection.getIn(), sendTime);

            } catch (Exception e) {
                statusError.addSuppressed(e);
                if (ServerStatusPinger.shouldBreakPing(e)) {
                    break;
                }
                if (e instanceof KickResponseException kickResponseException) {
                    if (kickResponseException.kickType.shouldBreak) {
                        break;
                    }
                }
            }
        }
        return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, statusError);
    }

    @Override
    public @NotNull String getName() {
        return "Classic Server Status Pinger";
    }

    private enum ClassicVersions {
        PROTOCOL_0X07((byte) 0x07, "c1.8.3-c1.10.1", AbsentPlayerIdentificationCodec.SALT),
        PROTOCOL_0X06((byte) 0x06, "c1.8.2", AbsentPlayerIdentificationCodec.SALT),
        PROTOCOL_0X05((byte) 0x05, "c1.6", AbsentPlayerIdentificationCodec.PASSWORD),
        PROTOCOL_0X04((byte) 0x04, "c1.3-c1.4.1", AbsentPlayerIdentificationCodec.PASSWORD),
        PROTOCOL_0X03((byte) 0x03, "c1.2", AbsentPlayerIdentificationCodec.PASSWORD),
        PROTOCOL_HEADLESS((byte) -0x01, "", AbsentPlayerIdentificationCodec.OLD) {

            @Override
            ServerVersionInfo getVersionInfo() {
                return ServerVersionInfo.of();
            }

        };

        final byte protocolVersion;
        final String protocolName;
        final AbsentPlayerIdentificationCodec absentPlayerIdentificationCodec;

        ClassicVersions(byte protocolVersion, String protocolName, AbsentPlayerIdentificationCodec absentPlayerIdentificationCodec) {
            this.protocolVersion = protocolVersion;
            this.protocolName = protocolName;
            this.absentPlayerIdentificationCodec = absentPlayerIdentificationCodec;
        }

        ServerVersionInfo getVersionInfo() {
            return ServerVersionInfo.of(protocolVersion, protocolName);
        }

        @NotNull ServerStatusResult.SuccessResult processResponse(
                @NotNull ServerAddressResolveResult.SuccessResult successResult,
                DataInputStream in,
                long packSendTime
        ) throws IOException {
            byte packetID = in.readByte();
            if (packetID == 0x0E) {
                String kickMessage = readClassicData(in, String.class);
                throw new KickResponseException(kickMessage);
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

        enum AbsentPlayerIdentificationCodec {
            SALT {
                @Override
                void write(DataOutputStream out, ClassicVersions version) throws IOException {
                    out.writeByte(0x00); // packet id

                    writeClassicData(out, version.protocolVersion);
                    writeClassicData(out, ServerStatusPinger.generateFakeName());
                    writeClassicData(out, "--");
                    writeClassicData(out, (byte) 0);
                }
            },
            PASSWORD {
                @Override
                void write(DataOutputStream out, ClassicVersions version) throws IOException {
                    out.writeByte(0x00); // packet id

                    writeClassicData(out, version.protocolVersion);
                    writeClassicData(out, ServerStatusPinger.generateFakeName());
                    writeClassicData(out, "--");
                }
            },
            OLD {
                @Override
                void write(DataOutputStream out, ClassicVersions version) throws IOException {
                    out.writeByte(0x00); // packet id
                    writeClassicData(out, ServerStatusPinger.generateFakeName());
                }
            };

            abstract void write(DataOutputStream out, ClassicVersions version) throws IOException;
        }
    }

    private static class KickResponseException extends IllegalStateException {
        private static final Map<String, KickResponseException.KickType> messageTypeMap = Map.of(
                "Too many connection!", KickType.TOO_MANY_CONNECTION,
                "You need to log in!", KickType.KEY_ERROR,
                "The name wasn't verified by minecraft.net!", KickType.KEY_ERROR,
                "The server is full!", KickResponseException.KickType.SERVER_FULL,
                "You're banned!", KickResponseException.KickType.IP_BANNED
        );
        private final KickResponseException.KickType kickType;

        public KickResponseException(String message) {
            super(message);

            this.kickType = messageTypeMap.getOrDefault(message, KickResponseException.KickType.OTHER);
        }

        public KickResponseException.KickType getKickType() {
            return kickType;
        }

        enum KickType {
            TOO_MANY_CONNECTION(true),
            SERVER_FULL(true),
            KEY_ERROR(true),
            IP_BANNED(true),
            OTHER(false);

            public final boolean shouldBreak;

            KickType(boolean shouldBreak) {
                this.shouldBreak = shouldBreak;
            }
        }
    }
}
