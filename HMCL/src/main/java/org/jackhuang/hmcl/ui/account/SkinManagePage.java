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
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.TextureModel;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.skin.SkinCanvas;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniRunning;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniWavingArms;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.skin.InvalidSkinException;
import org.jackhuang.hmcl.util.skin.NormalizedSkin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class SkinManagePage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("account.skin.manage"), 200));
    private final ObjectProperty<Account> account = new SimpleObjectProperty<>();

    private SkinCanvas canvas;
    private InvalidationListener skinBinding;

    // Offline UI controls
    private MultiFileItem<org.jackhuang.hmcl.auth.offline.Skin.Type> skinItem;
    private JFXTextField cslApiField;
    private JFXComboBox<TextureModel> modelCombobox;
    private FileSelector skinSelector;
    private FileSelector capeSelector;

    public SkinManagePage() {
    }

    public void loadAccount(Account account) {
        this.account.set(account);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void onPageShown() {
        if (canvas != null) {
            canvas.getAnimationPlayer().start();
        }
    }

    @Override
    public void onPageHidden() {
        if (canvas != null) {
            canvas.getAnimationPlayer().stop();
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SkinManagePageSkin(this);
    }

    private org.jackhuang.hmcl.auth.offline.Skin getOfflineSkin() {
        org.jackhuang.hmcl.auth.offline.Skin.Type type = skinItem.getSelectedData();
        if (type == org.jackhuang.hmcl.auth.offline.Skin.Type.LOCAL_FILE) {
            return new org.jackhuang.hmcl.auth.offline.Skin(type, cslApiField.getText(), modelCombobox.getValue(), skinSelector.getValue(), capeSelector.getValue());
        } else {
            String cslApi = type == org.jackhuang.hmcl.auth.offline.Skin.Type.CUSTOM_SKIN_LOADER_API ? cslApiField.getText() : null;
            return new org.jackhuang.hmcl.auth.offline.Skin(type, cslApi, null, null, null);
        }
    }

    private static class SkinManagePageSkin extends DecoratorAnimatedPageSkin<SkinManagePage> {

        SkinManagePageSkin(SkinManagePage skinnable) {
            super(skinnable);

            skinnable.canvas = new SkinCanvas(TexturesLoader.getDefaultSkinImage(), 400, 400, true);
            SkinCanvas canvas = skinnable.canvas;
            canvas.getAnimationPlayer().addSkinAnimation(
                    new SkinAniWavingArms(100, 2000, 7.5, canvas),
                    new SkinAniRunning(100, 100, 30, canvas)
            );
            canvas.enableRotation(.5);

            // Rebuild UI when account changes
            skinnable.account.addListener((obs, oldAccount, newAccount) -> {
                if (newAccount != null) {
                    rebuildUI(skinnable, canvas, newAccount);
                }
            });

            // Build initial UI if account is already set
            Account initialAccount = skinnable.account.get();
            if (initialAccount != null) {
                rebuildUI(skinnable, canvas, initialAccount);
            } else {
                StackPane canvasPane = new StackPane(canvas);
                canvasPane.setAlignment(Pos.CENTER);
                setCenter(canvasPane);
            }
        }

        private void rebuildUI(SkinManagePage skinnable, SkinCanvas canvas, Account account) {
            if (account instanceof OfflineAccount) {
                buildOfflineUI(skinnable, canvas, (OfflineAccount) account);
            } else {
                buildOnlineUI(skinnable, canvas, account);
            }
        }

        private void buildOfflineUI(SkinManagePage skinnable, SkinCanvas canvas, OfflineAccount account) {
            skinnable.skinItem = new MultiFileItem<>();
            skinnable.cslApiField = new JFXTextField();
            skinnable.modelCombobox = new JFXComboBox<>();
            skinnable.skinSelector = new FileSelector();
            skinnable.capeSelector = new FileSelector();

            MultiFileItem<org.jackhuang.hmcl.auth.offline.Skin.Type> skinItem = skinnable.skinItem;
            JFXTextField cslApiField = skinnable.cslApiField;
            JFXComboBox<TextureModel> modelCombobox = skinnable.modelCombobox;
            FileSelector skinSelector = skinnable.skinSelector;
            FileSelector capeSelector = skinnable.capeSelector;

            // Drag and drop on canvas
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
                        skinItem.setSelectedData(org.jackhuang.hmcl.auth.offline.Skin.Type.LOCAL_FILE);
                    });
                }
            });

            // CSL API field
            cslApiField.setPromptText(i18n("account.skin.type.csl_api.location.hint"));
            cslApiField.setValidators(new URLValidator());
            FXUtils.setValidateWhileTextChanged(cslApiField, true);

            // Skin type options
            skinItem.loadChildren(Arrays.asList(
                    new MultiFileItem.Option<>(i18n("message.default"), org.jackhuang.hmcl.auth.offline.Skin.Type.DEFAULT),
                    new MultiFileItem.Option<>(i18n("account.skin.type.steve"), org.jackhuang.hmcl.auth.offline.Skin.Type.STEVE),
                    new MultiFileItem.Option<>(i18n("account.skin.type.alex"), org.jackhuang.hmcl.auth.offline.Skin.Type.ALEX),
                    new MultiFileItem.Option<>(i18n("account.skin.type.local_file"), org.jackhuang.hmcl.auth.offline.Skin.Type.LOCAL_FILE),
                    new MultiFileItem.Option<>(i18n("account.skin.type.little_skin"), org.jackhuang.hmcl.auth.offline.Skin.Type.LITTLE_SKIN),
                    new MultiFileItem.Option<>(i18n("account.skin.type.csl_api"), org.jackhuang.hmcl.auth.offline.Skin.Type.CUSTOM_SKIN_LOADER_API)
            ));

            // Model combobox
            modelCombobox.setConverter(stringConverter(model -> i18n("account.skin.model." + model.modelName)));
            modelCombobox.getItems().setAll(TextureModel.WIDE, TextureModel.SLIM);

            // Load current skin settings
            if (account.getSkin() == null) {
                skinItem.setSelectedData(org.jackhuang.hmcl.auth.offline.Skin.Type.DEFAULT);
                modelCombobox.setValue(TextureModel.WIDE);
            } else {
                skinItem.setSelectedData(account.getSkin().getType());
                cslApiField.setText(account.getSkin().getCslApi());
                modelCombobox.setValue(account.getSkin().getTextureModel());
                skinSelector.setValue(account.getSkin().getLocalSkinPath());
                capeSelector.setValue(account.getSkin().getLocalCapePath());
            }

            // Reactive skin preview
            skinnable.skinBinding = FXUtils.observeWeak(() -> {
                skinnable.getOfflineSkin().load(account.getUsername())
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception != null) {
                                LOG.warning("Failed to load skin", exception);
                            } else {
                                UUID uuid = account.getUUID();
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
            }, skinItem.selectedDataProperty(), cslApiField.textProperty(), modelCombobox.valueProperty(), skinSelector.valueProperty(), capeSelector.valueProperty());

            // Dynamic sub-options pane
            StackPane skinOptionPane = new StackPane();
            skinOptionPane.setMaxWidth(300);

            // Hint pane shown above canvas when LITTLE_SKIN is selected
            HintPane littleSkinHint = new HintPane(MessageDialogPane.MessageType.INFO);
            littleSkinHint.setText(i18n("account.skin.type.little_skin.hint"));
            littleSkinHint.setMaxWidth(Double.MAX_VALUE);
            littleSkinHint.setMaxHeight(Region.USE_PREF_SIZE);
            littleSkinHint.setMinHeight(Region.USE_PREF_SIZE);
            littleSkinHint.setVisible(false);
            littleSkinHint.setManaged(false);

            FXUtils.onChangeAndOperate(skinItem.selectedDataProperty(), selectedData -> {
                GridPane gridPane = new GridPane();
                gridPane.setPadding(new Insets(0, 0, 10, 10));
                gridPane.setHgap(16);
                gridPane.setVgap(8);
                gridPane.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());

                boolean showHint = selectedData == org.jackhuang.hmcl.auth.offline.Skin.Type.LITTLE_SKIN;
                littleSkinHint.setVisible(showHint);
                littleSkinHint.setManaged(showHint);

                switch (selectedData) {
                    case DEFAULT:
                    case STEVE:
                    case ALEX:
                    case LITTLE_SKIN:
                        break;
                    case LOCAL_FILE:
                        gridPane.setPadding(new Insets(0, 0, 0, 10));
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

            // === Left sidebar ===
            VBox leftBox = new VBox();
            leftBox.getStyleClass().add("advanced-list-box-content");
            FXUtils.setLimitWidth(leftBox, 200);

            // Account header
            Canvas avatar = new Canvas(32, 32);
            TexturesLoader.bindAvatar(avatar, account);
            Label nameLabel = new Label(account.getCharacter());
            nameLabel.getStyleClass().add("title");
            Label typeLabel = new Label(Accounts.getLocalizedLoginTypeName(Accounts.getAccountFactory(account)));
            typeLabel.getStyleClass().add("subtitle");
            VBox accountInfo = new VBox(nameLabel, typeLabel);
            accountInfo.getStyleClass().add("two-line-list-item");
            HBox header = new HBox(8, avatar, accountInfo);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setPadding(new Insets(10));

            ClassTitle skinTitle = new ClassTitle(i18n("account.skin.source"));

            // Save / Cancel buttons
            JFXButton saveButton = new JFXButton(i18n("account.skin.save"));
            saveButton.getStyleClass().add("jfx-button-raised");
            saveButton.setStyle("-fx-background-color: #5C6BC0; -fx-text-fill: white;");
            saveButton.setOnAction(e -> {
                account.setSkin(skinnable.getOfflineSkin());
                Controllers.showToast(i18n("account.skin.save.success"));
                Controllers.navigate(Controllers.getAccountListPage());
            });
            saveButton.disableProperty().bind(
                    skinItem.selectedDataProperty().isEqualTo(org.jackhuang.hmcl.auth.offline.Skin.Type.CUSTOM_SKIN_LOADER_API)
                            .and(cslApiField.activeValidatorProperty().isNotNull()));

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));

            HBox buttons = new HBox(10, saveButton, cancelButton);
            buttons.setAlignment(Pos.CENTER);
            buttons.setPadding(new Insets(10));

            JFXHyperlink littleSkinLink = new JFXHyperlink(i18n("account.skin.type.little_skin"));
            littleSkinLink.setOnAction(e -> FXUtils.openLink("https://littleskin.cn/"));

            ScrollPane leftScrollPane = new ScrollPane(new VBox(header, skinTitle, skinItem));
            leftScrollPane.setFitToWidth(true);
            VBox.setVgrow(leftScrollPane, Priority.ALWAYS);

            leftBox.getChildren().setAll(leftScrollPane, littleSkinLink, buttons);

            setLeft(leftBox);

            // === Center content ===
            StackPane canvasPane = new StackPane(canvas);
            canvasPane.setAlignment(Pos.CENTER);

            VBox centerBox = new VBox(10);
            centerBox.setAlignment(Pos.CENTER);
            centerBox.setPadding(new Insets(20));
            centerBox.getChildren().setAll(littleSkinHint, canvasPane, skinOptionPane);

            ScrollPane centerScroll = new ScrollPane(centerBox);
            centerScroll.setFitToWidth(true);
            FXUtils.smoothScrolling(centerScroll);

            setCenter(centerScroll);
        }

        private void buildOnlineUI(SkinManagePage skinnable, SkinCanvas canvas, Account account) {
            // Detect whether this account supports skin upload
            boolean canUpload;
            String serverHomepage = null;
            String serverName = null;
            if (account instanceof AuthlibInjectorAccount aiAccount) {
                AuthlibInjectorServer server = aiAccount.getServer();
                serverName = server.getName();
                serverHomepage = server.getLinks().getOrDefault("homepage", server.getUrl());
                java.util.Optional<CompleteGameProfile> profile = aiAccount.getYggdrasilService()
                        .getProfileRepository().binding(aiAccount.getUUID()).get();
                canUpload = profile
                        .map(AuthlibInjectorAccount::getUploadableTextures)
                        .orElse(java.util.Collections.emptySet())
                        .contains(TextureType.SKIN);
            } else {
                canUpload = account.canUploadSkin();
            }

            // Bind current server skin to canvas
            TexturesLoader.skinBinding(account).addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    boolean isSlim = "slim".equals(newVal.getMetadata().get("model"));
                    canvas.updateSkin(newVal.getImage(), isSlim, null);
                }
            });
            // Initial load
            TexturesLoader.LoadedTexture initial = TexturesLoader.getDefaultSkin(account.getUUID());
            boolean isSlim = TexturesLoader.getDefaultModel(account.getUUID()) == TextureModel.SLIM;
            canvas.updateSkin(initial.getImage(), isSlim, null);

            SpinnerPane spinnerPane = new SpinnerPane();

            if (canUpload) {
                // Drag and drop for online upload
                canvas.addEventHandler(DragEvent.DRAG_OVER, e -> {
                    if (e.getDragboard().hasFiles()) {
                        Path file = e.getDragboard().getFiles().get(0).toPath();
                        if (FileUtils.getName(file).endsWith(".png"))
                            e.acceptTransferModes(TransferMode.COPY);
                    }
                });
                canvas.addEventHandler(DragEvent.DRAG_DROPPED, e -> {
                    if (e.isAccepted()) {
                        Path file = e.getDragboard().getFiles().get(0).toPath();
                        Platform.runLater(() -> doUploadSkin(canvas, account, file, spinnerPane));
                    }
                });
            }

            // === Left sidebar ===
            VBox leftBox = new VBox();
            leftBox.getStyleClass().add("advanced-list-box-content");
            FXUtils.setLimitWidth(leftBox, 200);

            Canvas avatar = new Canvas(32, 32);
            TexturesLoader.bindAvatar(avatar, account);
            Label nameLabel = new Label(account.getCharacter());
            nameLabel.getStyleClass().add("title");
            Label typeLabel = new Label(Accounts.getLocalizedLoginTypeName(Accounts.getAccountFactory(account)));
            typeLabel.getStyleClass().add("subtitle");
            VBox accountInfo = new VBox(nameLabel, typeLabel);
            accountInfo.getStyleClass().add("two-line-list-item");
            HBox header = new HBox(8, avatar, accountInfo);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setPadding(new Insets(10));

            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);

            if (canUpload) {
                JFXButton uploadButton = new JFXButton(i18n("account.skin.upload"));
                uploadButton.getStyleClass().add("jfx-button-raised");
                uploadButton.setStyle("-fx-background-color: #5C6BC0; -fx-text-fill: white;");
                uploadButton.setMaxWidth(Double.MAX_VALUE);
                uploadButton.setOnAction(e -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle(i18n("account.skin.upload"));
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("account.skin.file"), "*.png"));
                    Path selectedFile = FileUtils.toPath(chooser.showOpenDialog(Controllers.getStage()));
                    if (selectedFile != null) {
                        doUploadSkin(canvas, account, selectedFile, spinnerPane);
                    }
                });

                JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
                cancelButton.setMaxWidth(Double.MAX_VALUE);
                cancelButton.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));

                leftBox.getChildren().setAll(header, spacer, uploadButton, cancelButton);
            } else {
                // No upload support — guide user to the server's website
                if (serverHomepage != null) {
                    String homepage = serverHomepage;
                    JFXHyperlink serverLink = new JFXHyperlink(serverName != null ? serverName : homepage);
                    serverLink.setOnAction(e -> FXUtils.openLink(homepage));

                    HintPane hint = new HintPane(MessageDialogPane.MessageType.INFO);
                    hint.setText(i18n("account.skin.upload.server_unsupported"));

                    leftBox.getChildren().setAll(header, hint, serverLink, spacer);
                } else {
                    leftBox.getChildren().setAll(header, spacer);
                }

                JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
                cancelButton.setMaxWidth(Double.MAX_VALUE);
                cancelButton.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));
                leftBox.getChildren().add(cancelButton);
            }

            leftBox.setPadding(new Insets(0, 0, 10, 0));
            leftBox.setSpacing(8);

            setLeft(leftBox);

            // === Center content ===
            StackPane canvasPane = new StackPane(canvas);
            canvasPane.setAlignment(Pos.CENTER);
            spinnerPane.setContent(canvasPane);

            setCenter(spinnerPane);
        }

        private void doUploadSkin(SkinCanvas canvas, Account account, Path file, SpinnerPane spinnerPane) {
            spinnerPane.showSpinner();

            Task.runAsync(() -> {
                account.clearCache();
                try {
                    account.logIn();
                } catch (Exception ignored) {
                }
            }).thenRunAsync(() -> {
                Image skinImg;
                try (var input = Files.newInputStream(file)) {
                    skinImg = new Image(input);
                } catch (IOException e) {
                    throw new InvalidSkinException("Failed to read skin image", e);
                }
                if (skinImg.isError()) {
                    throw new InvalidSkinException("Failed to read skin image", skinImg.getException());
                }
                NormalizedSkin skin = new NormalizedSkin(skinImg);
                LOG.info("Uploading skin [" + file + "], model [" + (skin.isSlim() ? "slim" : "classic") + "]");
                account.uploadSkin(skin.isSlim(), file);
            }).thenRunAsync(() -> {
                account.clearCache();
                try {
                    account.logIn();
                } catch (Exception ignored) {
                }
            }).whenComplete(Schedulers.javafx(), e -> {
                spinnerPane.hideSpinner();
                if (e != null) {
                    Controllers.dialog(Accounts.localizeErrorMessage(e), i18n("account.skin.upload.failed"), MessageDialogPane.MessageType.ERROR);
                } else {
                    Controllers.showToast(i18n("account.skin.upload.success"));
                    // Refresh skin preview
                    TexturesLoader.skinBinding(account).addListener((obs, oldVal, newVal) -> {
                        if (newVal != null) {
                            boolean isSlim = "slim".equals(newVal.getMetadata().get("model"));
                            canvas.updateSkin(newVal.getImage(), isSlim, null);
                        }
                    });
                }
            }).start();
        }
    }
}
