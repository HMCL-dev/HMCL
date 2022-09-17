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
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.*;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    static final String HIPER_VERSION = "1.2.2";
    private static final String HIPER_DOWNLOAD_URL = "https://gitcode.net/to/hiper/-/raw/master/";
    private static final String HIPER_PACKAGES_URL = HIPER_DOWNLOAD_URL + "packages.sha1";
    private static final String HIPER_POINTS_URL = "https://cert.mcer.cn/point.yml";
    private static final Path HIPER_CONFIG_PATH = Metadata.HMCL_DIRECTORY.resolve("hiper.yml");
    public static final Path HIPER_PATH = getHiperLocalDirectory().resolve(getHiperFileName());
    public static final int HIPER_AGREEMENT_VERSION = 2;
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
            pair(Architecture.RISCV, "riscv64"),
            pair(Architecture.MIPSEL, "mipsle")
    );

    private static final Map<OperatingSystem, String> osMap = mapOf(
            pair(OperatingSystem.LINUX, "linux"),
            pair(OperatingSystem.WINDOWS, "windows"),
            pair(OperatingSystem.OSX, "darwin")
    );

    private static final String HIPER_TARGET_NAME = String.format("%s-%s",
            osMap.getOrDefault(OperatingSystem.CURRENT_OS, "windows"),
            archMap.getOrDefault(Architecture.CURRENT_ARCH, "amd64"));


    private static CompletableFuture<Map<String, String>> HASH;

    private MultiplayerManager() {
    }

    public static void clearConfiguration() {
        HIPER_CONFIG_PATH.toFile().delete();
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
                tasks = Arrays.asList(
                        getFileDownloadTask.apply(String.format("%s/hiper.exe", HIPER_TARGET_NAME), "hiper.exe"),
                        getFileDownloadTask.apply(String.format("%s/wintun.dll", HIPER_TARGET_NAME), "wintun.dll")
                        // getFileDownloadTask.apply("tap-windows-9.21.2.exe", "tap-windows-9.21.2.exe")
                );
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

    private static void verifyChecksumAndDeleteIfNotMatched(Path file, @Nullable String expectedChecksum) throws IOException {
        try {
            if (expectedChecksum != null) {
                ChecksumMismatchException.verifyChecksum(file, "SHA-1", expectedChecksum);
            }
        } catch (IOException e) {
            Files.deleteIfExists(file);
            throw e;
        }
    }

    public static CompletableFuture<HiperSession> startHiper(String token) {
        return getPackagesHash().thenApplyAsync(wrap(packagesHash -> {
            if (!Files.isRegularFile(HIPER_PATH)) {
                throw new HiperNotExistsException(HIPER_PATH);
            }

            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                verifyChecksumAndDeleteIfNotMatched(getHiperLocalDirectory().resolve("hiper.exe"), packagesHash.get(String.format("%s/hiper.exe", HIPER_TARGET_NAME)));
                verifyChecksumAndDeleteIfNotMatched(getHiperLocalDirectory().resolve("wintun.dll"), packagesHash.get(String.format("%s/wintun.dll", HIPER_TARGET_NAME)));
                // verifyChecksumAndDeleteIfNotMatched(getHiperLocalDirectory().resolve("tap-windows-9.21.2.exe"), packagesHash.get("tap-windows-9.21.2.exe"));
            } else {
                verifyChecksumAndDeleteIfNotMatched(getHiperLocalDirectory().resolve("hiper"), packagesHash.get(String.format("%s/hiper", HIPER_TARGET_NAME)));
            }

            // 下载 HiPer 配置文件
            String certFileContent;
            try {
                certFileContent = HttpRequest.GET(String.format("https://cert.mcer.cn/%s.yml", token)).getString() + "\nlogging:\n  format: json\n  file_path: ./hiper.log";
            } catch (IOException e) {
                throw new HiperInvalidTokenException();
            }
            FileUtils.writeText(HIPER_CONFIG_PATH, certFileContent);

            String[] commands = new String[]{HIPER_PATH.toString(), "-config", HIPER_CONFIG_PATH.toString()};
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
        return Metadata.HMCL_DIRECTORY.resolve("libraries").resolve("hiper").resolve("hiper").resolve(HIPER_VERSION);
    }

    public static class HiperSession extends ManagedProcess {
        private final EventManager<HiperExitEvent> onExit = new EventManager<>();
        private final EventManager<HiperIPEvent> onIPAllocated = new EventManager<>();
        private final EventManager<HiperIPEvent> onValidAt = new EventManager<>();
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
            LOG.info("[Hiper] " + log);

            if (log.contains("failed to load config")) {
                error = HiperExitEvent.INVALID_CONFIGURATION;
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
                        Optional<String> validAt = tryCast(logJson.get("valid"), String.class).orElse("");
                        validAt.ifPresent(s -> onValidAt.fireEvent(new HiperShowValidAtEvent(this, s)));
                    }
                }

                if (logJson.containsKey("network")) {
                    Map<?, ?> network = tryCast(logJson.get("network"), Map.class).orElse(Collections.emptyMap());
                    if (network.containsKey("IP") && msg.contains("Main HostMap created")) {
                        Optional<String> ip = tryCast(network.get("IP"), String.class);
                        ip.ifPresent(s -> onIPAllocated.fireEvent(new HiperIPEvent(this, s, validAt)));
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

        public EventManager<HiperExitEvent> onExit() {
            return onExit;
        }

        public EventManager<HiperIPEvent> onIPAllocated() {
            return onIPAllocated;
        }
        public EventManager<HiperIPEvent> onValidAt() {
            return onValidAt;
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
    }

    public static class HiperIPEvent extends Event {
        private final String ip;
        private final String validAt;

        public HiperIPEvent(Object source, String ip, String validAt) {
            super(source);
            this.ip = ip;
            this.validAt = validAt;
        }

        public String getIP() {
            return ip;
        }

        public String getValidAt() {
            return validAt;
        }
    }

        public static class HiperShowValidEvent extends Event {
        private final String validAt;

        public HiperShowValidEvent(Object source, String validAt) {
            super(source);
            this.validAt = validAt;
        }

        public String getValidAt() {
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

    public static class HiperNotExistsException extends RuntimeException {
        private final Path file;

        public HiperNotExistsException(Path file) {
            this.file = file;
        }

        public Path getFile() {
            return file;
        }
    }

    public static class HiperInvalidTokenException extends RuntimeException {
    }

    public static class HiperUnsupportedPlatformException extends RuntimeException {
    }

}
