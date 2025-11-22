package org.jackhuang.hmcl.ui.versions;

import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.game.WorldLockedException;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.InputDialogPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
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
        fileChooser.setInitialFileName(world.getWorldName());
        Path file = FileUtils.toPath(fileChooser.showSaveDialog(Controllers.getStage()));
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
        copyWorld(world, runnable, null);
    }

    public static void copyWorld(World world, Runnable runnable, FileChannel sessionLockChannel) {
        Path worldPath = world.getFile();
        Controllers.dialog(new InputDialogPane(
                i18n("world.copy.prompt"),
                "",
                (result, resolve, reject) -> {
                    if (StringUtils.isBlank(result)) {
                        reject.accept(i18n("world.copy.failed.empty_name"));
                        return;
                    }

                    if (result.contains("/") || result.contains("\\") || !FileUtils.isNameValid(result)) {
                        reject.accept(i18n("world.copy.failed.invalid_name"));
                        return;
                    }

                    Path targetDir = worldPath.resolveSibling(result);
                    if (Files.exists(targetDir)) {
                        reject.accept(i18n("world.copy.failed.already_exists"));
                        return;
                    }

                    try {
                        closeSessionLockChannel(world, sessionLockChannel);
                    } catch (IOException e) {
                        Controllers.dialog(i18n("world.locked.failed"), null, MessageDialogPane.MessageType.WARNING);
                        LOG.warning("unlock world fail", e);
                    }

                    Task.runAsync(Schedulers.io(), () -> world.copy(result))
                            .thenAcceptAsync(Schedulers.javafx(), (Void) -> Controllers.showToast(i18n("world.copy.success.toast")))
                            .thenAcceptAsync(Schedulers.javafx(), (Void) -> {
                                        if (runnable != null) {
                                            runnable.run();
                                        }
                                    }
                            ).whenComplete(Schedulers.javafx(), (exception) -> {
                                if (exception == null) {
                                    resolve.run();
                                } else {
                                    reject.accept(i18n("world.copy.failed"));
                                }
                            })
                            .start();
                }));
    }

    private static void closeSessionLockChannel(World world, FileChannel sessionLockChannel) throws IOException {
        if (sessionLockChannel != null) {
            try {
                sessionLockChannel.close();
            } catch (IOException e) {
                throw new IOException("Failed to close session lock channel", e);
            }
        }
    }

}
