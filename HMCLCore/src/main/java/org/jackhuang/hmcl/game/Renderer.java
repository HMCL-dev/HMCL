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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/// @author Glavo
@NotNullByDefault
public enum Renderer {
    DEFAULT(null, null, null),

    VULKAN(API.VULKAN, null, null),
    LAVAPIPE(API.VULKAN, "lavapipe", "lvp"),

    // Currently, Dozen does not support the VK_KHR_push_descriptor feature, so it cannot launch Minecraft 26.2
    // Using Dozen can run Minecraft 1.21.11 + VulkanMod, but it will cause the game to crash after playing for a while
    // DOZEN(API.VULKAN, "dzn", "dzn"),

    OPENGL(API.OPENGL, null, null),
    ZINK(API.OPENGL, "zink", null),
    LLVMPIPE(API.OPENGL, "llvmpipe", null),
    D3D12(API.OPENGL, "d3d12", null),
    ;

    /// All renderers.
    public static final List<Renderer> ALL = List.of(values());

    /// All renderers that are based on OpenGL.
    public static final List<Renderer> OPENGL_BASED = Stream.of(values())
            .filter(it -> it.getApi() == null || (it.getApi() == API.OPENGL && it != OPENGL))
            .toList();

    /// All renderers that are based on Vulkan.
    public static final List<Renderer> VULKAN_BASED = Stream.of(values())
            .filter(it -> it.getApi() == null || (it.getApi() == API.VULKAN && it != VULKAN))
            .toList();

    private final @Nullable Renderer.API api;

    private final @Nullable String loaderName;
    private final @Nullable String icdName;

    Renderer(@Nullable Renderer.API api, @Nullable String loaderName, @Nullable String icdName) {
        this.api = api;
        this.loaderName = loaderName;
        this.icdName = icdName;
    }

    /// Get the Graphics API used by this renderer.
    ///
    /// @return the API used by this renderer, or `null` if the renderer does not target a specific graphics API.
    public @Nullable API getApi() {
        return api;
    }

    public @Nullable String getMesaLoaderName() {
        return loaderName;
    }

    public @Nullable String getIcdName() {
        return icdName;
    }

    public @Nullable String getIcdFileName() {
        return icdName != null ? icdName + "_icd.json" : null;
    }

    /// The Graphics API.
    public enum API {
        OPENGL,
        VULKAN;

        private final String minecraftArg = name().toLowerCase(Locale.ROOT);

        public String getMinecraftArg() {
            return minecraftArg;
        }
    }
}
