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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public class DefaultLauncher extends Launcher {

    private final LibraryAnalyzer analyzer;

    public DefaultLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options) {
        this(repository, version, authInfo, options, null);
    }

    public DefaultLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener) {
        this(repository, version, authInfo, options, listener, true);
    }

    public DefaultLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean daemon) {
        super(repository, version, authInfo, options, listener, daemon);

        this.analyzer = LibraryAnalyzer.analyze(version, repository.getGameVersion(version).orElse(null));
    }

    private Command generateCommandLine(Path nativeFolder) throws IOException {
        CommandBuilder res = new CommandBuilder();

        switch (options.getProcessPriority()) {
            case HIGH:
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // res.add("cmd", "/C", "start", "unused title", "/B", "/high");
                } else if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() || OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                    res.addAll("nice", "-n", "-5");
                }
                break;
            case ABOVE_NORMAL:
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // res.add("cmd", "/C", "start", "unused title", "/B", "/abovenormal");
                } else if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() || OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                    res.addAll("nice", "-n", "-1");
                }
                break;
            case NORMAL:
                // do nothing
                break;
            case BELOW_NORMAL:
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // res.add("cmd", "/C", "start", "unused title", "/B", "/belownormal");
                } else if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() || OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                    res.addAll("nice", "-n", "1");
                }
                break;
            case LOW:
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // res.add("cmd", "/C", "start", "unused title", "/B", "/low");
                } else if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() || OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                    res.addAll("nice", "-n", "5");
                }
                break;
        }

        // Executable
        if (StringUtils.isNotBlank(options.getWrapper()))
            res.addAllWithoutParsing(StringUtils.tokenize(options.getWrapper(), getEnvVars()));

        res.add(options.getJava().getBinary().toString());

        res.addAllWithoutParsing(options.getOverrideJavaArguments());

        if (options.getMaxMemory() != null && options.getMaxMemory() > 0)
            res.addDefault("-Xmx", options.getMaxMemory() + "m");

        if (options.getMinMemory() != null && options.getMinMemory() > 0
                && (options.getMaxMemory() == null || options.getMinMemory() <= options.getMaxMemory()))
            res.addDefault("-Xms", options.getMinMemory() + "m");

        if (options.getMetaspace() != null && options.getMetaspace() > 0)
            if (options.getJava().getParsedVersion() < 8)
                res.addDefault("-XX:PermSize=", options.getMetaspace() + "m");
            else
                res.addDefault("-XX:MetaspaceSize=", options.getMetaspace() + "m");

        res.addAllDefaultWithoutParsing(options.getJavaArguments());

        Charset encoding = OperatingSystem.NATIVE_CHARSET;
        String fileEncoding = res.addDefault("-Dfile.encoding=", encoding.name());
        if (fileEncoding != null && !"-Dfile.encoding=COMPAT".equals(fileEncoding)) {
            try {
                encoding = Charset.forName(fileEncoding.substring("-Dfile.encoding=".length()));
            } catch (Throwable ex) {
                LOG.warning("Bad file encoding", ex);
            }
        }

        if (options.getJava().getParsedVersion() < 19) {
            res.addDefault("-Dsun.stdout.encoding=", encoding.name());
            res.addDefault("-Dsun.stderr.encoding=", encoding.name());
        } else {
            res.addDefault("-Dstdout.encoding=", encoding.name());
            res.addDefault("-Dstderr.encoding=", encoding.name());
        }

        // Fix RCE vulnerability of log4j2
        res.addDefault("-Djava.rmi.server.useCodebaseOnly=", "true");
        res.addDefault("-Dcom.sun.jndi.rmi.object.trustURLCodebase=", "false");
        res.addDefault("-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=", "false");

        String formatMsgNoLookups = res.addDefault("-Dlog4j2.formatMsgNoLookups=", "true");
        if (isUsingLog4j() && (options.isEnableDebugLogOutput() || !"-Dlog4j2.formatMsgNoLookups=false".equals(formatMsgNoLookups))) {
            res.addDefault("-Dlog4j.configurationFile=", FileUtils.getAbsolutePath(getLog4jConfigurationFile()));
        }

        // Default JVM Args
        if (!options.isNoGeneratedJVMArgs()) {
            appendJvmArgs(res);

            res.addDefault("-Dminecraft.client.jar=", FileUtils.getAbsolutePath(repository.getVersionJar(version)));

            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                res.addDefault("-Xdock:name=", "Minecraft " + version.getId());
                repository.getAssetObject(version.getId(), version.getAssetIndex().getId(), "icons/minecraft.icns")
                        .ifPresent(minecraftIcns -> {
                            res.addDefault("-Xdock:icon=", FileUtils.getAbsolutePath(minecraftIcns));
                        });
            }

            if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS)
                res.addDefault("-Duser.home=", options.getGameDir().toAbsolutePath().getParent().toString());

            boolean addProxyOptions = res.noneMatch(arg ->
                    arg.startsWith("-Djava.net.useSystemProxies=")
                            || arg.startsWith("-Dhttp.proxy")
                            || arg.startsWith("-Dhttps.proxy")
                            || arg.startsWith("-DsocksProxy")
                            || arg.startsWith("-Djava.net.socks.")
            );

            if (addProxyOptions) {
                if (options.getProxyOption() == null || options.getProxyOption() == ProxyOption.Default.INSTANCE) {
                    res.add("-Djava.net.useSystemProxies=true");
                } else if (options.getProxyOption() instanceof ProxyOption.Http httpProxy) {
                    res.add("-Dhttp.proxyHost=" + httpProxy.host());
                    res.add("-Dhttp.proxyPort=" + httpProxy.port());
                    res.add("-Dhttps.proxyHost=" + httpProxy.host());
                    res.add("-Dhttps.proxyPort=" + httpProxy.port());

                    if (StringUtils.isNotBlank(httpProxy.username())) {
                        res.add("-Dhttp.proxyUser=" + httpProxy.username());
                        res.add("-Dhttp.proxyPassword=" + Objects.requireNonNullElse(httpProxy.password(), ""));
                        res.add("-Dhttps.proxyUser=" + httpProxy.username());
                        res.add("-Dhttps.proxyPassword=" + Objects.requireNonNullElse(httpProxy.password(), ""));
                    }
                } else if (options.getProxyOption() instanceof ProxyOption.Socks socksProxy) {
                    res.add("-DsocksProxyHost=" + socksProxy.host());
                    res.add("-DsocksProxyPort=" + socksProxy.port());

                    if (StringUtils.isNotBlank(socksProxy.username())) {
                        res.add("-Djava.net.socks.username=" + socksProxy.username());
                        res.add("-Djava.net.socks.password=" + Objects.requireNonNullElse(socksProxy.password(), ""));
                    }
                }
            }

            final int javaVersion = options.getJava().getParsedVersion();
            final boolean is64bit = options.getJava().getBits() == Bits.BIT_64;

            if (!options.isNoGeneratedOptimizingJVMArgs()) {
                res.addUnstableDefault("UnlockExperimentalVMOptions", true);
                res.addUnstableDefault("UnlockDiagnosticVMOptions", true);

                // Using G1GC with its settings by default
                if (javaVersion >= 8
                        && res.noneMatch(arg -> "-XX:-UseG1GC".equals(arg) || (arg.startsWith("-XX:+Use") && arg.endsWith("GC")))) {
                    res.addUnstableDefault("UseG1GC", true);
                    res.addUnstableDefault("G1MixedGCCountTarget", "5");
                    res.addUnstableDefault("G1NewSizePercent", "20");
                    res.addUnstableDefault("G1ReservePercent", "20");
                    res.addUnstableDefault("MaxGCPauseMillis", "50");
                    res.addUnstableDefault("G1HeapRegionSize", "32m");
                }

                res.addUnstableDefault("OmitStackTraceInFastThrow", false);

                // JIT Options
                if (javaVersion <= 8) {
                    res.addUnstableDefault("MaxInlineLevel", "15");
                }
                if (is64bit && SystemInfo.getTotalMemorySize() > 4L * 1024 * 1024 * 1024) {
                    res.addUnstableDefault("DontCompileHugeMethods", false);
                    res.addUnstableDefault("MaxNodeLimit", "240000");
                    res.addUnstableDefault("NodeLimitFudgeFactor", "8000");
                    res.addUnstableDefault("TieredCompileTaskTimeout", "10000");
                    res.addUnstableDefault("ReservedCodeCacheSize", "400M");
                    if (javaVersion >= 9) {
                        res.addUnstableDefault("NonNMethodCodeHeapSize", "12M");
                        res.addUnstableDefault("ProfiledCodeHeapSize", "194M");
                    }

                    if (javaVersion >= 8) {
                        res.addUnstableDefault("NmethodSweepActivity", "1");
                    }
                }

                if (is64bit && (javaVersion >= 25 && javaVersion <= 26)) {
                    res.addUnstableDefault("UseCompactObjectHeaders", true);
                }

                // As 32-bit JVM allocate 320KB for stack by default rather than 64-bit version allocating 1MB,
                // causing Minecraft 1.13 crashed accounting for java.lang.StackOverflowError.
                if (!is64bit) {
                    res.addDefault("-Xss", "1m");
                }
            }

            if (javaVersion == 16)
                res.addDefault("--illegal-access=", "permit");

            if (javaVersion == 24 || javaVersion == 25)
                res.addDefault("--sun-misc-unsafe-memory-access=", "allow");

            res.addDefault("-Dfml.ignoreInvalidMinecraftCertificates=", "true");
            res.addDefault("-Dfml.ignorePatchDiscrepancies=", "true");
        }

        Set<String> classpath = repository.getClasspath(version);

        if (analyzer.has(LibraryAnalyzer.LibraryType.CLEANROOM)) {
            classpath.removeIf(c -> c.contains("2.9.4-nightly-20150209"));
        }

        Path jar = repository.getVersionJar(version);
        if (!Files.isRegularFile(jar))
            throw new IOException("Minecraft jar does not exist");
        classpath.add(FileUtils.getAbsolutePath(jar.toAbsolutePath()));

        // Provided Minecraft arguments
        Path gameAssets = repository.getActualAssetDirectory(version.getId(), version.getAssetIndex().getId());
        Map<String, String> configuration = getConfigurations();
        configuration.put("${classpath}", String.join(File.pathSeparator, classpath));
        configuration.put("${game_assets}", FileUtils.getAbsolutePath(gameAssets));
        configuration.put("${assets_root}", FileUtils.getAbsolutePath(gameAssets));

        Optional<String> gameVersion = repository.getGameVersion(version);

        // lwjgl assumes path to native libraries encoded by ASCII.
        // Here is a workaround for this issue: https://github.com/HMCL-dev/HMCL/issues/1141.
        String nativeFolderPath = FileUtils.getAbsolutePath(nativeFolder);
        Path tempNativeFolder = null;
        if ((OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                && !StringUtils.isASCII(nativeFolderPath)
                && gameVersion.isPresent() && GameVersionNumber.compare(gameVersion.get(), "1.19") < 0) {
            tempNativeFolder = Paths.get("/", "tmp", "hmcl-natives-" + UUID.randomUUID());
            nativeFolderPath = tempNativeFolder + File.pathSeparator + nativeFolderPath;
        }
        configuration.put("${natives_directory}", nativeFolderPath);

        res.addAll(Arguments.parseArguments(version.getArguments().map(Arguments::getJvm).orElseGet(this::getDefaultJVMArguments), configuration));
        Arguments argumentsFromAuthInfo = authInfo.getLaunchArguments(options);
        if (argumentsFromAuthInfo != null && argumentsFromAuthInfo.getJvm() != null && !argumentsFromAuthInfo.getJvm().isEmpty())
            res.addAll(Arguments.parseArguments(argumentsFromAuthInfo.getJvm(), configuration));

        for (String javaAgent : options.getJavaAgents()) {
            res.add("-javaagent:" + javaAgent);
        }

        res.add(version.getMainClass());

        res.addAll(Arguments.parseStringArguments(version.getMinecraftArguments().map(StringUtils::tokenize).orElseGet(ArrayList::new), configuration));

        Map<String, Boolean> features = getFeatures();
        version.getArguments().map(Arguments::getGame).ifPresent(arguments -> res.addAll(Arguments.parseArguments(arguments, configuration, features)));
        if (version.getMinecraftArguments().isPresent()) {
            res.addAll(Arguments.parseArguments(this.getDefaultGameArguments(), configuration, features));
        }
        if (argumentsFromAuthInfo != null && argumentsFromAuthInfo.getGame() != null && !argumentsFromAuthInfo.getGame().isEmpty())
            res.addAll(Arguments.parseArguments(argumentsFromAuthInfo.getGame(), configuration, features));

        if (options.getQuickPlayOption() instanceof QuickPlayOption.MultiPlayer multiPlayer) {
            String address = multiPlayer.serverIP();

            try {
                ServerAddress parsed = ServerAddress.parse(address);
                if (World.supportQuickPlay(GameVersionNumber.asGameVersion(gameVersion))) {
                    res.add("--quickPlayMultiplayer");
                    res.add(parsed.getPort() >= 0 ? address : parsed.getHost() + ":25565");
                } else {
                    res.add("--server");
                    res.add(parsed.getHost());
                    res.add("--port");
                    res.add(parsed.getPort() >= 0 ? String.valueOf(parsed.getPort()) : "25565");
                }
            } catch (IllegalArgumentException e) {
                LOG.warning("Invalid server address: " + address, e);
            }
        } else if (options.getQuickPlayOption() instanceof QuickPlayOption.SinglePlayer singlePlayer
                && World.supportQuickPlay(GameVersionNumber.asGameVersion(gameVersion))) {
            res.add("--quickPlaySingleplayer");
            res.add(singlePlayer.worldFolderName());
        } else if (options.getQuickPlayOption() instanceof QuickPlayOption.Realm realm
                && World.supportQuickPlay(GameVersionNumber.asGameVersion(gameVersion))) {
            res.add("--quickPlayRealms");
            res.add(realm.realmID());
        }

        if (options.isFullscreen())
            res.add("--fullscreen");

        // https://github.com/HMCL-dev/HMCL/issues/774
        if (options.getProxyOption() instanceof ProxyOption.Socks socksProxy) {
            res.add("--proxyHost");
            res.add(socksProxy.host());
            res.add("--proxyPort");
            res.add(String.valueOf(socksProxy.port()));
            if (StringUtils.isNotBlank(socksProxy.username())) {
                res.add("--proxyUser");
                res.add(socksProxy.username());
                res.add("--proxyPass");
                res.add(Objects.requireNonNullElse(socksProxy.password(), ""));
            }
        }

        res.addAllWithoutParsing(Arguments.parseStringArguments(options.getGameArguments(), configuration));

        res.removeIf(it -> getForbiddens().containsKey(it) && getForbiddens().get(it).get());
        return new Command(res, tempNativeFolder, encoding);
    }

    public Map<String, Boolean> getFeatures() {
        return Collections.singletonMap(
                "has_custom_resolution",
                options.getHeight() != null && options.getHeight() != 0 && options.getWidth() != null && options.getWidth() != 0
        );
    }

    private final Map<String, Supplier<Boolean>> forbiddens = mapOf(
            pair("-Xincgc", () -> options.getJava().getParsedVersion() >= 9)
    );

    protected Map<String, Supplier<Boolean>> getForbiddens() {
        return forbiddens;
    }

    protected List<Argument> getDefaultJVMArguments() {
        return Arguments.DEFAULT_JVM_ARGUMENTS;
    }

    protected List<Argument> getDefaultGameArguments() {
        return Arguments.DEFAULT_GAME_ARGUMENTS;
    }

    /**
     * Do something here.
     * i.e.
     * -Dminecraft.launcher.version=&lt;Your launcher name&gt;
     * -Dminecraft.launcher.brand=&lt;Your launcher version&gt;
     * -Dlog4j.configurationFile=&lt;Your custom log4j configuration&gt;
     */
    protected void appendJvmArgs(CommandBuilder result) {
    }

    public void decompressNatives(Path destination) throws NotDecompressingNativesException {
        try {
            FileUtils.cleanDirectoryQuietly(destination);
            for (Library library : version.getLibraries())
                if (library.isNative())
                    new Unzipper(repository.getLibraryFile(version, library), destination)
                            .setFilter((zipEntry, destFile, relativePath) -> {
                                if (!zipEntry.isDirectory() && !zipEntry.isUnixSymlink()
                                        && Files.isRegularFile(destFile)
                                        && zipEntry.getSize() == Files.size(destFile)) {
                                    return false;
                                }
                                String ext = FileUtils.getExtension(destFile);
                                if (ext.equals("sha1") || ext.equals("git"))
                                    return false;

                                if (options.isUseNativeGLFW() && FileUtils.getName(destFile).toLowerCase(Locale.ROOT).contains("glfw")) {
                                    return false;
                                }
                                if (options.isUseNativeOpenAL() && FileUtils.getName(destFile).toLowerCase(Locale.ROOT).contains("openal")) {
                                    return false;
                                }

                                return library.getExtract().shouldExtract(relativePath);
                            })
                            .setReplaceExistentFile(false).unzip();
        } catch (IOException e) {
            throw new NotDecompressingNativesException(e);
        }
    }

    private boolean isUsingLog4j() {
        return GameVersionNumber.compare(repository.getGameVersion(version).orElse("1.7"), "1.7") >= 0;
    }

    public Path getLog4jConfigurationFile() {
        return repository.getVersionRoot(version.getId()).resolve("log4j2.xml");
    }

    public void extractLog4jConfigurationFile() throws IOException {
        Path targetFile = getLog4jConfigurationFile();

        String sourcePath;

        if (GameVersionNumber.asGameVersion(repository.getGameVersion(version)).compareTo("1.12") < 0) {
            if (options.isEnableDebugLogOutput()) {
                sourcePath = "/assets/game/log4j2-1.7-debug.xml";
            } else {
                sourcePath = "/assets/game/log4j2-1.7.xml";
            }
        } else {
            if (options.isEnableDebugLogOutput()) {
                sourcePath = "/assets/game/log4j2-1.12-debug.xml";
            } else {
                sourcePath = "/assets/game/log4j2-1.12.xml";
            }
        }

        try (InputStream input = DefaultLauncher.class.getResourceAsStream(sourcePath)) {
            Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    protected Map<String, String> getConfigurations() {
        return mapOf(
                // defined by Minecraft official launcher
                pair("${auth_player_name}", authInfo.getUsername()),
                pair("${auth_session}", authInfo.getAccessToken()),
                pair("${auth_access_token}", authInfo.getAccessToken()),
                pair("${auth_uuid}", UUIDTypeAdapter.fromUUID(authInfo.getUUID())),
                pair("${version_name}", Optional.ofNullable(options.getVersionName()).orElse(version.getId())),
                pair("${profile_name}", Optional.ofNullable(options.getProfileName()).orElse("Minecraft")),
                pair("${version_type}", Optional.ofNullable(options.getVersionType()).orElse(version.getType().getId())),
                pair("${game_directory}", FileUtils.getAbsolutePath(repository.getRunDirectory(version.getId()))),
                pair("${user_type}", authInfo.getUserType()),
                pair("${assets_index_name}", version.getAssetIndex().getId()),
                pair("${user_properties}", authInfo.getUserProperties()),
                pair("${resolution_width}", options.getWidth().toString()),
                pair("${resolution_height}", options.getHeight().toString()),
                pair("${library_directory}", FileUtils.getAbsolutePath(repository.getLibrariesDirectory(version))),
                pair("${classpath_separator}", File.pathSeparator),
                pair("${primary_jar}", FileUtils.getAbsolutePath(repository.getVersionJar(version))),
                pair("${language}", Locale.getDefault().toLanguageTag()),

                // defined by HMCL
                // libraries_directory stands for historical reasons here. We don't know the official launcher
                // had already defined "library_directory" as the placeholder for path to ".minecraft/libraries"
                // when we propose this placeholder.
                pair("${libraries_directory}", FileUtils.getAbsolutePath(repository.getLibrariesDirectory(version))),
                // file_separator is used in -DignoreList
                pair("${file_separator}", File.separator),
                pair("${primary_jar_name}", FileUtils.getName(repository.getVersionJar(version)))
        );
    }

    @Override
    public ManagedProcess launch() throws IOException, InterruptedException {
        Path nativeFolder;
        if (options.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
            nativeFolder = repository.getNativeDirectory(version.getId(), options.getJava().getPlatform());
        } else {
            nativeFolder = Path.of(options.getNativesDir());
        }

        final Command command = generateCommandLine(nativeFolder);

        // To guarantee that when failed to generate launch command line, we will not call pre-launch command
        List<String> rawCommandLine = command.commandLine.asList();

        if (command.tempNativeFolder != null) {
            Files.deleteIfExists(command.tempNativeFolder);
            Files.createSymbolicLink(command.tempNativeFolder, nativeFolder.toAbsolutePath());
        }

        if (rawCommandLine.stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalStateException("Illegal command line " + rawCommandLine);
        }

        if (options.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
            decompressNatives(nativeFolder);
        }

        if (isUsingLog4j())
            extractLog4jConfigurationFile();

        Path runDirectory = repository.getRunDirectory(version.getId());

        if (StringUtils.isNotBlank(options.getPreLaunchCommand())) {
            ProcessBuilder builder = new ProcessBuilder(StringUtils.tokenize(options.getPreLaunchCommand(), getEnvVars())).directory(runDirectory.toFile());
            builder.environment().putAll(getEnvVars());
            SystemUtils.callExternalProcess(builder);
        }

        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(rawCommandLine).directory(runDirectory.toFile());
            if (listener == null) {
                builder.inheritIO();
            }
            Path appdata = options.getGameDir().toAbsolutePath().getParent();
            if (appdata != null) builder.environment().put("APPDATA", appdata.toString());

            builder.environment().putAll(getEnvVars());
            process = builder.start();
        } catch (IOException e) {
            throw new ProcessCreationException(e);
        }

        ManagedProcess p = new ManagedProcess(process, rawCommandLine);
        if (listener != null)
            startMonitors(p, listener, command.encoding, daemon);
        return p;
    }

    private Map<String, String> getEnvVars() {
        String versionName = Optional.ofNullable(options.getVersionName()).orElse(version.getId());
        Map<String, String> env = new LinkedHashMap<>();
        env.put("INST_NAME", versionName);
        env.put("INST_ID", versionName);
        env.put("INST_DIR", FileUtils.getAbsolutePath(repository.getVersionRoot(version.getId())));
        env.put("INST_MC_DIR", FileUtils.getAbsolutePath(repository.getRunDirectory(version.getId())));
        env.put("INST_JAVA", options.getJava().getBinary().toString());

        Renderer renderer = options.getRenderer();
        if (renderer != Renderer.DEFAULT) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                if (renderer != Renderer.LLVMPIPE)
                    env.put("GALLIUM_DRIVER", renderer.name().toLowerCase(Locale.ROOT));
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
                env.put("__GLX_VENDOR_LIBRARY_NAME", "mesa");
                switch (renderer) {
                    case LLVMPIPE:
                        env.put("LIBGL_ALWAYS_SOFTWARE", "1");
                        break;
                    case ZINK:
                        env.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                        /*
                         * The amdgpu DDX is missing support for modifiers, causing Zink to fail.
                         * Disable DRI3 to workaround this issue.
                         *
                         * Link: https://gitlab.freedesktop.org/mesa/mesa/-/issues/10093
                         */
                        env.put("LIBGL_KOPPER_DRI2", "1");
                        break;
                }
            }
        }

        if (analyzer.has(LibraryAnalyzer.LibraryType.FORGE)) {
            env.put("INST_FORGE", "1");
        }
        if (analyzer.has(LibraryAnalyzer.LibraryType.CLEANROOM)) {
            env.put("INST_CLEANROOM", "1");
        }
        if (analyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE)) {
            env.put("INST_NEOFORGE", "1");
        }
        if (analyzer.has(LibraryAnalyzer.LibraryType.LITELOADER)) {
            env.put("INST_LITELOADER", "1");
        }
        if (analyzer.has(LibraryAnalyzer.LibraryType.FABRIC)) {
            env.put("INST_FABRIC", "1");
        }
        if (analyzer.has(LibraryAnalyzer.LibraryType.OPTIFINE)) {
            env.put("INST_OPTIFINE", "1");
        }
        if (analyzer.has(LibraryAnalyzer.LibraryType.QUILT)) {
            env.put("INST_QUILT", "1");
        }
        if (analyzer.has(LibraryAnalyzer.LibraryType.LEGACY_FABRIC)) {
            env.put("INST_LEGACYFABRIC", "1");
        }

        env.putAll(options.getEnvironmentVariables());

        return env;
    }

    @Override
    public void makeLaunchScript(Path scriptFile) throws IOException {
        boolean isWindows = OperatingSystem.WINDOWS == OperatingSystem.CURRENT_OS;

        Path nativeFolder;
        if (options.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
            nativeFolder = repository.getNativeDirectory(version.getId(), options.getJava().getPlatform());
        } else {
            nativeFolder = Path.of(options.getNativesDir());
        }

        if (options.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
            decompressNatives(nativeFolder);
        }

        if (isUsingLog4j())
            extractLog4jConfigurationFile();

        String scriptExtension = FileUtils.getExtension(scriptFile);
        boolean usePowerShell = "ps1".equals(scriptExtension);

        if (!usePowerShell) {
            if (isWindows && !scriptExtension.equals("bat"))
                throw new IllegalArgumentException("The extension of " + scriptFile + " is not 'bat' or 'ps1' in Windows");
            else if (!isWindows && !(scriptExtension.equals("sh") || scriptExtension.equals("command")))
                throw new IllegalArgumentException("The extension of " + scriptFile + " is not 'sh', 'ps1' or 'command' in macOS/Linux");
        }

        final Command commandLine = generateCommandLine(nativeFolder);
        final String command = usePowerShell ? null : commandLine.commandLine.toString();
        Map<String, String> envVars = getEnvVars();

        if (!usePowerShell && isWindows) {
            if (command.length() > 8192) { // maximum length of the command in cmd
                throw new CommandTooLongException();
            }
        }

        Files.createDirectories(scriptFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(scriptFile)) {
            Charset charset = StandardCharsets.UTF_8;

            if (isWindows) {
                if (usePowerShell) {
                    // Write UTF-8 BOM
                    try {
                        outputStream.write(0xEF);
                        outputStream.write(0xBB);
                        outputStream.write(0xBF);
                    } catch (IOException e) {
                        outputStream.close();
                        throw e;
                    }
                } else {
                    charset = OperatingSystem.NATIVE_CHARSET;
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, charset))) {
                if (usePowerShell) {
                    if (isWindows) {
                        Path appdata = options.getGameDir().toAbsolutePath().getParent();
                        if (appdata != null) {
                            writer.write("$Env:APPDATA=");
                            writer.write(CommandBuilder.pwshString(appdata.toString()));
                            writer.newLine();
                        }
                    }
                    for (Map.Entry<String, String> entry : envVars.entrySet()) {
                        writer.write("$Env:" + entry.getKey() + "=");
                        writer.write(CommandBuilder.pwshString(entry.getValue()));
                        writer.newLine();
                    }
                    writer.write("Set-Location -LiteralPath ");
                    writer.write(CommandBuilder.pwshString(FileUtils.getAbsolutePath(repository.getRunDirectory(version.getId()))));
                    writer.newLine();


                    if (StringUtils.isNotBlank(options.getPreLaunchCommand())) {
                        writer.write('&');
                        for (String rawCommand : StringUtils.tokenize(options.getPreLaunchCommand(), envVars)) {
                            writer.write(' ');
                            writer.write(CommandBuilder.pwshString(rawCommand));
                        }
                        writer.newLine();
                    }

                    writer.write('&');
                    for (String rawCommand : commandLine.commandLine.asList()) {
                        writer.write(' ');
                        writer.write(CommandBuilder.pwshString(rawCommand));
                    }
                    writer.newLine();

                    if (StringUtils.isNotBlank(options.getPostExitCommand())) {
                        writer.write('&');
                        for (String rawCommand : StringUtils.tokenize(options.getPostExitCommand(), envVars)) {
                            writer.write(' ');
                            writer.write(CommandBuilder.pwshString(rawCommand));
                        }
                        writer.newLine();
                    }
                } else {
                    if (isWindows) {
                        writer.write("@echo off");
                        writer.newLine();

                        Path appdata = options.getGameDir().toAbsolutePath().getParent();
                        if (appdata != null) {
                            writer.write("set APPDATA=" + appdata);
                            writer.newLine();
                        }

                        for (Map.Entry<String, String> entry : envVars.entrySet()) {
                            writer.write("set " + entry.getKey() + "=" + CommandBuilder.toBatchStringLiteral(entry.getValue()));
                            writer.newLine();
                        }
                        writer.newLine();
                        writer.write(new CommandBuilder().addAll("cd", "/D", FileUtils.getAbsolutePath(repository.getRunDirectory(version.getId()))).toString());
                    } else {
                        writer.write("#!/usr/bin/env bash");
                        writer.newLine();
                        for (Map.Entry<String, String> entry : envVars.entrySet()) {
                            writer.write("export " + entry.getKey() + "=" + CommandBuilder.toShellStringLiteral(entry.getValue()));
                            writer.newLine();
                        }
                        if (commandLine.tempNativeFolder != null) {
                            writer.write(new CommandBuilder().addAll("ln", "-s", FileUtils.getAbsolutePath(nativeFolder), commandLine.tempNativeFolder.toString()).toString());
                            writer.newLine();
                        }
                        writer.write(new CommandBuilder().addAll("cd", FileUtils.getAbsolutePath(repository.getRunDirectory(version.getId()))).toString());
                    }
                    writer.newLine();
                    if (StringUtils.isNotBlank(options.getPreLaunchCommand())) {
                        writer.write(new CommandBuilder().addAll(StringUtils.tokenize(options.getPreLaunchCommand(), envVars)).toString());
                        writer.newLine();
                    }
                    writer.write(command);
                    writer.newLine();

                    if (StringUtils.isNotBlank(options.getPostExitCommand())) {
                        writer.write(new CommandBuilder().addAll(StringUtils.tokenize(options.getPostExitCommand(), envVars)).toString());
                        writer.newLine();
                    }

                    if (isWindows) {
                        writer.write("pause");
                        writer.newLine();
                    }
                    if (commandLine.tempNativeFolder != null) {
                        writer.write(new CommandBuilder().addAll("rm", commandLine.tempNativeFolder.toString()).toString());
                        writer.newLine();
                    }
                }
            }
        }
        FileUtils.setExecutable(scriptFile);
        if (!Files.isExecutable(scriptFile))
            throw new PermissionException();

        if (usePowerShell && !CommandBuilder.hasExecutionPolicy())
            throw new ExecutionPolicyLimitException();
    }

    private void startMonitors(ManagedProcess managedProcess, ProcessListener processListener, Charset encoding, boolean isDaemon) {
        processListener.setProcess(managedProcess);
        Thread stdout = Lang.thread(new StreamPump(managedProcess.getProcess().getInputStream(), it -> {
            processListener.onLog(it, false);
            managedProcess.addLine(it);
        }, encoding), "stdout-pump", isDaemon);
        managedProcess.addRelatedThread(stdout);
        Thread stderr = Lang.thread(new StreamPump(managedProcess.getProcess().getErrorStream(), it -> {
            processListener.onLog(it, true);
            managedProcess.addLine(it);
        }, encoding), "stderr-pump", isDaemon);
        managedProcess.addRelatedThread(stderr);
        managedProcess.addRelatedThread(Lang.thread(new ExitWaiter(managedProcess, Arrays.asList(stdout, stderr), (exitCode, exitType) -> {
            processListener.onExit(exitCode, exitType);

            if (StringUtils.isNotBlank(options.getPostExitCommand())) {
                try {
                    ProcessBuilder builder = new ProcessBuilder(StringUtils.tokenize(options.getPostExitCommand(), getEnvVars())).directory(options.getGameDir().toFile());
                    builder.environment().putAll(getEnvVars());
                    SystemUtils.callExternalProcess(builder);
                } catch (Throwable e) {
                    LOG.warning("An Exception happened while running exit command.", e);
                }
            }
        }), "exit-waiter", isDaemon));
    }

    private static final class Command {
        final CommandBuilder commandLine;
        final Path tempNativeFolder;
        final Charset encoding;

        Command(CommandBuilder commandBuilder, Path tempNativeFolder, Charset encoding) {
            this.commandLine = commandBuilder;
            this.tempNativeFolder = tempNativeFolder;
            this.encoding = encoding;
        }
    }
}
