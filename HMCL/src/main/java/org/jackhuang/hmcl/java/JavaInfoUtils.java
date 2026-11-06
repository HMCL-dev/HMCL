// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.java;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.NativePlatform;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/// Probes local Java executables for runtime information.
///
/// @author Glavo
/// @see <a href="https://github.com/Glavo/java-info">Glavo/java-info</a>
public final class JavaInfoUtils {

    /// Prevents instantiation.
    private JavaInfoUtils() {
    }

    /// Runs an absolute Java executable path and resolves its architecture against the local environment.
    /// Falls back to the host architecture if the reported name is unknown.
    ///
    /// @throws IOException if the executable cannot be probed or its Java version is missing
    public static @NotNull JavaInfo fromExecutable(Path executable) throws IOException {
        assert executable.isAbsolute();

        @Nullable Path thisPath = JarUtils.thisJarPath();
        if (thisPath == null) {
            throw new IOException("Failed to find current HMCL location");
        }

        try {
            @Nullable Result result = JsonUtils.GSON.fromJson(SystemUtils.run(
                    executable.toString(),
                    "-classpath",
                    thisPath.toString(),
                    org.glavo.info.Main.class.getName()
            ), Result.class);

            if (result == null) {
                throw new IOException("Failed to get Java info from " + executable);
            }

            if (result.javaVersion == null) {
                throw new IOException("Failed to get Java version from " + executable);
            }

            Architecture architecture = SystemInfo.detectArchitecture(result.osArch);
            NativePlatform platform = NativePlatform.of(SystemInfo.CURRENT_OS,
                    architecture != Architecture.UNKNOWN
                            ? architecture
                            : SystemInfo.SYSTEM_ARCH);

            return new JavaInfo(platform, result.javaVersion, result.javaVendor);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    /// Properties reported by the probed JVM; absent properties remain null.
    @JsonSerializable
    private record Result(@SerializedName("os.name") @Nullable String osName,
                          @SerializedName("os.arch") @Nullable String osArch,
                          @SerializedName("java.version") @Nullable String javaVersion,
                          @SerializedName("java.vendor") @Nullable String javaVendor) {
    }
}
