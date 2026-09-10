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

import javafx.beans.binding.ObjectBinding;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.game.skin.Skin;
import org.jackhuang.hmcl.game.skin.SkinModel;
import org.jackhuang.hmcl.game.skin.TextureType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.HintPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.StringUtils;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AuthlibInjectorAccountSkinPage extends SkinPageBase<AuthlibInjectorAccount> {
    private final ToggleGroup toggleGroup;

    public AuthlibInjectorAccountSkinPage(AuthlibInjectorAccount account) {
        super(account);
        var pair = createModelSelectBox();
        toggleGroup = pair.getValue();

        Task.supplyAsync(() -> {
            var textures = YggdrasilService.getTextures(account.getYggdrasilService().getCompleteGameProfile(account.getProfileID()).orElseThrow()).orElseThrow();
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
            toggleGroup.selectToggle(r.model() == SkinModel.WIDE ? toggleGroup.getToggles().get(1) : toggleGroup.getToggles().get(0));
        }).start();

        var uploadableTextures = getUploadableTextures();
        if (uploadableTextures.size() != 2) {
            var homePage = Optional.of(account.getServer().getLinks().get("homepage")).orElse(account.getServer().getUrl());
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            if (uploadableTextures.size() == 1)
                hintPane.setSegment(i18n("account.skin.yggdrasil.unsupported." + uploadableTextures.iterator().next().name().toLowerCase(Locale.ROOT), homePage));
            else hintPane.setSegment(i18n("account.skin.yggdrasil.unsupported.skin_and_cape", homePage));
            skinManagePane.leftRegion.getChildren().addAll(hintPane);
        }

        if (uploadableTextures.contains(TextureType.SKIN)) {
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

        if (uploadableTextures.contains(TextureType.CAPE)) {
            var capeButton = FXUtils.newRaisedButton(i18n("account.skin.manage.select.cape"));
            capeButton.setOnAction(event -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle(i18n("account.skin.manage.select.cape"));
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("account.cape.file"), "*.png"));
                Path selectedFile = Controllers.showOpenDialog(chooser);
                if (selectedFile == null) return;
                try {
                    setCapeTexture(FXUtils.loadImage(selectedFile));
                } catch (Exception e) {
                    LOG.warning("Failed to parse cape image", e);
                    Controllers.dialog(StringUtils.getStackTrace(e), i18n("message.error"));
                }
            });

            skinManagePane.leftRegion.getChildren().addAll(capeButton);
        }
    }

    @Override
    protected void onDrag(Path skin) {
        if (!getUploadableTextures().contains(TextureType.SKIN)) return;

        super.onDrag(skin);
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

    public Set<TextureType> getUploadableTextures() {
        ObjectBinding<Optional<CompleteGameProfile>> profile = account.getYggdrasilService().getProfileRepository().binding(account.getProfileID());

        return profile.get().map(AuthlibInjectorAccount::getUploadableTextures).orElse(emptySet());
    }
}
