package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.NativesDirectoryType;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class NativePatcher {
    private NativePatcher() {
    }

    public static Version patchNative(Version version, JavaVersion javaVersion, VersionSetting settings) {
        if (javaVersion.getArchitecture().isX86())
            return version;

        if (javaVersion.getPlatform().getOperatingSystem() == OperatingSystem.OSX
                && javaVersion.getArchitecture() == Architecture.ARM64
                && VersionNumber.VERSION_COMPARATOR.compare(version.getVersion(), "1.19") >= 0)
            return version;

        if (settings.getNativesDirType() != NativesDirectoryType.VERSION_FOLDER)
            return version;

        Map<String, Library> replacements = Hole.nativeReplacement.get(javaVersion.getPlatform());
        if (replacements == null) {
            LOG.warning("No alternative native library provided for platform " + javaVersion.getPlatform());
            return version;
        }

        ArrayList<Library> newLibraries = new ArrayList<>();
        for (Library library : version.getLibraries()) {
            if (!library.appliesToCurrentEnvironment()) {
                newLibraries.add(library);
                continue;
            }

            Library replacement = replacements.get(library.getName());
            if (replacement == null) {
                if (!library.isNative()
                        || (settings.isUseNativeGLFW() && library.getArtifactId().contains("glfw"))
                        || (settings.isUseNativeOpenAL() && library.getArtifactId().contains("openal"))) {
                    newLibraries.add(library);
                } else {
                    LOG.warning("No alternative native library " + library.getName() + "provided for platform " + javaVersion.getPlatform());
                    return version;
                }
            } else {
                newLibraries.add(replacement);
            }
        }

        return version.setLibraries(newLibraries);
    }

    private static final class Hole {
        static final Map<Platform, Map<String, Library>> nativeReplacement = new HashMap<>(); // TODO
    }
}
