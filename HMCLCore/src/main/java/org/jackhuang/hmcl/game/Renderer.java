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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// @author Glavo
@NotNullByDefault
public enum Renderer {
    DEFAULT(GraphicsAPI.DEFAULT, null, null),

    LAVAPIPE(GraphicsAPI.VULKAN, "lavapipe", "lvp"),

    // Currently, Dozen does not support the VK_KHR_push_descriptor feature, so it cannot launch Minecraft 26.2
    // Using Dozen can run Minecraft 1.21.11 + VulkanMod, but it will cause the game to crash after playing for a while
    DOZEN(GraphicsAPI.VULKAN, "dzn", "dzn"),

    LLVMPIPE(GraphicsAPI.OPENGL, "llvmpipe", null),
    ZINK(GraphicsAPI.OPENGL, "zink", null),
    D3D12(GraphicsAPI.OPENGL, "d3d12", null),
    ;

    /// Map the graphics API to supported renderers.
    public static final Map<GraphicsAPI, List<Renderer>> SUPPORTED;

    static {
        var map = new EnumMap<GraphicsAPI, List<Renderer>>(GraphicsAPI.class);

        for (var api : GraphicsAPI.values()) {
            map.put(api, Stream.of(values()).filter(it -> it.isSupported(api)).toList());
        }

        SUPPORTED = map;
    }

    private final GraphicsAPI api;

    private final @Nullable String loaderName;
    private final @Nullable String icdName;

    Renderer(GraphicsAPI api, @Nullable String loaderName, @Nullable String icdName) {
        this.api = api;
        this.loaderName = loaderName;
        this.icdName = icdName;
    }

    /// Get the Graphics API used by this renderer.
    public GraphicsAPI getApi() {
        return api;
    }

    public boolean isSupported(GraphicsAPI api) {
        return this.api == api || this.api == GraphicsAPI.DEFAULT;
    }

    public @Nullable String getMesaDriverName() {
        return loaderName;
    }

    public @Nullable String getIcdName() {
        return icdName;
    }

    public @Nullable String getIcdFileName() {
        return icdName != null ? icdName + "_icd.json" : null;
    }

}
