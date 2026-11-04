// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public sealed interface ServerPlayerInfo {

    static FullServerPlayerInfo of(int max, int online, @NotNull List<FullServerPlayerInfo.@NotNull Sample> samples) {
        return new FullServerPlayerInfo(max, online, samples);
    }

    static OnlyPlayerNumberServerVersionInfo of(int max, int online) {
        return new OnlyPlayerNumberServerVersionInfo(max, online);
    }

    static UnknownServerPlayerInfo of() {
        return UnknownServerPlayerInfo.instance;
    }

    final class UnknownServerPlayerInfo implements ServerPlayerInfo {
        static UnknownServerPlayerInfo instance = new UnknownServerPlayerInfo();

        private UnknownServerPlayerInfo() {
        }
    }

    record OnlyPlayerNumberServerVersionInfo(
            int max,
            int online
    ) implements ServerPlayerInfo {

    }

    record FullServerPlayerInfo(
            int max,
            int online,
            @NotNull List<@NotNull Sample> samples
    ) implements ServerPlayerInfo {
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
}
