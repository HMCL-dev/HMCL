/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.upgrade;

import static org.jackhuang.hmcl.ui.FXUtils.checkFxUserThread;
import static org.jackhuang.hmcl.util.IntVersionNumber.isIntVersionNumber;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.VersionNumber.asVersion;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.util.JavaVersion;
import org.jackhuang.hmcl.util.StringUtils;

import javafx.application.Platform;
import javafx.scene.layout.Region;

public final class UpdateHandler {
    private UpdateHandler() {}

    /**
     * @return whether to exit
     */
    public static boolean processArguments(String[] args) {
        if (!isIntVersionNumber(Metadata.VERSION)) {
            return false;
        }

        if (isNestedApplication()) {
            // updated from old versions
            try {
                performMigration();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to perform migration", e);
                JOptionPane.showMessageDialog(null, i18n("fatal.apply_update_failure", Metadata.PUBLISH_URL) + "\n" + StringUtils.getStackTrace(e), "Error", JOptionPane.ERROR_MESSAGE);
            }
            return true;
        }

        if (args.length == 2 && args[0].equals("--apply-to")) {
            try {
                applyUpdate(Paths.get(args[1]));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to apply update", e);
                JOptionPane.showMessageDialog(null, i18n("fatal.apply_update_failure", Metadata.PUBLISH_URL) + "\n" + StringUtils.getStackTrace(e), "Error", JOptionPane.ERROR_MESSAGE);
            }
            return true;
        }

        if (isFirstLaunchAfterUpgrade()) {
            JOptionPane.showMessageDialog(null, i18n("fatal.migration_requires_manual_reboot"), "Info", JOptionPane.INFORMATION_MESSAGE);
            return true;
        }

        Optional<LocalVersion> local = LocalRepository.getStored();
        if (local.isPresent()) {
            int difference = asVersion(local.get().getVersion()).compareTo(asVersion(Metadata.VERSION));

            if (difference < 0) {
                LocalRepository.downloadFromCurrent();

            } else if (difference > 0) {
                Optional<LocalVersion> current = LocalVersion.current();
                if (current.isPresent() && IntegrityChecker.isSelfVerified()) {
                    try {
                        requestUpdate(local.get().getLocation(), current.get().getLocation());
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Failed to update from local repository", e);
                        return false;
                    }
                    return true;
                } else {
                    return false;
                }
            }

        } else {
            LocalRepository.downloadFromCurrent();
        }

        return false;
    }

    private static void requestUpdate(Path updateTo, Path self) throws IOException {
        IntegrityChecker.requireVerifiedJar(updateTo);
        startJava(updateTo, "--apply-to", self.toString());
    }

    private static void applyUpdate(Path target) throws IOException {
        LocalRepository.applyTo(target);

        Optional<String> newVersion = LocalRepository.getStored().map(LocalVersion::getVersion);
        if (newVersion.isPresent()) {
            Optional<Path> newFilename = tryRename(target, newVersion.get());
            if (newFilename.isPresent()) {
                LOG.info("Move " + target + " to " + newFilename.get());
                try {
                    Files.move(target, newFilename.get());
                    target = newFilename.get();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to move target", e);
                }
            }
        } else {
            LOG.warning("Failed to find local repository");
        }

        startJava(target);
    }

    private static void startJava(Path jar, String... appArgs) throws IOException {
        List<String> commandline = new ArrayList<>();
        commandline.add(JavaVersion.fromCurrentEnvironment().getBinary().getAbsolutePath());
        commandline.add("-jar");
        commandline.add(jar.toAbsolutePath().toString());
        for (String arg : appArgs) {
            commandline.add(arg);
        }
        LOG.info("Starting process: " + commandline);
        new ProcessBuilder(commandline)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start();
    }

    public static void updateFrom(RemoteVersion version) {
        checkFxUserThread();

        Task task = LocalRepository.downloadFromRemote(version);
        TaskExecutor executor = task.executor();
        Region dialog = Controllers.taskDialog(executor, i18n("message.downloading"), "", null);
        thread(() -> {
            boolean success = executor.test();
            Platform.runLater(() -> dialog.fireEvent(new DialogCloseEvent()));
            if (success) {
                try {
                    Optional<LocalVersion> current = LocalVersion.current();
                    Optional<LocalVersion> stored = LocalRepository.getStored();
                    if (!current.isPresent()) {
                        throw new IOException("Failed to find current HMCL location");
                    }
                    if (!stored.isPresent()) {
                        throw new IOException("Failed to find local repository, this shouldn't happen");
                    }
                    if (!IntegrityChecker.isSelfVerified()) {
                        throw new IOException("Current JAR is not verified");
                    }
                    requestUpdate(stored.get().getLocation(), current.get().getLocation());
                    System.exit(0);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to update to " + version, e);
                    Platform.runLater(() -> Controllers.dialog(StringUtils.getStackTrace(e), i18n("update.failed"), MessageBox.ERROR_MESSAGE));
                    return;
                }
            } else {
                Throwable e = task.getLastException();
                LOG.log(Level.WARNING, "Failed to update to " + version, e);
                Platform.runLater(() -> Controllers.dialog(e.toString(), i18n("update.failed"), MessageBox.ERROR_MESSAGE));
            }
        });
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

    // ==== support for old versions ===
    private static void performMigration() throws IOException {
        LOG.info("Migrating from old versions");

        Path location = getParentApplicationLocation()
                .orElseThrow(() -> new IOException("Failed to get parent application location"));

        Optional<LocalVersion> local = LocalRepository.getStored();
        if (!local.isPresent() ||
                asVersion(local.get().getVersion()).compareTo(asVersion(Metadata.VERSION)) < 0) {
            LocalRepository.downloadFromCurrent();
        }
        local = LocalRepository.getStored();
        if (!local.isPresent()) {
            throw new IOException("Failed to find local repository");
        }

        requestUpdate(local.get().getLocation(), location);
    }

    /**
     * This method must be called from the main thread.
     */
    private static boolean isNestedApplication() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stacktrace.length; i++) {
            StackTraceElement element = stacktrace[i];
            if (Main.class.getName().equals(element.getClassName())) {
                // we've reached the main method
                if (i + 1 == stacktrace.length) {
                    return false;
                } else {
                    return true;
                }
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
        Optional<Path> currentPath = LocalVersion.current().map(LocalVersion::getLocation);
        if (currentPath.isPresent()) {
            Path updated = Launcher.HMCL_DIRECTORY.toPath().resolve("HMCL-" + Metadata.VERSION + ".jar");
            if (currentPath.get().toAbsolutePath().equals(updated.toAbsolutePath())) {
                return true;
            }
        }
        return false;
    }
    // ====
}
