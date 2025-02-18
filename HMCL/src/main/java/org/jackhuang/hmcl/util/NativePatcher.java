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
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

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
    private NativePatcher() {
    }

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

        if (arch.isX86() && (os == OperatingSystem.WINDOWS || os == OperatingSystem.LINUX || os == OperatingSystem.OSX))
            return version;

        if (arch == Architecture.ARM64 && (os == OperatingSystem.OSX || os == OperatingSystem.WINDOWS)
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

    public static Library getMesaLoader(JavaRuntime javaVersion) {
        return getNatives(javaVersion.getPlatform()).get("mesa-loader");
    }
}
