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
