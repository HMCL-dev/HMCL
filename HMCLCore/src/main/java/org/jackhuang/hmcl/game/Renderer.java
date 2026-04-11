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

import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@NotNullByDefault
public sealed interface Renderer {

    GraphicsAPI api();

    String name();

    default boolean isSupported(GraphicsAPI api) {
        return this.api() == api || this.api() == GraphicsAPI.DEFAULT;
    }

    enum Known implements Renderer {
        DEFAULT(GraphicsAPI.DEFAULT, ""),

        // Vulkan

        /// @see <a href="https://docs.mesa3d.org/drivers/llvmpipe.html">LLVMpipe - The Mesa 3D Graphics Library</a>
        LAVAPIPE(GraphicsAPI.VULKAN, "lvp") {
            @Override
            public String mesaDriverName() {
                return "lavapipe";
            }
        },

        /// ## Note
        /// Currently, Dozen does not support the VK_KHR_push_descriptor feature, so it cannot launch Minecraft 26.2
        /// Using Dozen can run Minecraft 1.21.11 + VulkanMod, but it will cause the game to crash after playing for a while
        DOZEN(GraphicsAPI.VULKAN, "dzn") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.WINDOWS;
            }

            @Override
            public String mesaDriverName() {
                return "dzn";
            }
        },

        /// @see <a href="https://developer.nvidia.com/vulkan">Vulkan Open Standard Modern GPU API | NVIDIA Developer</a>
        NVIDIA(GraphicsAPI.VULKAN, "nvidia"),

        /// @see <a href="https://docs.mesa3d.org/drivers/nvk.html">NVK - The Mesa 3D Graphics Library</a>
        NVIDIA_NVK(GraphicsAPI.VULKAN, "nouveau") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.LINUX;
            }
        },

        /// @see <a href="https://docs.mesa3d.org/drivers/radv.html">RADV - The Mesa 3D Graphics Library</a>
        AMD_RADV(GraphicsAPI.VULKAN, "radeon"),

        /// @see <a href="https://docs.mesa3d.org/drivers/anv.html">ANV - The Mesa 3D Graphics Library</a>
        INTEL_ANV(GraphicsAPI.VULKAN, "intel"),

        /// Intel HasVK driver.
        INTEL_HASVK(GraphicsAPI.VULKAN, "intel_hasvk"),

        MOLTENVK(GraphicsAPI.VULKAN, "MoltenVK") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.MACOS;
            }
        },

        /// @see <a href="https://docs.mesa3d.org/drivers/kosmickrisp.html">KosmicKrisp - The Mesa 3D Graphics Library</a>
        KOSMICKRISP(GraphicsAPI.VULKAN, "kosmickrisp_mesa") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.MACOS && platform.arch() == Architecture.ARM64;
            }
        },

        // OpenGL

        /// @see <a href="https://docs.mesa3d.org/drivers/llvmpipe.html">LLVMpipe - The Mesa 3D Graphics Library</a>
        LLVMPIPE(GraphicsAPI.OPENGL, "") {
            @Override
            public String mesaDriverName() {
                return "llvmpipe";
            }
        },
        ZINK(GraphicsAPI.OPENGL, "") {
            @Override
            public String mesaDriverName() {
                return "zink";
            }
        },
        D3D12(GraphicsAPI.OPENGL, "") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.WINDOWS;
            }

            @Override
            public String mesaDriverName() {
                return "d3d12";
            }
        },
        ;

        /// Map the graphics API to supported renderers.
        public static final Map<GraphicsAPI, List<Known>> SUPPORTED;

        static {
            var map = new EnumMap<GraphicsAPI, List<Known>>(GraphicsAPI.class);

            for (var api : GraphicsAPI.values()) {
                map.put(api, Stream.of(values())
                        .filter(it -> it.isSupported(api) && it.isSupportedOn(Platform.SYSTEM_PLATFORM))
                        .toList());
            }

            SUPPORTED = map;
        }

        private final GraphicsAPI api;

        private final String icdName;

        Known(GraphicsAPI api, String icdName) {
            this.api = api;
            this.icdName = icdName;
        }

        /// Get the Graphics API used by this renderer.
        @Override
        public GraphicsAPI api() {
            return api;
        }

        public @Nullable String mesaDriverName() {
            return null;
        }

        public String icdName() {
            return icdName;
        }
    }

    record Unknown(GraphicsAPI api, String name) implements Renderer {
    }

    default boolean isSupportedOn(Platform platform) {
        return true;
    }
}
