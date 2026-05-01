/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineSkinConfig;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.game.skin.TextureModel;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.FileSelector;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.skin.SkinCanvas;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniRunning;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniWavingArms;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class OfflineAccountSkinPane extends StackPane {
    private final OfflineAccount account;

    private final MultiFileItem<OfflineSkinConfig.Type> skinItem = new MultiFileItem<>();
    private final JFXComboBox<TextureModel> modelCombobox = new JFXComboBox<>();
    private final FileSelector skinSelector = new FileSelector();
    private final FileSelector capeSelector = new FileSelector();

    private final InvalidationListener skinBinding;

    public OfflineAccountSkinPane(OfflineAccount account) {
        this.account = account;

        getStyleClass().add("skin-pane");

        JFXDialogLayout layout = new JFXDialogLayout();
        getChildren().setAll(layout);
        layout.setHeading(new Label(i18n("account.skin")));

        BorderPane pane = new BorderPane();

        SkinCanvas canvas = new SkinCanvas(TexturesLoader.getDefaultSkinImage(), 300, 300, true);
        StackPane canvasPane = new StackPane(canvas);
        canvasPane.setPrefWidth(300);
        canvasPane.setPrefHeight(300);
        pane.setCenter(canvas);
        canvas.getAnimationPlayer().addSkinAnimation(new SkinAniWavingArms(100, 2000, 7.5, canvas), new SkinAniRunning(100, 100, 30, canvas));
        canvas.enableRotation(.5);

        canvas.addEventHandler(DragEvent.DRAG_OVER, e -> {
            if (e.getDragboard().hasFiles()) {
                Path file = e.getDragboard().getFiles().get(0).toPath();
                if (FileUtils.getName(file).endsWith(".png"))
                    e.acceptTransferModes(TransferMode.COPY);
            }
        });
        canvas.addEventHandler(DragEvent.DRAG_DROPPED, e -> {
            if (e.isAccepted()) {
                Path skin = e.getDragboard().getFiles().get(0).toPath();
                Platform.runLater(() -> {
                    skinSelector.setValue(FileUtils.getAbsolutePath(skin));
                    skinItem.setSelectedData(OfflineSkinConfig.Type.LOCAL_FILE);
                });
            }
        });

        StackPane skinOptionPane = new StackPane();
        skinOptionPane.setMaxWidth(300);
        VBox optionPane = new VBox(skinItem, skinOptionPane);
        pane.setRight(optionPane);

        skinSelector.maxWidthProperty().bind(skinOptionPane.maxWidthProperty().multiply(0.7));
        capeSelector.maxWidthProperty().bind(skinOptionPane.maxWidthProperty().multiply(0.7));

        layout.setBody(pane);

        skinItem.loadChildren(Arrays.asList(
                new MultiFileItem.Option<>(i18n("message.default"), OfflineSkinConfig.Type.DEFAULT),
                new MultiFileItem.Option<>(i18n("account.skin.type.steve"), OfflineSkinConfig.Type.STEVE),
                new MultiFileItem.Option<>(i18n("account.skin.type.alex"), OfflineSkinConfig.Type.ALEX),
                new MultiFileItem.Option<>(i18n("account.skin.type.local_file"), OfflineSkinConfig.Type.LOCAL_FILE)
        ));

        modelCombobox.setConverter(stringConverter(model -> i18n("account.skin.model." + model.modelName)));
        modelCombobox.getItems().setAll(TextureModel.WIDE, TextureModel.SLIM);

        if (account.getSkin() == null) {
            skinItem.setSelectedData(OfflineSkinConfig.Type.DEFAULT);
            modelCombobox.setValue(TextureModel.WIDE);
        } else {
            skinItem.setSelectedData(account.getSkin().type());
            modelCombobox.setValue(account.getSkin().textureModel());
            skinSelector.setValue(account.getSkin().localSkinPath());
            capeSelector.setValue(account.getSkin().localCapePath());
        }

        skinBinding = FXUtils.observeWeak(() -> {
            getSkin().load()
                    .whenComplete(Schedulers.javafx(), (result, exception) -> {
                        if (exception != null) {
                            LOG.warning("Failed to load skin", exception);
                            Controllers.showToast(i18n("message.failed"));
                        } else {
                            UUID uuid = this.account.getUUID();
                            if (result == null || result.skin() == null && result.cape() == null) {
                                canvas.updateSkin(
                                        TexturesLoader.getDefaultSkin(uuid).image(),
                                        TexturesLoader.getDefaultModel(uuid) == TextureModel.SLIM,
                                        null
                                );
                                return;
                            }
                            canvas.updateSkin(
                                    result.skin() != null ? result.skin().image() : TexturesLoader.getDefaultSkin(uuid).image(),
                                    result.model() == TextureModel.SLIM,
                                    result.cape() != null ? result.cape().image() : null);
                        }
                    }).start();
        }, skinItem.selectedDataProperty(), modelCombobox.valueProperty(), skinSelector.valueProperty(), capeSelector.valueProperty());

        FXUtils.onChangeAndOperate(skinItem.selectedDataProperty(), selectedData -> {
            GridPane gridPane = new GridPane();
            // Increase bottom padding to prevent the prompt from overlapping with the dialog action area

            gridPane.setPadding(new Insets(0, 0, 45, 10));
            gridPane.setHgap(16);
            gridPane.setVgap(8);
            gridPane.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());

            switch (selectedData) {
                case DEFAULT:
                case STEVE:
                case ALEX:
                    break;
                case LOCAL_FILE:
                    gridPane.setPadding(new Insets(0, 0, 0, 10));
                    gridPane.addRow(0, new Label(i18n("account.skin.model")), modelCombobox);
                    gridPane.addRow(1, new Label(i18n("account.skin")), skinSelector);
                    gridPane.addRow(2, new Label(i18n("account.cape")), capeSelector);
                    break;
            }

            skinOptionPane.getChildren().setAll(gridPane);
        });

        JFXButton acceptButton = new JFXButton(i18n("button.ok"));
        acceptButton.getStyleClass().add("dialog-accept");
        acceptButton.setOnAction(e -> {
            account.setSkin(getSkin());
            fireEvent(new DialogCloseEvent());
        });

        JFXHyperlink littleSkinLink = new JFXHyperlink(i18n("account.skin.type.little_skin"));
        littleSkinLink.setOnAction(e -> FXUtils.openLink("https://littleskin.cn/"));
        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        layout.setActions(littleSkinLink, acceptButton, cancelButton);
    }

    private OfflineSkinConfig getSkin() {
        OfflineSkinConfig.Type type = skinItem.getSelectedData();
        if (type == OfflineSkinConfig.Type.LOCAL_FILE) {
            return new OfflineSkinConfig(type, modelCombobox.getValue(), skinSelector.getValue(), capeSelector.getValue());
        } else {
            return new OfflineSkinConfig(type, null, null, null);
        }
    }
}
