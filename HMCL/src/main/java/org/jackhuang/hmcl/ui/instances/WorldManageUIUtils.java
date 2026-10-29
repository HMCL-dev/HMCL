// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.stage.FileChooser;
import org.jackhuang.hmcl.addon.world.WorldFolder;
import org.jackhuang.hmcl.game.WorldLockedException;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class WorldManageUIUtils {
    private WorldManageUIUtils() {
    }

    public static void delete(WorldFolder world, Runnable runnable) {
        delete(world, runnable, null);
    }

    public static void delete(WorldFolder world, Runnable runnable, FileChannel sessionLockChannel) {
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

    public static void export(WorldFolder world) {
        export(world, null);
    }

    public static void export(WorldFolder world, FileChannel sessionLockChannel) {
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

    public static void copyWorld(WorldFolder world, Runnable runnable) {
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

    public static void closeSessionLockChannel(WorldFolder world, FileChannel sessionLockChannel) throws IOException {
        if (sessionLockChannel != null) {
            try {
                sessionLockChannel.close();
                LOG.info("Closed session lock channel of the world " + world.getFileName());
            } catch (IOException e) {
                throw new IOException("Failed to close session lock channel of the world " + world.getFile(), e);
            }
        }
    }

    public static FileChannel getSessionLockChannel(WorldFolder world) {
        try {
            FileChannel lock = world.lock();
            LOG.info("Acquired lock on world " + world.getFileName());
            return lock;
        } catch (WorldLockedException ignored) {
            return null;
        }
    }

}
