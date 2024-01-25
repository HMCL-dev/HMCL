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
package org.jackhuang.hmcl.upgrade.hmcl;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import javafx.application.Platform;

import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.UpgradeDialog;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.ui.SwingUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.ui.FXUtils.checkFxUserThread;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class UpdateHandler {
    private UpdateHandler() {
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
                LOG.log(Level.WARNING, "Failed to perform migration", e);
                SwingUtils.showErrorDialog(i18n("fatal.apply_update_failure", Metadata.PUBLISH_URL) + "\n" + StringUtils.getStackTrace(e));
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
                LOG.log(Level.WARNING, "Failed to apply update", e);
                SwingUtils.showErrorDialog(i18n("fatal.apply_update_failure", Metadata.PUBLISH_URL) + "\n" + StringUtils.getStackTrace(e));
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
                LOG.log(Level.WARNING, "Failed to create temp file", e);
                return;
            }

            Task<?> task = new HMCLDownloadTask(version, downloaded);

            TaskExecutor executor = task.executor(false);
            Controllers.taskDialog(executor, i18n("message.downloading"), TaskCancellationAction.NORMAL);
            executor.start();
            thread(() -> {
                boolean success = executor.test();

                if (success) {
                    try {
                        if (!IntegrityChecker.isSelfVerified() && !IntegrityChecker.DISABLE_SELF_INTEGRITY_CHECK) {
                            throw new IOException("Current JAR is not verified");
                        }

                        requestUpdate(downloaded, getCurrentLocation());
                        System.exit(0);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Failed to update to " + version, e);
                        Platform.runLater(() -> Controllers.dialog(StringUtils.getStackTrace(e), i18n("update.failed"), MessageType.ERROR));
                    }

                } else {
                    Exception e = executor.getException();
                    LOG.log(Level.WARNING, "Failed to update to " + version, e);
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
                LOG.log(Level.WARNING, "Failed to move target", e);
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

    private static void startJava(Path jar, String... appArgs) throws IOException {
        List<String> commandline = new ArrayList<>();
        commandline.add(JavaVersion.fromCurrentEnvironment().getBinary().toString());
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            Object key = entry.getKey();
            if (key instanceof String && ((String) key).startsWith("hmcl.")) {
                commandline.add("-D" + key + "=" + entry.getValue());
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
            Path updated = Metadata.HMCL_DIRECTORY.resolve("HMCL-" + Metadata.VERSION + ".jar");
            if (currentPath.equals(updated.toAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    private static void breakForceUpdateFeature() {
        Path hmclVersionJson = Metadata.HMCL_DIRECTORY.resolve("hmclver.json");
        if (Files.isRegularFile(hmclVersionJson)) {
            try {
                Map<?, ?> content = new Gson().fromJson(FileUtils.readText(hmclVersionJson), Map.class);
                Object ver = content.get("ver");
                if (ver instanceof String && ((String) ver).startsWith("3.")) {
                    Files.delete(hmclVersionJson);
                    LOG.info("Successfully broke the force update feature");
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to break the force update feature", e);
            } catch (JsonParseException e) {
                hmclVersionJson.toFile().delete();
            }
        }
    }
}
