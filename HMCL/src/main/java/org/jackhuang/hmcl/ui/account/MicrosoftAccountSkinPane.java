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
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
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
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.skin.SkinCanvas;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniRunning;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniWavingArms;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.*;
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
    private TextureModel model;
    private Image localSkinImg;

    public MicrosoftAccountSkinPane(MicrosoftAccount account) {
        this.account = account;
        this.profile = account.getMinecraftProfileResponse().orElse(null);

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
        changeCapeButton.setOnAction(event -> Controllers.dialog(new MicrosoftAccountChangeCapeDialog()));
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

    public class MicrosoftAccountChangeCapeDialog extends JFXDialogLayout {
        private final MultiFileItem<MicrosoftService.MinecraftProfileResponseCape> capeItem = new MultiFileItem<>();
        private final ImageView capePreview = new ImageView();
        private final SpinnerPane capePreviewSpinner = new SpinnerPane();
        private Image previewCapeImage;
        private MicrosoftService.MinecraftProfileResponseCape currentCape;

        public MicrosoftAccountChangeCapeDialog() {
            setWidth(400);
            setHeading(new Label(i18n("account.cape.change")));
            BorderPane body = new BorderPane();

            capePreviewSpinner.setPrefHeight(150);
            capePreviewSpinner.setPrefWidth(100);

            initCapeItems();

            ScrollPane scrollPane = new ScrollPane(capeItem);

            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setMaxHeight(250);
            scrollPane.setMaxWidth(270);

            body.setCenter(scrollPane);
            FXUtils.smoothScrolling(scrollPane);

            body.setRight(capePreviewSpinner);

            capePreview.setViewport(new Rectangle2D(10, 0, 10 * 10, 17 * 10));
            capePreviewSpinner.setContent(capePreview);

            InvalidationListener updateCapePreviewListener = observable -> {
                if (capeItem.getSelectedData() == null) {
                    capePreview.setImage(null);
                    return;
                }
                updateCapePreview().whenComplete(Schedulers.javafx(), (exception -> {
                    if (exception == null) {
                        capePreviewSpinner.hideSpinner();
                    } else {
                        Logger.LOG.error("Failed to load cape preview", exception);
                    }
                })).start();
            };
            updateCapePreviewListener.invalidated(null);

            capeItem.selectedDataProperty().addListener(updateCapePreviewListener);

            getChildren().add(body);

            JFXButton saveButton = new JFXButton(i18n("button.save"));
            saveButton.getStyleClass().add("dialog-accept");

            saveButton.setOnAction(e -> {
                String cape = capeItem.getSelectedData().id();

                Task<?> updateCapeTask;
                if ("empty".equals(cape) && currentCape != null) {
                    updateCapeTask = Task.runAsync(account::hideCape);
                } else if (currentCape != null && cape.equals(currentCape.id())) {
                    updateCapeTask = null;
                } else {
                    updateCapeTask = Task.runAsync(() -> account.changeCape(cape));
                }

                if (updateCapeTask != null) {
                    updateCapeTask.whenComplete(Schedulers.javafx(), (exception) -> {
                        if (exception != null) {
                            Logger.LOG.error("Failed to change cape", exception);
                            Controllers.dialog(Accounts.localizeErrorMessage(exception), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                        }
                        fireEvent(new DialogCloseEvent());
                    }).start();
                } else {
                    fireEvent(new DialogCloseEvent());
                }
            });

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.getStyleClass().add("dialog-cancel");
            cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            onEscPressed(this, cancelButton::fire);

            setBody(body);
            setActions(saveButton, cancelButton);
        }

        public static Image scaleImageNearestNeighbor(Image img, double sx, double sy) {
            int ow = (int) img.getWidth(), oh = (int) img.getHeight();
            WritableImage scaled = new WritableImage((int) (ow * sx), (int) (oh * sy));
            for (int y = 0; y < scaled.getHeight(); y++)
                for (int x = 0; x < scaled.getWidth(); x++)
                    scaled.getPixelWriter().setColor(x, y, img.getPixelReader().getColor(Math.min(Math.max((int) (x / sx), 0), ow - 1), Math.min(Math.max((int) (y / sy), 0), oh - 1)));
            return scaled;
        }

        private void initCapeItems() {
            ArrayList<MultiFileItem.Option<MicrosoftService.MinecraftProfileResponseCape>> options = new ArrayList<>();
            List<MicrosoftService.MinecraftProfileResponseCape> capes = profile.getCapes();

            options.add(new MultiFileItem.Option<>(i18n("account.cape.none"), null));

            for (MicrosoftService.MinecraftProfileResponseCape cape : capes) {
                String key = "account.cape.name." + getCapeId(cape.alias());
                String displayName;

                if (I18n.hasKey(key)) {
                    displayName = i18n(key);
                } else {
                    LOG.warning("Cannot find key " + key + " in resource bundle");
                    displayName = cape.alias();
                }

                MultiFileItem.Option<MicrosoftService.MinecraftProfileResponseCape> option = new MultiFileItem.Option<>(displayName, cape);
                options.add(option);
                if (Objects.equals(cape.state(), "ACTIVE")) {
                    currentCape = cape;
                }
            }

            capeItem.loadChildren(options);
            capeItem.setSelectedData(currentCape);
        }

        private Task<?> updateCapePreview() {
            CompletableFuture<Image> imageFuture = new CompletableFuture<>();

            String imagePath = "/assets/img/cape/" + getCapeId(capeItem.getSelectedData().alias()) + ".png";
            URL imageURL = MicrosoftAccountChangeCapeDialog.class.getResource(imagePath);

            if (imageURL != null) {
                Image builtinImage = FXUtils.newBuiltinImage(imagePath);
                imageFuture.complete(builtinImage);
            } else {
                capePreviewSpinner.showSpinner();
                Task<Image> remoteImageTask = FXUtils.getRemoteImageTask(capeItem.getSelectedData().url(), 0, 0, false, false);
                remoteImageTask.whenComplete(Schedulers.javafx(), (loadedImage, exception) -> {
                    if (exception != null) {
                        LOG.warning("Cannot download cape image " + capeItem.getSelectedData().url(), exception);
                        imageFuture.completeExceptionally(exception);
                    } else {
                        imageFuture.complete(loadedImage);
                    }
                }).start();
            }

            return Task.fromCompletableFuture(imageFuture).thenRunAsync(Schedulers.javafx(), () -> {
                previewCapeImage = scaleImageNearestNeighbor(imageFuture.getNow(null), 10, 10);
                capePreview.setImage(previewCapeImage);
            }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception != null) {
                    Logger.LOG.error("Failed to load cape preview", exception);
                    capePreviewSpinner.hideSpinner();
                }
            });
        }

        private String getCapeId(String alias) {
            return alias.toLowerCase(Locale.ROOT).replace(" ", "_").replace("'", "_").replace("-", "_");
        }
    }

}
