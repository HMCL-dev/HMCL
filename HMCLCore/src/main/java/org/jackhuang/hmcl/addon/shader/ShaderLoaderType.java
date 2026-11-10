// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.shader;

import org.jackhuang.hmcl.addon.AddonLoaderType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

@NotNullByDefault
public enum ShaderLoaderType implements AddonLoaderType {
    OPTIFINE_IRIS("Optifine/Iris", "optifine", "iris"),
    APERTURE("Aperture", "aperture");

    private final String displayName;
    private final Set<String> names;

    ShaderLoaderType(String displayName, String... names) {
        this.displayName = displayName;
        this.names = Set.of(names);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public @Unmodifiable Set<String> names() {
        return names;
    }
}
