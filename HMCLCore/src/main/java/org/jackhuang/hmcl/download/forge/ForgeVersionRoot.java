// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.forge;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/// @author huangyuhui
@NotNullByDefault
record ForgeVersionRoot(String artifact,
                        String webpath,
                        @Nullable String adfly,
                        @Nullable String homepage,
                        @Nullable String name,
                        @Nullable Map<String, int[]> branches,
                        Map<String, int[]> mcversion,
                        @Nullable Map<String, Integer> promos,
                        Map<Integer, ForgeVersion> number) {

    public ForgeVersionRoot {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(mcversion, "mcversion");
    }

}
