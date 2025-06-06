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
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.platform.UnsupportedPlatformException;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class JavaManagementPage extends ListPageBase<JavaManagementPage.JavaItem> {

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
            String name = it.getName();
            return it.isDirectory() || name.endsWith(".zip") || name.endsWith(".tar.gz") || name.equals(OperatingSystem.CURRENT_OS.getJavaExecutable());
        }, files -> {
            for (File file : files) {
                if (file.isDirectory()) {
                    onAddJavaHome(file.toPath());
                } else {
                    String fileName = file.getName();

                    if (fileName.equals(OperatingSystem.CURRENT_OS.getJavaExecutable())) {
                        onAddJavaBinary(file.toPath());
                    } else if (fileName.endsWith(".zip") || fileName.endsWith(".tar.gz")) {
                        onInstallArchive(file.toPath());
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
        File file = chooser.showOpenDialog(Controllers.getStage());
        if (file != null) {
            JavaManager.getAddJavaTask(file.toPath()).whenComplete(Schedulers.javafx(), exception -> {
                if (exception != null) {
                    LOG.warning("Failed to add java", exception);
                    Controllers.dialog(i18n("java.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                }
            }).start();
        }
    }

    void onShowRestoreJavaPage() {
        Controllers.navigate(new JavaRestorePage(ConfigHolder.globalConfig().getDisabledJava()));
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

    // FXThread
    private void loadJava(Collection<JavaRuntime> javaRuntimes) {
        if (javaRuntimes != null) {
            List<JavaItem> items = new ArrayList<>();
            for (JavaRuntime java : javaRuntimes) {
                items.add(new JavaItem(java));
            }
            this.setItems(FXCollections.observableList(items));
            this.setLoading(false);
        } else
            this.setLoading(true);
    }

    static final class JavaItem extends Control {
        private final JavaRuntime java;

        public JavaItem(JavaRuntime java) {
            this.java = java;
        }

        public JavaRuntime getJava() {
            return java;
        }

        public void onReveal() {
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

        public void onRemove() {
            if (java.isManaged()) {
                Controllers.taskDialog(JavaManager.getUninstallJavaTask(java), i18n("java.uninstall"), TaskCancellationAction.NORMAL);
            } else {
                String path = java.getBinary().toString();
                ConfigHolder.globalConfig().getUserJava().remove(path);
                ConfigHolder.globalConfig().getDisabledJava().add(path);
                try {
                    JavaManager.removeJava(java);
                } catch (InterruptedException ignored) {
                }
            }
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new JavaRuntimeItemSkin(this);
        }

    }

    private static final class JavaRuntimeItemSkin extends SkinBase<JavaItem> {

        JavaRuntimeItemSkin(JavaItem control) {
            super(control);
            JavaRuntime java = control.getJava();
            String vendor = JavaInfo.normalizeVendor(java.getVendor());

            BorderPane root = new BorderPane();

            HBox center = new HBox();
            center.setMouseTransparent(true);
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);

            TwoLineListItem item = new TwoLineListItem();
            item.setTitle((java.isJDK() ? "JDK" : "JRE") + " " + java.getVersion());
            item.setSubtitle(java.getBinary().toString());
            item.getTags().add(i18n("java.info.architecture") + ": " + java.getArchitecture().getDisplayName());
            if (vendor != null)
                item.getTags().add(i18n("java.info.vendor") + ": " + vendor);
            BorderPane.setAlignment(item, Pos.CENTER);
            center.getChildren().setAll(item);
            root.setCenter(center);

            HBox right = new HBox();
            right.setAlignment(Pos.CENTER_RIGHT);
            {
                JFXButton revealButton = new JFXButton();
                revealButton.getStyleClass().add("toggle-icon4");
                revealButton.setGraphic(FXUtils.limitingSize(SVG.FOLDER_OPEN.createIcon(Theme.blackFill(), 24), 24, 24));
                revealButton.setOnAction(e -> control.onReveal());
                FXUtils.installFastTooltip(revealButton, i18n("reveal.in_file_manager"));

                JFXButton removeButton = new JFXButton();
                removeButton.getStyleClass().add("toggle-icon4");
                removeButton.setOnAction(e -> Controllers.confirm(
                        java.isManaged() ? i18n("java.uninstall.confirm") : i18n("java.disable.confirm"),
                        i18n("message.warning"),
                        control::onRemove,
                        null
                ));
                if (java.isManaged()) {
                    removeButton.setGraphic(FXUtils.limitingSize(SVG.DELETE_FOREVER.createIcon(Theme.blackFill(), 24), 24, 24));
                    FXUtils.installFastTooltip(removeButton, i18n("java.uninstall"));
                    if (JavaRuntime.CURRENT_JAVA != null && java.getBinary().equals(JavaRuntime.CURRENT_JAVA.getBinary()))
                        removeButton.setDisable(true);
                } else {
                    removeButton.setGraphic(FXUtils.limitingSize(SVG.DELETE.createIcon(Theme.blackFill(), 24), 24, 24));
                    FXUtils.installFastTooltip(removeButton, i18n("java.disable"));
                }

                right.getChildren().setAll(revealButton, removeButton);
            }
            root.setRight(right);

            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            getChildren().setAll(new RipplerContainer(root));
        }
    }

    private static final class JavaPageSkin extends ToolbarListPageSkin<JavaManagementPage> {

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
    }
}
