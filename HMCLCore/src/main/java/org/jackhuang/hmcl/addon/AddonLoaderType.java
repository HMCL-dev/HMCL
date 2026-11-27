// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.shader.ShaderLoaderType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/// For mods and shaders
@NotNullByDefault
public interface AddonLoaderType {

    List<AddonLoaderType> VALUES = Stream.<AddonLoaderType>concat(
            Arrays.stream(ModLoaderType.values()),
            Arrays.stream(ShaderLoaderType.values())
    ).toList();

    static @Nullable AddonLoaderType of(String name) {
        for (var type : VALUES) {
            if (type.names().contains(name.toLowerCase(Locale.ROOT)))
                return type;
        }
        return null;
    }

    String displayName();

    @Unmodifiable Set<String> names();

}
