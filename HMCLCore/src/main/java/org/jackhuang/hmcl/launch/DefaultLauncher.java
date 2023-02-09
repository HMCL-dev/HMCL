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
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * @author huangyuhui
 */
public class DefaultLauncher extends Launcher {

    public DefaultLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options) {
        this(repository, version, authInfo, options, null);
    }

    public DefaultLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener) {
        this(repository, version, authInfo, options, listener, true);
    }

    public DefaultLauncher(GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean daemon) {
        super(repository, version, authInfo, options, listener, daemon);
    }

    private Command generateCommandLine(File nativeFolder) throws IOException {
        CommandBuilder res = new CommandBuilder();

        switch (options.getProcessPriority()) {
            case HIGH:
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // res.add("cmd", "/C", "start", "unused title", "/B", "/high");
                } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                    res.add("nice", "-n", "-5");
                }
                break;
            case ABOVE_NORMAL:
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // res.add("cmd", "/C", "start", "unused title", "/B", "/abovenormal");
                } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                    res.add("nice", "-n", "-1");
                }
                break;
            case NORMAL:
                // do nothing
                break;
            case BELOW_NORMAL:
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // res.add("cmd", "/C", "start", "unused title", "/B", "/belownormal");
                } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                    res.add("nice", "-n", "1");
                }
                break;
            case LOW:
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // res.add("cmd", "/C", "start", "unused title", "/B", "/low");
                } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                    res.add("nice", "-n", "5");
                }
                break;
        }

        // Executable
        if (StringUtils.isNotBlank(options.getWrapper()))
            res.addAllWithoutParsing(StringUtils.tokenize(options.getWrapper()));

        res.add(options.getJava().getBinary().toString());

        res.addAllWithoutParsing(options.getOverrideJavaArguments());

        Proxy proxy = options.getProxy();
        if (proxy != null && StringUtils.isBlank(options.getProxyUser()) && StringUtils.isBlank(options.getProxyPass())) {
            InetSocketAddress address = (InetSocketAddress) options.getProxy().address();
            if (address != null) {
                String host = address.getHostString();
                int port = address.getPort();
                if (proxy.type() == Proxy.Type.HTTP) {
                    res.addDefault("-Dhttp.proxyHost=", host);
                    res.addDefault("-Dhttp.proxyPort=", String.valueOf(port));
                    res.addDefault("-Dhttps.proxyHost=", host);
                    res.addDefault("-Dhttps.proxyPort=", String.valueOf(port));
                } else if (proxy.type() == Proxy.Type.SOCKS) {
                    res.addDefault("-DsocksProxyHost=", host);
                    res.addDefault("-DsocksProxyPort=", String.valueOf(port));
                }
            }
        }

        if (options.getMaxMemory() != null && options.getMaxMemory() > 0)
            res.addDefault("-Xmx", options.getMaxMemory() + "m");

        if (options.getMinMemory() != null && options.getMinMemory() > 0
                && (options.getMaxMemory() == null || options.getMinMemory() <= options.getMaxMemory()))
            res.addDefault("-Xms", options.getMinMemory() + "m");

        if (options.getMetaspace() != null && options.getMetaspace() > 0)
            if (options.getJava().getParsedVersion() < JavaVersion.JAVA_8)
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
                LOG.log(Level.WARNING, "Bad file encoding", ex);
            }
        }
        res.addDefault("-Dsun.stdout.encoding=", encoding.name());
        res.addDefault("-Dsun.stderr.encoding=", encoding.name());

        // Fix RCE vulnerability of log4j2
        res.addDefault("-Djava.rmi.server.useCodebaseOnly=", "true");
        res.addDefault("-Dcom.sun.jndi.rmi.object.trustURLCodebase=", "false");
        res.addDefault("-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=", "false");

        String formatMsgNoLookups = res.addDefault("-Dlog4j2.formatMsgNoLookups=", "true");
        if (!"-Dlog4j2.formatMsgNoLookups=false".equals(formatMsgNoLookups) && isUsingLog4j()) {
            res.addDefault("-Dlog4j.configurationFile=", getLog4jConfigurationFile().getAbsolutePath());
        }

        // Default JVM Args
        if (!options.isNoGeneratedJVMArgs()) {
            appendJvmArgs(res);

            res.addDefault("-Dminecraft.client.jar=", repository.getVersionJar(version).toString());

            if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                res.addDefault("-Xdock:name=", "Minecraft " + version.getId());
                repository.getAssetObject(version.getId(), version.getAssetIndex().getId(), "icons/minecraft.icns")
                        .ifPresent(minecraftIcns -> {
                            res.addDefault("-Xdock:icon=", minecraftIcns.toAbsolutePath().toString());
                        });
            }

            if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS)
                res.addDefault("-Duser.home=", options.getGameDir().getParent());

            // Using G1GC with its settings by default
            if (options.getJava().getParsedVersion() >= JavaVersion.JAVA_8
                    && res.noneMatch(arg -> "-XX:-UseG1GC".equals(arg) || (arg.startsWith("-XX:+Use") && arg.endsWith("GC")))) {
                res.addUnstableDefault("UnlockExperimentalVMOptions", true);
                res.addUnstableDefault("UseG1GC", true);
                res.addUnstableDefault("G1NewSizePercent", "20");
                res.addUnstableDefault("G1ReservePercent", "20");
                res.addUnstableDefault("MaxGCPauseMillis", "50");
                res.addUnstableDefault("G1HeapRegionSize", "32m");
            }

            res.addUnstableDefault("UseAdaptiveSizePolicy", false);
            res.addUnstableDefault("OmitStackTraceInFastThrow", false);
            res.addUnstableDefault("DontCompileHugeMethods", false);

            // As 32-bit JVM allocate 320KB for stack by default rather than 64-bit version allocating 1MB,
            // causing Minecraft 1.13 crashed accounting for java.lang.StackOverflowError.
            if (options.getJava().getBits() == Bits.BIT_32) {
                res.addDefault("-Xss", "1m");
            }

            if (options.getJava().getParsedVersion() == JavaVersion.JAVA_16)
                res.addDefault("--illegal-access=", "permit");

            res.addDefault("-Dfml.ignoreInvalidMinecraftCertificates=", "true");
            res.addDefault("-Dfml.ignorePatchDiscrepancies=", "true");
        }

        List<String> classpath = repository.getClasspath(version);

        File jar = repository.getVersionJar(version);
        if (!jar.exists() || !jar.isFile())
            throw new IOException("Minecraft jar does not exist");
        classpath.add(jar.getAbsolutePath());

        // Provided Minecraft arguments
        Path gameAssets = repository.getActualAssetDirectory(version.getId(), version.getAssetIndex().getId());
        Map<String, String> configuration = getConfigurations();
        configuration.put("${classpath}", String.join(OperatingSystem.PATH_SEPARATOR, classpath));
        configuration.put("${game_assets}", gameAssets.toAbsolutePath().toString());
        configuration.put("${assets_root}", gameAssets.toAbsolutePath().toString());

        // lwjgl assumes path to native libraries encoded by ASCII.
        // Here is a workaround for this issue: https://github.com/huanghongxun/HMCL/issues/1141.
        String nativeFolderPath = nativeFolder.getAbsolutePath();
        Path tempNativeFolder = null;
        if ((OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
                && !StringUtils.isASCII(nativeFolderPath)) {
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

        if (StringUtils.isNotBlank(options.getServerIp())) {
            String[] args = options.getServerIp().split(":");
            res.add("--server");
            res.add(args[0]);
            res.add("--port");
            res.add(args.length > 1 ? args[1] : "25565");
        }

        if (options.isFullscreen())
            res.add("--fullscreen");

        if (options.getProxy() != null && options.getProxy().type() == Proxy.Type.SOCKS) {
            InetSocketAddress address = (InetSocketAddress) options.getProxy().address();
            if (address != null) {
                res.add("--proxyHost");
                res.add(address.getHostString());
                res.add("--proxyPort");
                res.add(String.valueOf(address.getPort()));
                if (StringUtils.isNotBlank(options.getProxyUser()) && StringUtils.isNotBlank(options.getProxyPass())) {
                    res.add("--proxyUser");
                    res.add(options.getProxyUser());
                    res.add("--proxyPass");
                    res.add(options.getProxyPass());
                }
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
            pair("-Xincgc", () -> options.getJava().getParsedVersion() >= JavaVersion.JAVA_9)
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

    public void decompressNatives(File destination) throws NotDecompressingNativesException {
        try {
            FileUtils.cleanDirectoryQuietly(destination);
            for (Library library : version.getLibraries())
                if (library.isNative())
                    new Unzipper(repository.getLibraryFile(version, library), destination)
                            .setFilter((zipEntry, isDirectory, destFile, path) -> {
                                if (!isDirectory && Files.isRegularFile(destFile) && Files.size(destFile) == Files.size(zipEntry))
                                    return false;
                                String ext = FileUtils.getExtension(destFile);
                                if (ext.equals("sha1") || ext.equals("git"))
                                    return false;

                                if (options.isUseNativeGLFW() && FileUtils.getName(destFile).toLowerCase(Locale.ROOT).contains("glfw")) {
                                    return false;
                                }
                                if (options.isUseNativeOpenAL() && FileUtils.getName(destFile).toLowerCase(Locale.ROOT).contains("openal")) {
                                    return false;
                                }

                                return library.getExtract().shouldExtract(path);
                            })
                            .setReplaceExistentFile(false).unzip();
        } catch (IOException e) {
            throw new NotDecompressingNativesException(e);
        }
    }

    private boolean isUsingLog4j() {
        return VersionNumber.VERSION_COMPARATOR.compare(repository.getGameVersion(version).orElse("1.7"), "1.7") >= 0;
    }

    public File getLog4jConfigurationFile() {
        return new File(repository.getVersionRoot(version.getId()), "log4j2.xml");
    }

    public void extractLog4jConfigurationFile() throws IOException {
        File targetFile = getLog4jConfigurationFile();
        InputStream source;
        if (VersionNumber.VERSION_COMPARATOR.compare(repository.getGameVersion(version).orElse("0.0"), "1.12") < 0) {
            source = DefaultLauncher.class.getResourceAsStream("/assets/game/log4j2-1.7.xml");
        } else {
            source = DefaultLauncher.class.getResourceAsStream("/assets/game/log4j2-1.12.xml");
        }

        try (InputStream input = source; OutputStream output = new FileOutputStream(targetFile)) {
            IOUtils.copyTo(input, output);
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
                pair("${game_directory}", repository.getRunDirectory(version.getId()).getAbsolutePath()),
                pair("${user_type}", authInfo.getUserType()),
                pair("${assets_index_name}", version.getAssetIndex().getId()),
                pair("${user_properties}", authInfo.getUserProperties()),
                pair("${resolution_width}", options.getWidth().toString()),
                pair("${resolution_height}", options.getHeight().toString()),
                pair("${library_directory}", repository.getLibrariesDirectory(version).getAbsolutePath()),
                pair("${classpath_separator}", OperatingSystem.PATH_SEPARATOR),
                pair("${primary_jar}", repository.getVersionJar(version).getAbsolutePath()),
                pair("${language}", Locale.getDefault().toString()),

                // defined by HMCL
                // libraries_directory stands for historical reasons here. We don't know the official launcher
                // had already defined "library_directory" as the placeholder for path to ".minecraft/libraries"
                // when we propose this placeholder.
                pair("${libraries_directory}", repository.getLibrariesDirectory(version).getAbsolutePath()),
                // file_separator is used in -DignoreList
                pair("${file_separator}", OperatingSystem.FILE_SEPARATOR),
                pair("${primary_jar_name}", FileUtils.getName(repository.getVersionJar(version).toPath()))
        );
    }

    @Override
    public ManagedProcess launch() throws IOException, InterruptedException {
        File nativeFolder;
        if (options.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
            nativeFolder = repository.getNativeDirectory(version.getId(), options.getJava().getPlatform());
        } else {
            nativeFolder = new File(options.getNativesDir());
        }

        final Command command = generateCommandLine(nativeFolder);

        // To guarantee that when failed to generate launch command line, we will not call pre-launch command
        List<String> rawCommandLine = command.commandLine.asList();

        if (command.tempNativeFolder != null) {
            Files.deleteIfExists(command.tempNativeFolder);
            Files.createSymbolicLink(command.tempNativeFolder, nativeFolder.toPath().toAbsolutePath());
        }

        if (rawCommandLine.stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalStateException("Illegal command line " + rawCommandLine);
        }

        if (options.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
            decompressNatives(nativeFolder);
        }

        if (isUsingLog4j())
            extractLog4jConfigurationFile();

        File runDirectory = repository.getRunDirectory(version.getId());

        if (StringUtils.isNotBlank(options.getPreLaunchCommand())) {
            ProcessBuilder builder = new ProcessBuilder(StringUtils.tokenize(options.getPreLaunchCommand())).directory(runDirectory);
            builder.environment().putAll(getEnvVars());
            SystemUtils.callExternalProcess(builder);
        }

        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(rawCommandLine).directory(runDirectory);
            if (listener == null) {
                builder.inheritIO();
            }
            String appdata = options.getGameDir().getAbsoluteFile().getParent();
            if (appdata != null) builder.environment().put("APPDATA", appdata);

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
        Map<String, String> env = new HashMap<>();
        env.put("INST_NAME", versionName);
        env.put("INST_ID", versionName);
        env.put("INST_DIR", repository.getVersionRoot(version.getId()).getAbsolutePath());
        env.put("INST_MC_DIR", repository.getRunDirectory(version.getId()).getAbsolutePath());
        env.put("INST_JAVA", options.getJava().getBinary().toString());

        if (options.isUseSoftwareRenderer() && OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            env.put("LIBGL_ALWAYS_SOFTWARE", "1");
            env.put("__GLX_VENDOR_LIBRARY_NAME", "mesa");
        }

        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version);
        if (analyzer.has(LibraryAnalyzer.LibraryType.FORGE)) {
            env.put("INST_FORGE", "1");
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
        return env;
    }

    @Override
    public void makeLaunchScript(File scriptFile) throws IOException {
        boolean isWindows = OperatingSystem.WINDOWS == OperatingSystem.CURRENT_OS;

        File nativeFolder;
        if (options.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
            nativeFolder = repository.getNativeDirectory(version.getId(), options.getJava().getPlatform());
        } else {
            nativeFolder = new File(options.getNativesDir());
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
            else if (!isWindows && !scriptExtension.equals("sh"))
                throw new IllegalArgumentException("The extension of " + scriptFile + " is not 'sh' or 'ps1' in macOS/Linux");
        }

        final Command commandLine = generateCommandLine(nativeFolder);
        final String command = usePowerShell ? null : commandLine.commandLine.toString();

        if (!usePowerShell && isWindows) {
            if (command.length() > 8192) { // maximum length of the command in cmd
                throw new CommandTooLongException();
            }
        }

        if (!FileUtils.makeFile(scriptFile))
            throw new IOException("Script file: " + scriptFile + " cannot be created.");

        OutputStream outputStream = new FileOutputStream(scriptFile);
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
                    writer.write("$Env:APPDATA=");
                    writer.write(CommandBuilder.pwshString(options.getGameDir().getAbsoluteFile().getParent()));
                    writer.newLine();
                }
                for (Map.Entry<String, String> entry : getEnvVars().entrySet()) {
                    writer.write("$Env:" + entry.getKey() + "=");
                    writer.write(CommandBuilder.pwshString(entry.getValue()));
                    writer.newLine();
                }
                writer.write("Set-Location -Path ");
                writer.write(CommandBuilder.pwshString(repository.getRunDirectory(version.getId()).getAbsolutePath()));
                writer.newLine();

                writer.write('&');
                for (String rawCommand : commandLine.commandLine.asList()) {
                    writer.write(' ');
                    writer.write(CommandBuilder.pwshString(rawCommand));
                }
                writer.newLine();
            } else {
                if (isWindows) {
                    writer.write("@echo off");
                    writer.newLine();
                    writer.write("set APPDATA=" + options.getGameDir().getAbsoluteFile().getParent());
                    writer.newLine();
                    for (Map.Entry<String, String> entry : getEnvVars().entrySet()) {
                        writer.write("set " + entry.getKey() + "=" + CommandBuilder.toBatchStringLiteral(entry.getValue()));
                        writer.newLine();
                    }
                    writer.newLine();
                    writer.write(new CommandBuilder().add("cd", "/D", repository.getRunDirectory(version.getId()).getAbsolutePath()).toString());
                } else {
                    writer.write("#!/usr/bin/env bash");
                    writer.newLine();
                    for (Map.Entry<String, String> entry : getEnvVars().entrySet()) {
                        writer.write("export " + entry.getKey() + "=" + CommandBuilder.toShellStringLiteral(entry.getValue()));
                        writer.newLine();
                    }
                    if (commandLine.tempNativeFolder != null) {
                        writer.write(new CommandBuilder().add("ln", "-s", nativeFolder.getAbsolutePath(), commandLine.tempNativeFolder.toString()).toString());
                        writer.newLine();
                    }
                    writer.write(new CommandBuilder().add("cd", repository.getRunDirectory(version.getId()).getAbsolutePath()).toString());
                }
                writer.newLine();
                if (StringUtils.isNotBlank(options.getPreLaunchCommand())) {
                    writer.write(options.getPreLaunchCommand());
                    writer.newLine();
                }
                writer.write(command);
                writer.newLine();
                if (StringUtils.isNotBlank(options.getPostExitCommand())) {
                    writer.write(options.getPostExitCommand());
                    writer.newLine();
                }

                if (isWindows) {
                    writer.write("pause");
                    writer.newLine();
                }
                if (commandLine.tempNativeFolder != null) {
                    writer.write(new CommandBuilder().add("rm", commandLine.tempNativeFolder.toString()).toString());
                    writer.newLine();
                }
            }
        }
        if (!scriptFile.setExecutable(true))
            throw new PermissionException();

        if (usePowerShell && !CommandBuilder.hasExecutionPolicy())
            throw new ExecutionPolicyLimitException();
    }

    private static void startMonitors(ManagedProcess managedProcess, ProcessListener processListener, Charset encoding, boolean isDaemon) {
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
        managedProcess.addRelatedThread(Lang.thread(new ExitWaiter(managedProcess, Arrays.asList(stdout, stderr), processListener::onExit), "exit-waiter", isDaemon));
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
