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
package org.jackhuang.hmcl.upgrade;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import javafx.application.Platform;
import org.jackhuang.hmcl.EntryPoint;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.UpgradeDialog;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.SwingUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.checkFxUserThread;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class UpdateHandler {
    private static final AtomicBoolean backgroundDownloadInProgress = new AtomicBoolean(false);

    private UpdateHandler() {
    }

    private static Path getUpdateCacheDirectory() {
        return Metadata.HMCL_GLOBAL_DIRECTORY.resolve("cache").resolve("update");
    }

    private static Path getStagedJarPath(String version) {
        return getUpdateCacheDirectory().resolve("HMCL-" + version + ".jar");
    }

    public static void onOutdatedStateMayHaveChanged() {
        if (!config().isBackgroundAutoDownloadUpdate()) {
            return;
        }
        if (!UpdateChecker.isOutdated()) {
            return;
        }
        RemoteVersion latest = UpdateChecker.getLatestVersion();
        if (latest == null) {
            return;
        }
        thread(() -> downloadUpdateInBackground(latest), "HMCL Background Update Download", true);
    }

    public static void tryAutoDownloadIfOutdated() {
        checkFxUserThread();
        onOutdatedStateMayHaveChanged();
    }

    private static void downloadUpdateInBackground(RemoteVersion version) {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && !OperatingSystem.isWindows7OrLater()) {
            return;
        }

        Path target = getStagedJarPath(version.version());

        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            LOG.warning("Failed to create update cache directory", e);
            return;
        }

        if (Files.isRegularFile(target)) {
            try {
                IntegrityChecker.verifyJar(target);
                notifyStagedUpdateReady(target, version);
                return;
            } catch (IOException | RuntimeException e) {
                LOG.info("Replacing invalid cached update file: " + target, e);
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ex) {
                    LOG.warning("Failed to delete invalid cached update file", ex);
                }
            }
        }

        if (!backgroundDownloadInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanOldStagedJarsExcept(target);
            Task<?> task = new HMCLDownloadTask(version, target);
            TaskExecutor executor = task.executor();
            boolean success = executor.test();
            if (!success) {
                Exception ex = executor.getException();
                if (ex instanceof CancellationException) {
                    try {
                        Files.deleteIfExists(target);
                    } catch (IOException ignored) {
                    }
                    return;
                }
                LOG.warning("Background HMCL update download failed", ex);
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                }
                return;
            }

            if (!IntegrityChecker.DISABLE_SELF_INTEGRITY_CHECK && !IntegrityChecker.isSelfVerified()) {
                LOG.warning("Background update download skipped: current JAR is not verified");
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                }
                return;
            }
            try {
                IntegrityChecker.verifyJar(target);
            } catch (IOException e) {
                LOG.warning("Downloaded update jar failed integrity check", e);
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                }
                return;
            }

            notifyStagedUpdateReady(target, version);
        } finally {
            backgroundDownloadInProgress.set(false);
        }
    }

    private static void cleanOldStagedJarsExcept(Path keep) {
        Path dir = keep.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> !p.equals(keep)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    LOG.warning("Failed to delete stale update file " + p, e);
                }
            });
        } catch (IOException e) {
            LOG.warning("Failed to list update cache directory", e);
        }
    }

    private static void notifyStagedUpdateReady(Path stagedJar, RemoteVersion version) {
        Platform.runLater(() -> Controllers.showPendingUpdateNotification(stagedJar, version));
    }

    public static void applyStagedUpdate(Path stagedJar) {
        checkFxUserThread();

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && !OperatingSystem.isWindows7OrLater()) {
            Controllers.dialog(i18n("fatal.apply_update_need_win7", Metadata.PUBLISH_URL), i18n("message.error"), MessageType.ERROR);
            return;
        }

        try {
            if (!Files.isRegularFile(stagedJar)) {
                Controllers.dialog(i18n("update.failed"), i18n("message.error"), MessageType.ERROR);
                return;
            }
            IntegrityChecker.verifyJar(stagedJar);
        } catch (IOException e) {
            LOG.warning("Staged update file is missing or invalid", e);
            Controllers.dialog(e.toString(), i18n("update.failed"), MessageType.ERROR);
            return;
        }

        try {
            prepareExitForUpdate();
            requestUpdate(stagedJar, getCurrentLocation());
            EntryPoint.exit(0);
        } catch (IOException e) {
            LOG.warning("Failed to apply staged update", e);
            Controllers.dialog(StringUtils.getStackTrace(e), i18n("update.failed"), MessageType.ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Controllers.dialog(StringUtils.getStackTrace(e), i18n("update.failed"), MessageType.ERROR);
        }
    }

    private static void prepareExitForUpdate() throws IOException, InterruptedException {
        if (!IntegrityChecker.DISABLE_SELF_INTEGRITY_CHECK && !IntegrityChecker.isSelfVerified()) {
            throw new IOException("Current JAR is not verified");
        }

        if (Platform.isFxApplicationThread()) {
            Controllers.saveWindowStates();
        } else {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    Controllers.saveWindowStates();
                } finally {
                    future.complete(null);
                }
            });
            try {
                future.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new IOException(cause);
            }
        }

        FileSaver.waitForAllSaves();
    }

    /**
     * @return whether to exit
     */
    public static boolean processArguments(String[] args) {
        breakForceUpdateFeature();

        if (isNestedApplication()) {
            // updated from old versions
            try {
                performMigration();
            } catch (IOException e) {
                LOG.warning("Failed to perform migration", e);
                SwingUtils.showErrorDialog(i18n("fatal.apply_update_failure", Metadata.MANUAL_UPDATE_URL) + "\n" + StringUtils.getStackTrace(e));
            }
            return true;
        }

        if (args.length == 2 && args[0].equals("--apply-to")) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && !OperatingSystem.isWindows7OrLater()) {
                SwingUtils.showErrorDialog(i18n("fatal.apply_update_need_win7", Metadata.PUBLISH_URL));
                return true;
            }

            try {
                applyUpdate(Paths.get(args[1]));
            } catch (IOException e) {
                LOG.warning("Failed to apply update", e);
                SwingUtils.showErrorDialog(i18n("fatal.apply_update_failure", Metadata.MANUAL_UPDATE_URL) + "\n" + StringUtils.getStackTrace(e));
            }
            return true;
        }

        if (isFirstLaunchAfterUpgrade()) {
            SwingUtils.showInfoDialog(i18n("fatal.migration_requires_manual_reboot"));
            return true;
        }

        return false;
    }

    public static void updateFrom(RemoteVersion version) {
        checkFxUserThread();

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && !OperatingSystem.isWindows7OrLater()) {
            Controllers.dialog(i18n("fatal.apply_update_need_win7", Metadata.PUBLISH_URL), i18n("message.error"), MessageType.ERROR);
            return;
        }

        Controllers.dialog(new UpgradeDialog(version, () -> {
            Path downloaded;
            try {
                downloaded = Files.createTempFile("hmcl-update-", ".jar");
            } catch (IOException e) {
                LOG.warning("Failed to create temp file", e);
                return;
            }

            Task<?> task = new HMCLDownloadTask(version, downloaded);

            TaskExecutor executor = task.executor();
            Controllers.taskDialog(executor, i18n("message.downloading"), TaskCancellationAction.NORMAL);
            thread(() -> {
                boolean success = executor.test();

                if (success) {
                    try {
                        prepareExitForUpdate();
                        requestUpdate(downloaded, getCurrentLocation());
                        EntryPoint.exit(0);
                    } catch (IOException e) {
                        LOG.warning("Failed to update to " + version, e);
                        Platform.runLater(() -> Controllers.dialog(StringUtils.getStackTrace(e), i18n("update.failed"), MessageType.ERROR));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Platform.runLater(() -> Controllers.dialog(StringUtils.getStackTrace(e), i18n("update.failed"), MessageType.ERROR));
                    }

                } else {
                    Exception e = executor.getException();
                    LOG.warning("Failed to update to " + version, e);
                    if (e instanceof CancellationException) {
                        Platform.runLater(() -> Controllers.showToast(i18n("message.cancelled")));
                    } else {
                        Platform.runLater(() -> Controllers.dialog(e.toString(), i18n("update.failed"), MessageType.ERROR));
                    }
                }
            });
        }));
    }

    private static void applyUpdate(Path target) throws IOException {
        LOG.info("Applying update to " + target);

        Path self = getCurrentLocation();
        if (!IntegrityChecker.DISABLE_SELF_INTEGRITY_CHECK && !IntegrityChecker.isSelfVerified()) {
            throw new IOException("Self verification failed");
        }
        ExecutableHeaderHelper.copyWithHeader(self, target);

        Optional<Path> newFilename = tryRename(target, Metadata.VERSION);
        if (newFilename.isPresent()) {
            LOG.info("Move " + target + " to " + newFilename.get());
            try {
                Files.move(target, newFilename.get());
                target = newFilename.get();
            } catch (IOException e) {
                LOG.warning("Failed to move target", e);
            }
        }

        startJava(target);
    }

    private static void requestUpdate(Path updateTo, Path self) throws IOException {
        if (!IntegrityChecker.DISABLE_SELF_INTEGRITY_CHECK) {
            IntegrityChecker.verifyJar(updateTo);
        }
        startJava(updateTo, "--apply-to", self.toString());
    }

    private static boolean shouldForwardChildHmclPropertyKey(String key) {
        return !"hmcl.version.override".equals(key);
    }

    private static boolean shouldForwardChildJvmDefinition(String inputArgument) {
        int eq = inputArgument.indexOf('=', 2);
        String key = eq == -1 ? inputArgument.substring(2) : inputArgument.substring(2, eq);
        return shouldForwardChildHmclPropertyKey(key);
    }

    public static void startJava(Path jar, String... appArgs) throws IOException {
        List<String> commandline = new ArrayList<>();
        commandline.add(JavaRuntime.getDefault().getBinary().toString());

        try {
            for (String inputArgument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (inputArgument.startsWith("-X")) {
                    commandline.add(inputArgument);
                } else if (inputArgument.startsWith("-D") && shouldForwardChildJvmDefinition(inputArgument)) {
                    commandline.add(inputArgument);
                }
            }
        } catch (Throwable ignored) {
            // ManagementFactory not available
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                if (entry.getKey() instanceof String key && key.startsWith("hmcl.")) {
                    if (!shouldForwardChildHmclPropertyKey(key)) {
                        continue;
                    }
                    commandline.add("-D" + key + "=" + entry.getValue());
                }
            }
        }

        commandline.add("-jar");
        commandline.add(jar.toAbsolutePath().toString());
        commandline.addAll(Arrays.asList(appArgs));
        LOG.info("Starting process: " + commandline);
        new ProcessBuilder(commandline)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start();
    }

    private static Optional<Path> tryRename(Path path, String newVersion) {
        String filename = path.getFileName().toString();
        Matcher matcher = Pattern.compile("^(?<prefix>[hH][mM][cC][lL][.-])(?<version>\\d+(?:\\.\\d+)*)(?<suffix>\\.[^.]+)$").matcher(filename);
        if (matcher.find()) {
            String newFilename = matcher.group("prefix") + newVersion + matcher.group("suffix");
            if (!newFilename.equals(filename)) {
                return Optional.of(path.resolveSibling(newFilename));
            }
        }
        return Optional.empty();
    }

    private static Path getCurrentLocation() throws IOException {
        Path path = JarUtils.thisJarPath();
        if (path == null) {
            throw new IOException("Failed to find current HMCL location");
        }
        return path;
    }

    // ==== support for old versions ===
    private static void performMigration() throws IOException {
        LOG.info("Migrating from old versions");

        Path location = getParentApplicationLocation()
                .orElseThrow(() -> new IOException("Failed to get parent application location"));

        requestUpdate(getCurrentLocation(), location);
    }

    /**
     * This method must be called from the main thread.
     */
    private static boolean isNestedApplication() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stacktrace.length; i++) {
            StackTraceElement element = stacktrace[i];
            if (Main.class.getName().equals(element.getClassName()) && "main".equals(element.getMethodName())) {
                // we've reached the main method
                return i + 1 != stacktrace.length;
            }
        }
        return false;
    }

    private static Optional<Path> getParentApplicationLocation() {
        String command = System.getProperty("sun.java.command");
        if (command != null) {
            Path path = Paths.get(command);
            if (Files.isRegularFile(path)) {
                return Optional.of(path.toAbsolutePath());
            }
        }
        return Optional.empty();
    }

    private static boolean isFirstLaunchAfterUpgrade() {
        Path currentPath = JarUtils.thisJarPath();
        if (currentPath != null) {
            Path updated = Metadata.HMCL_GLOBAL_DIRECTORY.resolve("HMCL-" + Metadata.VERSION + ".jar");
            if (currentPath.equals(updated.toAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    private static void breakForceUpdateFeature() {
        Path hmclVersionJson = Metadata.HMCL_GLOBAL_DIRECTORY.resolve("hmclver.json");
        if (Files.isRegularFile(hmclVersionJson)) {
            try {
                Map<?, ?> content = new Gson().fromJson(Files.readString(hmclVersionJson), Map.class);
                Object ver = content.get("ver");
                if (ver instanceof String && ((String) ver).startsWith("3.")) {
                    Files.delete(hmclVersionJson);
                    LOG.info("Successfully broke the force update feature");
                }
            } catch (IOException e) {
                LOG.warning("Failed to break the force update feature", e);
            } catch (JsonParseException e) {
                hmclVersionJson.toFile().delete();
            }
        }
    }
}
