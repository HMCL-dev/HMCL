// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
