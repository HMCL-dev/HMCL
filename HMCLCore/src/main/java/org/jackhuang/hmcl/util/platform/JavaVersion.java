/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.jackhuang.hmcl.util.Logging.LOG;

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
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#JAVA_9
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#JAVA_8
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#JAVA_7
     * @see org.jackhuang.hmcl.util.platform.JavaVersion#UNKNOWN
     */
    public int getParsedVersion() {
        return version;
    }

    private static final Pattern REGEX = Pattern.compile("version \"(?<version>(.*?))\"");
    private static final Pattern VERSION = Pattern.compile("^(?<version>[0-9]+)");

    public static final int UNKNOWN = -1;
    public static final int JAVA_7 = 7;
    public static final int JAVA_8 = 8;
    public static final int JAVA_9 = 9;
    public static final int JAVA_16 = 16;

    private static int parseVersion(String version) {
        Matcher matcher = VERSION.matcher(version);
        if (matcher.find()) {
            int head = Lang.parseInt(matcher.group(), -1);
            if (head > 1) return head;
        }
        if (version.contains("1.8"))
            return JAVA_8;
        else if (version.contains("1.7"))
            return JAVA_7;
        else
            return UNKNOWN;
    }

    private static final Map<Path, JavaVersion> fromExecutableCache = new ConcurrentHashMap<>();

    public static JavaVersion fromExecutable(Path executable) throws IOException {
        executable = executable.toRealPath();
        JavaVersion cachedJavaVersion = fromExecutableCache.get(executable);
        if (cachedJavaVersion != null)
            return cachedJavaVersion;

        Platform platform = Platform.BIT_32;
        String version = null;

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
        JavaVersion javaVersion = new JavaVersion(executable, version, platform);
        fromExecutableCache.put(executable, javaVersion);
        return javaVersion;
    }

    public static Path getExecutable(Path javaHome) {
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

    private static Collection<JavaVersion> JAVAS;
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    public static Collection<JavaVersion> getJavas() throws InterruptedException {
        if (JAVAS != null)
            return JAVAS;
        LATCH.await();
        return JAVAS;
    }

    public static synchronized void initialize() {
        if (JAVAS != null)
            throw new IllegalStateException("JavaVersions have already been initialized.");

        List<JavaVersion> javaVersions;

        try (Stream<Path> stream = searchPotentialJavaExecutables()) {
            javaVersions = lookupJavas(stream);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to search Java homes", e);
            javaVersions = new ArrayList<>();
        }

        // insert current java to the list
        if (!javaVersions.contains(CURRENT_JAVA)) {
            javaVersions.add(CURRENT_JAVA);
        }

        JAVAS = Collections.newSetFromMap(new ConcurrentHashMap<>());
        JAVAS.addAll(javaVersions);
        LATCH.countDown();
    }

    private static List<JavaVersion> lookupJavas(Stream<Path> javaExecutables) {
        return javaExecutables
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

    private static Stream<Path> searchPotentialJavaExecutables() throws IOException {
        List<Stream<Path>> javaExecutables = new ArrayList<>();
        switch (OperatingSystem.CURRENT_OS) {

            case WINDOWS:
                Path programFiles = Paths.get(Optional.ofNullable(System.getenv("ProgramFiles")).orElse("C:\\Program Files"));
                Path programFiles_x86 = Paths.get(Optional.ofNullable(System.getenv("ProgramFiles(x86)")).orElse("C:\\Program Files (x86)"));

                javaExecutables.add(queryJavaHomesInRegistryKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment\\").stream().map(JavaVersion::getExecutable));
                javaExecutables.add(queryJavaHomesInRegistryKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\").stream().map(JavaVersion::getExecutable));
                javaExecutables.add(queryJavaHomesInRegistryKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JRE\\").stream().map(JavaVersion::getExecutable));
                javaExecutables.add(queryJavaHomesInRegistryKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JDK\\").stream().map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles.resolve("Java")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles.resolve("BellSoft")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles.resolve("AdoptOpenJDK")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles.resolve("Zulu")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles.resolve("Microsoft")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles_x86.resolve("Java")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles_x86.resolve("BellSoft")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles_x86.resolve("AdoptOpenJDK")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles_x86.resolve("Zulu")).map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(programFiles_x86.resolve("Microsoft")).map(JavaVersion::getExecutable));
                if (System.getenv("PATH") != null) {
                    javaExecutables.add(Arrays.stream(System.getenv("PATH").split(";")).map(path -> Paths.get(path, "java.exe")));
                }
                if (System.getenv("HMCL_JRES") != null) {
                    javaExecutables.add(Arrays.stream(System.getenv("HMCL_JRES").split(";")).map(path -> Paths.get(path, "java.exe")));
                }
                break;

            case LINUX:
                javaExecutables.add(listDirectory(Paths.get("/usr/java")).map(JavaVersion::getExecutable)); // Oracle RPMs
                javaExecutables.add(listDirectory(Paths.get("/usr/lib/jvm")).map(JavaVersion::getExecutable)); // General locations
                javaExecutables.add(listDirectory(Paths.get("/usr/lib32/jvm")).map(JavaVersion::getExecutable)); // General locations
                if (System.getenv("PATH") != null) {
                    javaExecutables.add(Arrays.stream(System.getenv("PATH").split(":")).map(path -> Paths.get(path, "java")));
                }
                if (System.getenv("HMCL_JRES") != null) {
                    javaExecutables.add(Arrays.stream(System.getenv("HMCL_JRES").split(":")).map(path -> Paths.get(path, "java")));
                }
                break;

            case OSX:
                javaExecutables.add(listDirectory(Paths.get("/Library/Java/JavaVirtualMachines"))
                        .flatMap(dir -> Stream.of(dir.resolve("Contents/Home"), dir.resolve("Contents/Home/jre")))
                        .map(JavaVersion::getExecutable));
                javaExecutables.add(listDirectory(Paths.get("/System/Library/Java/JavaVirtualMachines"))
                        .map(dir -> dir.resolve("Contents/Home"))
                        .map(JavaVersion::getExecutable));
                javaExecutables.add(Stream.of(Paths.get("/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java")));
                javaExecutables.add(Stream.of(Paths.get("/Applications/Xcode.app/Contents/Applications/Application Loader.app/Contents/MacOS/itms/java/bin/java")));
                if (System.getenv("PATH") != null) {
                    javaExecutables.add(Arrays.stream(System.getenv("PATH").split(":")).map(path -> Paths.get(path, "java")));
                }
                if (System.getenv("HMCL_JRES") != null) {
                    javaExecutables.add(Arrays.stream(System.getenv("HMCL_JRES").split(":")).map(path -> Paths.get(path, "java")));
                }
                break;

            default:
                break;
        }
        return javaExecutables.stream().flatMap(stream -> stream);
    }

    private static Stream<Path> listDirectory(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            return Files.list(directory);
        } else {
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
                try {
                    homes.add(Paths.get(home));
                } catch (InvalidPathException e) {
                    LOG.log(Level.WARNING, "Invalid Java path in system registry: " + home);
                }
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
