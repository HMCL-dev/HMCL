// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.java;

import kala.compress.archivers.ArchiveEntry;
import org.jackhuang.hmcl.util.KeyValuePairUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.NativePlatform;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author Glavo
 */
public final class JavaInfo {

    public static int parseVersion(String version) {
        int startIndex = version.startsWith("1.") ? 2 : 0;
        int endIndex = startIndex;

        while (endIndex < version.length()) {
            char ch = version.charAt(endIndex);
            if (ch >= '0' && ch <= '9')
                endIndex++;
            else
                break;
        }

        try {
            return endIndex > startIndex ? Integer.parseInt(version.substring(startIndex, endIndex)) : -1;
        } catch (Throwable e) {
            // The version number is too long
            return -1;
        }
    }

    public static JavaInfo fromReleaseFile(BufferedReader reader) throws IOException {
        Map<String, String> properties = KeyValuePairUtils.loadProperties(reader);
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

        return new JavaInfo(NativePlatform.of(os, arch), javaVersion, vendor);
    }

    /// Reads JDK metadata and verifies that the archive contains a Java executable.
    /// On old-world LoongArch hosts, assumes Linux LoongArch64 archives use the host ABI.
    /// Does not distinguish new-world archives imported on an old-world host.
    public static <F, E extends ArchiveEntry> JavaInfo fromArchive(ArchiveFileTree<F, E> tree) throws IOException {
        return fromArchive(tree, SystemInfo.SYSTEM_ARCH);
    }

    /// Reads an archive using the supplied host architecture for the LoongArch ABI fallback.
    static <F, E extends ArchiveEntry> JavaInfo fromArchive(ArchiveFileTree<F, E> tree, Architecture hostArchitecture) throws IOException {
        if (tree.getRoot().getSubDirs().size() != 1 || !tree.getRoot().getFiles().isEmpty())
            throw new IOException();

        ArchiveFileTree.Dir<E> jdkRoot = tree.getRoot().getSubDirs().values().iterator().next();
        @Nullable E releaseEntry = jdkRoot.getFiles().get("release");
        if (releaseEntry == null)
            throw new IOException("Missing release file");

        JavaInfo info;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(tree.getInputStream(releaseEntry), StandardCharsets.UTF_8))) {
            info = JavaInfo.fromReleaseFile(reader);
        }

        @Nullable ArchiveFileTree.Dir<E> binDir = jdkRoot.getSubDirs().get("bin");
        @Nullable E executable = binDir == null ? null : binDir.getFiles().get(info.getPlatform().os().getJavaExecutable());
        if (executable == null)
            throw new IOException("Missing java executable file");

        if (hostArchitecture == Architecture.LOONGARCH64_OW
                && info.getPlatform().equals(NativePlatform.LINUX_LOONGARCH64)) {
            info = new JavaInfo(NativePlatform.LINUX_LOONGARCH64_OW, info.getVersion(), info.getVendor());
        }

        return info;
    }

    public static String normalizeVendor(String vendor) {
        if (vendor == null)
            return null;

        return switch (vendor) {
            case "N/A" -> null;
            case "Oracle Corporation" -> "Oracle";
            case "Azul Systems, Inc." -> "Azul";
            case "IBM Corporation", "International Business Machines Corporation", "Eclipse OpenJ9" -> "IBM";
            case "Eclipse Adoptium" -> "Adoptium";
            case "Amazon.com Inc." -> "Amazon";
            default -> vendor;
        };
    }

    public static final class Builder {
        private final NativePlatform platform;
        private final String version;
        private @Nullable String vendor;

        public Builder(NativePlatform platform, String version) {
            this.platform = platform;
            this.version = version;
        }

        @Contract("_ -> this")
        public Builder setVendor(@Nullable String vendor) {
            this.vendor = vendor;
            return this;
        }

        public JavaInfo build() {
            return new JavaInfo(platform, version, vendor);
        }
    }

    public static Builder newBuilder(@NotNull NativePlatform platform, @NotNull String version) {
        return new Builder(platform, version);
    }

    public static final JavaInfo CURRENT_ENVIRONMENT = new JavaInfo(SystemInfo.CURRENT_PLATFORM, System.getProperty("java.version"), System.getProperty("java.vendor"));

    private final NativePlatform platform;
    private final String version;
    private final @Nullable String vendor;

    private final transient int parsedVersion;
    private final transient VersionNumber versionNumber;

    public JavaInfo(NativePlatform platform, String version, @Nullable String vendor) {
        this.platform = platform;
        this.version = version;
        this.parsedVersion = parseVersion(version);
        this.versionNumber = VersionNumber.asVersion(version);
        this.vendor = vendor;
    }

    public NativePlatform getPlatform() {
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
