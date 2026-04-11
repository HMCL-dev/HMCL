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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

@NotNullByDefault
public sealed interface Renderer permits Renderer.Default, Renderer.Driver, Renderer.Unknown {

    Default DEFAULT = new Default();

    /// Parse a renderer from a string.
    static Renderer of(String name) {
        if ("DEFAULT".equalsIgnoreCase(name) || name.isBlank())
            return DEFAULT;

        String upper = name.toUpperCase(Locale.ROOT).trim();

        try {
            return OpenGL.valueOf(upper);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Vulkan.valueOf(upper);
        } catch (IllegalArgumentException ignored) {
        }

        return new Unknown(name);
    }

    String name();

    final class Default implements Renderer {
        private Default() {
        }

        @Override
        public String name() {
            return "DEFAULT";
        }
    }

    sealed interface Driver extends Renderer permits Vulkan, OpenGL {

        /// The graphics API that this driver belongs to.
        GraphicsAPI api();

        /// If this driver can be loaded via [mesa-loader-windows](https://github.com/HMCL-dev/mesa-loader-windows),
        /// return the driver name, otherwise return `null`.
        @Contract(pure = true)
        default @Nullable String mesaDriverName() {
            return null;
        }
    }

    enum Vulkan implements Driver {
        /// @see <a href="https://docs.mesa3d.org/drivers/llvmpipe.html">LLVMpipe - The Mesa 3D Graphics Library</a>
        LAVAPIPE("lvp") {
            @Override
            public String mesaDriverName() {
                return "lavapipe";
            }
        },

        /// ## Note
        /// Currently, Dozen does not support the VK_KHR_push_descriptor feature, so it cannot launch Minecraft 26.2
        /// Using Dozen can run Minecraft 1.21.11 + VulkanMod, but it will cause the game to crash after playing for a while
        DOZEN("dzn") {
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
        NVIDIA("nvidia"),

        /// @see <a href="https://docs.mesa3d.org/drivers/nvk.html">NVK - The Mesa 3D Graphics Library</a>
        NVIDIA_NVK("nouveau") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.LINUX;
            }
        },

        /// @see <a href="https://github.com/GPUOpen-Drivers/AMDVLK">GPUOpen-Drivers/AMDVLK - GitHub</a>
        AMDVLK("amd") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.LINUX;
            }
        },

        /// @see <a href="https://docs.mesa3d.org/drivers/radv.html">RADV - The Mesa 3D Graphics Library</a>
        AMD_RADV("radeon"),

        /// @see <a href="https://docs.mesa3d.org/drivers/anv.html">ANV - The Mesa 3D Graphics Library</a>
        INTEL_ANV("intel"),

        /// Intel HasVK driver.
        INTEL_HASVK("intel_hasvk"),

        MOLTENVK("MoltenVK") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.MACOS;
            }
        },

        /// @see <a href="https://docs.mesa3d.org/drivers/kosmickrisp.html">KosmicKrisp - The Mesa 3D Graphics Library</a>
        KOSMICKRISP("kosmickrisp_mesa") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.MACOS && platform.arch() == Architecture.ARM64;
            }
        },

        /// @see <a href="https://docs.mesa3d.org/drivers/powervr.html">PowerVR - The Mesa 3D Graphics Library</a>
        POWERVR("powervr") {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.LINUX;
            }
        };

        private final String icdName;

        Vulkan(String icdName) {
            this.icdName = icdName;
        }

        @Override
        public GraphicsAPI api() {
            return GraphicsAPI.VULKAN;
        }

        public String icdName() {
            return icdName;
        }
    }

    enum OpenGL implements Driver {
        /// @see <a href="https://docs.mesa3d.org/drivers/llvmpipe.html">LLVMpipe - The Mesa 3D Graphics Library</a>
        LLVMPIPE {
            @Override
            public String mesaDriverName() {
                return "llvmpipe";
            }
        },
        ZINK {
            @Override
            public String mesaDriverName() {
                return "zink";
            }
        },
        D3D12 {
            @Override
            public boolean isSupportedOn(Platform platform) {
                return platform.os() == OperatingSystem.WINDOWS;
            }

            @Override
            public String mesaDriverName() {
                return "d3d12";
            }
        };

        @Override
        public GraphicsAPI api() {
            return GraphicsAPI.VULKAN;
        }
    }

    /// Unknown renderer.
    record Unknown(String name) implements Renderer {
    }

    default boolean isSupportedOn(Platform platform) {
        return true;
    }
}
