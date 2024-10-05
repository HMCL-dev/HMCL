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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.game.JavaVersionConstraint;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.platform.UnsupportedPlatformException;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class JavaManager {

    private JavaManager() {
    }

    public static final HMCLJavaRepository REPOSITORY = new HMCLJavaRepository(Metadata.HMCL_DIRECTORY.resolve("java"));

    public static String getMojangJavaPlatform(Platform platform) {
        if (platform.getOperatingSystem() == OperatingSystem.WINDOWS) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                return "windows-x86";
            } else if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return "windows-x64";
            } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                return "windows-arm64";
            }
        } else if (platform.getOperatingSystem() == OperatingSystem.LINUX) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                return "linux-i386";
            } else if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return "linux";
            }
        } else if (platform.getOperatingSystem() == OperatingSystem.OSX) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return "mac-os";
            } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                return "mac-os-arm64";
            }
        }

        return null;
    }

    public static Path getExecutable(Path javaHome) {
        return javaHome.resolve("bin").resolve(OperatingSystem.CURRENT_OS.getJavaExecutable());
    }

    public static Path getMacExecutable(Path javaHome) {
        return javaHome.resolve("jre.bundle/Contents/Home/bin/java");
    }

    public static boolean isCompatible(Platform platform) {
        if (platform.getOperatingSystem() != OperatingSystem.CURRENT_OS)
            return false;

        Architecture architecture = platform.getArchitecture();
        if (architecture == Architecture.SYSTEM_ARCH || architecture == Architecture.CURRENT_ARCH)
            return true;

        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64)
                    return architecture == Architecture.X86;
                if (Architecture.SYSTEM_ARCH == Architecture.ARM64)
                    return OperatingSystem.SYSTEM_BUILD_NUMBER >= 21277 && architecture == Architecture.X86_64 || architecture == Architecture.X86;
                break;
            case LINUX:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64)
                    return architecture == Architecture.X86;
                break;
            case OSX:
                if (Architecture.SYSTEM_ARCH == Architecture.ARM64)
                    return architecture == Architecture.X86_64;
                break;
        }

        return false;
    }

    private static volatile Map<Path, JavaRuntime> allJava;
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    private static final ObjectProperty<Collection<JavaRuntime>> allJavaProperty = new SimpleObjectProperty<>();

    private static Map<Path, JavaRuntime> getAllJavaMap() throws InterruptedException {
        Map<Path, JavaRuntime> map = allJava;
        if (map == null) {
            LATCH.await();
            map = allJava;
        }
        return map;
    }

    private static void updateAllJavaProperty(Map<Path, JavaRuntime> javaRuntimes) {
        JavaRuntime[] array = javaRuntimes.values().toArray(new JavaRuntime[0]);
        Arrays.sort(array);
        allJavaProperty.set(Arrays.asList(array));
    }

    public static boolean isInitialized() {
        return allJava != null;
    }

    public static Collection<JavaRuntime> getAllJava() throws InterruptedException {
        return getAllJavaMap().values();
    }

    public static ObjectProperty<Collection<JavaRuntime>> getAllJavaProperty() {
        return allJavaProperty;
    }

    public static JavaRuntime getJava(Path executable) throws IOException, InterruptedException {
        executable = executable.toRealPath();

        JavaRuntime javaRuntime = getAllJavaMap().get(executable);
        if (javaRuntime != null) {
            return javaRuntime;
        }

        JavaInfo info = JavaInfo.fromExecutable(executable);
        return JavaRuntime.of(executable, info, false);
    }

    public static void refresh() {
        Task.supplyAsync(JavaManager::searchPotentialJavaExecutables).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (result != null) {
                LATCH.await();
                allJava = result;
                updateAllJavaProperty(result);
            }
        }).start();
    }

    public static Task<JavaRuntime> getAddJavaTask(Path binary) {
        return Task.supplyAsync("Get Java", () -> JavaManager.getJava(binary))
                .thenApplyAsync(Schedulers.javafx(), javaRuntime -> {
                    if (!JavaManager.isCompatible(javaRuntime.getPlatform())) {
                        throw new UnsupportedPlatformException("Incompatible platform: " + javaRuntime.getPlatform());
                    }

                    String pathString = javaRuntime.getBinary().toString();

                    ConfigHolder.globalConfig().getDisabledJava().remove(pathString);
                    if (ConfigHolder.globalConfig().getUserJava().add(pathString)) {
                        addJava(javaRuntime);
                    }
                    return javaRuntime;
                });
    }

    public static Task<JavaRuntime> getDownloadJavaTask(DownloadProvider downloadProvider, Platform platform, GameJavaVersion gameJavaVersion) {
        return REPOSITORY.getDownloadJavaTask(downloadProvider, platform, gameJavaVersion)
                .thenApplyAsync(Schedulers.javafx(), java -> {
                    addJava(java);
                    return java;
                });
    }

    public static Task<JavaRuntime> getInstallJavaTask(Platform platform, String name, Map<String, Object> update, Path archiveFile) {
        return REPOSITORY.getInstallJavaTask(platform, name, update, archiveFile)
                .thenApplyAsync(Schedulers.javafx(), java -> {
                    addJava(java);
                    return java;
                });
    }

    public static Task<Void> getUninstallJavaTask(JavaRuntime java) {
        assert java.isManaged();
        Path root = REPOSITORY.getPlatformRoot(java.getPlatform());
        Path relativized = root.relativize(java.getBinary());

        if (relativized.getNameCount() > 1) {
            FXUtils.runInFX(() -> {
                try {
                    removeJava(java);
                } catch (InterruptedException e) {
                    throw new AssertionError("Unreachable code", e);
                }
            });

            String name = relativized.getName(0).toString();
            return REPOSITORY.getUninstallJavaTask(java.getPlatform(), name);
        } else {
            return Task.completed(null);
        }
    }

    // FXThread
    public static void addJava(JavaRuntime java) throws InterruptedException {
        Map<Path, JavaRuntime> oldMap = getAllJavaMap();
        if (!oldMap.containsKey(java.getBinary())) {
            HashMap<Path, JavaRuntime> newMap = new HashMap<>(oldMap);
            newMap.put(java.getBinary(), java);
            allJava = newMap;
            updateAllJavaProperty(newMap);
        }
    }

    // FXThread
    public static void removeJava(JavaRuntime java) throws InterruptedException {
        removeJava(java.getBinary());
    }

    // FXThread
    public static void removeJava(Path realPath) throws InterruptedException {
        Map<Path, JavaRuntime> oldMap = getAllJavaMap();
        if (oldMap.containsKey(realPath)) {
            HashMap<Path, JavaRuntime> newMap = new HashMap<>(oldMap);
            newMap.remove(realPath);
            allJava = newMap;
            updateAllJavaProperty(newMap);
        }
    }

    private static int compareJavaVersion(JavaRuntime java1, JavaRuntime java2, GameJavaVersion suggestedJavaVersion) {
        if (suggestedJavaVersion != null) {
            boolean b1 = java1.getParsedVersion() == suggestedJavaVersion.getMajorVersion();
            boolean b2 = java2.getParsedVersion() == suggestedJavaVersion.getMajorVersion();

            if (b1 != b2)
                return b1 ? 1 : -1;
        }

        return java1.getVersionNumber().compareTo(java2.getVersionNumber());
    }

    @Nullable
    public static JavaRuntime findSuitableJava(GameVersionNumber gameVersion, Version version) throws InterruptedException {
        return findSuitableJava(getAllJava(), gameVersion, version);
    }

    @Nullable
    public static JavaRuntime findSuitableJava(Collection<JavaRuntime> javaRuntimes, GameVersionNumber gameVersion, Version version) {
        LibraryAnalyzer analyzer = version != null ? LibraryAnalyzer.analyze(version, gameVersion != null ? gameVersion.toString() : null) : null;

        boolean forceX86 = Architecture.SYSTEM_ARCH == Architecture.ARM64
                && (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS || OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
                && (gameVersion == null || gameVersion.compareTo("1.6") < 0);

        GameJavaVersion suggestedJavaVersion =
                (version != null && gameVersion != null && gameVersion.compareTo("1.7.10") >= 0) ? version.getJavaVersion() : null;

        JavaRuntime mandatory = null;
        JavaRuntime suggested = null;
        for (JavaRuntime java : javaRuntimes) {
            if (forceX86) {
                if (!java.getArchitecture().isX86())
                    continue;
            } else {
                if (java.getArchitecture() != Architecture.SYSTEM_ARCH)
                    continue;
            }

            boolean violationMandatory = false;
            boolean violationSuggested = false;

            for (JavaVersionConstraint constraint : JavaVersionConstraint.ALL) {
                if (constraint.appliesToVersion(gameVersion, version, java, analyzer)) {
                    if (!constraint.checkJava(gameVersion, version, java)) {
                        if (constraint.isMandatory()) {
                            violationMandatory = true;
                        } else {
                            violationSuggested = true;
                        }
                    }
                }
            }

            if (!violationMandatory) {
                if (mandatory == null) mandatory = java;
                else if (compareJavaVersion(java, mandatory, suggestedJavaVersion) > 0)
                    mandatory = java;

                if (!violationSuggested) {
                    if (suggested == null) suggested = java;
                    else if (compareJavaVersion(java, suggested, suggestedJavaVersion) > 0)
                        suggested = java;
                }
            }
        }

        return suggested != null ? suggested : mandatory;
    }

    public static void initialize() {
        Map<Path, JavaRuntime> allJava = searchPotentialJavaExecutables();
        JavaManager.allJava = allJava;
        LATCH.countDown();
        FXUtils.runInFX(() -> updateAllJavaProperty(allJava));
    }

    // search java

    private static Map<Path, JavaRuntime> searchPotentialJavaExecutables() {
        Map<Path, JavaRuntime> javaRuntimes = new HashMap<>();
        searchAllJavaInRepository(javaRuntimes, Platform.SYSTEM_PLATFORM);
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64)
                    searchAllJavaInRepository(javaRuntimes, Platform.WINDOWS_X86);
                if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    if (OperatingSystem.SYSTEM_BUILD_NUMBER >= 21277)
                        searchAllJavaInRepository(javaRuntimes, Platform.WINDOWS_X86_64);
                    searchAllJavaInRepository(javaRuntimes, Platform.WINDOWS_X86);
                }
                break;
            case OSX:
                if (Architecture.SYSTEM_ARCH == Architecture.ARM64)
                    searchAllJavaInRepository(javaRuntimes, Platform.OSX_X86_64);
                break;
        }

        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                queryJavaInRegistryKey(javaRuntimes, "HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment\\");
                queryJavaInRegistryKey(javaRuntimes, "HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\");
                queryJavaInRegistryKey(javaRuntimes, "HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JRE\\");
                queryJavaInRegistryKey(javaRuntimes, "HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JDK\\");

                searchJavaInProgramFiles(javaRuntimes, "ProgramFiles", "C:\\Program Files");
                searchJavaInProgramFiles(javaRuntimes, "ProgramFiles(x86)", "C:\\Program Files (x86)");
                if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    searchJavaInProgramFiles(javaRuntimes, "ProgramFiles(ARM)", "C:\\Program Files (ARM)");
                }
                break;
            case LINUX:
                searchAllJavaInDirectory(javaRuntimes, Paths.get("/usr/java"));      // Oracle RPMs
                searchAllJavaInDirectory(javaRuntimes, Paths.get("/usr/lib/jvm"));   // General locations
                searchAllJavaInDirectory(javaRuntimes, Paths.get("/usr/lib32/jvm")); // General locations
                searchAllJavaInDirectory(javaRuntimes, Paths.get("/usr/lib64/jvm")); // General locations
                searchAllJavaInDirectory(javaRuntimes, Paths.get(System.getProperty("user.home"), "/.sdkman/candidates/java")); // SDKMAN!
                break;
            case OSX:
                tryAddJavaHome(javaRuntimes, Paths.get("/Library/Java/JavaVirtualMachines/Contents/Home"));
                tryAddJavaHome(javaRuntimes, Paths.get(System.getProperty("user.home"), "/Library/Java/JavaVirtualMachines/Contents/Home"));
                tryAddJavaExecutable(javaRuntimes, Paths.get("/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java"));
                tryAddJavaExecutable(javaRuntimes, Paths.get("/Applications/Xcode.app/Contents/Applications/Application Loader.app/Contents/MacOS/itms/java/bin/java"));
                // Homebrew
                tryAddJavaExecutable(javaRuntimes, Paths.get("/opt/homebrew/opt/java/bin/java"));
                searchAllJavaInDirectory(javaRuntimes, Paths.get("/opt/homebrew/Cellar/openjdk"));
                try (DirectoryStream<Path> dirs = Files.newDirectoryStream(Paths.get("/opt/homebrew/Cellar"), "openjdk@*")) {
                    for (Path dir : dirs) {
                        searchAllJavaInDirectory(javaRuntimes, dir);
                    }
                } catch (IOException e) {
                    LOG.warning("Failed to get subdirectories of /opt/homebrew/Cellar");
                }
                break;

            default:
                break;
        }

        // Search Minecraft bundled runtimes
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && Architecture.SYSTEM_ARCH.isX86()) {
            FileUtils.tryGetPath(System.getenv("localappdata"), "Packages\\Microsoft.4297127D64EC6_8wekyb3d8bbwe\\LocalCache\\Local\\runtime")
                    .ifPresent(it -> searchAllOfficialJava(javaRuntimes, it, false));

            FileUtils.tryGetPath(Lang.requireNonNullElse(System.getenv("ProgramFiles(x86)"), "C:\\Program Files (x86)"), "Minecraft Launcher\\runtime")
                    .ifPresent(it -> searchAllOfficialJava(javaRuntimes, it, false));
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.X86_64) {
            searchAllOfficialJava(javaRuntimes, Paths.get(System.getProperty("user.home")).resolve(".minecraft/runtime"), false);
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
            searchAllOfficialJava(javaRuntimes, Paths.get(System.getProperty("user.home")).resolve("Library/Application Support/minecraft/runtime"), false);
        }
        searchAllOfficialJava(javaRuntimes, CacheRepository.getInstance().getCacheDirectory().resolve("java"), true);

        // Search in PATH.
        if (System.getenv("PATH") != null) {
            String[] paths = System.getenv("PATH").split(OperatingSystem.PATH_SEPARATOR);
            for (String path : paths) {
                try {
                    tryAddJavaExecutable(javaRuntimes, Paths.get(path, OperatingSystem.CURRENT_OS.getJavaExecutable()));
                } catch (InvalidPathException ignored) {
                }
            }
        }

        if (System.getenv("HMCL_JRES") != null) {
            String[] paths = System.getenv("HMCL_JRES").split(OperatingSystem.PATH_SEPARATOR);
            for (String path : paths) {
                try {
                    tryAddJavaHome(javaRuntimes, Paths.get(path));
                } catch (InvalidPathException ignored) {
                }
            }
        }

        for (String javaPath : ConfigHolder.globalConfig().getUserJava()) {
            try {
                tryAddJavaExecutable(javaRuntimes, Paths.get(javaPath));
            } catch (InvalidPathException e) {
                LOG.warning("Invalid Java path: " + javaPath);
            }
        }

        JavaRuntime currentJava = JavaRuntime.CURRENT_JAVA;
        if (currentJava != null
                && !javaRuntimes.containsKey(currentJava.getBinary())
                && !ConfigHolder.globalConfig().getDisabledJava().contains(currentJava.getBinary().toString())) {
            javaRuntimes.put(currentJava.getBinary(), currentJava);
        }

        LOG.trace(javaRuntimes.values().stream().sorted()
                .map(it -> String.format(" - %s %s (%s, %s): %s",
                        it.isJDK() ? "JDK" : "JRE",
                        it.getVersion(),
                        it.getPlatform().getArchitecture().getDisplayName(),
                        Lang.requireNonNullElse(it.getVendor(), "Unknown"),
                        it.getBinary()))
                .collect(Collectors.joining("\n", "Finished Java lookup, found " + javaRuntimes.size() + "\n", "")));

        return javaRuntimes;
    }

    private static void tryAddJavaHome(Map<Path, JavaRuntime> javaRuntimes, Path javaHome) {
        Path executable = getExecutable(javaHome);
        if (!Files.isRegularFile(executable)) {
            return;
        }

        try {
            executable = executable.toRealPath();
        } catch (IOException e) {
            LOG.warning("Failed to resolve path " + executable, e);
            return;
        }

        if (javaRuntimes.containsKey(executable) || ConfigHolder.globalConfig().getDisabledJava().contains(executable.toString())) {
            return;
        }

        JavaInfo info = null;

        Path releaseFile = javaHome.resolve("release");
        if (Files.exists(releaseFile)) {
            try {
                info = JavaInfo.fromReleaseFile(releaseFile);
            } catch (IOException e) {
                try {
                    info = JavaInfo.fromExecutable(executable, false);
                } catch (IOException e2) {
                    e2.addSuppressed(e);
                    LOG.warning("Failed to lookup Java executable at " + executable, e2);
                }
            }
        }

        if (info != null && isCompatible(info.getPlatform()))
            javaRuntimes.put(executable, JavaRuntime.of(executable, info, false));
    }

    private static void tryAddJavaExecutable(Map<Path, JavaRuntime> javaRuntimes, Path executable) {
        try {
            executable = executable.toRealPath();
        } catch (IOException e) {
            return;
        }

        if (javaRuntimes.containsKey(executable) || ConfigHolder.globalConfig().getDisabledJava().contains(executable.toString())) {
            return;
        }

        JavaInfo info = null;
        try {
            info = JavaInfo.fromExecutable(executable);
        } catch (IOException e) {
            LOG.warning("Failed to lookup Java executable at " + executable, e);
        }

        if (info != null && isCompatible(info.getPlatform())) {
            javaRuntimes.put(executable, JavaRuntime.of(executable, info, false));
        }
    }

    private static void tryAddJavaInComponentDir(Map<Path, JavaRuntime> javaRuntimes, String platform, Path component, boolean verify) {
        Path sha1File = component.resolve(platform).resolve(component.getFileName() + ".sha1");
        if (!Files.isRegularFile(sha1File))
            return;

        Path dir = component.resolve(platform).resolve(component.getFileName());

        if (verify) {
            try (BufferedReader reader = Files.newBufferedReader(sha1File)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;

                    int idx = line.indexOf(" /#//");
                    if (idx <= 0)
                        throw new IOException("Illegal line: " + line);

                    Path file = dir.resolve(line.substring(0, idx));

                    // Should we check the sha1 of files? This will take a lot of time.
                    if (Files.notExists(file))
                        throw new NoSuchFileException(file.toAbsolutePath().toString());
                }
            } catch (IOException e) {
                LOG.warning("Failed to verify Java in " + component, e);
                return;
            }
        }

        if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
            Path macPath = dir.resolve("jre.bundle/Contents/Home");
            if (Files.exists(macPath)) {
                tryAddJavaHome(javaRuntimes, macPath);
                return;
            } else
                LOG.warning("The Java is not in 'jre.bundle/Contents/Home'");
        }

        tryAddJavaHome(javaRuntimes, dir);
    }

    private static void searchAllJavaInRepository(Map<Path, JavaRuntime> javaRuntimes, Platform platform) {
        for (JavaRuntime java : REPOSITORY.getAllJava(platform)) {
            javaRuntimes.put(java.getBinary(), java);
        }
    }

    private static void searchAllOfficialJava(Map<Path, JavaRuntime> javaRuntimes, Path directory, boolean verify) {
        if (!Files.isDirectory(directory))
            return;
        // Examples:
        // $HOME/Library/Application Support/minecraft/runtime/java-runtime-beta/mac-os/java-runtime-beta/jre.bundle/Contents/Home
        // $HOME/.minecraft/runtime/java-runtime-beta/linux/java-runtime-beta

        String javaPlatform = getMojangJavaPlatform(Platform.SYSTEM_PLATFORM);
        if (javaPlatform != null) {
            searchAllOfficialJava(javaRuntimes, directory, javaPlatform, verify);
        }

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                searchAllOfficialJava(javaRuntimes, directory, getMojangJavaPlatform(Platform.WINDOWS_X86), verify);
            } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                if (OperatingSystem.SYSTEM_BUILD_NUMBER >= 21277) {
                    searchAllOfficialJava(javaRuntimes, directory, getMojangJavaPlatform(Platform.WINDOWS_X86_64), verify);
                }
                searchAllOfficialJava(javaRuntimes, directory, getMojangJavaPlatform(Platform.WINDOWS_X86), verify);
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX && Architecture.CURRENT_ARCH == Architecture.ARM64) {
            searchAllOfficialJava(javaRuntimes, directory, getMojangJavaPlatform(Platform.OSX_X86_64), verify);
        }
    }

    private static void searchAllOfficialJava(Map<Path, JavaRuntime> javaRuntimes, Path directory, String platform, boolean verify) {
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(directory)) {
            // component can be jre-legacy, java-runtime-alpha, java-runtime-beta, java-runtime-gamma or any other being added in the future.
            for (Path component : dir) {
                tryAddJavaInComponentDir(javaRuntimes, platform, component, verify);
            }
        } catch (IOException e) {
            LOG.warning("Failed to list java-runtime directory " + directory, e);
        }
    }

    private static void searchAllJavaInDirectory(Map<Path, JavaRuntime> javaRuntimes, Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path subDir : stream) {
                tryAddJavaHome(javaRuntimes, subDir);
            }
        } catch (IOException e) {
            LOG.warning("Failed to find Java in " + directory, e);
        }
    }

    private static void searchJavaInProgramFiles(Map<Path, JavaRuntime> javaRuntimes, String env, String defaultValue) {
        String programFiles = Lang.requireNonNullElse(System.getenv(env), defaultValue);
        Path path;
        try {
            path = Paths.get(programFiles);
        } catch (InvalidPathException ignored) {
            return;
        }

        for (String vendor : new String[]{"Java", "BellSoft", "AdoptOpenJDK", "Zulu", "Microsoft", "Eclipse Foundation", "Semeru"}) {
            searchAllJavaInDirectory(javaRuntimes, path.resolve(vendor));
        }
    }

    // ==== Windows Registry Support ====
    private static void queryJavaInRegistryKey(Map<Path, JavaRuntime> javaRuntimes, String location) {
        for (String java : querySubFolders(location)) {
            if (!querySubFolders(java).contains(java + "\\MSI"))
                continue;
            String home = queryRegisterValue(java, "JavaHome");
            if (home != null) {
                try {
                    tryAddJavaHome(javaRuntimes, Paths.get(home));
                } catch (InvalidPathException e) {
                    LOG.warning("Invalid Java path in system registry: " + home);
                }
            }
        }
    }

    private static List<String> querySubFolders(String location) {
        List<String> res = new ArrayList<>();

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "reg", "query", location});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), OperatingSystem.NATIVE_CHARSET))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.startsWith(location) && !line.equals(location)) {
                        res.add(line);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to query sub folders of " + location, e);
        }
        return res;
    }

    private static String queryRegisterValue(String location, String name) {
        boolean last = false;

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "reg", "query", location, "/v", name});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), OperatingSystem.NATIVE_CHARSET))) {
                for (String line; (line = reader.readLine()) != null; ) {
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
        } catch (IOException e) {
            LOG.warning("Failed to query register value of " + location, e);
        }

        return null;
    }
}
