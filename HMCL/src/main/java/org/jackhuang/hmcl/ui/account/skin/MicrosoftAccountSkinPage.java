/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.account.skin;

import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXRippler;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.game.skin.Skin;
import org.jackhuang.hmcl.game.skin.SkinModel;
import org.jackhuang.hmcl.game.skin.TextureType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class MicrosoftAccountSkinPage extends SkinPageBase<MicrosoftAccount> {
    private static final double CAPE_SCALE = 6;
    private static final double CAPE_PREVIEW_WIDTH = 10 * CAPE_SCALE;
    private static final double CAPE_PREVIEW_HEIGHT = 17 * CAPE_SCALE;
    private static final MicrosoftService.MinecraftProfileResponseCape NONE_CAPE = new MicrosoftService.MinecraftProfileResponseCape("none", null, null, "none");

    private final ToggleGroup modelToggleGroup;
    private final ToggleGroup capeToggleGroup = new ToggleGroup();
    private MicrosoftService.MinecraftProfileResponseCape currentCape;

    public MicrosoftAccountSkinPage(MicrosoftAccount account) {
        super(account);

        getStyleClass().add("microsoft-skin-manage");

        var pair = createModelSelectBox();
        modelToggleGroup = pair.getValue();

        Task.supplyAsync(() -> {
            var profile = account.getService().getCompleteGameProfile(account.getProfileID()).orElseThrow();
            var textures = YggdrasilService.getTextures(profile).orElseThrow();

            var skinTex = textures.get(TextureType.SKIN);
            Image skinImg = (skinTex != null && StringUtils.isNotBlank(skinTex.url()))
                    ? TexturesLoader.loadTexture(skinTex).image()
                    : TexturesLoader.getDefaultSkin(account.getProfileID()).image();

            boolean isSlim = (skinTex != null && skinTex.metadata() != null)
                    ? SkinModel.SLIM.modelName.equals(skinTex.metadata().get("model"))
                    : TexturesLoader.getDefaultModel(account.getProfileID()).isSlim();

            var capeTex = textures.get(TextureType.CAPE);
            Image capeImg = (capeTex != null && StringUtils.isNotBlank(capeTex.url()))
                    ? TexturesLoader.loadTexture(capeTex).image()
                    : null;

            return new Skin(isSlim ? SkinModel.SLIM : SkinModel.WIDE, skinImg, capeImg);
        }).whenComplete(Schedulers.javafx(), (skin, e) -> {
            if (e != null) {
                Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
                return;
            }
            skinObjectProperty.set(skin);
            if (skin != null) {
                modelToggleGroup.selectToggle(skin.model() == SkinModel.WIDE ? modelToggleGroup.getToggles().get(1) : modelToggleGroup.getToggles().get(0));
            }
        }).start();

        HBox capeList = new HBox(0);
        capeList.setAlignment(Pos.CENTER);

        capeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            Skin currentSkin = skinObjectProperty.get();
            if (currentSkin == null) return;

            if (newVal.getUserData() instanceof MicrosoftService.MinecraftProfileResponseCape cape && !"none".equals(cape.id())) {
                getCapeImageTask(cape)
                    .thenAcceptAsync(Schedulers.javafx(), img -> skinObjectProperty.set(new Skin(currentSkin.model(), currentSkin.skin(), img)))
                    .start();
            } else {
                skinObjectProperty.set(new Skin(currentSkin.model(), currentSkin.skin(), null));
            }
        });

        ScrollPane capeScrollPane = new ScrollPane(capeList);
        capeScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        capeScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        capeScrollPane.setFitToHeight(true);
        capeScrollPane.setPannable(true);
        FXUtils.smoothScrolling(capeScrollPane);

        StackPane scrollWrapper = new StackPane(capeScrollPane);
        scrollWrapper.getStyleClass().add("scroll-wrapper");
        scrollWrapper.setMaxWidth(264);

        SpinnerPane capeListSpinnerPane = new SpinnerPane();
        capeListSpinnerPane.setPrefWidth(264);
        capeListSpinnerPane.setMaxWidth(264);
        capeListSpinnerPane.setPrefHeight(145);
        capeListSpinnerPane.setContent(scrollWrapper);
        capeListSpinnerPane.showSpinner();


        Task.supplyAsync(() -> account.getMinecraftProfileResponse().orElseThrow().getCapes())
                .whenComplete(Schedulers.javafx(), (capes, e) -> {
                    capeListSpinnerPane.hideSpinner();
                    if (e != null) {
                        Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
                        return;
                    }
                    populateCapeList(capes, capeList);
                }).start();

        Button skinButton = FXUtils.newRaisedButton(i18n("account.skin.manage.select.skin"));
        skinButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n("account.skin.manage.select.skin"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("account.skin.file"), "*.png"));
            Path selectedFile = Controllers.showOpenDialog(chooser);
            if (selectedFile != null) {
                try {
                    setSkinTexture(FXUtils.loadImage(selectedFile));
                } catch (Exception e) {
                    LOG.warning("Failed to parse skin image", e);
                    Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
                }
            }
        });

        skinManagePane.leftRegion.getChildren().addAll(skinButton, pair.getKey(), capeListSpinnerPane);
    }

    private void populateCapeList(List<MicrosoftService.MinecraftProfileResponseCape> capes, HBox capeList) {
        capes.add(0, NONE_CAPE);

        currentCape = capes.stream().filter(c -> "ACTIVE".equals(c.state())).findFirst().orElse(null);

        if (currentCape == null) currentCape = NONE_CAPE;

        List<RipplerContainer> items = new ArrayList<>();
        for (int i = 0; i < capes.size(); i++) {
            MicrosoftService.MinecraftProfileResponseCape cape = capes.get(i);
            CapeItem item = new CapeItem(cape, capeToggleGroup, i == capes.size() - 1);

            if (cape == currentCape) {
                capeToggleGroup.selectToggle(item.getRadioButton());
            }

            var ripplerContainor = new RipplerContainer(item);
            ripplerContainor.setPosition(JFXRippler.RipplerPos.FRONT);
            FXUtils.onClicked(ripplerContainor, () -> capeToggleGroup.selectToggle(item.getRadioButton()));
            items.add(ripplerContainor);
        }
        capeList.getChildren().addAll(items);
    }

    @Override
    protected void onSaveChanges() {
        super.onSaveChanges();
        try {
            Skin current = skinObjectProperty.get();
            if (current != null) {
                account.uploadSkin(current.model().isSlim(), FXUtils.getInputStreamFromImage(current.skin(), "png"));
            }

            Toggle selectedToggle = capeToggleGroup.getSelectedToggle();
            if (selectedToggle != null) {
                Object data = selectedToggle.getUserData();
                if (data instanceof MicrosoftService.MinecraftProfileResponseCape cape && !"none".equals(cape.id())) {
                    if (currentCape == null || !cape.id().equals(currentCape.id())) {
                        account.updateCape(cape.id());
                    }
                } else {
                    account.updateCape(null);
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to upload skin", e);
            Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
        }
    }

    private static class CapeItem extends VBox {
        private final JFXRadioButton radioButton;

        public CapeItem(MicrosoftService.MinecraftProfileResponseCape cape, ToggleGroup toggleGroup, boolean isLast) {
            super(4);

            getStyleClass().add("cape-item");
            setAlignment(Pos.CENTER);
            setMaxHeight(Double.MAX_VALUE);

            SpinnerPane spinnerPane = new SpinnerPane();
            spinnerPane.setPrefSize(CAPE_PREVIEW_WIDTH, CAPE_PREVIEW_HEIGHT);
            spinnerPane.setMaxSize(CAPE_PREVIEW_WIDTH, CAPE_PREVIEW_HEIGHT);

            ImageView capePreview = new ImageView();
            if (!"none".equals(cape.id())) {
                capePreview.setViewport(new Rectangle2D(CAPE_SCALE, 0, 10 * CAPE_SCALE, 17 * CAPE_SCALE));
            }
            capePreview.setFitWidth(CAPE_PREVIEW_WIDTH);
            capePreview.setFitHeight(CAPE_PREVIEW_HEIGHT);

            spinnerPane.setContent(capePreview);
            spinnerPane.showSpinner();

            getCapeImageTask(cape).thenAcceptAsync(Schedulers.javafx(), result -> {
                capePreview.setImage(FXUtils.scaleImageNearestNeighbor(result, CAPE_SCALE, CAPE_SCALE));
                spinnerPane.hideSpinner();
            }).start();

            getChildren().add(spinnerPane);

            String key = "account.cape.name." + toCapeId(cape.alias());
            String displayName = I18n.hasKey(key) ? i18n(key) : cape.alias();

            radioButton = new JFXRadioButton(displayName);
            radioButton.setToggleGroup(toggleGroup);
            radioButton.setUserData(cape);
            radioButton.setAlignment(Pos.CENTER);
            radioButton.setMaxWidth(Double.MAX_VALUE);
            radioButton.setMaxHeight(Double.MAX_VALUE);

            getChildren().add(radioButton);
        }

        public RadioButton getRadioButton() {
            return radioButton;
        }
    }

    private static String toCapeId(String alias) {
        return alias.toLowerCase(Locale.ROOT).replace(" ", "_").replace("'", "_").replace("-", "_");
    }

    private static Task<Image> getCapeImageTask(MicrosoftService.MinecraftProfileResponseCape cape) {
        String builtinImagePath = "/assets/img/capes/" + toCapeId(cape.alias()) + ".png";
        URL builtinImageURL = MicrosoftAccountSkinPage.class.getResource(builtinImagePath);

        if (builtinImageURL != null) {
            return Task.completed(FXUtils.newBuiltinImage(builtinImagePath));
        } else {
            return FXUtils.getRemoteImageTask(cape.url(), 0, 0, false, false);
        }
    }
}
