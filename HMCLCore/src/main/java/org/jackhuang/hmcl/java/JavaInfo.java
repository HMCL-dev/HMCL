package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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

    // load

    private static Map<String, String> readReleaseFile(Path releaseFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(releaseFile)) {
            Map<String, String> res = new HashMap<>();

            String line;
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }

                String name = line.substring(0, idx);
                String value;

                if (line.length() > idx + 2 && line.charAt(idx + 1) == '"' && line.charAt(line.length() - 1) == '"') {
                    value = line.substring(idx + 2, line.length() - 1);
                } else {
                    value = line.substring(idx + 1);
                }

                res.put(name, value);
            }
            return res;
        }
    }

    public static JavaInfo fromReleaseFile(Path releaseFile) throws IOException {
        Map<String, String> properties = readReleaseFile(releaseFile);
        String osName = properties.get("OS_NAME");
        String osArch = properties.get("OS_ARCH");
        String vendor = properties.get("IMPLEMENTOR");
        if ("".equals(osName) && "OpenJDK BSD Porting Team".equals(vendor)) {
            osName = "FreeBSD";
        }

        OperatingSystem os = OperatingSystem.parseOSName(osName);
        Architecture arch = Architecture.parseArchName(osArch);
        String javaVersion = properties.get("JAVA_VERSION");

        if (os == OperatingSystem.UNKNOWN || arch == Architecture.UNKNOWN || javaVersion == null) {
            return null;
        }

        return new JavaInfo(Platform.getPlatform(os, arch), javaVersion, vendor);
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
                        JavaInfo info = fromReleaseFile(releaseFile);
                        if (info != null)
                            return info;
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
                    if (version != null && vendor != null) {
                        break;
                    } else {
                        continue;
                    }
                }

                idx = line.indexOf(JAVA_VERSION);
                if (idx >= 0) {
                    version = line.substring(idx + JAVA_VERSION.length()).trim();
                    if (osArch != null && vendor != null) {
                        break;
                    } else {
                        continue;
                    }
                }

                idx = line.indexOf(JAVA_VENDOR);
                if (idx >= 0) {
                    vendor = line.substring(idx + JAVA_VENDOR.length()).trim();
                    if (osArch != null && version != null) {
                        break;
                    } else {
                        //noinspection UnnecessaryContinue
                        continue;
                    }
                }
            }
        }

        if (osArch != null) {
            platform = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.parseArchName(osArch));
        }

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

            if (platform == null) {
                platform = Platform.getPlatform(OperatingSystem.CURRENT_OS, is64Bit ? Architecture.X86_64 : Architecture.X86);
            }

            if (version == null) {
                return null;
            }
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
