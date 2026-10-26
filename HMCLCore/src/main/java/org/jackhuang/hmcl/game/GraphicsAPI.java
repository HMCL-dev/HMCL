// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Locale;

/// The Graphics API.
@NotNullByDefault
public enum GraphicsAPI {
    DEFAULT,
    OPENGL,
    VULKAN;

    private static final GameVersionNumber VERSION_26_2_SNAP1 = GameVersionNumber.asGameVersion("26.2-snapshot-1");
    private static final GameVersionNumber VERSION_26_2 = GameVersionNumber.asGameVersion("26.2");

    public static GraphicsAPI getDefaultGraphicsAPI(GameVersionNumber gameVersionNumber) {
        // Before Minecraft 26.2-snapshot-1, all versions only supported OpenGL
        if (gameVersionNumber.compareTo(VERSION_26_2_SNAP1) < 0) {
            return OPENGL;
        }

        // From Minecraft 26.2-snapshot-1 to 26.2-rc-2, defaults to Vulkan
        if (gameVersionNumber.compareTo(VERSION_26_2) < 0) {
            return VULKAN;
        }

        // After the official release of Minecraft 26.2, the default graphics API switched back to OpenGL
        return OPENGL;
    }

    private final String minecraftArg = name().toLowerCase(Locale.ROOT);

    public String getMinecraftArg() {
        return minecraftArg;
    }

    public boolean isSupported(GameVersionNumber version) {
        return switch (this) {
            case DEFAULT, OPENGL -> true;
            case VULKAN -> version.compareTo(VERSION_26_2_SNAP1) >= 0;
        };
    }
}
