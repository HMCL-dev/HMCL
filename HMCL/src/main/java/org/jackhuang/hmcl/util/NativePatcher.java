package org.jackhuang.hmcl.util;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class NativePatcher {
    private NativePatcher() {
    }

    private static final Library NONEXISTENT_LIBRARY = new Library(null);

    private static final Map<Platform, Map<String, Library>> natives = new HashMap<>();

    private static Map<String, Library> getNatives(Platform platform) {
        return natives.computeIfAbsent(platform, p -> {
            //noinspection ConstantConditions
            try (Reader reader = new InputStreamReader(NativePatcher.class.getResourceAsStream("/assets/natives.json"), StandardCharsets.UTF_8)) {
                Map<String, Map<String, Library>> natives = JsonUtils.GSON.fromJson(reader, new TypeToken<Map<String, Map<String, Library>>>() {
                }.getType());

                return natives.getOrDefault(p.toString(), Collections.emptyMap());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load native library list", e);
                return Collections.emptyMap();
            }
        });
    }

    public static Version patchNative(Version version, String gameVersion, JavaVersion javaVersion, VersionSetting settings) {
        if (settings.getNativesDirType() == NativesDirectoryType.CUSTOM) {
            if (gameVersion != null && VersionNumber.VERSION_COMPARATOR.compare(gameVersion, "1.19") < 0)
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

        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX
                && (useNativeGLFW || useNativeOpenAL)
                && VersionNumber.VERSION_COMPARATOR.compare(gameVersion, "1.19") >= 0) {

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

        if (settings.isNotPatchNatives())
            return version;

        if (javaVersion.getArchitecture().isX86())
            return version;

        if (javaVersion.getPlatform().getOperatingSystem() == OperatingSystem.OSX
                && javaVersion.getArchitecture() == Architecture.ARM64
                && gameVersion != null
                && VersionNumber.VERSION_COMPARATOR.compare(gameVersion, "1.19") >= 0)
            return version;

        Map<String, Library> replacements = getNatives(javaVersion.getPlatform());
        if (replacements.isEmpty()) {
            LOG.warning("No alternative native library provided for platform " + javaVersion.getPlatform());
            return version;
        }

        ArrayList<Library> newLibraries = new ArrayList<>();
        for (Library library : version.getLibraries()) {
            if (!library.appliesToCurrentEnvironment())
                continue;

            if (library.isNative()) {
                Library replacement = replacements.getOrDefault(library.getName() + ":natives", NONEXISTENT_LIBRARY);
                if (replacement == NONEXISTENT_LIBRARY) {
                    LOG.warning("No alternative native library " + library.getName() + " provided for platform " + javaVersion.getPlatform());
                    newLibraries.add(library);
                } else if (replacement != null) {
                    newLibraries.add(replacement);
                }
            } else {
                Library replacement = replacements.getOrDefault(library.getName(), NONEXISTENT_LIBRARY);
                if (replacement == NONEXISTENT_LIBRARY) {
                    newLibraries.add(library);
                } else if (replacement != null) {
                    newLibraries.add(replacement);
                }
            }
        }

        return version.setLibraries(newLibraries);
    }

    public static Library getMesaLoader(JavaVersion javaVersion, Renderer renderer) {
        return getNatives(javaVersion.getPlatform()).get(renderer == Renderer.LLVMPIPE ? "software-renderer-loader" : "mesa-loader");
    }
}
