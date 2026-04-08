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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/// @author Glavo
@NotNullByDefault
public enum Renderer {
    DEFAULT(null),
    ZINK(API.OPENGL),
    LLVMPIPE(API.OPENGL),
    D3D12(API.OPENGL),
    LAVAPIPE(API.VULKAN);

    /// All renderers.
    public static final List<Renderer> ALL = List.of(values());

    /// All renderers that are based on OpenGL.
    public static final List<Renderer> OPENGL_BASED = Stream.of(values())
            .filter(it -> it.getApi() == null || it.getApi() == API.OPENGL)
            .toList();

    private final @Nullable Renderer.API api;

    Renderer(@Nullable Renderer.API api) {
        this.api = api;
    }

    /// Get the Graphics API used by this renderer.
    ///
    /// @return the API used by this renderer, or `null` if the renderer does not target a specific graphics API.
    public @Nullable API getApi() {
        return api;
    }

    /// The Graphics API.
    public enum API {
        OPENGL,
        VULKAN,
    }
}
