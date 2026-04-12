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

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.HardwareVendor;
import org.jackhuang.hmcl.util.platform.macos.HomebrewUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
@NotNullByDefault
public sealed interface Renderer permits Renderer.Default, Renderer.Driver, Renderer.Unknown {

    /// Default renderer.
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

    /// Get all supported renderers for a given graphics API.
    static @Unmodifiable List<Renderer> getSupported(GraphicsAPI api) {
        return switch (api) {
            case DEFAULT -> List.of(DEFAULT);
            case VULKAN -> Vulkan.Holder.SUPPORTED;
            case OPENGL -> OpenGL.SUPPORTED;
        };
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
        /// Mesa Lavapipe driver.
        ///
        /// It is a software rasterizer.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/llvmpipe.html">LLVMpipe - The Mesa 3D Graphics Library</a>
        LAVAPIPE("lvp") {
            @Override
            public String mesaDriverName() {
                return "lavapipe";
            }
        },

        /// Mesa Dozen driver.
        ///
        /// It is a Vulkan driver based on DirectX 12.
        ///
        /// ## Note
        /// Currently, Dozen does not support the VK_KHR_push_descriptor feature, so it cannot launch Minecraft 26.2
        /// Using Dozen can run Minecraft 1.21.11 + VulkanMod, but it will cause the game to crash after playing for a while
        DOZEN("dzn") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.WINDOWS;
            }

            @Override
            public String mesaDriverName() {
                return "dzn";
            }
        },

        /// NVIDIA Vulkan driver.
        ///
        /// It is a Vulkan driver for NVIDIA GPUs.
        ///
        /// @see <a href="https://developer.nvidia.com/vulkan">Vulkan Open Standard Modern GPU API | NVIDIA Developer</a>
        NVIDIA_VULKAN("nvidia") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return Vulkan.hasCard(cards, HardwareVendor.NVIDIA);
            }
        },

        /// Mesa NVK driver.
        ///
        /// It is a Vulkan driver for NVIDIA GPUs.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/nvk.html">NVK - The Mesa 3D Graphics Library</a>
        NVIDIA_NVK("nouveau") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.LINUX && Vulkan.hasCard(cards, HardwareVendor.NVIDIA);
            }
        },

        /// AMD Open Source Driver for Vulkan
        ///
        /// It is a Vulkan driver for AMD GPUs.
        ///
        /// @see <a href="https://github.com/GPUOpen-Drivers/AMDVLK">GPUOpen-Drivers/AMDVLK - GitHub</a>
        AMDVLK("amd") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.LINUX && Vulkan.hasCard(cards, HardwareVendor.AMD);
            }
        },

        /// Mesa RADV driver.
        ///
        /// It is a Vulkan driver for AMD GCN/RDNA GPUs.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/radv.html">RADV - The Mesa 3D Graphics Library</a>
        AMD_RADV("radeon") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return Vulkan.hasCard(cards, HardwareVendor.AMD);
            }
        },

        /// Intel Vulkan driver.
        ///
        /// It is a Vulkan driver for Intel GPUs.
        INTEL_VULKAN("ig") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.WINDOWS && Vulkan.hasCard(cards, HardwareVendor.INTEL);
            }
        },

        /// Mesa ANV driver.
        ///
        /// It is a Vulkan driver for Intel GPUs.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/anv.html">ANV - The Mesa 3D Graphics Library</a>
        INTEL_ANV("intel") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() != OperatingSystem.WINDOWS && Vulkan.hasCard(cards, HardwareVendor.INTEL);
            }
        },

        /// Intel HASVK driver.
        ///
        /// It is a Vulkan driver for Intel Gen7 (Ivy Bridge / Haswell) and Gen8 (Broadwell) graphics.
        ///
        /// @see <a href="https://gitlab.freedesktop.org/mesa/mesa/-/merge_requests/18208">intel: split vulkan driver between gfx7/8 and above</a>
        INTEL_HASVK("intel_hasvk") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() != OperatingSystem.WINDOWS
                        && cards != null
                        && cards.stream().anyMatch(card -> card.getVendor() == HardwareVendor.INTEL && card.getName().startsWith("Intel HD Graphics "));
            }
        },

        /// Qualcomm Vulkan driver.
        ///
        /// It is a Vulkan driver for Qualcomm Adreno GPUs.
        QUALCOMM("qc") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.equals(Platform.WINDOWS_ARM64);
            }
        },

        /// Mesa Turnip driver.
        ///
        /// It is a Vulkan driver for Qualcomm Adreno GPUs.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/freedreno.html">Freedreno - The Mesa 3D Graphics Library</a>
        TURNIP("freedreno") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() != OperatingSystem.WINDOWS && platform.arch().isArm();
            }
        },

        /// MoltenVK driver.
        ///
        /// It is a Vulkan driver for macOS, iOS, tvOS, and visionOS.
        ///
        /// @see <a href="https://github.com/KhronosGroup/MoltenVK">KhronosGroup/MoltenVK - GitHub</a>
        MOLTENVK("MoltenVK") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.MACOS;
            }
        },

        /// Mesa KosmicKrisp driver.
        ///
        /// It is a Vulkan driver for Apple Silicon hardware.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/kosmickrisp.html">KosmicKrisp - The Mesa 3D Graphics Library</a>
        KOSMICKRISP("kosmickrisp_mesa") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.MACOS && platform.arch() == Architecture.ARM64;
            }
        },

        /// Mesa PowerVR driver.
        ///
        /// It is a Vulkan driver for Imagination Technologies PowerVR GPUs.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/powervr.html">PowerVR - The Mesa 3D Graphics Library</a>
        POWERVR("powervr") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.LINUX && Vulkan.hasCard(cards, HardwareVendor.IMG);
            }
        },

        /// Mesa PanVK driver.
        ///
        /// It is a Vulkan driver for ARM Mali GPUs.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/panfrost.html">Panfrost - The Mesa 3D Graphics Library</a>
        PANVK("panfrost") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.LINUX && platform.arch().isArm();
            }
        },

        /// Mesa V3DV driver.
        ///
        /// It is a Vulkan driver for the Raspberry Pi 4 and Raspberry Pi 5.
        ///
        /// @see <a href="https://docs.mesa3d.org/drivers/v3dv.html">V3DV - The Mesa 3D Graphics Library</a>
        V3DV("broadcom") {
            @Override
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.LINUX && platform.arch().isArm()
                        && Vulkan.hasCard(cards, HardwareVendor.BROADCOM);
            }
        };

        private static final class Holder {
            static final List<Renderer> SUPPORTED;
            static final Map<Vulkan, Path> DRIVER_TO_ICD_FILE;

            static {
                var driverToIcdFile = new EnumMap<Vulkan, Path>(Vulkan.class);
                var supported = new LinkedHashSet<Renderer>();


                // Helper
                String archName = switch (Architecture.SYSTEM_ARCH) {
                    case X86 -> "i686";
                    case X86_64 -> "x86_64";
                    case ARM64 -> "aarch64";
                    default -> Architecture.SYSTEM_ARCH.getCheckedName();
                };

                var icdFileNamePattern = Pattern.compile("(?<name>[a-zA-Z0-9_-]+)_icd(?:\\." + Pattern.quote(archName) + ")?\\.json");
                Map<String, Vulkan> icdNameToDriver = Stream.of(values()).collect(Collectors.toMap(Vulkan::icdName, Function.identity()));

                supported.add(DEFAULT);

                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    supported.addAll(List.of(LAVAPIPE, DOZEN));

                    List<GraphicsCard> graphicsCards = SystemInfo.getGraphicsCards();
                    if (graphicsCards != null) {
                        EnumSet<Vulkan> foundSupported = EnumSet.noneOf(Vulkan.class);
                        for (GraphicsCard card : graphicsCards) {
                            if (!card.getVulkanDriverFiles().isEmpty()) {
                                for (Path icdFile : card.getVulkanDriverFiles()) {
                                    String fileName = FileUtils.getName(icdFile);
                                    if (!fileName.endsWith(".json"))
                                        continue;

                                    Vulkan driver;

                                    Matcher matcher = icdFileNamePattern.matcher(fileName);
                                    if (matcher.matches()) {
                                        String icdName = matcher.group("name");
                                        driver = icdNameToDriver.get(icdName);
                                    } else {
                                        switch (fileName.substring(0, fileName.length() - ".json".length())) {
                                            case "igvk64" -> {
                                                if (Architecture.SYSTEM_ARCH.getBits() == Bits.BIT_64)
                                                    driver = INTEL_VULKAN;
                                                else
                                                    continue;
                                            }
                                            case "igvk32" -> {
                                                if (Architecture.SYSTEM_ARCH.getBits() == Bits.BIT_32)
                                                    driver = INTEL_VULKAN;
                                                else
                                                    continue;
                                            }
                                            case "nv-vk64" -> {
                                                if (Architecture.SYSTEM_ARCH.getBits() == Bits.BIT_64)
                                                    driver = NVIDIA_VULKAN;
                                                else
                                                    continue;
                                            }
                                            case "nv-vk32" -> {
                                                if (Architecture.SYSTEM_ARCH.getBits() == Bits.BIT_32)
                                                    driver = NVIDIA_VULKAN;
                                                else
                                                    continue;
                                            }
                                            case "amd-vulkan64" -> {
                                                if (Architecture.SYSTEM_ARCH.getBits() == Bits.BIT_64)
                                                    driver = AMDVLK;
                                                else
                                                    continue;
                                            }
                                            case "amd-vulkan32" -> {
                                                if (Architecture.SYSTEM_ARCH.getBits() == Bits.BIT_32)
                                                    driver = AMDVLK;
                                                else
                                                    continue;
                                            }
                                            case "qcvk_icd_arm64x" -> {
                                                if (Architecture.SYSTEM_ARCH == Architecture.ARM64)
                                                    driver = QUALCOMM;
                                                else
                                                    continue;
                                            }
                                            default -> {
                                                continue;
                                            }
                                        }
                                    }

                                    driverToIcdFile.putIfAbsent(driver, icdFile);
                                    foundSupported.add(driver);
                                }
                            }
                        }
                        supported.addAll(foundSupported);
                    }

                } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                    // LWJGL integrates MoltenVK, so it is always available
                    supported.add(MOLTENVK);

                    // We need libvulkan.1.dylib to load custom Vulkan drivers
                    if (Files.isRegularFile(HomebrewUtils.LIB_VULKAN)) {
                        Path kosmickrispIcd = HomebrewUtils.HOMEBREW_PREFIX.resolve("share/vulkan/icd.d/kosmickrisp_mesa_icd." + archName + ".json");
                        if (Files.isRegularFile(kosmickrispIcd)) {
                            driverToIcdFile.put(KOSMICKRISP, kosmickrispIcd);
                            supported.add(KOSMICKRISP);
                        }

                        Path lvpIcd = HomebrewUtils.HOMEBREW_PREFIX.resolve("share/vulkan/icd.d/lvp_icd." + archName + ".json");
                        if (Files.isRegularFile(lvpIcd)) {
                            driverToIcdFile.put(LAVAPIPE, lvpIcd);
                            supported.add(LAVAPIPE);
                        }
                    }
                } else {
                    List<Path> icdDirs = switch (OperatingSystem.CURRENT_OS) {
                        case LINUX -> List.of(
                                Path.of("/usr/share/vulkan/icd.d"),
                                Path.of("/etc/vulkan/icd.d")
                        );
                        case FREEBSD -> List.of(Path.of("/usr/local/share/vulkan/icd.d"));
                        default -> List.of();
                    };

                    EnumSet<Vulkan> foundSupported = EnumSet.noneOf(Vulkan.class);
                    for (Path icdDir : icdDirs) {
                        if (!Files.isDirectory(icdDir))
                            continue;
                        try (Stream<Path> stream = Files.list(icdDir)) {
                            for (Path icdFile : Lang.toIterable(stream)) {
                                String fileName = icdFile.getFileName().toString();

                                Matcher matcher = icdFileNamePattern.matcher(fileName);
                                if (matcher.matches()) {
                                    String icdName = matcher.group("name");

                                    Vulkan driver = icdNameToDriver.get(icdName);
                                    if (driver != null) {
                                        driverToIcdFile.put(driver, icdFile);

                                        if (driver.isSupported(Platform.CURRENT_PLATFORM, SystemInfo.getGraphicsCards())) {
                                            foundSupported.add(driver);
                                        }
                                    }
                                }
                            }
                        } catch (IOException e) {
                            LOG.warning("Failed to read Vulkan ICD files in " + icdDir, e);
                        }
                    }

                    supported.addAll(foundSupported);
                }

                SUPPORTED = List.copyOf(supported);
                DRIVER_TO_ICD_FILE = Collections.unmodifiableMap(driverToIcdFile);
            }
        }

        private final String icdName;

        Vulkan(String icdName) {
            this.icdName = icdName;
        }

        private static boolean hasCard(@Nullable List<GraphicsCard> cards, HardwareVendor vendor) {
            return cards != null && cards.stream().anyMatch(card -> card.getVendor() == vendor);
        }

        @Override
        public GraphicsAPI api() {
            return GraphicsAPI.VULKAN;
        }

        @Contract(pure = true)
        public String icdName() {
            return icdName;
        }

        /// Get the path to the ICD file for this driver.
        ///
        /// If the ICD file does not exist, return `null`.
        @Contract(pure = true)
        public @Nullable Path icdFile() {
            return Holder.DRIVER_TO_ICD_FILE.get(this);
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
            public boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
                return platform.os() == OperatingSystem.WINDOWS;
            }

            @Override
            public String mesaDriverName() {
                return "d3d12";
            }
        };

        static final List<Renderer> SUPPORTED =
                Stream.concat(
                        Stream.of(DEFAULT),
                        Stream.of(OpenGL.values()).filter(it -> it.isSupported(
                                Platform.CURRENT_PLATFORM,
                                null // We don't need to pass graphics cards for OpenGL yet
                        ))
                ).toList();

        @Override
        public GraphicsAPI api() {
            return GraphicsAPI.OPENGL;
        }
    }

    /// Unknown renderer.
    record Unknown(String name) implements Renderer {
    }

    /// Whether this renderer is supported on the given platform and graphics cards.
    ///
    /// If `cards` is `null`, it means that the graphics cards are unknown.
    default boolean isSupported(Platform platform, @Nullable List<GraphicsCard> cards) {
        return true;
    }
}
