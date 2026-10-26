// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
}
