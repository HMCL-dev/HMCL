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
package org.jackhuang.hmcl.patchpack;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.versioning.MavenVersionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public record PatchpackInfo(@SerializedName("formatVersion") int formatVersion,
                            @SerializedName("name") @NotNull String name,
                            @SerializedName("description") @Nullable String description,
                            @SerializedName("modpackVersionRange") @Nullable String modpackVersionRange,
                            @SerializedName("authors") @Nullable @Unmodifiable List<String> authors,
                            @SerializedName("url") @Nullable String url, @SerializedName("diff") @Nullable Diff diff) {
    public static final String FILE_NAME = "patchpackinfo.json";

    public @Nullable MavenVersionRange parsedModpackVersionRange() {
        if (modpackVersionRange == null || modpackVersionRange.isBlank()) return null;

        try {
            return MavenVersionRange.parse(modpackVersionRange);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isOutOfRange(@Nullable String modpackVersion) {
        if (modpackVersion == null) return false;

        MavenVersionRange range = parsedModpackVersionRange();
        return range != null && !range.contains(modpackVersion);
    }

    public record Diff(@SerializedName("delete") @Nullable @Unmodifiable List<String> delete,
                       @SerializedName("rename") @Nullable @Unmodifiable List<Rename> rename) {
        public record Rename(@SerializedName("from") @NotNull String from, @SerializedName("to") @NotNull String to) {
        }
    }
}
