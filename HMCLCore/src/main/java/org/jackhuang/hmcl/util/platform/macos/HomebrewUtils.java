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
package org.jackhuang.hmcl.util.platform.macos;

import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;

/// @author Glavo
@NotNullByDefault
public final class HomebrewUtils {

    /// The path to Homebrew prefix.
    ///
    /// - For macOS x86_64, it is `/usr/local`;
    /// - For macOS ARM64, it is `/opt/homebrew`;
    /// - For other operating systems, it is undefined.
    public static final Path HOMEBREW_PREFIX;

    /// The path to `libvulkan.1.dylib`.
    ///
    /// For non-macOS operating systems, it is undefined.
    public static final Path LIB_VULKAN;

    static {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            // TODO: custom Homebrew prefix for macOS
            HOMEBREW_PREFIX = Architecture.SYSTEM_ARCH.isX86()
                    ? Path.of("/usr/local")
                    : Path.of("/opt/homebrew");

            LIB_VULKAN = HOMEBREW_PREFIX.resolve("lib/libvulkan.1.dylib");
        } else {
            // For other operating systems, we don't need Homebrew.
            var placeholder = Path.of("");
            HOMEBREW_PREFIX = placeholder;
            LIB_VULKAN = placeholder;
        }
    }

    private HomebrewUtils() {
    }
}
