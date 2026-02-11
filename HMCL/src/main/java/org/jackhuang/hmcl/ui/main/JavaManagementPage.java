/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.UnsupportedPlatformException;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class JavaManagementPage extends ListPageBase<JavaRuntime> {

    @SuppressWarnings("FieldCanBeLocal")
    private final ChangeListener<Collection<JavaRuntime>> listener;

    private final Runnable onInstallJava;

    public JavaManagementPage() {
        this.listener = FXUtils.onWeakChangeAndOperate(JavaManager.getAllJavaProperty(), this::loadJava);

        if (Platform.SYSTEM_PLATFORM.equals(OperatingSystem.LINUX, Architecture.LOONGARCH64_OW)) {
            onInstallJava = () -> FXUtils.openLink("https://www.loongnix.cn/zh/api/java/");
        } else {
            onInstallJava = JavaDownloadDialog.showDialogAction(DownloadProviders.getDownloadProvider());
        }

        FXUtils.applyDragListener(this, it -> {
            String name = FileUtils.getName(it);
            return Files.isDirectory(it) || name.endsWith(".zip") || name.endsWith(".tar.gz") || name.equals(OperatingSystem.CURRENT_OS.getJavaExecutable());
        }, files -> {
            for (Path file : files) {
                if (Files.isDirectory(file)) {
                    onAddJavaHome(file);
                } else {
                    String fileName = FileUtils.getName(file);

                    if (fileName.equals(OperatingSystem.CURRENT_OS.getJavaExecutable())) {
                        onAddJavaBinary(file);
                    } else if (fileName.endsWith(".zip") || fileName.endsWith(".tar.gz")) {
                        onInstallArchive(file);
                    } else {
                        throw new AssertionError("Unreachable code");
                    }
                }
            }
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JavaPageSkin(this);
    }

    void onAddJava() {
        FileChooser chooser = new FileChooser();
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java", "java.exe"));
        chooser.setTitle(i18n("settings.game.java_directory.choose"));
        Path file = FileUtils.toPath(chooser.showOpenDialog(Controllers.getStage()));
        if (file != null) {
            JavaManager.getAddJavaTask(file).whenComplete(Schedulers.javafx(), exception -> {
                if (exception != null) {
                    LOG.warning("Failed to add java", exception);
                    Controllers.dialog(i18n("java.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                }
            }).start();
        }
    }

    void onShowRestoreJavaPage() {
        Controllers.navigateForward(new JavaRestorePage(ConfigHolder.globalConfig().getDisabledJava()));
    }

    private void onAddJavaBinary(Path file) {
        JavaManager.getAddJavaTask(file).whenComplete(Schedulers.javafx(), exception -> {
            if (exception != null) {
                LOG.warning("Failed to add java", exception);
                Controllers.dialog(i18n("java.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }).start();
    }

    private void onAddJavaHome(Path file) {
        Task.composeAsync(() -> {
            Path releaseFile = file.resolve("release");
            if (Files.notExists(releaseFile))
                throw new IOException("Missing release file " + releaseFile);
            return JavaManager.getAddJavaTask(file.resolve("bin").resolve(OperatingSystem.CURRENT_OS.getJavaExecutable()));
        }).whenComplete(Schedulers.javafx(), exception -> {
            if (exception != null) {
                LOG.warning("Failed to add java", exception);
                Controllers.dialog(i18n("java.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }).start();
    }

    private void onInstallArchive(Path file) {
        Task.supplyAsync(() -> {
            try (ArchiveFileTree<?, ?> tree = ArchiveFileTree.open(file)) {
                JavaInfo info = JavaInfo.fromArchive(tree);

                if (!JavaManager.isCompatible(info.getPlatform()))
                    throw new UnsupportedPlatformException(info.getPlatform().toString());

                return Pair.pair(tree.getRoot().getSubDirs().keySet().iterator().next(), info);
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                Controllers.getDecorator().startWizard(new SinglePageWizardProvider(controller ->
                        new JavaInstallPage(controller::onFinish, result.getValue(), null, null, result.getKey(), file)));
            } else {
                if (exception instanceof UnsupportedPlatformException) {
                    Controllers.dialog(i18n("java.install.failed.unsupported_platform"), null, MessageDialogPane.MessageType.WARNING);
                } else {
                    Controllers.dialog(i18n("java.install.failed.invalid"), null, MessageDialogPane.MessageType.WARNING);
                }
            }
        }).start();
    }

    @FXThread
    private void loadJava(Collection<JavaRuntime> javaRuntimes) {
        if (javaRuntimes != null) {
            this.setItems(FXCollections.observableArrayList(javaRuntimes));
            this.setLoading(false);
        } else {
            this.setLoading(true);
        }
    }

    private static final class JavaPageSkin extends ToolbarListPageSkin<JavaRuntime, JavaManagementPage> {

        JavaPageSkin(JavaManagementPage skinnable) {
            super(skinnable);
        }

        @Override
        protected List<Node> initializeToolbar(JavaManagementPage skinnable) {
            ArrayList<Node> res = new ArrayList<>(4);

            res.add(createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, JavaManager::refresh));
            if (skinnable.onInstallJava != null) {
                res.add(createToolbarButton2(i18n("java.download"), SVG.DOWNLOAD, skinnable.onInstallJava));
            }
            res.add(createToolbarButton2(i18n("java.add"), SVG.ADD, skinnable::onAddJava));

            JFXButton disableJava = createToolbarButton2(i18n("java.disabled.management"), SVG.FORMAT_LIST_BULLETED, skinnable::onShowRestoreJavaPage);
            disableJava.disableProperty().bind(Bindings.isEmpty(ConfigHolder.globalConfig().getDisabledJava()));
            res.add(disableJava);

            return res;
        }

        @Override
        protected ListCell<JavaRuntime> createListCell(JFXListView<JavaRuntime> listView) {
            return new JavaItemCell(listView);
        }
    }

    private static final class JavaItemCell extends ListCell<JavaRuntime> {
        private final Node graphic;
        private final TwoLineListItem content;

        private SVG removeIcon;
        private final StackPane removeIconPane;
        private final Tooltip removeTooltip = new Tooltip();

        JavaItemCell(JFXListView<JavaRuntime> listView) {
            BorderPane root = new BorderPane();

            HBox center = new HBox();
            center.setMouseTransparent(true);
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);

            this.content = new TwoLineListItem();
            HBox.setHgrow(content, Priority.ALWAYS);

            BorderPane.setAlignment(content, Pos.CENTER);
            center.getChildren().setAll(content);
            root.setCenter(center);

            HBox right = new HBox();
            right.setAlignment(Pos.CENTER_RIGHT);
            {
                JFXButton revealButton = new JFXButton();
                revealButton.setGraphic(SVG.FOLDER_OPEN.createIcon());
                revealButton.getStyleClass().add("toggle-icon4");
                revealButton.setOnAction(e -> {
                    JavaRuntime java = getItem();
                    if (java != null)
                        onReveal(java);
                });
                FXUtils.installFastTooltip(revealButton, i18n("reveal.in_file_manager"));

                JFXButton removeButton = new JFXButton();
                removeButton.getStyleClass().add("toggle-icon4");
                removeButton.setOnAction(e -> {
                    JavaRuntime java = getItem();
                    if (java != null)
                        onRemove(java);
                });
                FXUtils.installFastTooltip(removeButton, removeTooltip);

                this.removeIconPane = new StackPane();
                removeIconPane.setAlignment(Pos.CENTER);
                FXUtils.setLimitWidth(removeIconPane, 24);
                FXUtils.setLimitHeight(removeIconPane, 24);
                removeButton.setGraphic(removeIconPane);

                right.getChildren().setAll(revealButton, removeButton);
            }
            root.setRight(right);

            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            this.graphic = new RipplerContainer(root);

            FXUtils.limitCellWidth(listView, this);
        }

        @Override
        protected void updateItem(JavaRuntime item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                content.setTitle((item.isJDK() ? "JDK" : "JRE") + " " + item.getVersion());
                content.setSubtitle(item.getBinary().toString());

                content.getTags().clear();
                content.addTag(i18n("java.info.architecture") + ": " + item.getArchitecture().getDisplayName());
                String vendor = JavaInfo.normalizeVendor(item.getVendor());
                if (vendor != null)
                    content.addTag(i18n("java.info.vendor") + ": " + vendor);

                SVG newRemoveIcon = item.isManaged() ? SVG.DELETE_FOREVER : SVG.DELETE;
                if (removeIcon != newRemoveIcon) {
                    removeIcon = newRemoveIcon;
                    removeIconPane.getChildren().setAll(removeIcon.createIcon(24));
                    removeTooltip.setText(item.isManaged() ? i18n("java.uninstall") : i18n("java.disable"));
                }

                setGraphic(graphic);
            }
        }

        private void onReveal(JavaRuntime java) {
            Path target;
            Path parent = java.getBinary().getParent();
            if (parent != null
                    && parent.getParent() != null
                    && parent.getFileName() != null
                    && parent.getFileName().toString().equals("bin")
                    && Files.exists(parent.getParent().resolve("release"))) {
                target = parent.getParent();
            } else {
                target = java.getBinary();
            }

            FXUtils.showFileInExplorer(target);
        }

        private void onRemove(JavaRuntime java) {
            if (java.isManaged()) {
                Controllers.confirm(
                        i18n("java.uninstall.confirm"),
                        i18n("message.warning"),
                        () -> Controllers.taskDialog(JavaManager.getUninstallJavaTask(java), i18n("java.uninstall"), TaskCancellationAction.NORMAL),
                        null
                );
            } else {
                Controllers.confirm(
                        i18n("java.disable.confirm"),
                        i18n("message.warning"),
                        () -> {
                            String path = java.getBinary().toString();
                            ConfigHolder.globalConfig().getUserJava().remove(path);
                            ConfigHolder.globalConfig().getDisabledJava().add(path);
                            try {
                                JavaManager.removeJava(java);
                            } catch (InterruptedException ignored) {
                            }
                        },
                        null
                );
            }
        }
    }
}
