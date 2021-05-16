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
import org.jackhuang.hmcl.game.Argument;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 *
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

    private CommandBuilder generateCommandLine(File nativeFolder) throws IOException {
        CommandBuilder res = new CommandBuilder();

        // Executable
        if (StringUtils.isNotBlank(options.getWrapper()))
            res.add(options.getWrapper());

        res.add(options.getJava().getBinary().toString());

        res.addAllWithoutParsing(options.getJavaArguments());

        // JVM Args
        if (!options.isNoGeneratedJVMArgs()) {
            appendJvmArgs(res);

            res.add("-Dminecraft.client.jar=" + repository.getVersionJar(version));

            if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                res.add("-Xdock:name=Minecraft " + version.getId());
                res.add("-Xdock:icon=" + repository.getAssetObject(version.getId(), version.getAssetIndex().getId(), "icons/minecraft.icns").getAbsolutePath());
            }

            if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS)
                res.add("-Duser.home=" + options.getGameDir().getParent());

            // Force using G1GC with its settings
            if (options.getJava().getParsedVersion() >= JavaVersion.JAVA_8) {
                res.add("-XX:+UnlockExperimentalVMOptions");
                res.add("-XX:+UseG1GC");
                res.add("-XX:G1NewSizePercent=20");
                res.add("-XX:G1ReservePercent=20");
                res.add("-XX:MaxGCPauseMillis=50");
                res.add("-XX:G1HeapRegionSize=16M");
            }

            if (options.getMetaspace() != null && options.getMetaspace() > 0)
                if (options.getJava().getParsedVersion() < JavaVersion.JAVA_8)
                    res.add("-XX:PermSize= " + options.getMetaspace() + "m");
                else
                    res.add("-XX:MetaspaceSize=" + options.getMetaspace() + "m");

            res.add("-XX:-UseAdaptiveSizePolicy");
            res.add("-XX:-OmitStackTraceInFastThrow");
            res.add("-Xmn128m");

            // As 32-bit JVM allocate 320KB for stack by default rather than 64-bit version allocating 1MB,
            // causing Minecraft 1.13 crashed accounting for java.lang.StackOverflowError.
            if (options.getJava().getPlatform() == Platform.BIT_32) {
                res.add("-Xss1M");
            }

            if (options.getMaxMemory() != null && options.getMaxMemory() > 0)
                res.add("-Xmx" + options.getMaxMemory() + "m");

            if (options.getMinMemory() != null && options.getMinMemory() > 0)
                res.add("-Xms" + options.getMinMemory() + "m");

            if (options.getJava().getParsedVersion() >= JavaVersion.JAVA_16)
                res.add("--illegal-access=permit");

            res.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
            res.add("-Dfml.ignorePatchDiscrepancies=true");
        }

        Proxy proxy = options.getProxy();
        if (proxy != null && StringUtils.isBlank(options.getProxyUser()) && StringUtils.isBlank(options.getProxyPass())) {
            InetSocketAddress address = (InetSocketAddress) options.getProxy().address();
            if (address != null) {
                String host = address.getHostString();
                int port = address.getPort();
                if (proxy.type() == Proxy.Type.HTTP) {
                    res.add("-Dhttp.proxyHost=" + host);
                    res.add("-Dhttp.proxyPort=" + port);
                    res.add("-Dhttps.proxyHost=" + host);
                    res.add("-Dhttps.proxyPort=" + port);
                } else if (proxy.type() == Proxy.Type.SOCKS) {
                    res.add("-DsocksProxyHost=" + host);
                    res.add("-DsocksProxyPort=" + port);
                }
            }
        }

        LinkedList<String> classpath = new LinkedList<>();
        for (Library library : version.getLibraries())
            if (library.appliesToCurrentEnvironment() && !library.isNative()) {
                File f = repository.getLibraryFile(version, library);
                if (f.exists() && f.isFile())
                    classpath.add(f.getAbsolutePath());
            }

        File jar = repository.getVersionJar(version);
        if (!jar.exists() || !jar.isFile())
            throw new IOException("Minecraft jar does not exist");
        classpath.add(jar.getAbsolutePath());

        // Provided Minecraft arguments
        File gameAssets = repository.getActualAssetDirectory(version.getId(), version.getAssetIndex().getId());
        Map<String, String> configuration = getConfigurations();
        configuration.put("${classpath}", String.join(OperatingSystem.PATH_SEPARATOR, classpath));
        configuration.put("${natives_directory}", nativeFolder.getAbsolutePath());
        configuration.put("${game_assets}", gameAssets.getAbsolutePath());
        configuration.put("${assets_root}", gameAssets.getAbsolutePath());
        configuration.put("${libraries_directory}", repository.getLibrariesDirectory(version).getAbsolutePath());

        res.addAll(Arguments.parseArguments(version.getArguments().map(Arguments::getJvm).orElseGet(this::getDefaultJVMArguments), configuration));
        if (authInfo.getArguments() != null && authInfo.getArguments().getJvm() != null && !authInfo.getArguments().getJvm().isEmpty())
            res.addAll(Arguments.parseArguments(authInfo.getArguments().getJvm(), configuration));

        res.add(version.getMainClass());

        res.addAll(Arguments.parseStringArguments(version.getMinecraftArguments().map(StringUtils::tokenize).orElseGet(LinkedList::new), configuration));

        Map<String, Boolean> features = getFeatures();
        version.getArguments().map(Arguments::getGame).ifPresent(arguments -> res.addAll(Arguments.parseArguments(arguments, configuration, features)));
        if (version.getMinecraftArguments().isPresent()) {
            res.addAll(Arguments.parseArguments(this.getDefaultGameArguments(), configuration, features));
        }
        if (authInfo.getArguments() != null && authInfo.getArguments().getGame() != null && !authInfo.getArguments().getGame().isEmpty())
            res.addAll(Arguments.parseArguments(authInfo.getArguments().getGame(), configuration, features));

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

        res.addAllWithoutParsing(options.getGameArguments());

        res.removeIf(it -> getForbiddens().containsKey(it) && getForbiddens().get(it).get());
        return res;
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
                                return library.getExtract().shouldExtract(path);
                            })
                            .setReplaceExistentFile(false).unzip();
        } catch (IOException e) {
            throw new NotDecompressingNativesException(e);
        }
    }

    protected Map<String, String> getConfigurations() {
        return mapOf(
                pair("${auth_player_name}", authInfo.getUsername()),
                pair("${auth_session}", authInfo.getAccessToken()),
                pair("${auth_access_token}", authInfo.getAccessToken()),
                pair("${auth_uuid}", UUIDTypeAdapter.fromUUID(authInfo.getUUID())),
                pair("${version_name}", Optional.ofNullable(options.getVersionName()).orElse(version.getId())),
                pair("${profile_name}", Optional.ofNullable(options.getProfileName()).orElse("Minecraft")),
                pair("${version_type}", Optional.ofNullable(options.getVersionType()).orElse(version.getType().getId())),
                pair("${game_directory}", repository.getRunDirectory(version.getId()).getAbsolutePath()),
                pair("${user_type}", "mojang"),
                pair("${assets_index_name}", version.getAssetIndex().getId()),
                pair("${user_properties}", authInfo.getUserProperties()),
                pair("${resolution_width}", options.getWidth().toString()),
                pair("${resolution_height}", options.getHeight().toString())
        );
    }

    @Override
    public ManagedProcess launch() throws IOException, InterruptedException {
        File nativeFolder = repository.getNativeDirectory(version.getId());

        // To guarantee that when failed to generate launch command line, we will not call pre-launch command
        List<String> rawCommandLine = generateCommandLine(nativeFolder).asList();

        if (rawCommandLine.stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalStateException("Illegal command line " + rawCommandLine);
        }

        decompressNatives(nativeFolder);

        File runDirectory = repository.getRunDirectory(version.getId());

        if (StringUtils.isNotBlank(options.getPreLaunchCommand())) {
            String versionName = Optional.ofNullable(options.getVersionName()).orElse(version.getId());
            String preLaunchCommand = options.getPreLaunchCommand()
                    .replace("$INST_NAME", versionName)
                    .replace("$INST_ID", versionName)
                    .replace("$INST_DIR", repository.getVersionRoot(version.getId()).getAbsolutePath())
                    .replace("$INST_MC_DIR", repository.getRunDirectory(version.getId()).getAbsolutePath())
                    .replace("$INST_JAVA", options.getJava().getBinary().toString());

            new ProcessBuilder(StringUtils.tokenize(preLaunchCommand))
                    .directory(runDirectory).start().waitFor();
        }

        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(rawCommandLine).directory(runDirectory);
            if (listener == null) {
                builder.inheritIO();
            }
            String appdata = options.getGameDir().getAbsoluteFile().getParent();
            if (appdata != null) builder.environment().put("APPDATA", appdata);
            process = builder.start();
        } catch (IOException e) {
            throw new ProcessCreationException(e);
        }

        ManagedProcess p = new ManagedProcess(process, rawCommandLine);
        if (listener != null)
            startMonitors(p, listener, daemon);
        return p;
    }

    @Override
    public void makeLaunchScript(File scriptFile) throws IOException {
        boolean isWindows = OperatingSystem.WINDOWS == OperatingSystem.CURRENT_OS;

        File nativeFolder = repository.getNativeDirectory(version.getId());
        decompressNatives(nativeFolder);

        if (isWindows && !FileUtils.getExtension(scriptFile).equals("bat"))
            throw new IllegalArgumentException("The extension of " + scriptFile + " is not 'bat' in Windows");
        else if (!isWindows && !FileUtils.getExtension(scriptFile).equals("sh"))
            throw new IllegalArgumentException("The extension of " + scriptFile + " is not 'sh' in macOS/Linux");

        if (!FileUtils.makeFile(scriptFile))
            throw new IOException("Script file: " + scriptFile + " cannot be created.");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(scriptFile)))) {
            if (isWindows) {
                writer.write("@echo off");
                writer.newLine();
                writer.write("set APPDATA=" + options.getGameDir().getAbsoluteFile().getParent());
                writer.newLine();
                writer.write(new CommandBuilder().add("cd", "/D", repository.getRunDirectory(version.getId()).getAbsolutePath()).toString());
                writer.newLine();
            }
            if (StringUtils.isNotBlank(options.getPreLaunchCommand())) {
                writer.write(options.getPreLaunchCommand());
                writer.newLine();
            }
            writer.write(generateCommandLine(nativeFolder).toString());
        }
        if (!scriptFile.setExecutable(true))
            throw new PermissionException();
    }

    private void startMonitors(ManagedProcess managedProcess, ProcessListener processListener, boolean isDaemon) {
        processListener.setProcess(managedProcess);
        Thread stdout = Lang.thread(new StreamPump(managedProcess.getProcess().getInputStream(), it -> {
            processListener.onLog(it, Optional.ofNullable(Log4jLevel.guessLevel(it)).orElse(Log4jLevel.INFO));
            managedProcess.addLine(it);
        }), "stdout-pump", isDaemon);
        managedProcess.addRelatedThread(stdout);
        Thread stderr = Lang.thread(new StreamPump(managedProcess.getProcess().getErrorStream(), it -> {
            processListener.onLog(it, Log4jLevel.ERROR);
            managedProcess.addLine(it);
        }), "stderr-pump", isDaemon);
        managedProcess.addRelatedThread(stderr);
        managedProcess.addRelatedThread(Lang.thread(new ExitWaiter(managedProcess, Arrays.asList(stdout, stderr), processListener::onExit), "exit-waiter", isDaemon));
    }
}
