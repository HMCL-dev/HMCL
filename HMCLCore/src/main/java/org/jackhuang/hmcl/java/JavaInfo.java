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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.jackhuang.hmcl.util.KeyValuePairProperties;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Glavo
 */
public final class JavaInfo {
    public static int parseVersion(String version) {
        try {
            int idx = version.indexOf('.');
            if (idx < 0) {
                idx = version.indexOf('u');
                return idx > 0 ? Integer.parseInt(version.substring(0, idx)) : Integer.parseInt(version);
            } else {
                int major = Integer.parseInt(version.substring(0, idx));
                if (major != 1) {
                    return major;
                } else {
                    int idx2 = version.indexOf('.', idx + 1);
                    if (idx2 < 0) {
                        return -1;
                    }
                    return Integer.parseInt(version.substring(idx + 1, idx2));
                }
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static JavaInfo fromReleaseFile(BufferedReader reader) throws IOException {
        KeyValuePairProperties properties = KeyValuePairProperties.load(reader);
        String osName = properties.get("OS_NAME");
        String osArch = properties.get("OS_ARCH");
        String vendor = properties.get("IMPLEMENTOR");

        OperatingSystem os = "".equals(osName) && "OpenJDK BSD Porting Team".equals(vendor)
                ? OperatingSystem.FREEBSD
                : OperatingSystem.parseOSName(osName);

        Architecture arch = Architecture.parseArchName(osArch);
        String javaVersion = properties.get("JAVA_VERSION");

        if (os == OperatingSystem.UNKNOWN)
            throw new IOException("Unknown operating system: " + osName);

        if (arch == Architecture.UNKNOWN)
            throw new IOException("Unknown architecture: " + osArch);

        if (javaVersion == null)
            throw new IOException("Missing Java version");

        return new JavaInfo(Platform.getPlatform(os, arch), javaVersion, vendor);
    }

    public static JavaInfo fromReleaseFile(Path releaseFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(releaseFile)) {
            return fromReleaseFile(reader);
        }
    }

    public static <F, E extends ArchiveEntry> JavaInfo fromArchive(ArchiveFileTree<F, E> tree) throws IOException {
        if (tree.getRoot().getSubDirs().size() != 1 || !tree.getRoot().getFiles().isEmpty())
            throw new IOException();

        ArchiveFileTree.Dir<E> jdkRoot = tree.getRoot().getSubDirs().values().iterator().next();
        E releaseEntry = jdkRoot.getFiles().get("release");
        if (releaseEntry == null)
            throw new IOException("Missing release file");

        JavaInfo info;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(tree.getInputStream(releaseEntry), StandardCharsets.UTF_8))) {
            info = JavaInfo.fromReleaseFile(reader);
        }

        ArchiveFileTree.Dir<E> binDir = jdkRoot.getSubDirs().get("bin");
        if (binDir == null || binDir.getFiles().get(info.getPlatform().getOperatingSystem().getJavaExecutable()) == null)
            throw new IOException("Missing java executable file");

        return info;
    }

    public static String normalizeVendor(String vendor) {
        if (vendor == null)
            return null;

        switch (vendor) {
            case "N/A":
                return null;
            case "Oracle Corporation":
                return "Oracle";
            case "Azul Systems, Inc.":
                return "Azul";
            case "IBM Corporation":
            case "International Business Machines Corporation":
            case "Eclipse OpenJ9":
                return "IBM";
            case "Eclipse Adoptium":
                return "Adoptium";
            case "Amazon.com Inc.":
                return "Amazon";
            default:
                return vendor;
        }
    }

    private static final String OS_ARCH = "os.arch = ";
    private static final String JAVA_VERSION = "java.version = ";
    private static final String JAVA_VENDOR = "java.vendor = ";
    private static final String VERSION_PREFIX = "version \"";

    public static JavaInfo fromExecutable(Path executable) throws IOException {
        return fromExecutable(executable, true);
    }

    public static JavaInfo fromExecutable(Path executable, boolean tryFindReleaseFile) throws IOException {
        assert executable.isAbsolute();
        Path parent = executable.getParent();
        if (tryFindReleaseFile && parent != null && parent.getFileName() != null && parent.getFileName().toString().equals("bin")) {
            Path javaHome = parent.getParent();
            if (javaHome != null && javaHome.getFileName() != null) {
                Path releaseFile = javaHome.resolve("release");
                String javaHomeName = javaHome.getFileName().toString();
                if ((javaHomeName.contains("jre") || javaHomeName.contains("jdk") || javaHomeName.contains("openj9")) && Files.isRegularFile(releaseFile)) {
                    try {
                        return fromReleaseFile(releaseFile);
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        String osArch = null;
        String version = null;
        String vendor = null;
        Platform platform = null;

        String executablePath = executable.toString();

        Process process = new ProcessBuilder(executablePath, "-XshowSettings:properties", "-version").start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), OperatingSystem.NATIVE_CHARSET))) {
            for (String line; (line = reader.readLine()) != null; ) {

                int idx = line.indexOf(OS_ARCH);
                if (idx >= 0) {
                    osArch = line.substring(idx + OS_ARCH.length()).trim();
                    if (version != null && vendor != null)
                        break;
                    else
                        continue;
                }

                idx = line.indexOf(JAVA_VERSION);
                if (idx >= 0) {
                    version = line.substring(idx + JAVA_VERSION.length()).trim();
                    if (osArch != null && vendor != null)
                        break;
                    else
                        continue;
                }

                idx = line.indexOf(JAVA_VENDOR);
                if (idx >= 0) {
                    vendor = line.substring(idx + JAVA_VENDOR.length()).trim();
                    if (osArch != null && version != null)
                        break;
                    else
                        //noinspection UnnecessaryContinue
                        continue;
                }
            }
        }

        if (osArch != null)
            platform = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.parseArchName(osArch));

        // Java 6
        if (version == null) {
            boolean is64Bit = false;
            process = new ProcessBuilder(executablePath, "-version").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), OperatingSystem.NATIVE_CHARSET))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (version == null) {
                        int idx = line.indexOf(VERSION_PREFIX);
                        if (idx >= 0) {
                            int begin = idx + VERSION_PREFIX.length();
                            int end = line.indexOf('"', begin);
                            if (end >= 0) {
                                version = line.substring(begin, end);
                            }
                        }
                    }

                    if (line.contains("64-Bit"))
                        is64Bit = true;
                }
            }

            if (platform == null)
                platform = Platform.getPlatform(OperatingSystem.CURRENT_OS, is64Bit ? Architecture.X86_64 : Architecture.X86);

            if (version == null)
                throw new IOException("Cannot determine version");
        }

        return new JavaInfo(platform, version, vendor);
    }

    public static final JavaInfo CURRENT_ENVIRONMENT = new JavaInfo(Platform.CURRENT_PLATFORM, System.getProperty("java.version"), System.getProperty("java.vendor"));

    private final Platform platform;
    private final String version;
    private final @Nullable String vendor;

    private final transient int parsedVersion;
    private final transient VersionNumber versionNumber;

    public JavaInfo(Platform platform, String version, @Nullable String vendor) {
        this.platform = platform;
        this.version = version;
        this.parsedVersion = parseVersion(version);
        this.versionNumber = VersionNumber.asVersion(version);
        this.vendor = vendor;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getVersion() {
        return version;
    }

    public VersionNumber getVersionNumber() {
        return versionNumber;
    }

    public int getParsedVersion() {
        return parsedVersion;
    }

    public @Nullable String getVendor() {
        return vendor;
    }

    @Override
    public String toString() {
        return JsonUtils.GSON.toJson(this);
    }
}
