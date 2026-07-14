/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.ImmutableSequencedMap;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/// @author huangyuhui
@JsonSerializable
@NotNullByDefault
public record LibrariesDownloadInfo(
        LibraryDownloadInfo artifact,
        @Nullable @Unmodifiable Map<String, LibraryDownloadInfo> classifiers) {

    public LibrariesDownloadInfo {
        classifiers = classifiers == null ? null : ImmutableSequencedMap.copyOf(classifiers);
    }

    public LibrariesDownloadInfo(LibraryDownloadInfo artifact) {
        this(artifact, null);
    }

    @Override
    public Map<String, LibraryDownloadInfo> classifiers() {
        return classifiers == null ? ImmutableSequencedMap.of() : classifiers;
    }
}
