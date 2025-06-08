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

import kala.compress.archivers.ArchiveEntry;
import org.jackhuang.hmcl.util.KeyValuePairUtils;
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
