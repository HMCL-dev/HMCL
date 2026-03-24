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
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.game.WorldLockedException;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.InputDialogPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class WorldManageUIUtils {
    private WorldManageUIUtils() {
    }

    public static void delete(World world, Runnable runnable) {
        Controllers.confirm(i18n("button.remove.confirm"), i18n("world.delete"), () -> Task.runAsync(world::delete).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                runnable.run();
            } else if (exception instanceof WorldLockedException) {
                Controllers.dialog(i18n("world.locked.failed"), null, MessageDialogPane.MessageType.WARNING);
            } else {
                Controllers.dialog(i18n("world.delete.failed", StringUtils.getStackTrace(exception)), null, MessageDialogPane.MessageType.WARNING);
            }
        }).start(), null);
    }

    public static void export(World world) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("world.export.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("world"), "*.zip"));
        fileChooser.setInitialFileName(world.getWorldName() + ".zip");
        Path file = FileUtils.toPath(fileChooser.showSaveDialog(Controllers.getStage()));
        if (file == null) {
            return;
        }

        Controllers.getDecorator().startWizard(new SinglePageWizardProvider(controller -> new WorldExportPage(world, file, controller::onFinish)));
    }

    public static void copyWorld(World world, Runnable runnable) {
        Controllers.dialog(new InputDialogPane(i18n("world.duplicate.prompt"), world.getWorldName(), (result, handler) -> {
            Task.runAsync(Schedulers.io(), () -> world.copy(result)).thenAcceptAsync(Schedulers.javafx(), (Void) -> Controllers.showToast(i18n("world.duplicate.success.toast"))).thenAcceptAsync(Schedulers.javafx(), (Void) -> {
                if (runnable != null) {
                    runnable.run();
                }
            }).whenComplete(Schedulers.javafx(), (throwable) -> {
                if (throwable == null) {
                    handler.resolve();
                } else {
                    handler.reject(i18n("world.duplicate.failed"));
                    LOG.warning("Failed to duplicate world " + world.getFile(), throwable);
                }
            }).start();
        }));
    }

    public static void renameWorld(World world, Runnable runnable) {
        renameWorld(world, newWorldName -> runnable.run(), newWorldPath -> runnable.run());
    }

    public static void renameWorld(World world, Consumer<String> notRenameFolderConsumer, Consumer<Path> renameFolderConsumer) {
        Controllers.prompt(new PromptDialogPane.Builder(i18n("world.rename.prompt"), (res, handler) -> {
            String newWorldName = ((PromptDialogPane.Builder.StringQuestion) res.get(0)).getValue();
            boolean renameFolder = ((PromptDialogPane.Builder.BooleanQuestion) res.get(1)).getValue();
            if (StringUtils.isNotBlank(newWorldName)) {
                if (newWorldName.equals(world.getWorldName())) {
                    handler.resolve();
                    return;
                }

                try {
                    if (renameFolder) {
                        if (renameFolderConsumer != null) {
                            renameFolderConsumer.accept(world.rename(newWorldName));
                        }
                    } else {
                        world.setWorldName(newWorldName);
                        if (notRenameFolderConsumer != null) {
                            notRenameFolderConsumer.accept(newWorldName);
                        }
                    }
                    handler.resolve();
                } catch (IOException e) {
                    LOG.warning("Failed to set world name", e);
                    handler.reject(i18n("world.duplicate.failed"));
                }
            } else {
                handler.reject(i18n("world.duplicate.failed"));
            }
        })
                .addQuestion(new PromptDialogPane.Builder.StringQuestion(null, world.getWorldName()))
                .addQuestion(new PromptDialogPane.Builder.BooleanQuestion("重命名世界文件夹", false)));
    }
}
