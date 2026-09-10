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
