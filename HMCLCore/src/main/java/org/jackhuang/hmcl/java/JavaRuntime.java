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
package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.Bits;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Glavo
 */
public final class JavaRuntime implements Comparable<JavaRuntime> {

    public static JavaRuntime of(Path binary, JavaInfo info, boolean isManaged) {
        String javacName = info.getPlatform().getOperatingSystem() == OperatingSystem.WINDOWS ? "javac.exe" : "javac";
        return new JavaRuntime(binary, info, isManaged, Files.isRegularFile(binary.resolveSibling(javacName)));
    }

    private final Path binary;
    private final JavaInfo info;
    private final boolean isManaged;
    private final boolean isJDK;

    public JavaRuntime(Path binary, JavaInfo info, boolean isManaged, boolean isJDK) {
        this.binary = binary;
        this.info = info;
        this.isManaged = isManaged;
        this.isJDK = isJDK;
    }

    public boolean isManaged() {
        return isManaged;
    }

    public Path getBinary() {
        return binary;
    }

    public String getVersion() {
        return info.getVersion();
    }

    public Platform getPlatform() {
        return info.getPlatform();
    }

    public Architecture getArchitecture() {
        return getPlatform().getArchitecture();
    }

    public Bits getBits() {
        return getPlatform().getBits();
    }

    public VersionNumber getVersionNumber() {
        return info.getVersionNumber();
    }

    /**
     * The major version of Java installation.
     */
    public int getParsedVersion() {
        return info.getParsedVersion();
    }

    public String getVendor() {
        return info.getVendor();
    }

    public boolean isJDK() {
        return isJDK;
    }

    @Override
    public int compareTo(@NotNull JavaRuntime that) {
        if (this.isManaged != that.isManaged) {
            return this.isManaged ? -1 : 1;
        }

        int c = Integer.compare(this.getParsedVersion(), that.getParsedVersion());
        if (c != 0)
            return c;

        c = this.getVersionNumber().compareTo(that.getVersionNumber());
        if (c != 0)
            return c;

        c = this.getArchitecture().compareTo(that.getArchitecture());
        if (c != 0)
            return c;

        return this.getBinary().compareTo(that.getBinary());
    }

    @Override
    public int hashCode() {
        return binary.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JavaRuntime)) return false;
        JavaRuntime that = (JavaRuntime) o;
        return this.getBinary().equals(that.getBinary());
    }

    public static final JavaRuntime CURRENT_JAVA;
    public static final int CURRENT_VERSION;
    public static final boolean CURRENT_JIT_ENABLED;

    public static JavaRuntime getDefault() {
        return CURRENT_JAVA;
    }

    static {
        String javaHome = System.getProperty("java.home");
        Path executable = null;
        if (javaHome != null) {
            executable = Paths.get(javaHome, "bin", OperatingSystem.CURRENT_OS.getJavaExecutable());
            try {
                executable = executable.toRealPath();
            } catch (IOException ignored) {
            }

            if (!Files.isRegularFile(executable)) {
                executable = null;
            }
        }

        CURRENT_JAVA = executable != null ? JavaRuntime.of(executable, JavaInfo.CURRENT_ENVIRONMENT, false) : null;
        CURRENT_VERSION = JavaInfo.CURRENT_ENVIRONMENT.getParsedVersion();

        String vmInfo = System.getProperty("java.vm.info", "");
        CURRENT_JIT_ENABLED = !vmInfo.contains("interpreted mode") // HotSpot
                && !vmInfo.contains("JIT disabled"); // J9
    }
}
