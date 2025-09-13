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
import com.jfoenix.controls.JFXDialogLayout;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.skin.SkinCanvas;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniRunning;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniWavingArms;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class MicrosoftAccountSkinPane extends StackPane {
    private final MicrosoftAccount account;
    private final SkinCanvas canvas;
    private final SpinnerPane canvasSpinnerPane = new SpinnerPane();
    private final SpinnerPane updateSkinButtonSpinnerPane = new SpinnerPane();
    private final MicrosoftService.MinecraftProfileResponse profile;
    private final MicrosoftAccountChangeCapeDialog accountChangeCapeDialog;
    private TextureModel model;
    private Image localSkinImg;

    public MicrosoftAccountSkinPane(MicrosoftAccount account) {
        this.account = account;
        this.profile = account.getMinecraftProfileResponse().orElse(null);
        this.accountChangeCapeDialog = new MicrosoftAccountChangeCapeDialog(account, profile);

        JFXDialogLayout layout = new JFXDialogLayout();
        getChildren().setAll(layout);
        layout.setHeading(new Label(i18n("account.cape_skin")));

        canvas = new SkinCanvas(TexturesLoader.getDefaultSkinImage(), 300, 300, true);
        canvas.enableRotation(.5);
        canvasSpinnerPane.setContent(canvas);
        canvasSpinnerPane.showSpinner();
        canvasSpinnerPane.setPrefWidth(300);
        canvasSpinnerPane.setPrefHeight(300);

        BorderPane pane = new BorderPane();
        pane.setCenter(canvasSpinnerPane);

        HBox actionsBox = new HBox();
        actionsBox.setPadding(new Insets(0, 0, 0, 0));

        StackPane skinOptionPane = new StackPane(actionsBox);
        skinOptionPane.setMaxWidth(300);

        updateCanvas().start();

        JFXButton uploadSkinButton = FXUtils.newBorderButton(i18n("account.skin.upload"));
        updateSkinButtonSpinnerPane.setContent(uploadSkinButton);
        updateSkinButtonSpinnerPane.setPrefSize(150, 40);
        uploadSkinButton.setOnAction(event -> {
            updateSkinButtonSpinnerPane.showSpinner();
            Task<?> task = uploadSkin();
            if (task != null) {
                task.start();
            } else {
                updateSkinButtonSpinnerPane.hideSpinner();
            }
        });

        JFXButton changeCapeButton = FXUtils.newBorderButton(i18n("account.cape.change"));
        updateSkinButtonSpinnerPane.setContent(uploadSkinButton);
        changeCapeButton.setOnAction(event -> Controllers.dialog(accountChangeCapeDialog));
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        actionsBox.getChildren().add(updateSkinButtonSpinnerPane);
        actionsBox.getChildren().add(changeCapeButton);

        pane.setBottom(skinOptionPane);

        JFXButton closeButton = new JFXButton(i18n("button.ok"));
        closeButton.getStyleClass().add("dialog-accept");
        closeButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, closeButton::fire);

        layout.setBody(pane);
        layout.setActions(closeButton);
    }

    private Task<?> uploadSkin() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("account.skin.upload"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("account.skin.file"), "*.png"));
        File selectedFile = chooser.showOpenDialog(Controllers.getStage());
        if (selectedFile == null) {
            return null;
        }
        CompletableFuture<TextureModel> future = new CompletableFuture<>();
        MessageDialogPane.Builder builder = new MessageDialogPane.Builder(i18n("account.skin.select_model"), i18n("account.skin.upload"), MessageDialogPane.MessageType.QUESTION);
        builder.addAction(i18n("account.skin.model.default"), () -> future.complete(TextureModel.WIDE));
        builder.addAction(i18n("account.skin.model.slim"), () -> future.complete(TextureModel.SLIM));

        builder.addCancel(updateSkinButtonSpinnerPane::hideSpinner);

        Controllers.dialog(builder.build());

        return Task.runAsync(() -> {
            model = future.get();

            try (FileInputStream input = new FileInputStream(selectedFile)) {
                localSkinImg = new Image(input);
            }

            boolean isSlim = model.equals(TextureModel.SLIM);
            LOG.info("Uploading skin [" + selectedFile + "], model [" + model + "]");
            account.uploadSkin(isSlim, selectedFile.toPath());
        }).whenComplete(Schedulers.javafx(), e -> {
            updateSkinButtonSpinnerPane.hideSpinner();
            updateCanvas().start();
            if (e != null) {
                Logger.LOG.error("Failed to upload skin", e);
                Controllers.dialog(Accounts.localizeErrorMessage(e), i18n("account.skin.upload.failed"), MessageDialogPane.MessageType.ERROR);
            } else {
                Controllers.dialog(i18n("message.success"), i18n("account.skin.upload"), MessageDialogPane.MessageType.SUCCESS);
            }
        });
    }

    private Task<?> updateCanvas() {
        return Task.runAsync(() -> {
            Map<TextureType, Texture> textures = account.getTextures().get().get();
            Texture capeTexture = textures.get(TextureType.CAPE);
            Texture skinTexture = textures.get(TextureType.SKIN);

            Task<Image> capeTask = (capeTexture != null) ? FXUtils.getRemoteImageTask(capeTexture.getUrl(), 0, 0, false, false) : Task.completed(null);

            Task<Image> skinTask = (skinTexture != null) ? FXUtils.getRemoteImageTask(skinTexture.getUrl(), 0, 0, false, false) : Task.completed(null);

            Task<Void> combinedTask = Task.allOf(capeTask, skinTask).thenRunAsync(Schedulers.javafx(), () -> {
                Image loadedCapeImg = capeTask.getResult();
                Image loadedSkinImg = skinTask.getResult();

                boolean isSlim = Objects.equals(profile.getSkins().get(0).getVariant(), "SLIM") || Objects.equals(model, TextureModel.SLIM);

                canvas.updateSkin(localSkinImg != null ? localSkinImg : loadedSkinImg, isSlim, loadedCapeImg);

                canvasSpinnerPane.hideSpinner();
                canvas.getAnimationPlayer().addSkinAnimation(new SkinAniWavingArms(100, 2000, 7.5, canvas), new SkinAniRunning(100, 100, 30, canvas));
            });

            combinedTask.start();
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to initiate skin update", exception);
                Controllers.showToast(i18n("message.failed"));
            }
        });
    }
}
