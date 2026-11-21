// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import com.google.gson.JsonElement;
import org.jackhuang.hmcl.server.status.pinger.ServerStatusPinger;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

public record ServerStatus(
        @NotNull ServerStatusPinger pinger,
        @NotNull ServerAddress originalServerAddress,
        @NotNull InetSocketAddress resolvedAddress,
        long networkLatency,
        @NotNull ProtocolVersion protocolVersion,
        @NotNull PlayerList playerList,
        @NotNull JsonElement description,
        @Nullable String favicon,
        @Nullable ServerModList modList
) {
    public record ProtocolVersion(
            @NotNull VersionType versionType,
            @Nullable String specifiedProtocolName,
            int protocolNumber
    ) {
        public static final ProtocolVersion UNKNOWN = new ProtocolVersion(VersionType.UNKNOWN, null, -1);

        public enum VersionType {
            RELEASE,
            SNAPSHOT,
            LEGACY,
            UNKNOWN
        }
    }

    public record PlayerList(
            int max,
            int online,
            boolean numbered,
            @Nullable List<@NotNull Sample> samples
    ) {
        public static final PlayerList UNKNOWN = new PlayerList(-1, -1, false, null);

        public PlayerList(
                int max,
                int online
        ) {
            this(max, online, true, null);
        }

        public record Sample(
                @NotNull String name,
                @NotNull UUID id
        ) {
            public boolean isAnonymous() {
                if (name.equals("Anonymous Player")) {
                    return id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0;
                }
                return false;
            }
        }
    }

    public record ServerModList(
            @Nullable String type,
            @NotNull List<Mod> modList
    ) {
        public record Mod(
                @NotNull String modId,
                @NotNull String version
        ) {

        }
    }
}
