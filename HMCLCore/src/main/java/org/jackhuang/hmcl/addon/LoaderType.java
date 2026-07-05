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
import org.jackhuang.hmcl.util.Either;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.Locale;

/// For mods and shaders
public interface LoaderType {

    static boolean mightBeLoader(String str) {
        if (StringUtils.isBlank(str)
                || !StringUtils.isASCII(str)
                || "client".equalsIgnoreCase(str) || "server".equalsIgnoreCase(str))
            return false;
        int l = str.length();
        for (int i = 0; i < l; i++) {
            char c = str.charAt(i);
            if (c != '-' && c != ' ' && !StringUtils.isAlphabetic(c)) return false;
        }
        return true;
    }

    static Either<LoaderType, String> toEither(String loader) {
        return switch (loader.toLowerCase(Locale.ROOT)) {
            case "fabric" -> Either.left(ModLoaderType.FABRIC);
            case "forge" -> Either.left(ModLoaderType.FORGE);
            case "neoforge" -> Either.left(ModLoaderType.NEO_FORGE);
            case "quilt" -> Either.left(ModLoaderType.QUILT);
            case "liteloader" -> Either.left(ModLoaderType.LITE_LOADER);
            case "legacy-fabric" -> Either.left(ModLoaderType.LEGACY_FABRIC);
            default -> Either.right(loader);
        };
    }

}
