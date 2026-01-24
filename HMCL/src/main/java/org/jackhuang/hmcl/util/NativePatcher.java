/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.OSVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.gson.JsonUtils.mapTypeOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class NativePatcher {

    private static final Library NONEXISTENT_LIBRARY = new Library(null);

    private static final Map<Platform, Map<String, Library>> natives = new HashMap<>();

    private static Map<String, Library> getNatives(Platform platform) {
        return natives.computeIfAbsent(platform, p -> {
            //noinspection ConstantConditions
            try (Reader reader = new InputStreamReader(NativePatcher.class.getResourceAsStream("/assets/natives.json"), StandardCharsets.UTF_8)) {
                Map<String, Map<String, Library>> natives = JsonUtils.GSON.fromJson(reader, mapTypeOf(String.class, mapTypeOf(String.class, Library.class)));
                return natives.getOrDefault(p.toString(), Collections.emptyMap());
            } catch (IOException e) {
                LOG.warning("Failed to load native library list", e);
                return Collections.emptyMap();
            }
        });
    }

    public static Version patchNative(DefaultGameRepository repository,
                                      Version version, String gameVersion,
                                      JavaRuntime javaVersion,
                                      VersionSetting settings,
                                      List<String> javaArguments) {
        if (settings.getNativesDirType() == NativesDirectoryType.CUSTOM) {
            if (gameVersion != null && GameVersionNumber.compare(gameVersion, "1.19") < 0)
                return version;

            ArrayList<Library> newLibraries = new ArrayList<>();
            for (Library library : version.getLibraries()) {
                if (!library.appliesToCurrentEnvironment())
                    continue;

                if (library.getClassifier() == null
                        || !library.getArtifactId().startsWith("lwjgl")
                        || !library.getClassifier().startsWith("natives")) {
                    newLibraries.add(library);
                }
            }
            return version.setLibraries(newLibraries);
        }

        final boolean useNativeGLFW = settings.isUseNativeGLFW();
        final boolean useNativeOpenAL = settings.isUseNativeOpenAL();

        if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() && (useNativeGLFW || useNativeOpenAL)
                && gameVersion != null && GameVersionNumber.compare(gameVersion, "1.19") >= 0) {

            version = version.setLibraries(version.getLibraries().stream()
                    .filter(library -> {
                        if (library.getClassifier() != null && library.getClassifier().startsWith("natives")
                                && "org.lwjgl".equals(library.getGroupId())) {
                            if ((useNativeGLFW && "lwjgl-glfw".equals(library.getArtifactId()))
                                    || (useNativeOpenAL && "lwjgl-openal".equals(library.getArtifactId()))) {
                                LOG.info("Filter out " + library.getName());
                                return false;
                            }
                        }

                        return true;
                    })
                    .collect(Collectors.toList()));
        }

        // Try patch natives

        OperatingSystem os = javaVersion.getPlatform().getOperatingSystem();
        Architecture arch = javaVersion.getArchitecture();
        GameVersionNumber gameVersionNumber = gameVersion != null ? GameVersionNumber.asGameVersion(gameVersion) : null;

        if (settings.isNotPatchNatives())
            return version;

        if (arch.isX86() && (os == OperatingSystem.WINDOWS || os == OperatingSystem.LINUX || os == OperatingSystem.MACOS))
            return version;

        if (arch == Architecture.ARM64 && (os == OperatingSystem.MACOS || os == OperatingSystem.WINDOWS)
                && gameVersionNumber != null
                && gameVersionNumber.compareTo("1.19") >= 0)
            return version;

        Map<String, Library> replacements = getNatives(javaVersion.getPlatform());
        if (replacements.isEmpty()) {
            LOG.warning("No alternative native library provided for platform " + javaVersion.getPlatform());
            return version;
        }

        boolean lwjglVersionChanged = false;
        ArrayList<Library> newLibraries = new ArrayList<>();
        for (Library library : version.getLibraries()) {
            if (!library.appliesToCurrentEnvironment())
                continue;

            if (library.isNative()) {
                Library replacement = replacements.getOrDefault(library.getName() + ":natives", NONEXISTENT_LIBRARY);
                if (replacement == NONEXISTENT_LIBRARY) {
                    LOG.warning("No alternative native library " + library.getName() + ":natives provided for platform " + javaVersion.getPlatform());
                    newLibraries.add(library);
                } else if (replacement != null) {
                    LOG.info("Replace " + library.getName() + ":natives with " + replacement.getName());
                    newLibraries.add(replacement);
                }
            } else {
                Library replacement = replacements.getOrDefault(library.getName(), NONEXISTENT_LIBRARY);
                if (replacement == NONEXISTENT_LIBRARY) {
                    newLibraries.add(library);
                } else if (replacement != null) {
                    LOG.info("Replace " + library.getName() + " with " + replacement.getName());
                    newLibraries.add(replacement);

                    if ("org.lwjgl:lwjgl".equals(library.getName()) && !Objects.equals(library.getVersion(), replacement.getVersion())) {
                        lwjglVersionChanged = true;
                    }
                }
            }
        }

        if (lwjglVersionChanged) {
            ModManager modManager = repository.getModManager(version.getId());
            try {
                for (LocalModFile mod : modManager.getMods()) {
                    if ("sodium".equals(mod.getId())) {
                        // https://github.com/CaffeineMC/sodium/issues/2561
                        javaArguments.add("-Dsodium.checks.issue2561=false");
                        break;
                    }
                }
            } catch (Throwable e) {
                LOG.warning("Failed to get mods", e);
            }
        }

        return version.setLibraries(newLibraries);
    }

    public static @Nullable Library getWindowsMesaLoader(@NotNull JavaRuntime javaVersion, @NotNull Renderer renderer, @NotNull OSVersion windowsVersion) {
        if (renderer == Renderer.DEFAULT)
            return null;

        if (windowsVersion.isAtLeast(OSVersion.WINDOWS_10)) {
            return getNatives(javaVersion.getPlatform()).get("mesa-loader");
        } else if (windowsVersion.isAtLeast(OSVersion.WINDOWS_7)) {
            if (renderer == Renderer.LLVMPIPE)
                return getNatives(javaVersion.getPlatform()).get("software-renderer-loader");
            else
                return null;
        } else {
            return null;
        }
    }

    public static SupportStatus checkSupportedStatus(GameVersionNumber gameVersion, Platform platform,
                                                     OSVersion systemVersion) {
        if (platform.equals(Platform.WINDOWS_X86_64)) {
            if (!systemVersion.isAtLeast(OSVersion.WINDOWS_7) && gameVersion.isAtLeast("1.20.5", "24w14a"))
                return SupportStatus.UNSUPPORTED;

            return SupportStatus.OFFICIAL_SUPPORTED;
        }

        if (platform.equals(Platform.MACOS_X86_64) || platform.equals(Platform.LINUX_X86_64))
            return SupportStatus.OFFICIAL_SUPPORTED;

        if (platform.equals(Platform.WINDOWS_X86) || platform.equals(Platform.LINUX_X86)) {
            if (gameVersion.isAtLeast("1.20.5", "24w14a"))
                return SupportStatus.UNSUPPORTED;
            else
                return SupportStatus.OFFICIAL_SUPPORTED;
        }

        if (platform.equals(Platform.WINDOWS_ARM64) || platform.equals(Platform.MACOS_ARM64)) {
            if (gameVersion.compareTo("1.19") >= 0)
                return SupportStatus.OFFICIAL_SUPPORTED;

            String minVersion = platform.getOperatingSystem() == OperatingSystem.WINDOWS
                    ? "1.8"
                    : "1.6";

            return gameVersion.compareTo(minVersion) >= 0
                    ? SupportStatus.LAUNCHER_SUPPORTED
                    : SupportStatus.TRANSLATION_SUPPORTED;
        }

        String minVersion = null;
        String maxVersion = null;

        if (platform.equals(Platform.FREEBSD_X86_64)) {
            minVersion = "1.13";
        } else if (platform.equals(Platform.LINUX_ARM64)) {
            minVersion = "1.6";
        } else if (platform.equals(Platform.LINUX_RISCV64)) {
            minVersion = "1.8";
            maxVersion = "1.21.5";
        } else if (platform.equals(Platform.LINUX_LOONGARCH64)) {
            minVersion = "1.6";
        } else if (platform.equals(Platform.LINUX_LOONGARCH64_OW)) {
            minVersion = "1.6";
            maxVersion = "1.20.1";
        } else if (platform.equals(Platform.LINUX_MIPS64EL) || platform.equals(Platform.LINUX_ARM32)) {
            minVersion = "1.8";
            maxVersion = "1.20.1";
        }

        if (minVersion != null) {
            if (gameVersion.compareTo(minVersion) >= 0) {
                if (maxVersion != null && gameVersion.compareTo(maxVersion) > 0)
                    return SupportStatus.UNSUPPORTED;

                String[] defaultGameVersions = GameVersionNumber.getDefaultGameVersions();
                if (defaultGameVersions.length > 0 && gameVersion.compareTo(defaultGameVersions[0]) > 0) {
                    return SupportStatus.UNTESTED;
                }
                return SupportStatus.LAUNCHER_SUPPORTED;
            } else {
                return SupportStatus.UNSUPPORTED;
            }
        }

        return SupportStatus.UNTESTED;
    }

    public enum SupportStatus {
        OFFICIAL_SUPPORTED,
        LAUNCHER_SUPPORTED,
        TRANSLATION_SUPPORTED,
        UNTESTED,
        UNSUPPORTED,
    }

    private NativePatcher() {
    }
}
