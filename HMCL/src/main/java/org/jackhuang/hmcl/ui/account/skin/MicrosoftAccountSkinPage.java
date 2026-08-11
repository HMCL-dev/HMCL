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

import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
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
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.StringUtils;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class MicrosoftAccountSkinPage extends SkinPageBase<MicrosoftAccount> {
    private final ToggleGroup modelToggleGroup;
    private final ToggleGroup capeToggleGroup = new ToggleGroup();
    private List<MicrosoftService.MinecraftProfileResponseCape> capes;

    public MicrosoftAccountSkinPage(MicrosoftAccount account) {
        super(account);

        var pair = createModelSelectBox();
        modelToggleGroup = pair.getValue();

        Task.supplyAsync(() -> {
            var textures = YggdrasilService.getTextures(account.getService().getCompleteGameProfile(account.getProfileID()).orElseThrow()).orElseThrow();
            var skin = textures.get(TextureType.SKIN);

            Image skinImg;
            if (skin == null || StringUtils.isBlank(skin.url()))
                skinImg = TexturesLoader.getDefaultSkin(account.getProfileID()).image();
            else skinImg = TexturesLoader.loadTexture(skin).image();

            boolean isSlim;
            if (skin != null && skin.metadata() != null) {
                isSlim = skin.metadata().get("model").equals(SkinModel.SLIM.modelName);
            } else isSlim = TexturesLoader.getDefaultModel(account.getProfileID()).isSlim();

            Image capeImg = null;
            if (textures.get(TextureType.CAPE) != null && !StringUtils.isBlank(textures.get(TextureType.CAPE).url())) {
                capeImg = TexturesLoader.loadTexture(textures.get(TextureType.CAPE)).image();
            }

            return new Skin(isSlim ? SkinModel.SLIM : SkinModel.WIDE, skinImg, capeImg);
        }).whenComplete(Schedulers.javafx(), (r, e) -> {
            if (e != null) Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
            skinObjectProperty.set(r);
            modelToggleGroup.selectToggle(r.model() == SkinModel.WIDE ? modelToggleGroup.getToggles().get(1) : modelToggleGroup.getToggles().get(0));
        }).start();

        var capeList = new HBox(5);


        Task.supplyAsync(() -> {
                    var response = account.getMinecraftProfileResponse();
                    capes = response.orElseThrow().getCapes();
                    return capes;
                }).whenComplete(Schedulers.javafx(), (r, e) -> {
                    if (e != null) Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
                    var items = r.stream().map(it -> new CapeItem(it, capeToggleGroup))
                            .toList();
                    capeList.getChildren().setAll(items);

                    skinManagePane.leftRegion.getChildren().add(capeList);
                })
                .start();

        var skinButton = FXUtils.newRaisedButton(i18n("account.skin.manage.select.skin"));
        skinButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n("account.skin.manage.select.skin"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("account.skin.file"), "*.png"));
            Path selectedFile = Controllers.showOpenDialog(chooser);
            if (selectedFile == null) return;
            try {
                setSkinTexture(FXUtils.loadImage(selectedFile));
            } catch (Exception e) {
                LOG.warning("Failed to parse cape image", e);
                Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
            }
        });

        skinManagePane.leftRegion.getChildren().addAll(skinButton, pair.getKey());
    }

    @Override
    protected void onSaveChanges() {
        super.onSaveChanges();

        try {
            var current = skinObjectProperty.get();
            account.uploadSkin(current.model().isSlim(), FXUtils.getInputStreamFromImage(current.skin(), "png"));
        } catch (Exception e) {
            LOG.warning("Failed to upload skin", e);
            Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
        }
    }

    private static class CapeItem extends VBox {
        public CapeItem(MicrosoftService.MinecraftProfileResponseCape cape, ToggleGroup toggleGroup) {
            super(5);

            SpinnerPane spinnerPane = new SpinnerPane();
            FXUtils.setLimitHeight(spinnerPane, 64);
            FXUtils.setLimitWidth(spinnerPane, 32);
            ImageView imageView = new ImageView();
            spinnerPane.setContent(imageView);

            String imagePath = "/assets/img/cape/" + getCapeId(cape.alias()) + ".png";
            URL imageURL = MicrosoftAccountSkinPage.class.getResource(imagePath);

            if (imageURL != null) {
                imageView.setImage(FXUtils.newBuiltinImage(imagePath));
            } else {
                spinnerPane.showSpinner();
                FXUtils.getRemoteImageTask(cape.url(), 0, 0, false, false).thenAcceptAsync(Schedulers.javafx(), result -> {
                    spinnerPane.hideSpinner();
                    imageView.setImage(result);
                }).start();
            }

            getChildren().add(spinnerPane);
        }
    }

    private static String getCapeId(String alias) {
        return alias.toLowerCase(Locale.ROOT).replace(" ", "_").replace("'", "_").replace("-", "_");
    }
}
