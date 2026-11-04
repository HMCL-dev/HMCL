// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.pinger;

import com.google.gson.JsonArray;
import org.jackhuang.hmcl.server.resolver.ServerAddressResolveResult;
import org.jackhuang.hmcl.server.status.ServerPlayerInfo;
import org.jackhuang.hmcl.server.status.ServerStatus;
import org.jackhuang.hmcl.server.status.ServerStatusResult;
import org.jackhuang.hmcl.server.status.ServerVersionInfo;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// reference: https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Protocol_version_numbers#Alpha
// a1.0.15 to 11w50a
public sealed abstract class OldServerStatusPinger implements ServerStatusPinger {
    protected final String simpleName;

    protected OldServerStatusPinger(String simpleName) {
        this.simpleName = simpleName;
    }

    protected abstract List<Version> getVersions();

    @Override
    public @NotNull ServerStatusResult getStatus(@NotNull ServerAddressResolveResult.SuccessResult successResult) {
        IOException statusError = new IOException("Failed to get server status(" + simpleName + ").");
        for (Version checkVersion : getVersions()) {
            try (Connection connection = ServerStatusPinger.createConnection(successResult)) {
                checkVersion.writeHandshakeRequest(connection.getOut());
                String username = checkVersion.processHandshakeResponse(connection.getIn());
                long sendTime = System.currentTimeMillis();
                checkVersion.absentLoginRequestCodec.write(connection.getOut(), checkVersion);
                return checkVersion.processLoginResponse(username, successResult, connection.getIn(), sendTime);

            } catch (Exception e) {
                statusError.addSuppressed(e);
                if (ServerStatusPinger.shouldBreakPing(e)) {
                    break;
                }
                if (e instanceof KickResponseException kickResponseException) {
                    if (kickResponseException.kickType.shouldBreak) {
                        break;
                    }
                    // Perhaps a binary search approach could be considered to accelerate the process based on the results of the kicks.
                }
            }
        }
        return ServerStatusResult.failure(ServerStatusResult.FailureResult.Reason.EXCEPTION, statusError);
    }

    protected enum AbsentLoginRequestCodec {
        KEY {
            @Override
            void write(DataOutputStream out, Version version) throws IOException {
                out.write(0x01);
                out.writeInt(version.protocolVersion);
                version.stringCodec.write(out, ServerStatusPinger.generateFakeName());
                out.writeLong(0);
                out.writeInt(0);
                out.writeByte(0);
                out.writeByte(0);
                out.writeByte(0);
                out.writeByte(0);
            }
        },
        SALT {
            @Override
            void write(DataOutputStream out, Version version) throws IOException {
                out.write(0x01);
                out.writeInt(version.protocolVersion);
                version.stringCodec.write(out, ServerStatusPinger.generateFakeName());
                version.stringCodec.write(out, "");
                out.writeLong(0);
                out.writeByte(0);
            }
        },
        PASSWORD {
            @Override
            void write(DataOutputStream out, Version version) throws IOException {
                out.write(0x01);
                out.writeInt(version.protocolVersion);
                version.stringCodec.write(out, ServerStatusPinger.generateFakeName());
                version.stringCodec.write(out, "");
            }
        };

        abstract void write(DataOutputStream out, Version version) throws IOException;
    }

    protected enum StringCodec {
        NEWER {
            @Override
            public void write(DataOutputStream out, String string) throws IOException {
                if (string.length() > 32767) {
                    throw new IOException("String too big");
                }

                out.writeShort(string.length());
                out.writeChars(string);
            }

            @Override
            public String read(DataInputStream in, int size) throws IOException {
                short stringLength = in.readShort();
                if (stringLength > size) {
                    throw new IOException("Received string length longer than maximum allowed (" + stringLength + " > " + size + ")");
                }

                if (stringLength < 0) {
                    throw new IOException("Received string length is less than zero! Weird string!");
                }

                StringBuilder stringBuilder = new StringBuilder();
                for (byte b = 0; b < stringLength; b++) {
                    stringBuilder.append(in.readChar());
                }
                return stringBuilder.toString();
            }
        },
        OLD {
            @Override
            public void write(DataOutputStream out, String string) throws IOException {
                out.writeUTF(string);
            }

            @Override
            public String read(DataInputStream in, int size) throws IOException {
                String string = in.readUTF();
                if (string.length() > size) {
                    throw new IOException("Received string length longer than maximum allowed (" + string.length() + " > " + size + ")");
                }
                return string;
            }
        };

        public abstract void write(DataOutputStream out, String string) throws IOException;

        public abstract String read(DataInputStream in, int size) throws IOException;
    }

    public static final class AlphaServerStatusPinger extends OldServerStatusPinger {
        public static AlphaServerStatusPinger instance = new AlphaServerStatusPinger();

        private final List<Version> versions = List.of(
                new Version((byte) 0x06, "a1.2.6-a1.2.3", StringCodec.OLD, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x05, "a1.2.3", StringCodec.OLD, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x04, "a1.2.2-a1.2.2-1617", StringCodec.OLD, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x03, "a1.2.1-a1.2.0", StringCodec.OLD, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x02, "a1.1.2-a1.1.0", StringCodec.OLD, AbsentLoginRequestCodec.PASSWORD),
                new Version((byte) 0x01, "a1.0.17", StringCodec.OLD, AbsentLoginRequestCodec.PASSWORD),
                new Version((byte) 0x0E, "a1.0.16", StringCodec.OLD, AbsentLoginRequestCodec.PASSWORD),
                new Version((byte) 0x0d, "a1.0.15", StringCodec.OLD, AbsentLoginRequestCodec.PASSWORD)
        );

        private AlphaServerStatusPinger() {
            super("alpha");
        }

        @Override
        public @NotNull String getName() {
            return "Alpha Server Status Pinger";
        }

        @Override
        protected List<Version> getVersions() {
            return versions;
        }
    }

    public static final class BetaServerStatusPinger extends OldServerStatusPinger {

        public static BetaServerStatusPinger instance = new BetaServerStatusPinger();

        private final List<Version> versions = List.of(
                new Version((byte) 0x16, "b1.9-pre6", StringCodec.NEWER, AbsentLoginRequestCodec.KEY),
                new Version((byte) 0x15, "b1.9-pre5", StringCodec.NEWER, AbsentLoginRequestCodec.KEY),
                new Version((byte) 0x14, "b1.9-pre4-1441,b1.9-pre4-1435", StringCodec.NEWER, AbsentLoginRequestCodec.KEY),
                new Version((byte) 0x13, "b1.9-pre4-1425,b1.9-pre3,b1.9-pre2", StringCodec.NEWER, AbsentLoginRequestCodec.KEY),
                new Version((byte) 0x12, "b1.9-pre1", StringCodec.NEWER, AbsentLoginRequestCodec.KEY),
                new Version((byte) 0x11, "b1.8.1-b1.8", StringCodec.NEWER, AbsentLoginRequestCodec.KEY),
                new Version((byte) 0x0f, "b1.8-pre", StringCodec.NEWER, AbsentLoginRequestCodec.KEY),
                new Version((byte) 0x0e, "b1.7.3-b1.7", StringCodec.NEWER, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x0d, "b1.7-pre-b1.6", StringCodec.NEWER, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x0c, "b1.6-tb3,", StringCodec.NEWER, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x0b, "b1.6-pre,b1.5", StringCodec.NEWER, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x0a, "b1.4", StringCodec.OLD, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x09, "b1.3", StringCodec.OLD, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x08, "b1.2", StringCodec.OLD, AbsentLoginRequestCodec.SALT),
                new Version((byte) 0x07, "b1.1-b1.0", StringCodec.OLD, AbsentLoginRequestCodec.SALT)
        );

        private BetaServerStatusPinger() {
            super("beta");
        }

        @Override
        public @NotNull String getName() {
            return "Beta Server Status Pinger";
        }

        @Override
        protected List<Version> getVersions() {
            return versions;
        }
    }

    private static class KickResponseException extends IllegalStateException {
        private static final Map<String, KickType> messageTypeMap = Map.of(
                "Outdated server!", KickType.OUTDATED_SERVER,
                "Outdated client!", KickType.OUTDATED_CLIENT,
                "Failed to verify username!", KickType.KEY_ERROR,
                "The server is full!", KickType.SERVER_FULL
        );
        private final KickType kickType;

        public KickResponseException(String message) {
            super(message);

            this.kickType = messageTypeMap.getOrDefault(message, KickType.OTHER);
        }

        public KickType getKickType() {
            return kickType;
        }

        enum KickType {
            OUTDATED_SERVER(false),
            OUTDATED_CLIENT(false),
            KEY_ERROR(true),
            BANNED_IP(true),
            SERVER_FULL(true),
            OTHER(false);

            public final boolean shouldBreak;

            KickType(boolean shouldBreak) {
                this.shouldBreak = shouldBreak;
            }
        }
    }

    protected final class Version {
        private final byte protocolVersion;
        private final String protocolName;
        private final StringCodec stringCodec;
        private final AbsentLoginRequestCodec absentLoginRequestCodec;

        protected Version(
                byte protocolVersion,
                String protocolName,
                StringCodec stringCodec,
                AbsentLoginRequestCodec absentLoginRequestCodec
        ) {
            this.protocolVersion = protocolVersion;
            this.protocolName = protocolName;
            this.stringCodec = stringCodec;
            this.absentLoginRequestCodec = absentLoginRequestCodec;
        }

        void writeHandshakeRequest(DataOutputStream out) throws IOException {
            out.write(0x02);
            String str = ServerStatusPinger.generateFakeName();
            stringCodec.write(out, str);
        }

        String processHandshakeResponse(DataInputStream in) throws IOException {
            int packetID = in.read();
            if (packetID == 0xff) {
                String message = stringCodec.read(in, 32768);
                throw new KickResponseException(message);
            }

            if (packetID != 0x02) {
                throw new IllegalArgumentException("Invalid handshake response");
            }
            return stringCodec.read(in, 32768);
        }

        @NotNull ServerStatusResult.SuccessResult processLoginResponse(@NotNull String username, @NotNull ServerAddressResolveResult.SuccessResult successResult, DataInputStream in, long packSendTime) throws IOException {
            int packetID = in.read();
            if (packetID == 0xff) {
                String message = stringCodec.read(in, 32768);
                throw new KickResponseException(message);
            }

            if (packetID != 0x01) {
                throw new IllegalArgumentException("Invalid login response");
            }

            // skip, useless data

            ServerVersionInfo versionInfo = getVersionInfo();
            JsonArray description = new JsonArray();

            long networkLatency = System.currentTimeMillis() - packSendTime;
            return ServerStatusResult.SuccessResult.success(
                    successResult.getRawAddress(), OldServerStatusPinger.this, networkLatency, new ServerStatus(
                            versionInfo, ServerPlayerInfo.of(), description, null, false, null
                    )
            );
        }

        ServerVersionInfo getVersionInfo() {
            return ServerVersionInfo.of(protocolVersion, protocolName);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Version) obj;
            return this.protocolVersion == that.protocolVersion &&
                    Objects.equals(this.protocolName, that.protocolName) &&
                    Objects.equals(this.stringCodec, that.stringCodec) &&
                    Objects.equals(this.absentLoginRequestCodec, that.absentLoginRequestCodec);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protocolVersion, protocolName, stringCodec, absentLoginRequestCodec);
        }

        @Override
        public String toString() {
            return "Version[" +
                    "protocolVersion=" + protocolVersion + ", " +
                    "protocolName=" + protocolName + ", " +
                    "stringCodec=" + stringCodec + ", " +
                    "absentLoginRequestCodec=" + absentLoginRequestCodec + ']';
        }

    }
}
