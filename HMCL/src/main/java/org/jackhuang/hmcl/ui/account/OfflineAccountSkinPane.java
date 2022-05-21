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
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import moe.mickey.minecraft.skin.fx.SkinCanvas;
import moe.mickey.minecraft.skin.fx.animation.SkinAniRunning;
import moe.mickey.minecraft.skin.fx.animation.SkinAniWavingArms;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.Skin;
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

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

        SkinCanvas canvas = new SkinCanvas(SkinCanvas.STEVE, 300, 300, true);
        StackPane canvasPane = new StackPane(canvas);
        canvasPane.setPrefWidth(300);
        canvasPane.setPrefHeight(300);
        pane.setCenter(canvas);
        canvas.getAnimationPlayer().addSkinAnimation(new SkinAniWavingArms(100, 2000, 7.5, canvas), new SkinAniRunning(100, 100, 30, canvas));
        canvas.enableRotation(.5);

        canvas.addEventHandler(DragEvent.DRAG_OVER, e -> {
            if (e.getDragboard().hasFiles()) {
                File file = e.getDragboard().getFiles().get(0);
                if (file.getAbsolutePath().endsWith(".png"))
                    e.acceptTransferModes(TransferMode.COPY);
            }
        });
        canvas.addEventHandler(DragEvent.DRAG_DROPPED, e -> {
            if (e.isAccepted()) {
                File skin = e.getDragboard().getFiles().get(0);
                Platform.runLater(() -> {
                    skinSelector.setValue(skin.getAbsolutePath());
                    skinItem.setSelectedData(Skin.Type.LOCAL_FILE);
                });
            }
        });

        StackPane skinOptionPane = new StackPane();
        skinOptionPane.setMaxWidth(300);
        VBox optionPane = new VBox(skinItem, skinOptionPane);
        pane.setRight(optionPane);

        layout.setBody(pane);

        cslApiField.setPromptText(i18n("account.skin.type.csl_api.location.hint"));
        cslApiField.setValidators(new URLValidator());

        skinItem.loadChildren(Arrays.asList(
                new MultiFileItem.Option<>(i18n("message.default"), Skin.Type.DEFAULT),
                new MultiFileItem.Option<>("Steve", Skin.Type.STEVE),
                new MultiFileItem.Option<>("Alex", Skin.Type.ALEX),
                new MultiFileItem.Option<>(i18n("account.skin.type.local_file"), Skin.Type.LOCAL_FILE),
                new MultiFileItem.Option<>("LittleSkin", Skin.Type.LITTLE_SKIN),
                new MultiFileItem.Option<>(i18n("account.skin.type.csl_api"), Skin.Type.CUSTOM_SKIN_LOADER_API)
        ));

        modelCombobox.setConverter(stringConverter(model -> i18n("account.skin.model." + model.modelName)));
        modelCombobox.getItems().setAll(TextureModel.STEVE, TextureModel.ALEX);

        if (account.getSkin() == null) {
            skinItem.setSelectedData(Skin.Type.DEFAULT);
            modelCombobox.setValue(TextureModel.STEVE);
        } else {
            skinItem.setSelectedData(account.getSkin().getType());
            cslApiField.setText(account.getSkin().getCslApi());
            modelCombobox.setValue(account.getSkin().getTextureModel());
            skinSelector.setValue(account.getSkin().getLocalSkinPath());
            capeSelector.setValue(account.getSkin().getLocalCapePath());
        }

        skinBinding = FXUtils.observeWeak(() -> {
            getSkin().load(account.getUsername())
                    .whenComplete(Schedulers.javafx(), (result, exception) -> {
                        if (exception != null) {
                            LOG.log(Level.WARNING, "Failed to load skin", exception);
                            Controllers.showToast(i18n("message.failed"));
                        } else {
                            if (result == null || result.getSkin() == null && result.getCape() == null) {
                                canvas.updateSkin(getDefaultTexture(), isDefaultSlim(), null);
                                return;
                            }
                            canvas.updateSkin(
                                    result.getSkin() != null ? new Image(result.getSkin().getInputStream()) : getDefaultTexture(),
                                    result.getModel() == TextureModel.ALEX,
                                    result.getCape() != null ? new Image(result.getCape().getInputStream()) : null);
                        }
                    }).start();
        }, skinItem.selectedDataProperty(), cslApiField.textProperty(), skinSelector.valueProperty(), capeSelector.valueProperty());

        FXUtils.onChangeAndOperate(skinItem.selectedDataProperty(), selectedData -> {
            GridPane gridPane = new GridPane();
            gridPane.setPadding(new Insets(0, 0, 0, 10));
            gridPane.setHgap(16);
            gridPane.setVgap(8);
            gridPane.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());

            switch (selectedData) {
                case DEFAULT:
                case STEVE:
                case ALEX:
                    break;
                case LITTLE_SKIN:
                    HintPane hint = new HintPane(MessageDialogPane.MessageType.INFO);
                    hint.setText(i18n("account.skin.type.little_skin.hint"));
                    gridPane.addRow(0, hint);
                    break;
                case LOCAL_FILE:
                    gridPane.addRow(0, new Label(i18n("account.skin.model")), modelCombobox);
                    gridPane.addRow(1, new Label(i18n("account.skin")), skinSelector);
                    gridPane.addRow(2, new Label(i18n("account.cape")), capeSelector);
                    break;
                case CUSTOM_SKIN_LOADER_API:
                    gridPane.addRow(0, new Label(i18n("account.skin.type.csl_api.location")), cslApiField);
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

    private Skin getSkin() {
        return new Skin(skinItem.getSelectedData(), cslApiField.getText(), modelCombobox.getValue(), skinSelector.getValue(), capeSelector.getValue());
    }

    private boolean isDefaultSlim() {
        return TextureModel.detectUUID(account.getUUID()) == TextureModel.ALEX;
    }

    private Image getDefaultTexture() {
        if (isDefaultSlim()) {
            return SkinCanvas.ALEX;
        } else {
            return SkinCanvas.STEVE;
        }
    }

}
