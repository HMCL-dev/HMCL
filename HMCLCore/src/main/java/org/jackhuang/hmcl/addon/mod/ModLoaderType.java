// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.mod;

import org.jackhuang.hmcl.addon.AddonLoaderType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

@NotNullByDefault
public enum ModLoaderType implements AddonLoaderType {
    UNKNOWN("Unknown", null),
    FORGE("Forge", "INST_FORGE", "forge"),
    CLEANROOM("Cleanroom", "INST_CLEANROOM", "cleanroom"),
    NEO_FORGE("NeoForge", "INST_NEOFORGE", "neoforge"),
    FABRIC("Fabric", "INST_FABRIC", "fabric"),
    QUILT("Quilt", "INST_QUILT", "quilt"),
    LITE_LOADER("LiteLoader", "INST_LITELOADER", "liteloader"),
    LEGACY_FABRIC("Legacy Fabric", "INST_LEGACYFABRIC", "legacy-fabric");

    private final String displayName;
    private final @Nullable String envVarName;
    private final Set<String> names;

    ModLoaderType(String displayName, @Nullable String envVarName, String... names) {
        this.displayName = displayName;
        this.envVarName = envVarName;
        this.names = Set.of(names);
    }

    public @Nullable String getEnvVarName() {
        return envVarName;
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
