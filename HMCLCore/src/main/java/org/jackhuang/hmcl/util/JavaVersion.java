/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a Java installation.
 *
 * @author huangyuhui
 */
public final class JavaVersion implements Serializable {

    private final File binary;
    private final String longVersion;
    private final Platform platform;
    private final int version;

    public JavaVersion(File binary, String longVersion, Platform platform) {
        this.binary = binary;
        this.longVersion = longVersion;
        this.platform = platform;
        version = parseVersion(longVersion);
    }

    public File getBinary() {
        return binary;
    }

    public String getVersion() {
        return longVersion;
    }

    public Platform getPlatform() {
        return platform;
    }

    /**
     * The major version of Java installation.
     *
     * @see org.jackhuang.hmcl.util.JavaVersion#JAVA_X
     * @see org.jackhuang.hmcl.util.JavaVersion#JAVA_9
     * @see org.jackhuang.hmcl.util.JavaVersion#JAVA_8
     * @see org.jackhuang.hmcl.util.JavaVersion#JAVA_7
     * @see org.jackhuang.hmcl.util.JavaVersion#JAVA_6
     * @see org.jackhuang.hmcl.util.JavaVersion#JAVA_5
     * @see org.jackhuang.hmcl.util.JavaVersion#UNKNOWN
     */
    public int getParsedVersion() {
        return version;
    }

    private static final Pattern REGEX = Pattern.compile("version \"(?<version>(.*?))\"");

    public static final int UNKNOWN = -1;
    public static final int JAVA_5 = 50;
    public static final int JAVA_6 = 60;
    public static final int JAVA_7 = 70;
    public static final int JAVA_8 = 80;
    public static final int JAVA_9 = 90;
    public static final int JAVA_X = 100;

    private static int parseVersion(String version) {
        if (version.startsWith("10") || version.startsWith("X"))
            return JAVA_X;
        else if (version.contains("1.9.") || version.startsWith("9"))
            return JAVA_9;
        else if (version.contains("1.8"))
            return JAVA_8;
        else if (version.contains("1.7"))
            return JAVA_7;
        else if (version.contains("1.6"))
            return JAVA_6;
        else if (version.contains("1.5"))
            return JAVA_5;
        else
            return UNKNOWN;
    }

    public static JavaVersion fromExecutable(File executable) throws IOException {
        File actualFile = executable;
        Platform platform = Platform.BIT_32;
        String version = null;

        if ("javaw".equals(FileUtils.getNameWithoutExtension(actualFile)))
            actualFile = new File(actualFile.getAbsoluteFile().getParentFile(), "java");

        try {
            Process process = new ProcessBuilder(actualFile.getAbsolutePath(), "-version").start();
            process.waitFor();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    Matcher m = REGEX.matcher(line);
                    if (m.find())
                        version = m.group("version");
                    if (line.contains("64-Bit"))
                        platform = Platform.BIT_64;
                }
            }
        } catch (InterruptedException e) {
            throw new IOException("Interrupted scanning the java version.", e);
        }

        if (version == null)
            throw new IOException("No matched Java version.");

        if (parseVersion(version) == UNKNOWN)
            throw new IOException("Unrecognized Java version " + version);
        return new JavaVersion(actualFile, version, platform);
    }

    private static JavaVersion fromKnownExecutable(File file, String version) {
        return new JavaVersion(file, version, Platform.UNKNOWN);
    }

    public static JavaVersion fromJavaHome(File home) throws IOException {
        return fromExecutable(getJavaFile(home));
    }

    private static File getJavaFile(File home) {
        File path = new File(home, "bin");
        File javaw = new File(path, "javae.exe");
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && javaw.isFile())
            return javaw;
        else
            return new File(path, "java"); // Both linux and windows allow this.
    }

    public static JavaVersion fromCurrentEnvironment() {
        return THIS_JAVA;
    }

    public static final JavaVersion THIS_JAVA = new JavaVersion(
            getJavaFile(new File(System.getProperty("java.home"))),
            System.getProperty("java.version"),
            Platform.PLATFORM
    );

    private static List<JavaVersion> JAVAS;
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    public static Optional<List<JavaVersion>> getJREsImmediately() {
        return Optional.ofNullable(JAVAS);
    }

    public static List<JavaVersion> getJREs() throws InterruptedException {
        if (JAVAS != null)
            return JAVAS;
        LATCH.await();
        return JAVAS;
    }

    public static synchronized void initialize() throws IOException {
        if (JAVAS != null)
            throw new IllegalStateException("JavaVersions have already been initialized.");
        List<JavaVersion> javaVersions;
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                javaVersions = queryWindows();
                break;
            case LINUX:
                javaVersions = queryLinux();
                break;
            case OSX:
                javaVersions = queryMacintosh();
                break;
            default:
                javaVersions = Collections.emptyList();
                break;
        }

        boolean isCurrentJavaIncluded = false;
        for (int i = 0; i < javaVersions.size(); i++) {
            if (THIS_JAVA.getBinary().equals(javaVersions.get(i).getBinary())) {
                javaVersions.set(i, THIS_JAVA);
                isCurrentJavaIncluded = true;
                break;
            }
        }
        if (!isCurrentJavaIncluded) {
            javaVersions.add(THIS_JAVA);
        }

        JAVAS = Collections.unmodifiableList(javaVersions);
        LATCH.countDown();
    }

    private static List<JavaVersion> queryLinux() throws IOException {
        Path jvmDir = Paths.get("/usr/lib/jvm");
        if (Files.isDirectory(jvmDir)) {
            return Files.list(jvmDir)
                    .filter(dir -> Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS))
                    .map(dir -> dir.resolve("bin/java"))
                    .filter(Files::isExecutable)
                    .flatMap(executable -> {
                        try {
                            return Stream.of(fromExecutable(executable.toFile()));
                        } catch (IOException e) {
                            Logging.LOG.log(Level.WARNING, "Couldn't determine java " + executable, e);
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private static List<JavaVersion> queryMacintosh() throws IOException {
        LinkedList<JavaVersion> res = new LinkedList<>();

        File currentJRE = new File("/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home");
        if (currentJRE.exists())
            res.add(fromJavaHome(currentJRE));
        File[] files = new File("/Library/Java/JavaVirtualMachines/").listFiles();
        if (files != null)
            for (File file : files)
                res.add(fromJavaHome(new File(file, "Contents/Home")));

        return res;
    }

    private static List<JavaVersion> queryWindows() {
        LinkedList<JavaVersion> res = new LinkedList<>();
        Lang.ignoringException(() -> res.addAll(queryRegisterKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment\\")));
        Lang.ignoringException(() -> res.addAll(queryRegisterKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\")));
        Lang.ignoringException(() -> res.addAll(queryRegisterKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JRE\\")));
        Lang.ignoringException(() -> res.addAll(queryRegisterKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JDK\\")));
        return res;
    }

    private static List<String> querySubFolders(String location) throws IOException, InterruptedException {
        List<String> res = new LinkedList<>();
        String[] cmd = new String[] { "cmd", "/c", "reg", "query", location };
        Process process = Runtime.getRuntime().exec(cmd);
        process.waitFor();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null; )
                if (line.startsWith(location) && !line.equals(location))
                    res.add(line);
        }
        return res;
    }

    private static List<JavaVersion> queryRegisterKey(String location) throws IOException, InterruptedException {
        List<JavaVersion> res = new LinkedList<>();
        for (String java : querySubFolders(location)) {
            String home = queryRegisterValue(java, "JavaHome");
            if (home != null)
                res.add(fromJavaHome(new File(home)));
        }
        return res;
    }

    private static String queryRegisterValue(String location, String name) throws IOException, InterruptedException {
        String[] cmd = new String[] { "cmd", "/c", "reg", "query", location, "/v", name };
        boolean last = false;
        Process process = Runtime.getRuntime().exec(cmd);
        process.waitFor();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null; )
                if (StringUtils.isNotBlank(line)) {
                    if (last && line.trim().startsWith(name)) {
                        int begins = line.indexOf(name);
                        if (begins > 0) {
                            String s2 = line.substring(begins + name.length());
                            begins = s2.indexOf("REG_SZ");
                            if (begins > 0)
                                return s2.substring(begins + "REG_SZ".length()).trim();
                        }
                    }
                    if (location.equals(line.trim()))
                        last = true;
                }
        }
        return null;
    }
}
