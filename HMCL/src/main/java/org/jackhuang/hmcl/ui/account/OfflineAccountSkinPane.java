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
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.Skin;
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
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

    private final MultiFileItem<Skin.Type> skinItem = new MultiFileItem<>();
    private final JFXTextField cslApiField = new JFXTextField();
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

        SkinCanvas canvas = new SkinCanvas(TexturesLoader.getDefaultSkinImage(), 260, 260, true);
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
                    skinItem.setSelectedData(Skin.Type.LOCAL_FILE);
                });
            }
        });

        StackPane skinOptionPane = new StackPane();
        pane.setRight(skinOptionPane);

        skinSelector.setMaxWidth(Double.MAX_VALUE);
        capeSelector.setMaxWidth(Double.MAX_VALUE);
        modelCombobox.setMaxWidth(Double.MAX_VALUE);
        cslApiField.setMaxWidth(Double.MAX_VALUE);

        GridPane.setHgrow(modelCombobox, Priority.ALWAYS);
        GridPane.setHgrow(skinSelector, Priority.ALWAYS);
        GridPane.setHgrow(capeSelector, Priority.ALWAYS);
        GridPane.setHgrow(cslApiField, Priority.ALWAYS);

        layout.setBody(pane);

        cslApiField.setPromptText(i18n("account.skin.type.csl_api.location.hint"));
        cslApiField.setValidators(new URLValidator());
        FXUtils.setValidateWhileTextChanged(cslApiField, true);

        skinItem.loadChildren(Arrays.asList(
                new MultiFileItem.Option<>(i18n("message.default"), Skin.Type.DEFAULT),
                new MultiFileItem.Option<>(i18n("account.skin.type.steve"), Skin.Type.STEVE),
                new MultiFileItem.Option<>(i18n("account.skin.type.alex"), Skin.Type.ALEX),
                new MultiFileItem.Option<>(i18n("account.skin.type.local_file"), Skin.Type.LOCAL_FILE),
                new MultiFileItem.Option<>(i18n("account.skin.type.little_skin"), Skin.Type.LITTLE_SKIN),
                new MultiFileItem.Option<>(i18n("account.skin.type.csl_api"), Skin.Type.CUSTOM_SKIN_LOADER_API)
        ));

        modelCombobox.setConverter(stringConverter(model -> i18n("account.skin.model." + model.modelName)));
        modelCombobox.getItems().setAll(TextureModel.WIDE, TextureModel.SLIM);

        if (account.getSkin() == null) {
            skinItem.setSelectedData(Skin.Type.DEFAULT);
            modelCombobox.setValue(TextureModel.WIDE);
        } else {
            skinItem.setSelectedData(account.getSkin().getType());
            cslApiField.setText(account.getSkin().getCslApi());
            modelCombobox.setValue(account.getSkin().getTextureModel());
            skinSelector.setValue(account.getSkin().getLocalSkinPath());
            capeSelector.setValue(account.getSkin().getLocalCapePath());
        }

        PauseTransition pauseTransition = new PauseTransition(Duration.seconds(1));

        Runnable loadSkin = () -> {
            getSkin().load(account.getProfileName())
                    .whenComplete(Schedulers.javafx(), (result, exception) -> {
                        if (exception != null) {
                            LOG.warning("Failed to load skin", exception);
                            Controllers.showToast(i18n("message.failed"));
                        } else {
                            UUID uuid = this.account.getProfileID();
                            if (result == null || result.getSkin() == null && result.getCape() == null) {
                                canvas.updateSkin(
                                        TexturesLoader.getDefaultSkin(uuid).getImage(),
                                        TexturesLoader.getDefaultModel(uuid) == TextureModel.SLIM,
                                        null
                                );
                                return;
                            }
                            canvas.updateSkin(
                                    result.getSkin() != null ? result.getSkin().getImage() : TexturesLoader.getDefaultSkin(uuid).getImage(),
                                    result.getModel() == TextureModel.SLIM,
                                    result.getCape() != null ? result.getCape().getImage() : null);
                        }
                    }).start();
        };

        pauseTransition.setOnFinished(e -> loadSkin.run());

        skinBinding = FXUtils.observeWeak(() -> {
            Skin.Type selectedType = skinItem.getSelectedData();

            if (selectedType == Skin.Type.CUSTOM_SKIN_LOADER_API) {
                if (!cslApiField.validate()) {
                    pauseTransition.stop();
                    return;
                }
                pauseTransition.playFromStart();
            } else {
                pauseTransition.stop();
                loadSkin.run();
            }
        }, skinItem.selectedDataProperty(), cslApiField.textProperty(), modelCombobox.valueProperty(), skinSelector.valueProperty(), capeSelector.valueProperty());

        FXUtils.onChangeAndOperate(skinItem.selectedDataProperty(), selectedData -> {
            GridPane gridPane = new GridPane();

            gridPane.setPadding(new Insets(0, 0, 0, 10));
            gridPane.setHgap(16);
            gridPane.setVgap(12);

            ColumnConstraints column = new ColumnConstraints();
            column.setHgrow(Priority.ALWAYS);
            gridPane.getColumnConstraints().setAll(column);

            switch (selectedData) {
                case DEFAULT:
                case STEVE:
                case ALEX:
                    break;

                case LITTLE_SKIN:
                    HintPane hint = new HintPane(MessageDialogPane.MessageType.INFO);
                    hint.setText(i18n("account.skin.type.little_skin.hint"));

                    hint.setMaxWidth(Double.MAX_VALUE);
                    GridPane.setHgrow(hint, Priority.ALWAYS);

                    gridPane.addRow(0, hint);
                    break;

                case LOCAL_FILE:
                    modelCombobox.setMaxWidth(Double.MAX_VALUE);
                    skinSelector.setMaxWidth(Double.MAX_VALUE);
                    capeSelector.setMaxWidth(Double.MAX_VALUE);

                    GridPane.setHgrow(modelCombobox, Priority.ALWAYS);
                    GridPane.setHgrow(skinSelector, Priority.ALWAYS);
                    GridPane.setHgrow(capeSelector, Priority.ALWAYS);

                    gridPane.addRow(0, new Label(i18n("account.skin.model")));
                    gridPane.addRow(1, modelCombobox);

                    gridPane.addRow(2, new Label(i18n("account.skin")));
                    gridPane.addRow(3, skinSelector);

                    gridPane.addRow(4, new Label(i18n("account.cape")));
                    gridPane.addRow(5, capeSelector);
                    break;

                case CUSTOM_SKIN_LOADER_API:
                    cslApiField.setMaxWidth(Double.MAX_VALUE);
                    GridPane.setHgrow(cslApiField, Priority.ALWAYS);

                    gridPane.addRow(0, new Label(i18n("account.skin.type.csl_api.location")));
                    gridPane.addRow(1, cslApiField);
                    break;
            }

            HBox body = new HBox(20);

            skinItem.setPrefWidth(170);
            skinItem.setMinWidth(170);

            body.getChildren().add(skinItem);

            if (!gridPane.getChildren().isEmpty()) {
                VBox right = new VBox();
                right.getChildren().add(gridPane);

                right.setPrefWidth(230);
                HBox.setHgrow(right, Priority.ALWAYS);

                body.getChildren().add(right);
            }

            skinOptionPane.getChildren().setAll(body);
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

        acceptButton.disableProperty().bind(
                skinItem.selectedDataProperty().isEqualTo(Skin.Type.CUSTOM_SKIN_LOADER_API)
                        .and(cslApiField.activeValidatorProperty().isNotNull()));

        layout.setActions(littleSkinLink, acceptButton, cancelButton);
    }

    private Skin getSkin() {
        Skin.Type type = skinItem.getSelectedData();
        if (type == Skin.Type.LOCAL_FILE) {
            return new Skin(type, cslApiField.getText(), modelCombobox.getValue(), skinSelector.getValue(), capeSelector.getValue());
        } else {
            String cslApi = type == Skin.Type.CUSTOM_SKIN_LOADER_API ? cslApiField.getText() : null;
            return new Skin(type, cslApi, null, null, null);
        }
    }
}
