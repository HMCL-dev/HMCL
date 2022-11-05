/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.multiplayer;

import com.google.gson.JsonParseException;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.util.Lang.*;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.io.ChecksumMismatchException.verifyChecksum;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    // static final String HIPER_VERSION = "1.2.2";
    private static final String HIPER_DOWNLOAD_URL = "https://gitcode.net/to/hiper/-/raw/master/";
    private static final String HIPER_PACKAGES_URL = HIPER_DOWNLOAD_URL + "packages.sha1";
    private static final String HIPER_POINTS_URL = "https://cert.mcer.cn/point.yml";
    private static final Path HIPER_TEMP_CONFIG_PATH = Metadata.HMCL_DIRECTORY.resolve("hiper.yml");
    private static final Path HIPER_CONFIG_DIR = Metadata.HMCL_DIRECTORY.resolve("hiper-config");
    public static final Path HIPER_PATH = getHiperLocalDirectory().resolve(getHiperFileName());
    public static final int HIPER_AGREEMENT_VERSION = 3;
    private static final String REMOTE_ADDRESS = "127.0.0.1";
    private static final String LOCAL_ADDRESS = "0.0.0.0";

    private static final Map<Architecture, String> archMap = mapOf(
            pair(Architecture.ARM32, "arm-7"),
            pair(Architecture.ARM64, "arm64"),
            pair(Architecture.X86, "386"),
            pair(Architecture.X86_64, "amd64"),
            pair(Architecture.LOONGARCH64, "loong64"),
            pair(Architecture.MIPS, "mips"),
            pair(Architecture.MIPS64, "mips64"),
            pair(Architecture.MIPS64EL, "mips64le"),
            pair(Architecture.PPC64LE, "ppc64le"),
            pair(Architecture.RISCV64, "riscv64"),
            pair(Architecture.MIPSEL, "mipsle")
    );

    private static final Map<OperatingSystem, String> osMap = mapOf(
            pair(OperatingSystem.LINUX, "linux"),
            pair(OperatingSystem.WINDOWS, "windows"),
            pair(OperatingSystem.OSX, "darwin")
    );

    private static final String HIPER_TARGET_NAME = String.format("%s-%s",
            osMap.getOrDefault(OperatingSystem.CURRENT_OS, "windows"),
            archMap.getOrDefault(Architecture.SYSTEM_ARCH, "amd64"));

    private static final String GSUDO_VERSION = "1.7.1";
    private static final String GSUDO_TARGET_ARCH = Architecture.SYSTEM_ARCH == Architecture.X86_64 ? "amd64" : "x86";
    private static final String GSUDO_FILE_NAME = "gsudo.exe";
    private static final String GSUDO_DOWNLOAD_URL = "https://gitcode.net/glavo/gsudo-release/-/raw/75c952ea3afe8792b0db4fe9bab87d41b21e5895/" + GSUDO_TARGET_ARCH + "/" + GSUDO_FILE_NAME;
    private static final Path GSUDO_LOCAL_FILE = Metadata.HMCL_DIRECTORY.resolve("libraries").resolve("gsudo").resolve("gsudo").resolve(GSUDO_VERSION).resolve(GSUDO_TARGET_ARCH).resolve(GSUDO_FILE_NAME);
    private static final boolean USE_GSUDO;

    static final boolean IS_ADMINISTRATOR;

    static final BooleanBinding tokenInvalid = Bindings.createBooleanBinding(
            () -> {
                String token = globalConfig().multiplayerTokenProperty().getValue();
                return token == null || token.isEmpty() || !StringUtils.isAlphabeticOrNumber(token);
            },
            globalConfig().multiplayerTokenProperty());

    private static final DateFormat HIPER_VALID_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        boolean isAdministrator = false;
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"net.exe", "session"});
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroy();
                } else {
                    isAdministrator = process.exitValue() == 0;
                }
            } catch (Throwable ignored) {
            }
            USE_GSUDO = !isAdministrator && OperatingSystem.SYSTEM_BUILD_NUMBER >= 10000;
        } else {
            isAdministrator = "root".equals(System.getProperty("user.name"));
            USE_GSUDO = false;
        }
        IS_ADMINISTRATOR = isAdministrator;
    }

    private static CompletableFuture<Map<String, String>> HASH;

    private MultiplayerManager() {
    }

    public static Path getConfigPath(String token) {
        return HIPER_CONFIG_DIR.resolve(Hex.encodeHex(DigestUtils.digest("SHA-1", token)) + ".yml");
    }

    public static void clearConfiguration() {
        try {
            Files.deleteIfExists(HIPER_TEMP_CONFIG_PATH);
            Files.deleteIfExists(getConfigPath(ConfigHolder.globalConfig().getMultiplayerToken()));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete config", e);
        }
    }

    private static CompletableFuture<Map<String, String>> getPackagesHash() {
        FXUtils.checkFxUserThread();
        if (HASH == null) {
            HASH = CompletableFuture.supplyAsync(wrap(() -> {
                String hashList = HttpRequest.GET(HIPER_PACKAGES_URL).getString();
                Map<String, String> hashes = new HashMap<>();
                for (String line : hashList.split("\n")) {
                    String[] items = line.trim().split(" {2}");
                    if (items.length == 2 && items[0].length() == 40) {
                        hashes.put(items[1], items[0]);
                    } else {
                        LOG.warning("Failed to parse Hiper packages.sha1 file, line: " + line);
                    }
                }
                if (USE_GSUDO) {
                    hashes.put(GSUDO_FILE_NAME, HttpRequest.GET(GSUDO_DOWNLOAD_URL + ".sha1").getString().trim());
                }
                return hashes;
            }));
        }
        return HASH;
    }

    public static Task<Void> downloadHiper() {
        return Task.fromCompletableFuture(getPackagesHash()).thenComposeAsync(packagesHash -> {

            BiFunction<String, String, FileDownloadTask> getFileDownloadTask = (String remotePath, String localFileName) -> {
                String hash = packagesHash.get(remotePath);
                return new FileDownloadTask(
                        NetworkUtils.toURL(String.format("%s%s", HIPER_DOWNLOAD_URL, remotePath)),
                        getHiperLocalDirectory().resolve(localFileName).toFile(),
                        hash == null ? null : new FileDownloadTask.IntegrityCheck("SHA-1", hash));
            };

            List<Task<?>> tasks;
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                if (!packagesHash.containsKey(String.format("%s/hiper.exe", HIPER_TARGET_NAME))) {
                    throw new HiperUnsupportedPlatformException();
                }
                tasks = new ArrayList<>(4);

                tasks.add(getFileDownloadTask.apply(String.format("%s/hiper.exe", HIPER_TARGET_NAME), "hiper.exe"));
                tasks.add(getFileDownloadTask.apply(String.format("%s/wintun.dll", HIPER_TARGET_NAME), "wintun.dll"));
                // tasks.add(getFileDownloadTask.apply("tap-windows-9.21.2.exe", "tap-windows-9.21.2.exe"));
                if (USE_GSUDO)
                    tasks.add(new FileDownloadTask(
                            NetworkUtils.toURL(GSUDO_DOWNLOAD_URL),
                            GSUDO_LOCAL_FILE.toFile(),
                            new FileDownloadTask.IntegrityCheck("SHA-1", packagesHash.get(GSUDO_FILE_NAME))
                    ));
            } else {
                if (!packagesHash.containsKey(String.format("%s/hiper", HIPER_TARGET_NAME))) {
                    throw new HiperUnsupportedPlatformException();
                }
                tasks = Collections.singletonList(getFileDownloadTask.apply(String.format("%s/hiper", HIPER_TARGET_NAME), "hiper"));
            }
            return Task.allOf(tasks).thenRunAsync(() -> {
                if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                    Set<PosixFilePermission> perm = Files.getPosixFilePermissions(HIPER_PATH);
                    perm.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(HIPER_PATH, perm);
                }
            });
        });
    }

    public static void downloadHiperConfig(String token, Path configPath) throws IOException {
        String certFileContent = HttpRequest.GET(String.format("https://cert.mcer.cn/%s.yml", token)).getString();
        if (!certFileContent.equals("")) {
            FileUtils.writeText(configPath, certFileContent);
        }
    }

    public static CompletableFuture<HiperSession> startHiper(String token) {
        return getPackagesHash().thenComposeAsync(packagesHash -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    verifyChecksum(getHiperLocalDirectory().resolve("hiper.exe"), "SHA-1", packagesHash.get(String.format("%s/hiper.exe", HIPER_TARGET_NAME)));
                    verifyChecksum(getHiperLocalDirectory().resolve("wintun.dll"), "SHA-1", packagesHash.get(String.format("%s/wintun.dll", HIPER_TARGET_NAME)));
                    // verifyChecksumAndDeleteIfNotMatched(getHiperLocalDirectory().resolve("tap-windows-9.21.2.exe"), packagesHash.get("tap-windows-9.21.2.exe"));
                    if (USE_GSUDO)
                        verifyChecksum(GSUDO_LOCAL_FILE, "SHA-1", packagesHash.get(GSUDO_FILE_NAME));
                } else {
                    verifyChecksum(getHiperLocalDirectory().resolve("hiper"), "SHA-1", packagesHash.get(String.format("%s/hiper", HIPER_TARGET_NAME)));
                }

                future.complete(null);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to verify HiPer files", e);
                Platform.runLater(() -> Controllers.taskDialog(MultiplayerManager.downloadHiper()
                        .whenComplete(exception -> {
                            if (exception == null)
                                future.complete(null);
                            else
                                future.completeExceptionally(exception);
                        }), i18n("multiplayer.download"), TaskCancellationAction.NORMAL));
            }
            return future;
        }).thenApplyAsync(wrap(ignored -> {
            Path configPath = getConfigPath(token);
            Files.createDirectories(configPath.getParent());

            // 下载 HiPer 配置文件
            Logging.registerForbiddenToken(token, "<hiper token>");
            try {
                downloadHiperConfig(token, configPath);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "configuration file cloud cache token has been not available, try to use the local configuration file", e);
            }

            if (Files.exists(configPath)) {
                Files.copy(configPath, HIPER_TEMP_CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
                try (BufferedWriter output = Files.newBufferedWriter(HIPER_TEMP_CONFIG_PATH, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    output.write("\n");
                    output.write("logging:\n");
                    output.write("  format: json\n");
                    output.write("  file_path: '" + Metadata.HMCL_DIRECTORY.resolve("logs").resolve("hiper.log").toString().replace("'", "''") + "'\n");
                }
            }

            String[] commands = new String[]{HIPER_PATH.toString(), "-config", HIPER_TEMP_CONFIG_PATH.toString()};

            if (!IS_ADMINISTRATOR) {
                switch (OperatingSystem.CURRENT_OS) {
                    case WINDOWS:
                        if (USE_GSUDO)
                            commands = new String[]{GSUDO_LOCAL_FILE.toString(), HIPER_PATH.toString(), "-config", HIPER_TEMP_CONFIG_PATH.toString()};
                        break;
                    case LINUX:
                        String askpass = System.getProperty("hmcl.askpass", System.getenv("HMCL_ASKPASS"));
                        if ("user".equalsIgnoreCase(askpass))
                            commands = new String[]{"sudo", "-A", HIPER_PATH.toString(), "-config", HIPER_TEMP_CONFIG_PATH.toString()};
                        else if ("false".equalsIgnoreCase(askpass))
                            commands = new String[]{"sudo", "--non-interactive", HIPER_PATH.toString(), "-config", HIPER_TEMP_CONFIG_PATH.toString()};
                        else {
                            if (Files.exists(Paths.get("/usr/bin/pkexec")))
                                commands = new String[]{"/usr/bin/pkexec", HIPER_PATH.toString(), "-config", HIPER_TEMP_CONFIG_PATH.toString()};
                            else
                                commands = new String[]{"sudo", "--non-interactive", HIPER_PATH.toString(), "-config", HIPER_TEMP_CONFIG_PATH.toString()};
                        }
                        break;
                    case OSX:
                        commands = new String[]{"sudo", "--non-interactive", HIPER_PATH.toString(), "-config", HIPER_TEMP_CONFIG_PATH.toString()};
                        break;
                }
            }

            Process process = new ProcessBuilder()
                    .command(commands)
                    .start();

            return new HiperSession(process, Arrays.asList(commands));
        }));
    }

    public static String getHiperFileName() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return "hiper.exe";
        } else {
            return "hiper";
        }
    }

    public static Path getHiperLocalDirectory() {
        return Metadata.HMCL_DIRECTORY.resolve("libraries").resolve("hiper").resolve("hiper").resolve("binary");
    }

    public static class HiperSession extends ManagedProcess {
        private final EventManager<HiperExitEvent> onExit = new EventManager<>();
        private final EventManager<HiperIPEvent> onIPAllocated = new EventManager<>();
        private final EventManager<HiperShowValidUntilEvent> onValidUntil = new EventManager<>();
        private final BufferedWriter writer;
        private int error = 0;

        HiperSession(Process process, List<String> commands) {
            super(process, commands);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            LOG.info("Started hiper with command: " + new CommandBuilder().addAll(commands));

            addRelatedThread(Lang.thread(this::waitFor, "HiperExitWaiter", true));
            pumpInputStream(this::onLog);
            pumpErrorStream(this::onLog);

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void onLog(String log) {
            if (!log.startsWith("{")) {
                LOG.warning("[HiPer] " + log);

                if (log.startsWith("failed to load config"))
                    error = HiperExitEvent.INVALID_CONFIGURATION;
                else if (log.startsWith("sudo: ") || log.startsWith("Error getting authority") || log.startsWith("Error: An error occurred trying to start process"))
                    error = HiperExitEvent.NO_SUDO_PRIVILEGES;
                else if (log.startsWith("Failed to write to log, can't rename log file")) {
                    error = HiperExitEvent.NO_SUDO_PRIVILEGES;
                    stop();
                }

                return;
            }

            try {
                Map<?, ?> logJson = JsonUtils.fromNonNullJson(log, Map.class);
                String msg = "";
                if (logJson.containsKey("msg")) {
                    msg = tryCast(logJson.get("msg"), String.class).orElse("");
                    if (msg.contains("Failed to get a tun/tap device")) {
                        error = HiperExitEvent.FAILED_GET_DEVICE;
                    }
                    if (msg.contains("Failed to load certificate from config")) {
                        error = HiperExitEvent.FAILED_LOAD_CONFIG;
                    }
                    if (msg.contains("Validity of client certificate")) {
                        Optional<String> validUntil = tryCast(logJson.get("valid"), String.class);
                        if (validUntil.isPresent()) {
                            try {
                                synchronized (HIPER_VALID_TIME_FORMAT) {
                                    Date date = HIPER_VALID_TIME_FORMAT.parse(validUntil.get());
                                    onValidUntil.fireEvent(new HiperShowValidUntilEvent(this, date));
                                }
                            } catch (JsonParseException | ParseException e) {
                                LOG.log(Level.WARNING, "Failed to parse certification expire time string: " + validUntil.get());
                            }
                        }
                    }
                }

                if (logJson.containsKey("network")) {
                    Map<?, ?> network = tryCast(logJson.get("network"), Map.class).orElse(Collections.emptyMap());
                    if (network.containsKey("IP") && msg.contains("Main HostMap created")) {
                        Optional<String> ip = tryCast(network.get("IP"), String.class);
                        ip.ifPresent(s -> onIPAllocated.fireEvent(new HiperIPEvent(this, s)));
                    }
                }
            } catch (JsonParseException e) {
                LOG.log(Level.WARNING, "Failed to parse hiper log: " + log, e);
            }
        }

        private void waitFor() {
            try {
                int exitCode = getProcess().waitFor();
                LOG.info("Hiper exited with exitcode " + exitCode);
                if (error != 0) {
                    onExit.fireEvent(new HiperExitEvent(this, error));
                } else {
                    onExit.fireEvent(new HiperExitEvent(this, exitCode));
                }
            } catch (InterruptedException e) {
                onExit.fireEvent(new HiperExitEvent(this, HiperExitEvent.INTERRUPTED));
            } finally {
                try {
                    if (writer != null)
                        writer.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to close Hiper stdin writer", e);
                }
            }
            destroyRelatedThreads();
        }

        @Override
        public void stop() {
            try {
                writer.write("quit\n");
                writer.flush();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to quit HiPer", e);
            }
            try {
                getProcess().waitFor(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            super.stop();
        }

        public EventManager<HiperExitEvent> onExit() {
            return onExit;
        }

        public EventManager<HiperIPEvent> onIPAllocated() {
            return onIPAllocated;
        }

        public EventManager<HiperShowValidUntilEvent> onValidUntil() {
            return onValidUntil;
        }

    }

    public static class HiperExitEvent extends Event {
        private final int exitCode;

        public HiperExitEvent(Object source, int exitCode) {
            super(source);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }

        public static final int INTERRUPTED = -1;
        public static final int INVALID_CONFIGURATION = -2;
        public static final int CERTIFICATE_EXPIRED = -3;
        public static final int FAILED_GET_DEVICE = -4;
        public static final int FAILED_LOAD_CONFIG = -5;
        public static final int NO_SUDO_PRIVILEGES = -6;
    }

    public static class HiperIPEvent extends Event {
        private final String ip;

        public HiperIPEvent(Object source, String ip) {
            super(source);
            this.ip = ip;
        }

        public String getIP() {
            return ip;
        }
    }

    public static class HiperShowValidUntilEvent extends Event {
        private final Date validAt;

        public HiperShowValidUntilEvent(Object source, Date validAt) {
            super(source);
            this.validAt = validAt;
        }

        public Date getValidUntil() {
            return validAt;
        }
    }

    public static class HiperExitException extends RuntimeException {
        private final int exitCode;
        private final boolean ready;

        public HiperExitException(int exitCode, boolean ready) {
            this.exitCode = exitCode;
            this.ready = ready;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isReady() {
            return ready;
        }
    }

    public static class HiperExitTimeoutException extends RuntimeException {
    }

    public static class HiperSessionExpiredException extends HiperInvalidConfigurationException {
    }

    public static class HiperInvalidConfigurationException extends RuntimeException {
    }

    public static class JoinRequestTimeoutException extends RuntimeException {
    }

    public static class PeerConnectionTimeoutException extends RuntimeException {
    }

    public static class ConnectionErrorException extends RuntimeException {
    }

    public static class KickedException extends RuntimeException {
        private final String reason;

        public KickedException(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    public static class HiperInvalidTokenException extends RuntimeException {
    }

    public static class HiperUnsupportedPlatformException extends RuntimeException {
    }

}
