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
