/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.SimpleTaskResult;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 *
 * @author huangyuhui
 */
public class DefaultLauncher extends Launcher {

    public DefaultLauncher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options) {
        this(repository, versionId, authInfo, options, null);
    }

    public DefaultLauncher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options, ProcessListener listener) {
        this(repository, versionId, authInfo, options, listener, true);
    }

    public DefaultLauncher(GameRepository repository, String versionId, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean daemon) {
        super(repository, versionId, authInfo, options, listener, daemon);
    }

    private List<String> generateCommandLine(File nativeFolder) throws IOException {
        List<String> res = new LinkedList<>();

        // Executable
        if (StringUtils.isNotBlank(options.getWrapper()))
            res.add(options.getWrapper());

        res.add(options.getJava().getBinary().toString());

        if (StringUtils.isNotBlank(options.getJavaArgs()))
            res.addAll(StringUtils.tokenize(options.getJavaArgs()));

        // JVM Args
        if (!options.isNoGeneratedJVMArgs()) {
            appendJvmArgs(res);

            res.add("-Dminecraft.client.jar=" + repository.getVersionJar(version));

            if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                res.add("-Xdock:name=Minecraft " + version.getId());
                res.add("-Xdock:icon=" + repository.getAssetObject(version.getId(), version.getAssetIndex().getId(), "icons/minecraft.icns").getAbsolutePath());
            }

            Map<DownloadType, LoggingInfo> logging = version.getLogging();
            if (logging != null) {
                LoggingInfo loggingInfo = logging.get(DownloadType.CLIENT);
                if (loggingInfo != null) {
                    File loggingFile = repository.getLoggingObject(version.getId(), version.getAssetIndex().getId(), loggingInfo);
                    if (loggingFile.exists())
                        res.add(loggingInfo.getArgument().replace("${path}", loggingFile.getAbsolutePath()));
                }
            }

            if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS)
                res.add("-Duser.home=" + options.getGameDir().getParent());

            if (options.getJava().getParsedVersion() >= JavaVersion.JAVA_7) {
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

            if (options.getMaxMemory() != null && options.getMaxMemory() > 0)
                res.add("-Xmx" + options.getMaxMemory() + "m");

            if (options.getMinMemory() != null && options.getMinMemory() > 0)
                res.add("-Xms" + options.getMinMemory() + "m");

            res.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
            res.add("-Dfml.ignorePatchDiscrepancies=true");
        }

        LinkedList<File> lateload = new LinkedList<>();
        StringBuilder classpath = new StringBuilder();
        for (Library library : version.getLibraries())
            if (library.appliesToCurrentEnvironment() && !library.isNative()) {
                File f = repository.getLibraryFile(version, library);
                if (f.exists() && f.isFile())
                    if (library.isLateload())
                        lateload.add(f);
                    else
                        classpath.append(f.getAbsolutePath()).append(OperatingSystem.PATH_SEPARATOR);
            }
        for (File library : lateload)
            classpath.append(library.getAbsolutePath()).append(OperatingSystem.PATH_SEPARATOR);

        File jar = repository.getVersionJar(version);
        if (!jar.exists() || !jar.isFile())
            throw new IOException("Minecraft jar does not exist");
        classpath.append(jar.getAbsolutePath());

        // Provided Minecraft arguments
        File gameAssets = repository.getActualAssetDirectory(version.getId(), version.getAssetIndex().getId());
        Map<String, String> configuration = getConfigurations();
        configuration.put("${classpath}", classpath.toString());
        configuration.put("${natives_directory}", nativeFolder.getAbsolutePath());
        configuration.put("${game_assets}", gameAssets.getAbsolutePath());
        configuration.put("${assets_root}", gameAssets.getAbsolutePath());

        res.addAll(Arguments.parseArguments(version.getArguments().map(Arguments::getJvm).orElseGet(this::getDefaultJVMArguments), configuration));
        res.add(version.getMainClass());

        Map<String, Boolean> features = getFeatures();
        res.addAll(Arguments.parseArguments(version.getArguments().map(Arguments::getGame).orElseGet(this::getDefaultGameArguments), configuration, features));
        res.addAll(Arguments.parseStringArguments(version.getMinecraftArguments().map(StringUtils::tokenize).orElseGet(LinkedList::new), configuration));

        // Optional Minecraft arguments
        if (options.getHeight() != null && options.getHeight() != 0 && options.getWidth() != null && options.getWidth() != 0) {
            res.add("--height");
            res.add(options.getHeight().toString());
            res.add("--width");
            res.add(options.getWidth().toString());
        }

        if (StringUtils.isNotBlank(options.getServerIp())) {
            String[] args = options.getServerIp().split(":");
            res.add("--server");
            res.add(args[0]);
            res.add("--port");
            res.add(args.length > 1 ? args[1] : "25565");
        }

        if (options.isFullscreen())
            res.add("--fullscreen");

        if (StringUtils.isNotBlank(options.getProxyHost()) && StringUtils.isNotBlank(options.getProxyPort())) {
            res.add("--proxyHost");
            res.add(options.getProxyHost());
            res.add("--proxyPort");
            res.add(options.getProxyPort());
            if (StringUtils.isNotBlank(options.getProxyUser()) && StringUtils.isNotBlank(options.getProxyPass())) {
                res.add("--proxyUser");
                res.add(options.getProxyUser());
                res.add("--proxyPass");
                res.add(options.getProxyPass());
            }
        }

        if (StringUtils.isNotBlank(options.getMinecraftArgs()))
            res.addAll(StringUtils.tokenize(options.getMinecraftArgs()));

        return res.stream()
                .filter(it -> !getForbiddens().containsKey(it) || !getForbiddens().get(it).get())
                .collect(Collectors.toList());
    }

    public Map<String, Boolean> getFeatures() {
        return Collections.singletonMap(
                "has_custom_resolution",
                options.getHeight() != null && options.getHeight() != 0 && options.getWidth() != null && options.getWidth() != 0
        );
    }

    private final Map<String, Supplier<Boolean>> forbiddens = Lang.mapOf(
            new Pair<>("-Xincgc", () -> options.getJava().getParsedVersion() >= JavaVersion.JAVA_9)
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
    protected void appendJvmArgs(List<String> result) {
    }

    public void decompressNatives(File destination) throws IOException {
        for (Library library : version.getLibraries())
            if (library.isNative())
                CompressingUtils.unzip(repository.getLibraryFile(version, library),
                        destination,
                        "",
                        library.getExtract()::shouldExtract,
                        false);
    }

    protected Map<String, String> getConfigurations() {
        return Lang.mapOf(
                new Pair<>("${auth_player_name}", authInfo.getUsername()),
                new Pair<>("${auth_session}", authInfo.getAuthToken()),
                new Pair<>("${auth_access_token}", authInfo.getAuthToken()),
                new Pair<>("${auth_uuid}", authInfo.getUserId()),
                new Pair<>("${version_name}", Optional.ofNullable(options.getVersionName()).orElse(version.getId())),
                new Pair<>("${profile_name}", Optional.ofNullable(options.getProfileName()).orElse("Minecraft")),
                new Pair<>("${version_type}", version.getType().getId()),
                new Pair<>("${game_directory}", repository.getRunDirectory(version.getId()).getAbsolutePath()),
                new Pair<>("${user_type}", authInfo.getUserType().toString().toLowerCase()),
                new Pair<>("${assets_index_name}", version.getAssetIndex().getId()),
                new Pair<>("${user_properties}", authInfo.getUserProperties())
        );
    }

    @Override
    public ManagedProcess launch() throws IOException, InterruptedException {
        File nativeFolder = Files.createTempDirectory("minecraft").toFile();
        List<String> rawCommandLine = generateCommandLine(nativeFolder);

        // To guarantee that when failed to generate code, we will not call precalled command
        ProcessBuilder builder = new ProcessBuilder(rawCommandLine);

        decompressNatives(nativeFolder);

        if (StringUtils.isNotBlank(options.getPrecalledCommand()))
            Runtime.getRuntime().exec(options.getPrecalledCommand()).waitFor();

        builder.directory(repository.getRunDirectory(version.getId()))
                .environment().put("APPDATA", options.getGameDir().getAbsoluteFile().getParent());
        ManagedProcess p = new ManagedProcess(builder.start(), rawCommandLine);
        if (listener == null)
            startMonitors(p);
        else
            startMonitors(p, listener, daemon);
        return p;
    }

    public final TaskResult<ManagedProcess> launchAsync() {
        return new SimpleTaskResult<>(LAUNCH_ASYNC_ID, this::launch);
    }

    @Override
    public void makeLaunchScript(File scriptFile) throws IOException {
        boolean isWindows = OperatingSystem.WINDOWS == OperatingSystem.CURRENT_OS;

        File nativeFolder = repository.getNativeDirectory(versionId);
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
                writer.write("set APPDATA=" + options.getGameDir().getParent());
                writer.newLine();
                writer.write("cd /D %APPDATA%");
                writer.newLine();
            }
            if (StringUtils.isNotBlank(options.getPrecalledCommand())) {
                writer.write(options.getPrecalledCommand());
                writer.newLine();
            }
            writer.write(StringUtils.makeCommand(generateCommandLine(nativeFolder)));
        }
        if (!scriptFile.setExecutable(true))
            throw new IOException("Cannot make script file '" + scriptFile + "' executable.");
    }

    public final Task makeLaunchScriptAsync(File file) {
        return Task.of(() -> makeLaunchScript(file));
    }

    private void startMonitors(ManagedProcess managedProcess) {
        managedProcess.addRelatedThread(Lang.thread(new StreamPump(managedProcess.getProcess().getInputStream()), "stdout-pump", true));
        managedProcess.addRelatedThread(Lang.thread(new StreamPump(managedProcess.getProcess().getErrorStream()), "stderr-pump", true));
    }

    private void startMonitors(ManagedProcess managedProcess, ProcessListener processListener) {
        startMonitors(managedProcess, processListener, true);
    }

    private void startMonitors(ManagedProcess managedProcess, ProcessListener processListener, boolean isDaemon) {
        boolean enablesLoggingInfo = version.getLogging() != null && version.getLogging().containsKey(DownloadType.CLIENT);
        if (enablesLoggingInfo)
            startMonitorsWithLoggingInfo(managedProcess, processListener, isDaemon);
        else
            startMonitorsWithoutLoggingInfo(managedProcess, processListener, isDaemon);
    }

    private void startMonitorsWithLoggingInfo(ManagedProcess managedProcess, ProcessListener processListener, boolean isDaemon) {
        processListener.setProcess(managedProcess);
        Log4jHandler logHandler = new Log4jHandler((line, level) -> {
            processListener.onLog(line, level);
            managedProcess.addLine(line);
        });
        logHandler.start();
        managedProcess.addRelatedThread(logHandler);
        Thread stdout = Lang.thread(new StreamPump(managedProcess.getProcess().getInputStream(), logHandler::newLine), "stdout-pump", isDaemon);
        managedProcess.addRelatedThread(stdout);
        Thread stderr = Lang.thread(new StreamPump(managedProcess.getProcess().getErrorStream(), it -> {
            processListener.onLog(it + OperatingSystem.LINE_SEPARATOR, Log4jLevel.ERROR);
            managedProcess.addLine(it);
        }), "stderr-pump", isDaemon);
        managedProcess.addRelatedThread(stderr);
        managedProcess.addRelatedThread(Lang.thread(new ExitWaiter(managedProcess, Arrays.asList(stdout, stderr), (exitCode, exitType) -> {
            logHandler.onStopped();
            processListener.onExit(exitCode, exitType);
        }), "exit-waiter", isDaemon));
    }

    private void startMonitorsWithoutLoggingInfo(ManagedProcess managedProcess, ProcessListener processListener, boolean isDaemon) {
        processListener.setProcess(managedProcess);
        Thread stdout = Lang.thread(new StreamPump(managedProcess.getProcess().getInputStream(), it -> {
            processListener.onLog(it + OperatingSystem.LINE_SEPARATOR, Optional.ofNullable(Log4jLevel.guessLevel(it)).orElse(Log4jLevel.INFO));
            managedProcess.addLine(it);
        }), "stdout-pump", isDaemon);
        managedProcess.addRelatedThread(stdout);
        Thread stderr = Lang.thread(new StreamPump(managedProcess.getProcess().getErrorStream(), it -> {
            processListener.onLog(it + OperatingSystem.LINE_SEPARATOR, Log4jLevel.ERROR);
            managedProcess.addLine(it);
        }), "stderr-pump", isDaemon);
        managedProcess.addRelatedThread(stderr);
        managedProcess.addRelatedThread(Lang.thread(new ExitWaiter(managedProcess, Arrays.asList(stdout, stderr), (exitCode, exitType) -> processListener.onExit(exitCode, exitType)), "exit-waiter", isDaemon));
    }

    public static final String LAUNCH_ASYNC_ID = "process";
    public static final String LAUNCH_SCRIPT_ASYNC_ID = "script";
}
