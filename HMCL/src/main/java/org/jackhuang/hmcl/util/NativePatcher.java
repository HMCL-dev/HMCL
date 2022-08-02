package org.jackhuang.hmcl.util;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.NativesDirectoryType;
import org.jackhuang.hmcl.game.Version;
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

    public static Version patchNative(Version version, JavaVersion javaVersion, VersionSetting settings) {
        if (javaVersion.getArchitecture().isX86())
            return version;

        if (javaVersion.getPlatform().getOperatingSystem() == OperatingSystem.OSX
                && javaVersion.getArchitecture() == Architecture.ARM64
                && VersionNumber.VERSION_COMPARATOR.compare(version.getVersion(), "1.19") >= 0)
            return version;

        if (settings.isNotPatchNatives() || settings.getNativesDirType() != NativesDirectoryType.VERSION_FOLDER)
            return version;

        Map<String, Library> replacements = Hole.nativeReplacement.get(javaVersion.getPlatform().toString());
        if (replacements == null) {
            LOG.warning("No alternative native library provided for platform " + javaVersion.getPlatform());
            return version;
        }

        ArrayList<Library> newLibraries = new ArrayList<>();
        for (Library library : version.getLibraries()) {
            if (!library.appliesToCurrentEnvironment()) {
                continue;
            }

            if (library.isNative()) {
                Library replacement = replacements.get(library.getName() + ":natives");
                if (replacement == null) {
                    if (!(settings.isUseNativeGLFW() && library.getArtifactId().contains("glfw"))
                            && !(settings.isUseNativeOpenAL() && library.getArtifactId().contains("openal"))) {
                        LOG.warning("No alternative native library " + library.getName() + "provided for platform " + javaVersion.getPlatform());
                        return version;
                    }
                    newLibraries.add(library);
                } else {
                    newLibraries.add(replacement);
                }
            } else {
                newLibraries.add(replacements.getOrDefault(library.getName(), library));
            }
        }

        return version.setLibraries(newLibraries);
    }

    public static Library getSoftwareRendererLoader(JavaVersion javaVersion) {
        Map<String, Library> map = Hole.nativeReplacement.get(javaVersion.getPlatform().toString());
        return map != null ? map.get("software-renderer-loader") : null;
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
