/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.platform;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static org.jackhuang.hmcl.util.Logging.LOG;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

/**
 * Represents a Java installation.
 *
 * @author huangyuhui
 */
public final class JavaVersion {

    private final Path binary;
    private final String longVersion;
    private final Platform platform;
    private final int version;

    public JavaVersion(Path binary, String longVersion, Platform platform) {
        this.binary = binary;
        this.longVersion = longVersion;
        this.platform = platform;
        version = parseVersion(longVersion);
    }

    public Path getBinary() {
        return binary;
    }

    public String getVersion() {
        return longVersion;
    }

    public Platform getPlatform() {
        return platform;
    }

    public VersionNumber getVersionNumber() {
        return VersionNumber.asVersion(longVersion);
    }

    /**
     * The major version of Java installation.
     *
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#JAVA_11
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#JAVA_10
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#JAVA_9
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#JAVA_8
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#JAVA_7
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#UNKNOWN
     */
    public int getParsedVersion() {
        return version;
    }

    private static final Pattern REGEX = Pattern.compile("version \"(?<version>(.*?))\"");

    public static final int UNKNOWN = -1;
    public static final int JAVA_7 = 70;
    public static final int JAVA_8 = 80;
    public static final int JAVA_9 = 90;
    public static final int JAVA_10 = 100;
    public static final int JAVA_11 = 110;

    private static int parseVersion(String version) {
        if (version.startsWith("11"))
            return JAVA_11;
        else if (version.startsWith("10"))
            return JAVA_10;
        else if (version.startsWith("9"))
            return JAVA_9;
        else if (version.contains("1.8"))
            return JAVA_8;
        else if (version.contains("1.7"))
            return JAVA_7;
        else
            return UNKNOWN;
    }

    public static JavaVersion fromExecutable(Path executable) throws IOException {
        Platform platform = Platform.BIT_32;
        String version = null;

        // javaw is only used on windows
        if ("javaw.exe".equalsIgnoreCase(executable.getFileName().toString())) {
            executable = executable.resolveSibling("java.exe");
        }

        executable = executable.toRealPath();

        Process process = new ProcessBuilder(executable.toString(), "-version").start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            for (String line; (line = reader.readLine()) != null;) {
                Matcher m = REGEX.matcher(line);
                if (m.find())
                    version = m.group("version");
                if (line.contains("64-Bit"))
                    platform = Platform.BIT_64;
            }
        }

        if (version == null)
            throw new IOException("No Java version is matched");

        if (parseVersion(version) == UNKNOWN)
            throw new IOException("Unrecognized Java version " + version);

        return new JavaVersion(executable, version, platform);
    }

    private static Path getExecutable(Path javaHome) {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return javaHome.resolve("bin").resolve("java.exe");
        } else {
            return javaHome.resolve("bin").resolve("java");
        }
    }

    public static JavaVersion fromCurrentEnvironment() {
        return CURRENT_JAVA;
    }

    public static final JavaVersion CURRENT_JAVA;

    static {
        Path currentExecutable = getExecutable(Paths.get(System.getProperty("java.home")));
        try {
            currentExecutable = currentExecutable.toRealPath();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to resolve current Java path: " + currentExecutable, e);
        }
        CURRENT_JAVA = new JavaVersion(
                currentExecutable,
                System.getProperty("java.version"),
                Platform.PLATFORM);
    }

    private static List<JavaVersion> JAVAS;
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    public static List<JavaVersion> getJavas() throws InterruptedException {
        if (JAVAS != null)
            return JAVAS;
        LATCH.await();
        return JAVAS;
    }

    public static synchronized void initialize() {
        if (JAVAS != null)
            throw new IllegalStateException("JavaVersions have already been initialized.");

        List<JavaVersion> javaVersions;

        try {
            javaVersions = lookupJavas(searchPotentialJavaHomes());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to search Java homes", e);
            javaVersions = new ArrayList<>();
        }

        // insert current java to the list
        if (!javaVersions.contains(CURRENT_JAVA)) {
            javaVersions.add(CURRENT_JAVA);
        }

        JAVAS = unmodifiableList(javaVersions);
        LATCH.countDown();
    }

    private static List<JavaVersion> lookupJavas(Stream<Path> javaHomes) {
        return javaHomes
                .filter(Files::isDirectory)
                .map(JavaVersion::getExecutable)
                .filter(Files::isExecutable)
                .flatMap(executable -> { // resolve symbolic links
                    try {
                        return Stream.of(executable.toRealPath());
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Failed to lookup Java executable at " + executable, e);
                        return Stream.empty();
                    }
                })
                .distinct() // remove duplicated javas
                .flatMap(executable -> {
                    if (executable.equals(CURRENT_JAVA.getBinary())) {
                        return Stream.of(CURRENT_JAVA);
                    }
                    try {
                        return Stream.of(fromExecutable(executable));
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Failed to determine Java at " + executable, e);
                        return Stream.empty();
                    }
                })
                .collect(toList());
    }

    private static Stream<Path> searchPotentialJavaHomes() throws IOException {
        switch (OperatingSystem.CURRENT_OS) {

            case WINDOWS:
                List<Path> locations = new ArrayList<>();
                locations.addAll(queryJavaHomesInRegistryKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment\\"));
                locations.addAll(queryJavaHomesInRegistryKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\"));
                locations.addAll(queryJavaHomesInRegistryKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JRE\\"));
                locations.addAll(queryJavaHomesInRegistryKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JDK\\"));
                return locations.stream();

            case LINUX:
                Path linuxJvmDir = Paths.get("/usr/lib/jvm");
                if (Files.isDirectory(linuxJvmDir)) {
                    return Files.list(linuxJvmDir);
                }
                return Stream.empty();

            case OSX:
                Path osxJvmDir = Paths.get("/Library/Java/JavaVirtualMachines");
                if (Files.isDirectory(osxJvmDir)) {
                    return Files.list(osxJvmDir)
                            .map(dir -> dir.resolve("Contents/Home"));
                }
                return Stream.empty();

            default:
                return Stream.empty();
        }
    }

    // ==== Windows Registry Support ====
    private static List<Path> queryJavaHomesInRegistryKey(String location) throws IOException {
        List<Path> homes = new ArrayList<>();
        for (String java : querySubFolders(location)) {
            if (!querySubFolders(java).contains(java + "\\MSI"))
                continue;
            String home = queryRegisterValue(java, "JavaHome");
            if (home != null) {
                homes.add(Paths.get(home));
            }
        }
        return homes;
    }

    private static List<String> querySubFolders(String location) throws IOException {
        List<String> res = new ArrayList<>();

        Process process = Runtime.getRuntime().exec(new String[] { "cmd", "/c", "reg", "query", location });
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.startsWith(location) && !line.equals(location)) {
                    res.add(line);
                }
            }
        }
        return res;
    }

    private static String queryRegisterValue(String location, String name) throws IOException {
        boolean last = false;
        Process process = Runtime.getRuntime().exec(new String[] { "cmd", "/c", "reg", "query", location, "/v", name });

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (StringUtils.isNotBlank(line)) {
                    if (last && line.trim().startsWith(name)) {
                        int begins = line.indexOf(name);
                        if (begins > 0) {
                            String s2 = line.substring(begins + name.length());
                            begins = s2.indexOf("REG_SZ");
                            if (begins > 0) {
                                return s2.substring(begins + "REG_SZ".length()).trim();
                            }
                        }
                    }
                    if (location.equals(line.trim())) {
                        last = true;
                    }
                }
            }
        }
        return null;
    }
    // ====
}
