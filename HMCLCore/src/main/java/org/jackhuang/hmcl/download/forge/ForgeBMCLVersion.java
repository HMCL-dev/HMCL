// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@NotNullByDefault
@JsonSerializable
record ForgeBMCLVersion(
        @Nullable String branch,
        int build,
        String mcversion,
        @Nullable String modified,
        String version,
        List<File> files) {

    public ForgeBMCLVersion {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(mcversion, "mcversion");
        Objects.requireNonNull(version, "version");
    }

    @JsonSerializable
    public record File(String format, String category, String hash) {
    }
}
