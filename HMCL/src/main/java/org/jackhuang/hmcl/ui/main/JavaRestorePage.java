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
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class JavaRestorePage extends ListPageBase<JavaRestorePage.DisabledJavaItem> implements DecoratorPage {

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.fromTitle(i18n("java.disabled.management")));

    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationListener listener;

    public JavaRestorePage(ObservableSet<String> disabledJava) {
        this.listener = o -> {
            ArrayList<DisabledJavaItem> result = new ArrayList<>(disabledJava.size());
            for (String path : disabledJava) {
                Path realPath = null;

                try {
                    realPath = Paths.get(path).toRealPath();
                } catch (IOException ignored) {
                }

                result.add(new DisabledJavaItem(disabledJava, path, realPath));
            }
            result.sort((a, b) -> {
                if (a.realPath == null && b.realPath != null)
                    return -1;
                if (a.realPath != null && b.realPath == null)
                    return 1;
                return a.path.compareTo(b.path);
            });
            this.setItems(FXCollections.observableList(result));
        };
        disabledJava.addListener(new WeakInvalidationListener(listener));
        listener.invalidated(disabledJava);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JavaRestorePageSkin(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    static final class DisabledJavaItem extends Control {
        final ObservableSet<String> disabledJava;
        final String path;
        final Path realPath;

        DisabledJavaItem(ObservableSet<String> disabledJava, String path, Path realPath) {
            this.disabledJava = disabledJava;
            this.path = path;
            this.realPath = realPath;
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new DisabledJavaItemSkin(this);
        }

        void onReveal() {
            if (realPath != null) {
                Path target;
                Path parent = realPath.getParent();
                if (parent != null
                        && parent.getParent() != null
                        && parent.getFileName() != null
                        && parent.getFileName().toString().equals("bin")
                        && Files.exists(parent.getParent().resolve("release"))) {
                    target = parent.getParent();
                } else {
                    target = realPath;
                }

                FXUtils.showFileInExplorer(target);
            }
        }

        void onRestore() {
            disabledJava.remove(path);
            JavaManager.getAddJavaTask(realPath).whenComplete(Schedulers.javafx(), exception -> {
                if (exception != null) {
                    LOG.warning("Failed to add java", exception);
                    Controllers.dialog(i18n("java.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                }
            }).start();
        }

        void onRemove() {
            disabledJava.remove(path);
        }
    }

    private static final class DisabledJavaItemSkin extends SkinBase<DisabledJavaItem> {
        DisabledJavaItemSkin(DisabledJavaItem skinnable) {
            super(skinnable);

            BorderPane root = new BorderPane();

            Label label = new Label(skinnable.path);
            BorderPane.setAlignment(label, Pos.CENTER_LEFT);
            root.setCenter(label);

            HBox right = new HBox();
            right.setAlignment(Pos.CENTER_RIGHT);
            {
                JFXButton revealButton = new JFXButton();
                revealButton.getStyleClass().add("toggle-icon4");
                revealButton.setGraphic(SVG.FOLDER_OPEN.createIcon());
                revealButton.setOnAction(e -> skinnable.onReveal());
                FXUtils.installFastTooltip(revealButton, i18n("reveal.in_file_manager"));

                if (skinnable.realPath == null) {
                    revealButton.setDisable(true);

                    JFXButton removeButton = new JFXButton();
                    removeButton.getStyleClass().add("toggle-icon4");
                    removeButton.setGraphic(SVG.DELETE.createIcon());
                    removeButton.setOnAction(e -> skinnable.onRemove());
                    FXUtils.installFastTooltip(removeButton, i18n("java.disabled.management.remove"));

                    right.getChildren().setAll(revealButton, removeButton);
                } else {
                    JFXButton restoreButton = new JFXButton();
                    restoreButton.getStyleClass().add("toggle-icon4");
                    restoreButton.setGraphic(SVG.RESTORE.createIcon());
                    restoreButton.setOnAction(e -> skinnable.onRestore());
                    FXUtils.installFastTooltip(restoreButton, i18n("java.disabled.management.restore"));

                    right.getChildren().setAll(revealButton, restoreButton);
                }
            }
            root.setRight(right);

            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            this.getChildren().setAll(new RipplerContainer(root));
        }
    }

    private static final class JavaRestorePageSkin extends ToolbarListPageSkin<DisabledJavaItem, JavaRestorePage> {
        JavaRestorePageSkin(JavaRestorePage skinnable) {
            super(skinnable);
        }

        @Override
        protected List<Node> initializeToolbar(JavaRestorePage skinnable) {
            return Collections.emptyList();
        }
    }
}
