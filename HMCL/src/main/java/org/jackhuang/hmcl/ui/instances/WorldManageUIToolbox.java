// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.game.WorldManager;
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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public record WorldManageUIToolbox(World world, WorldManager manager) {
    public void delete(Runnable runnable) {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("world.delete"),
                () -> Task.runAsync(() -> {
                    world.close();
                    manager.delete(world);
                }).whenComplete(Schedulers.javafx(), exception -> {
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

    public void export() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("world.export.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("world"), "*.zip"));
        fileChooser.setInitialFileName(world.getWorldName() + ".zip");
        Path file = Controllers.showSaveDialog(fileChooser);
        if (file == null) {
            return;
        }

        world.close();
        Controllers.getDecorator().startWizard(new SinglePageWizardProvider(controller -> new WorldExportPage(world, file, controller::onFinish)));
    }

    public void copyWorld(@Nullable Runnable runnable) {
        Path worldPath = world.getFile();
        Controllers.prompt(i18n("world.duplicate.prompt"), (result, handler) -> {
            Path targetDir = worldPath.resolveSibling(result);
            if (Files.exists(targetDir)) {
                handler.reject(i18n("world.duplicate.failed.already_exists"));
                return;
            }

            Task.runAsync(Schedulers.io(), () -> manager.copy(world, result)).whenComplete(Schedulers.javafx(), (throwable) -> {
                if (throwable == null) {
                    Controllers.showToast(i18n("world.duplicate.success.toast"));
                    if (runnable != null)
                        runnable.run();
                    handler.resolve();
                } else {
                    handler.reject(i18n("world.duplicate.failed"));
                    LOG.warning("Failed to duplicate world " + world.getFile(), throwable);
                }
            }).start();
        }, "", new RequiredValidator(), new Validator(i18n("world.duplicate.failed.invalid_name"), FileUtils::isNameValid));
    }
}
