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
package org.jackhuang.hmcl.server;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record ServerStatus(
        long networkLatency,
        @NotNull Version version,
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
