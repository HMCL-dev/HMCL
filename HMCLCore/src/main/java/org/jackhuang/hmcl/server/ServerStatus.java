// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping#Status_Response
public record ServerStatus(
        @Nullable Version version,
        @NotNull Players players,
        @NotNull JsonElement description,
        @Nullable String favicon,
        boolean enforcesSecureChat,
        @Nullable ModInfo modInfo
) {

    public record Version(
            @NotNull String name,
            int version
    ) {

    }

    public record Players(
            int max,
            int online,
            @NotNull List<@NotNull Sample> samples
    ) {
        public record Sample(
                @NotNull String name,
                @NotNull UUID id
        ) {
            public static Sample ANONYMOUS_PLAYER = new Sample("Anonymous Player", new UUID(0, 0));
        }
    }

    public record ModInfo(
            @NotNull String type,
            @NotNull List<Mod> modList
    ) {
        public record Mod(
                @NotNull String modId,
                @NotNull String version
        ) {

        }
    }
}
