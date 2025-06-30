/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.java;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Glavo
 * @see <a href="https://github.com/Glavo/java-info">Glavo/java-info</a>
 */
public final class JavaInfoUtils {

    private JavaInfoUtils() {
    }

    private static Path tryFindReleaseFile(Path executable) {
        Path parent = executable.getParent();
        if (parent != null && parent.getFileName() != null && parent.getFileName().toString().equals("bin")) {
            Path javaHome = parent.getParent();
            if (javaHome != null && javaHome.getFileName() != null) {
                Path releaseFile = javaHome.resolve("release");
                String javaHomeName = javaHome.getFileName().toString();
                if ((javaHomeName.contains("jre") || javaHomeName.contains("jdk") || javaHomeName.contains("openj9"))
                        && Files.isRegularFile(releaseFile)) {
                    return releaseFile;
                }
            }
        }
        return null;
    }

    public static @NotNull JavaInfo fromExecutable(Path executable, boolean tryFindReleaseFile) throws IOException {
        assert executable.isAbsolute();

        Path releaseFile;
        if (tryFindReleaseFile && (releaseFile = tryFindReleaseFile(executable)) != null) {
            try {
                return JavaInfo.fromReleaseFile(releaseFile);
            } catch (IOException ignored) {
            }
        }

        Path thisPath = JarUtils.thisJarPath();

        if (thisPath == null) {
            throw new IOException("Failed to find current HMCL location");
        }

        try {
            Result result = JsonUtils.GSON.fromJson(SystemUtils.run(
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

            Architecture architecture = Architecture.parseArchName(result.osArch);
            Platform platform = Platform.getPlatform(OperatingSystem.CURRENT_OS,
                    architecture != Architecture.UNKNOWN
                            ? architecture
                            : Architecture.SYSTEM_ARCH);


            return new JavaInfo(platform, result.javaVersion, result.javaVendor);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    private static final class Result {
        @SerializedName("os.name")
        public String osName;
        @SerializedName("os.arch")
        public String osArch;
        @SerializedName("java.version")
        public String javaVersion;
        @SerializedName("java.vendor")
        public String javaVendor;
    }
}
