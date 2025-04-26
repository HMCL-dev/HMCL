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
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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
import org.jackhuang.hmcl.util.skin.InvalidSkinException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
    private MicrosoftService.MinecraftProfileResponse profile;
    private TextureModel model;
    private Image localSkinImg;

    public MicrosoftAccountSkinPane(MicrosoftAccount account) {
        this.account = account;
        this.profile = account.getMinecraftProfileResponse().get();

        getStyleClass().add("skin-pane");

        JFXDialogLayout layout = new JFXDialogLayout();
        getChildren().setAll(layout);
        layout.setHeading(new Label(i18n("account.skin")));

        canvas = new SkinCanvas(TexturesLoader.getDefaultSkinImage(), 300, 300, true);
        canvas.enableRotation(.5);
        canvasSpinnerPane.setContent(canvas);
        canvasSpinnerPane.showSpinner();

        StackPane canvasPane = new StackPane(canvasSpinnerPane);
        canvasPane.setPrefWidth(300);
        canvasPane.setPrefHeight(300);

        BorderPane pane = new BorderPane();
        pane.setCenter(canvasPane);

        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(0, 0, 0, 10));

        StackPane skinOptionPane = new StackPane(gridPane);
        skinOptionPane.setMaxWidth(300);

        updateCanvas().start();

        JFXButton acceptButton = new JFXButton(i18n("button.ok"));
        acceptButton.getStyleClass().add("dialog-accept");
        acceptButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

        JFXButton uploadSkinButton = new JFXButton(i18n("account.skin.upload"));
        updateSkinButtonSpinnerPane.setContent(uploadSkinButton);
        updateSkinButtonSpinnerPane.setPrefSize(150, 40);
        uploadSkinButton.getStyleClass().add("jfx-button-raised");
        uploadSkinButton.setOnAction(event -> {
            updateSkinButtonSpinnerPane.showSpinner();
            Task<?> task = uploadSkin();
            if (task != null) {
                task.start();
            } else {
                updateSkinButtonSpinnerPane.hideSpinner();
            }
        });

        JFXButton changeCapeButton = new JFXButton(i18n("account.cape.change"));
        updateSkinButtonSpinnerPane.setContent(uploadSkinButton);
        changeCapeButton.getStyleClass().add("jfx-button-raised");
        changeCapeButton.setOnAction(event -> Controllers.dialog(new MicrosoftAccountChangeCapeDialog(account, account.getMinecraftProfileResponse().get())));
        gridPane.addRow(0, updateSkinButtonSpinnerPane);
        gridPane.addRow(0, changeCapeButton);

        pane.setRight(skinOptionPane);

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        layout.setBody(pane);
        layout.setActions(acceptButton, cancelButton);
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
        Controllers.dialog(builder.build());

        return Task.runAsync(() -> {
            model = future.get();
            try (FileInputStream input = new FileInputStream(selectedFile)) {
                localSkinImg = new Image(input);
            } catch (IOException e) {
                throw new InvalidSkinException("Failed to read skin image", e);
            }
            if (localSkinImg.isError()) {
                throw new InvalidSkinException("Failed to read skin image", localSkinImg.getException());
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
        return Task.supplyAsync(Schedulers.defaultScheduler(), () -> {
            Map<TextureType, Texture> textures = account.getTextures().get().get();

            Texture cape = textures.get(TextureType.CAPE);

            Image remoteCapeImg;
            if (cape != null){
                remoteCapeImg = FXUtils.newRemoteImage(textures.get(TextureType.CAPE).getUrl());
            } else {
                remoteCapeImg = null;
            }

            Image remoteSkinImg = FXUtils.newRemoteImage(textures.get(TextureType.SKIN).getUrl());
            FXUtils.runInFX(() -> {
                canvas.updateSkin(localSkinImg != null ? localSkinImg : remoteSkinImg,
                        model == null ? Objects.equals(profile.getSkins().get(0).getVariant(), "SLIM") : model.equals(TextureModel.SLIM),
                        remoteCapeImg);
            });
            return null;
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                canvasSpinnerPane.hideSpinner();
                canvas.getAnimationPlayer().addSkinAnimation(new SkinAniWavingArms(100, 2000, 7.5, canvas), new SkinAniRunning(100, 100, 30, canvas));
            } else {
                LOG.warning("Failed to load skin", exception);
                Controllers.showToast(i18n("message.failed"));
            }
        });
    }

}
