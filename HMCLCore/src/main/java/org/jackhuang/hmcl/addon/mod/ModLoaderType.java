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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
public enum ModLoaderType {
    UNKNOWN(null),
    FORGE("INST_FORGE"),
    CLEANROOM("INST_CLEANROOM"),
    NEO_FORGE("INST_NEOFORGE"),
    FABRIC("INST_FABRIC"),
    QUILT("INST_QUILT"),
    LITE_LOADER("INST_LITELOADER"),
    LEGACY_FABRIC("INST_LEGACYFABRIC");

    private final @Nullable String envVarName;

    ModLoaderType(@Nullable String envVarName) {
        this.envVarName = envVarName;
    }

    public @Nullable String getEnvVarName() {
        return envVarName;
    }
}
