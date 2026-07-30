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
