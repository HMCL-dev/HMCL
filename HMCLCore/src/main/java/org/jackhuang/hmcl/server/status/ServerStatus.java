// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ServerStatus(
        @NotNull ServerVersionInfo version,
        @NotNull ServerPlayerInfo players,
        @NotNull JsonElement description,
        @Nullable String favicon,
        boolean enforcesSecureChat,
        @Nullable ServerModInfo modInfo
) {

    public record ServerModInfo(
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
