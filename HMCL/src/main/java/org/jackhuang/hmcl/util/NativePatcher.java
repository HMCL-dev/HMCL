package org.jackhuang.hmcl.util;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class NativePatcher {
    private NativePatcher() {
    }

    private static final Library NONEXISTENT_LIBRARY = new Library(null);

    public static Version patchNative(Version version, String gameVersion, JavaVersion javaVersion, VersionSetting settings) {
        if (settings.isNotPatchNatives())
            return version;

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

        if (javaVersion.getArchitecture().isX86())
            return version;

        if (javaVersion.getPlatform().getOperatingSystem() == OperatingSystem.OSX
                && javaVersion.getArchitecture() == Architecture.ARM64
                && gameVersion != null
                && VersionNumber.VERSION_COMPARATOR.compare(gameVersion, "1.19") >= 0)
            return version;

        Map<String, Library> replacements = Hole.nativeReplacement.get(javaVersion.getPlatform().toString());
        if (replacements == null) {
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
                    if (!(settings.isUseNativeGLFW() && library.getArtifactId().contains("glfw"))
                            && !(settings.isUseNativeOpenAL() && library.getArtifactId().contains("openal"))) {
                        LOG.warning("No alternative native library " + library.getName() + " provided for platform " + javaVersion.getPlatform());
                        return version;
                    }
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
        Map<String, Library> map = Hole.nativeReplacement.get(javaVersion.getPlatform().toString());
        return map != null ? map.get(renderer == Renderer.LLVMPIPE ? "software-renderer-loader" : "mesa-loader") : null;
    }

    private static final class Hole {
        static Map<String, Map<String, Library>> nativeReplacement;

        static {
            //noinspection ConstantConditions
            try (Reader reader = new InputStreamReader(NativePatcher.class.getResourceAsStream("/assets/natives.json"))) {
                nativeReplacement = JsonUtils.GSON.fromJson(reader, new TypeToken<Map<String, Map<String, Library>>>() {
                }.getType());
            } catch (IOException e) {
                nativeReplacement = Collections.emptyMap();
                LOG.log(Level.WARNING, "Failed to load native library list", e);
            }
        }
    }
}
