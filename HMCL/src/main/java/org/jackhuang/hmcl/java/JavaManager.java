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

import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
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
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.platform.windows.WinReg;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class JavaManager {

    private JavaManager() {
    }

    private static final String[] KNOWN_VENDOR_DIRECTORIES = {
            "Java",
            "BellSoft",
            "AdoptOpenJDK",
            "Zulu",
            "Microsoft",
            "Eclipse Foundation",
            "Semeru"
    };

    public static final HMCLJavaRepository REPOSITORY = new HMCLJavaRepository(Metadata.HMCL_GLOBAL_DIRECTORY.resolve("java"));
    public static final HMCLJavaRepository LOCAL_REPOSITORY = new HMCLJavaRepository(Metadata.HMCL_CURRENT_DIRECTORY.resolve("java"));

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
        } else if (platform.getOperatingSystem() == OperatingSystem.MACOS) {
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
            case MACOS:
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

        JavaInfo info = JavaInfoUtils.fromExecutable(executable);
        return JavaRuntime.of(executable, info, false);
    }

    public static void refresh() {
        Task.supplyAsync(() -> searchPotentialJavaExecutables(false)).whenComplete(Schedulers.javafx(), (result, exception) -> {
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

        Path platformRoot;
        try {
            platformRoot = REPOSITORY.getPlatformRoot(java.getPlatform()).toRealPath();
        } catch (Throwable ignored) {
            return Task.completed(null);
        }

        if (!java.getBinary().startsWith(platformRoot))
            return Task.completed(null);

        Path relativized = platformRoot.relativize(java.getBinary());
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

    private static JavaRuntime chooseJava(@Nullable JavaRuntime java1, JavaRuntime java2) {
        if (java1 == null)
            return java2;

        if (java1.getParsedVersion() != java2.getParsedVersion())
            // Prefer the Java version that is closer to the game's recommended Java version
            return java1.getParsedVersion() < java2.getParsedVersion() ? java1 : java2;
        else
            return java1.getVersionNumber().compareTo(java2.getVersionNumber()) >= 0 ? java1 : java2;
    }

    @Nullable
    public static JavaRuntime findSuitableJava(GameVersionNumber gameVersion, Version version) throws InterruptedException {
        return findSuitableJava(getAllJava(), gameVersion, version);
    }

    @Nullable
    public static JavaRuntime findSuitableJava(Collection<JavaRuntime> javaRuntimes, GameVersionNumber gameVersion, Version version) {
        LibraryAnalyzer analyzer = version != null ? LibraryAnalyzer.analyze(version, gameVersion != null ? gameVersion.toString() : null) : null;

        boolean forceX86 = Architecture.SYSTEM_ARCH == Architecture.ARM64
                && (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS || OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                && (gameVersion == null || gameVersion.compareTo("1.6") < 0);

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
                mandatory = chooseJava(mandatory, java);

                if (!violationSuggested)
                    suggested = chooseJava(suggested, java);
            }
        }

        return suggested != null ? suggested : mandatory;
    }

    public static void initialize() {
        Map<Path, JavaRuntime> allJava = searchPotentialJavaExecutables(true);
        JavaManager.allJava = allJava;
        LATCH.countDown();
        FXUtils.runInFX(() -> updateAllJavaProperty(allJava));
    }

    // search java

    private static Map<Path, JavaRuntime> searchPotentialJavaExecutables(boolean useCache) {
        Searcher searcher = new Searcher(Metadata.HMCL_GLOBAL_DIRECTORY.resolve("javaCache.json"));
        if (useCache)
            searcher.loadCache();

        searcher.searchAllJavaInRepository(Platform.SYSTEM_PLATFORM);
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64)
                    searcher.searchAllJavaInRepository(Platform.WINDOWS_X86);
                if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    if (OperatingSystem.SYSTEM_BUILD_NUMBER >= 21277)
                        searcher.searchAllJavaInRepository(Platform.WINDOWS_X86_64);
                    searcher.searchAllJavaInRepository(Platform.WINDOWS_X86);
                }
                break;
            case MACOS:
                if (Architecture.SYSTEM_ARCH == Architecture.ARM64)
                    searcher.searchAllJavaInRepository(Platform.MACOS_X86_64);
                break;
        }

        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                searcher.queryJavaInRegistryKey(WinReg.HKEY.HKEY_LOCAL_MACHINE, "SOFTWARE\\JavaSoft\\Java Runtime Environment");
                searcher.queryJavaInRegistryKey(WinReg.HKEY.HKEY_LOCAL_MACHINE, "SOFTWARE\\JavaSoft\\Java Development Kit");
                searcher.queryJavaInRegistryKey(WinReg.HKEY.HKEY_LOCAL_MACHINE, "SOFTWARE\\JavaSoft\\JRE");
                searcher.queryJavaInRegistryKey(WinReg.HKEY.HKEY_LOCAL_MACHINE, "SOFTWARE\\JavaSoft\\JDK");

                searcher.searchJavaInProgramFiles("ProgramFiles", "C:\\Program Files");
                searcher.searchJavaInProgramFiles("ProgramFiles(x86)", "C:\\Program Files (x86)");
                break;
            case LINUX:
                searcher.searchAllJavaInDirectory(Path.of("/usr/java"));      // Oracle RPMs
                searcher.searchAllJavaInDirectory(Path.of("/usr/lib/jvm"));   // General locations
                searcher.searchAllJavaInDirectory(Path.of("/usr/lib32/jvm")); // General locations
                searcher.searchAllJavaInDirectory(Path.of("/usr/lib64/jvm")); // General locations
                searcher.searchAllJavaInDirectory(Path.of(System.getProperty("user.home"), "/.sdkman/candidates/java")); // SDKMAN!
                break;
            case MACOS:
                searcher.searchJavaInMacJavaVirtualMachines(Path.of("/Library/Java/JavaVirtualMachines"));
                searcher.searchJavaInMacJavaVirtualMachines(Path.of(System.getProperty("user.home"), "/Library/Java/JavaVirtualMachines"));
                searcher.tryAddJavaExecutable(Path.of("/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home/bin/java"));
                searcher.tryAddJavaExecutable(Path.of("/Applications/Xcode.app/Contents/Applications/Application Loader.app/Contents/MacOS/itms/java/bin/java"));
                // Homebrew
                searcher.tryAddJavaExecutable(Path.of("/opt/homebrew/opt/java/bin/java"));
                searcher.searchAllJavaInDirectory(Path.of("/opt/homebrew/Cellar/openjdk"));
                try (DirectoryStream<Path> dirs = Files.newDirectoryStream(Path.of("/opt/homebrew/Cellar"), "openjdk@*")) {
                    for (Path dir : dirs) {
                        searcher.searchAllJavaInDirectory(dir);
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
                    .ifPresent(it -> searcher.searchAllOfficialJava(it, false));

            FileUtils.tryGetPath(Lang.requireNonNullElse(System.getenv("ProgramFiles(x86)"), "C:\\Program Files (x86)"), "Minecraft Launcher\\runtime")
                    .ifPresent(it -> searcher.searchAllOfficialJava(it, false));
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.X86_64) {
            searcher.searchAllOfficialJava(Path.of(System.getProperty("user.home"), ".minecraft/runtime"), false);
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            searcher.searchAllOfficialJava(Path.of(System.getProperty("user.home"), "Library/Application Support/minecraft/runtime"), false);
        }
        searcher.searchAllOfficialJava(CacheRepository.getInstance().getCacheDirectory().resolve("java"), true);

        // Search in PATH.
        if (System.getenv("PATH") != null) {
            String[] paths = System.getenv("PATH").split(File.pathSeparator);
            for (String path : paths) {
                // https://github.com/HMCL-dev/HMCL/issues/4079
                // https://github.com/Meloong-Git/PCL/issues/4261
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && path.toLowerCase(Locale.ROOT)
                        .contains("\\common files\\oracle\\java\\")) {
                    continue;
                }

                try {
                    searcher.tryAddJavaExecutable(Path.of(path, OperatingSystem.CURRENT_OS.getJavaExecutable()));
                } catch (InvalidPathException ignored) {
                }
            }
        }

        if (System.getenv("HMCL_JRES") != null) {
            String[] paths = System.getenv("HMCL_JRES").split(File.pathSeparator);
            for (String path : paths) {
                try {
                    searcher.tryAddJavaHome(Path.of(path));
                } catch (InvalidPathException ignored) {
                }
            }
        }

        searcher.searchAllJavaInDirectory(Path.of(System.getProperty("user.home"), ".jdks"));

        for (String javaPath : ConfigHolder.globalConfig().getUserJava()) {
            try {
                searcher.tryAddJavaExecutable(Path.of(javaPath));
            } catch (InvalidPathException e) {
                LOG.warning("Invalid Java path: " + javaPath);
            }
        }

        JavaRuntime currentJava = JavaRuntime.CURRENT_JAVA;
        if (currentJava != null
                && !searcher.javaRuntimes.containsKey(currentJava.getBinary())
                && !ConfigHolder.globalConfig().getDisabledJava().contains(currentJava.getBinary().toString())) {
            searcher.addResult(currentJava.getBinary(), currentJava);
        }

        searcher.saveCache();

        LOG.trace(searcher.javaRuntimes.values().stream().sorted()
                .map(it -> String.format(" - %s %s (%s, %s): %s",
                        it.isJDK() ? "JDK" : "JRE",
                        it.getVersion(),
                        it.getPlatform().getArchitecture().getDisplayName(),
                        Lang.requireNonNullElse(it.getVendor(), "Unknown"),
                        it.getBinary()))
                .collect(Collectors.joining("\n", "Finished Java lookup, found " + searcher.javaRuntimes.size() + "\n", "")));
        return searcher.javaRuntimes;
    }

    private static final class Searcher {
        private final Path cacheFile;
        final Map<Path, JavaRuntime> javaRuntimes = new HashMap<>();
        private final LinkedHashMap<Path, JavaInfoCache> caches = new LinkedHashMap<>();
        private final Set<Path> failed = new HashSet<>();
        private boolean needRefreshCache = false;

        Searcher(Path cacheFile) {
            this.cacheFile = cacheFile;
        }

        private static final Pattern CACHE_VERSION_PATTERN = Pattern.compile("(?<major>\\d+)(?:\\.(?<minor>\\d+))?");
        private static final int CACHE_MAJOR_VERSION = 0;
        private static final int CACHE_MINOR_VERSION = 0;

        private record JavaInfoCache(String key, JavaInfo info) {
        }

        void loadCache() {
            if (Files.notExists(cacheFile))
                return;

            try {
                JsonObject jsonFile = JsonUtils.fromJsonFile(cacheFile, JsonObject.class);
                JsonElement fileVersion = jsonFile.get("version");

                Matcher matcher;
                if (jsonFile.get("version") instanceof JsonPrimitive version
                        && (matcher = CACHE_VERSION_PATTERN.matcher(version.getAsString())).matches()) {
                    int major = Integer.parseInt(matcher.group("major"));

                    String minorString = matcher.group("minor");
                    int minor = minorString != null ? Integer.parseInt(minorString) : 0;

                    if (major != CACHE_MAJOR_VERSION || minor < CACHE_MINOR_VERSION)
                        throw new IOException("Unsupported cache file, version: %s".formatted(version.getAsString()));
                } else
                    throw new IOException("Invalid version JSON: " + fileVersion);

                JsonArray cachesArray = jsonFile.getAsJsonArray("caches");

                for (JsonElement element : cachesArray) {
                    try {
                        var obj = (JsonObject) element;

                        Path realPath = Path.of(obj.getAsJsonPrimitive("path").getAsString()).toRealPath();
                        String key = obj.getAsJsonPrimitive("key").getAsString();

                        OperatingSystem osName = OperatingSystem.parseOSName(obj.getAsJsonPrimitive("os.name").getAsString());
                        Architecture osArch = Architecture.parseArchName(obj.getAsJsonPrimitive("os.arch").getAsString());
                        String javaVersion = obj.getAsJsonPrimitive("java.version").getAsString();

                        JavaInfo.Builder infoBuilder = JavaInfo.newBuilder(Platform.getPlatform(osName, osArch), javaVersion);

                        if (obj.get("java.vendor") instanceof JsonPrimitive vendor)
                            infoBuilder.setVendor(vendor.getAsString());

                        caches.put(realPath, new JavaInfoCache(key, infoBuilder.build()));
                    } catch (Exception e) {
                        LOG.warning("Invalid cache: " + element);
                        needRefreshCache = true;
                    }
                }
            } catch (Exception ex) {
                LOG.warning("Failed to load cache file: " + cacheFile);
                needRefreshCache = true;
            }
        }

        void saveCache() {
            if (!needRefreshCache)
                return;

            needRefreshCache = false;
            try {
                FileUtils.saveSafely(cacheFile, output -> {
                    try (var writer = new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                        writer.beginObject();

                        writer.name("version").value(CACHE_MAJOR_VERSION);

                        writer.name("caches");
                        writer.beginArray();
                        for (Map.Entry<Path, JavaInfoCache> entry : caches.entrySet()) {
                            Path path = entry.getKey();
                            JavaInfoCache cache = entry.getValue();
                            JavaInfo info = cache.info();

                            writer.beginObject();

                            writer.name("path").value(path.toString());
                            writer.name("key").value(cache.key());

                            writer.name("os.name").value(info.getPlatform().os().getCheckedName());
                            writer.name("os.arch").value(info.getPlatform().arch().getCheckedName());
                            writer.name("java.version").value(info.getVersion());
                            if (info.getVendor() != null)
                                writer.name("java.vendor").value(info.getVendor());

                            writer.endObject();
                        }
                        writer.endArray();

                        writer.endObject();
                    }
                });
            } catch (Exception e) {
                LOG.warning("Failed to save cache file: " + cacheFile);
            }
        }

        private static @Nullable String createCacheKey(Path realPath) {
            Path binDir = realPath.getParent();
            if (binDir == null || !FileUtils.getName(binDir).equals("bin"))
                return null;

            if (Files.isRegularFile(realPath.resolveSibling("ikvm.properties")))
                return null;

            Path javaHome = binDir.getParent();
            if (javaHome == null)
                return null;

            Path libDir = javaHome.resolve("lib");
            if (!Files.isDirectory(libDir))
                return null;

            BasicFileAttributes launcherAttributes;
            String releaseHash = null;
            BasicFileAttributes coreLibsAttributes = null;

            try {
                launcherAttributes = Files.readAttributes(realPath, BasicFileAttributes.class);

                Path releaseFile = libDir.resolve("release");
                if (Files.exists(releaseFile)) {
                    releaseHash = DigestUtils.digestToString("SHA-1", releaseFile);
                } else {
                    Path coreLibsFile = libDir.resolve("rt.jar");
                    if (!Files.isRegularFile(coreLibsFile)) {
                        coreLibsFile = javaHome.resolve("jre/lib/rt.jar");
                        if (!Files.isRegularFile(coreLibsFile))
                            return null;

                        coreLibsAttributes = Files.readAttributes(coreLibsFile, BasicFileAttributes.class);
                    }
                }
            } catch (Exception e) {
                LOG.warning("Failed to create cache key for " + realPath, e);
                return null;
            }

            StringJoiner joiner = new StringJoiner(",");

            joiner.add("sz:" + launcherAttributes.size());
            joiner.add("lm:" + launcherAttributes.lastModifiedTime().toMillis());

            if (releaseHash != null)
                joiner.add(releaseHash);

            if (coreLibsAttributes != null) {
                joiner.add("rsz:" + coreLibsAttributes.size());
                joiner.add("rlm:" + coreLibsAttributes.lastModifiedTime().toMillis());
            }

            return joiner.toString();
        }

        void addResult(Path realPath, JavaRuntime javaRuntime) {
            javaRuntimes.put(realPath, javaRuntime);
        }

        void tryAddJavaHome(Path javaHome) {
            tryAddJavaExecutable(getExecutable(javaHome));
        }

        void tryAddJavaExecutable(Path executable) {
            tryAddJavaExecutable(executable, false);
        }

        void tryAddJavaExecutable(Path executable, boolean isManaged) {
            try {
                executable = executable.toRealPath();
            } catch (IOException e) {
                return;
            }

            if (javaRuntimes.containsKey(executable)
                    || failed.contains(executable)
                    || ConfigHolder.globalConfig().getDisabledJava().contains(executable.toString())) {
                return;
            }

            String cacheKey = createCacheKey(executable);
            if (cacheKey != null) {
                JavaInfoCache cache = caches.get(executable);
                if (cache != null) {
                    if (isCompatible(cache.info().getPlatform()) && cacheKey.equals(cache.key())) {
                        javaRuntimes.put(executable, JavaRuntime.of(executable, cache.info(), isManaged));
                        return;
                    } else {
                        caches.remove(executable);
                        needRefreshCache = true;
                    }
                }
            } else if (caches.remove(executable) != null) {
                needRefreshCache = true;
            }

            JavaInfo info;
            try {
                info = JavaInfoUtils.fromExecutable(executable);
            } catch (IOException e) {
                LOG.warning("Failed to lookup Java executable at " + executable, e);
                failed.add(executable);
                return;
            }

            if (cacheKey != null) {
                caches.put(executable, new JavaInfoCache(cacheKey, info));
                needRefreshCache = true;
            }

            javaRuntimes.put(executable, JavaRuntime.of(executable, info, isManaged));
        }

        void tryAddJavaInComponentDir(String platform, Path component, boolean verify) {
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

            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                Path macPath = dir.resolve("jre.bundle/Contents/Home");
                if (Files.exists(macPath)) {
                    tryAddJavaHome(macPath);
                    return;
                } else
                    LOG.warning("The Java is not in 'jre.bundle/Contents/Home'");
            }

            tryAddJavaHome(dir);
        }

        void searchAllJavaInRepository(Platform platform) {
            for (Path java : REPOSITORY.getAllJava(platform)) {
                tryAddJavaExecutable(java, true);
            }

            for (Path java : LOCAL_REPOSITORY.getAllJava(platform)) {
                tryAddJavaExecutable(java, true);
            }

            if (platform.os() == OperatingSystem.MACOS) {
                // In the past, we used 'osx' as the checked name for macOS
                Path platformRoot = REPOSITORY.getPlatformRoot(platform).resolveSibling("osx-" + platform.getArchitecture().getCheckedName());
                searchAllJavaInDirectory(platformRoot);
            }
        }

        void searchAllOfficialJava(Path directory, boolean verify) {
            if (!Files.isDirectory(directory))
                return;
            // Examples:
            // $HOME/Library/Application Support/minecraft/runtime/java-runtime-beta/mac-os/java-runtime-beta/jre.bundle/Contents/Home
            // $HOME/.minecraft/runtime/java-runtime-beta/linux/java-runtime-beta

            String javaPlatform = getMojangJavaPlatform(Platform.SYSTEM_PLATFORM);
            if (javaPlatform != null) {
                searchAllOfficialJava(directory, javaPlatform, verify);
            }

            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                    searchAllOfficialJava(directory, getMojangJavaPlatform(Platform.WINDOWS_X86), verify);
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    if (OperatingSystem.SYSTEM_BUILD_NUMBER >= 21277) {
                        searchAllOfficialJava(directory, getMojangJavaPlatform(Platform.WINDOWS_X86_64), verify);
                    }
                    searchAllOfficialJava(directory, getMojangJavaPlatform(Platform.WINDOWS_X86), verify);
                }
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS && Architecture.CURRENT_ARCH == Architecture.ARM64) {
                searchAllOfficialJava(directory, getMojangJavaPlatform(Platform.MACOS_X86_64), verify);
            }
        }

        void searchAllOfficialJava(Path directory, String platform, boolean verify) {
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(directory)) {
                // component can be jre-legacy, java-runtime-alpha, java-runtime-beta, java-runtime-gamma or any other being added in the future.
                for (Path component : dir) {
                    tryAddJavaInComponentDir(platform, component, verify);
                }
            } catch (IOException e) {
                LOG.warning("Failed to list java-runtime directory " + directory, e);
            }
        }

        void searchAllJavaInDirectory(Path directory) {
            if (!Files.isDirectory(directory)) {
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path subDir : stream) {
                    tryAddJavaHome(subDir);
                }
            } catch (IOException e) {
                LOG.warning("Failed to find Java in " + directory, e);
            }
        }

        void searchJavaInProgramFiles(String env, String defaultValue) {
            String programFiles = Lang.requireNonNullElse(System.getenv(env), defaultValue);
            Path path;
            try {
                path = Path.of(programFiles);
            } catch (InvalidPathException ignored) {
                return;
            }

            for (String vendor : KNOWN_VENDOR_DIRECTORIES) {
                searchAllJavaInDirectory(path.resolve(vendor));
            }
        }

        void searchJavaInMacJavaVirtualMachines(Path directory) {
            if (!Files.isDirectory(directory)) {
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path subDir : stream) {
                    tryAddJavaHome(subDir.resolve("Contents/Home"));
                }
            } catch (IOException e) {
                LOG.warning("Failed to find Java in " + directory, e);
            }
        }

        // ==== Windows Registry Support ====
        void queryJavaInRegistryKey(WinReg.HKEY hkey, String location) {
            WinReg reg = WinReg.INSTANCE;
            if (reg == null)
                return;

            for (String java : reg.querySubKeys(hkey, location)) {
                if (!reg.querySubKeys(hkey, java).contains(java + "\\MSI"))
                    continue;
                if (reg.queryValue(hkey, java, "JavaHome") instanceof String home) {
                    try {
                        tryAddJavaHome(Path.of(home));
                    } catch (InvalidPathException e) {
                        LOG.warning("Invalid Java path in system registry: " + home);
                    }
                }
            }
        }

    }
}
