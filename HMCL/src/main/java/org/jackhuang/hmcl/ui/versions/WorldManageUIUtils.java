/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import javafx.stage.FileChooser;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.game.WorldLockedException;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class WorldManageUIUtils {
    private WorldManageUIUtils() {
    }

    public static void downloadWorld(DownloadProvider downloadProvider, HMCLGameRepository repository, @Nullable String version, RemoteAddon.Version file) {
        if (version == null) version = repository.getSelectedInstance();

        Path runDirectory = repository.hasVersion(version) ? repository.getRunDirectory(version) : repository.getBaseDirectory();
        Path savesDirectory = runDirectory.resolve("saves");

        Path worldZip;
        List<URI> downloadURLs;
        try {
            downloadURLs = downloadProvider.injectURLWithCandidates(file.file().url());
            worldZip = Files.createTempFile("world", ".zip");
        } catch (IOException | IllegalArgumentException e) {
            Controllers.dialog(
                    i18n("install.failed.downloading.detail", file.file().url()) + "\n" + StringUtils.getStackTrace(e),
                    i18n("download.failed.no_code"), MessageDialogPane.MessageType.ERROR);
            return;
        }

        Controllers.taskDialog(
                new FileDownloadTask(downloadURLs, worldZip)
                        .setName(file.name())
                        .whenComplete(Schedulers.javafx(), exception -> {
                            if (exception != null) {
                                if (exception instanceof CancellationException) {
                                    Controllers.showToast(i18n("message.cancelled"));
                                } else {
                                    Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                                }
                            } else {
                                installWorld(worldZip, savesDirectory, () -> Controllers.showToast(i18n("install.success")));
                            }
                        }),
                i18n("message.downloading"),
                TaskCancellationAction.NORMAL
        );
    }

    public static void installWorld(Path zipFile, Path savesDir, @Nullable Runnable runnable) {
        // Only accept one world file because user is required to confirm the new world name
        // Or too many input dialogs are popped.
        Task.supplyAsync(Schedulers.io(), () -> new World(zipFile))
                .whenComplete(Schedulers.javafx(), world -> {
                    Controllers.prompt(
                            i18n("world.name.enter"),
                            (name, handler) -> {
                                Task.runAsync(Schedulers.io(), () -> world.install(savesDir, name))
                                        .whenComplete(Schedulers.javafx(), () -> {
                                            handler.resolve();
                                            if (runnable != null) runnable.run();
                                        }, e -> {
                                            if (e instanceof FileAlreadyExistsException)
                                                handler.reject(i18n("world.add.failed", i18n("world.add.already_exists")));
                                            else if (e instanceof IOException && e.getCause() instanceof InvalidPathException)
                                                handler.reject(i18n("world.add.failed", i18n("install.new_game.malformed")));
                                            else
                                                handler.reject(i18n("world.add.failed", e.getClass().getName() + ": " + e.getLocalizedMessage()));
                                        }).start();
                            }, world.getWorldName(),
                            new RequiredValidator(),
                            new Validator(i18n("world.add.already_exists"), name -> !Files.exists(savesDir.resolve(name))),
                            new Validator(i18n("install.new_game.malformed"), FileUtils::isNameValid)
                    );
                }, e -> {
                    LOG.warning("Unable to parse world file " + zipFile, e);
                    Controllers.dialog(i18n("world.add.invalid"));
                }).start();
    }

    public static void delete(World world, Runnable runnable) {
        delete(world, runnable, null);
    }

    public static void delete(World world, Runnable runnable, FileChannel sessionLockChannel) {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("world.delete"),
                () -> Task.runAsync(() -> closeSessionLockChannel(world, sessionLockChannel))
                        .thenRunAsync(world::delete)
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception == null) {
                                runnable.run();
                            } else if (exception instanceof WorldLockedException) {
                                Controllers.dialog(i18n("world.locked.failed"), null, MessageDialogPane.MessageType.WARNING);
                            } else {
                                Controllers.dialog(i18n("world.delete.failed", StringUtils.getStackTrace(exception)), null, MessageDialogPane.MessageType.WARNING);
                            }
                        }).start(),
                null
        );
    }

    public static void export(World world) {
        export(world, null);
    }

    public static void export(World world, FileChannel sessionLockChannel) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("world.export.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("world"), "*.zip"));
        fileChooser.setInitialFileName(world.getWorldName() + ".zip");
        Path file = Controllers.showSaveDialog(fileChooser);
        if (file == null) {
            return;
        }

        try {
            closeSessionLockChannel(world, sessionLockChannel);
        } catch (IOException e) {
            return;
        }

        Controllers.getDecorator().startWizard(new SinglePageWizardProvider(controller -> new WorldExportPage(world, file, controller::onFinish)));
    }

    public static void copyWorld(World world, Runnable runnable) {
        Path worldPath = world.getFile();
        Controllers.prompt(
                i18n("world.duplicate.prompt"),
                (result, handler) -> {
                    Path targetDir = worldPath.resolveSibling(result);
                    if (Files.exists(targetDir)) {
                        handler.reject(i18n("world.duplicate.failed.already_exists"));
                        return;
                    }

                    Task.runAsync(Schedulers.io(), () -> world.copy(result))
                            .thenAcceptAsync(Schedulers.javafx(), (Void) -> Controllers.showToast(i18n("world.duplicate.success.toast")))
                            .thenAcceptAsync(Schedulers.javafx(), (Void) -> {
                                        if (runnable != null) {
                                            runnable.run();
                                        }
                                    }
                            ).whenComplete(Schedulers.javafx(), (throwable) -> {
                                if (throwable == null) {
                                    handler.resolve();
                                } else {
                                    handler.reject(i18n("world.duplicate.failed"));
                                    LOG.warning("Failed to duplicate world " + world.getFile(), throwable);
                                }
                            })
                            .start();
                }, "", new RequiredValidator(), new Validator(i18n("world.duplicate.failed.invalid_name"), FileUtils::isNameValid));
    }

    public static void closeSessionLockChannel(World world, FileChannel sessionLockChannel) throws IOException {
        if (sessionLockChannel != null) {
            try {
                sessionLockChannel.close();
                LOG.info("Closed session lock channel of the world " + world.getFileName());
            } catch (IOException e) {
                throw new IOException("Failed to close session lock channel of the world " + world.getFile(), e);
            }
        }
    }

    public static FileChannel getSessionLockChannel(World world) {
        try {
            FileChannel lock = world.lock();
            LOG.info("Acquired lock on world " + world.getFileName());
            return lock;
        } catch (WorldLockedException ignored) {
            return null;
        }
    }

}
