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

import com.jfoenix.controls.JFXComboBox;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineSkinConfig;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.game.skin.Skin;
import org.jackhuang.hmcl.game.skin.SkinModel;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.FileSelector;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class OfflineAccountSkinPage extends SkinPageBase<OfflineAccount> {
    private final MultiFileItem<OfflineSkinConfig.Type> skinTypeItem = new MultiFileItem<>();
    private final JFXComboBox<SkinModel> modelCombobox = new JFXComboBox<>();
    private final FileSelector skinSelector = new FileSelector();
    private final FileSelector capeSelector = new FileSelector();

    public OfflineAccountSkinPage(OfflineAccount account) {
        super(account);

        skinTypeItem.loadChildren(Arrays.asList(new MultiFileItem.Option<>(i18n("message.default"), OfflineSkinConfig.Type.DEFAULT), new MultiFileItem.Option<>(i18n("account.skin.type.steve"), OfflineSkinConfig.Type.STEVE), new MultiFileItem.Option<>(i18n("account.skin.type.alex"), OfflineSkinConfig.Type.ALEX), new MultiFileItem.Option<>(i18n("account.skin.type.local_file"), OfflineSkinConfig.Type.LOCAL_FILE)));

        modelCombobox.setConverter(FXUtils.stringConverter(model -> i18n("account.skin.model." + model.modelName)));
        modelCombobox.getItems().setAll(SkinModel.WIDE, SkinModel.SLIM);

        OfflineSkinConfig config = account.getSkin();
        if (config == null) {
            skinTypeItem.setSelectedData(OfflineSkinConfig.Type.DEFAULT);
            modelCombobox.setValue(SkinModel.WIDE);
        } else {
            skinTypeItem.setSelectedData(config.type());
            modelCombobox.setValue(config.textureModel() != null ? config.textureModel() : SkinModel.WIDE);
            skinSelector.setValue(config.localSkinPath());
            capeSelector.setValue(config.localCapePath());
        }

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(16);
        grid.setVgap(10);

        ChangeListener<OfflineSkinConfig.Type> listener = (obs, oldVal, newVal) -> {
            grid.getChildren().clear();
            if (newVal == OfflineSkinConfig.Type.LOCAL_FILE) {
                grid.addRow(0, new Label(i18n("account.skin.model")), modelCombobox);
                grid.addRow(1, new Label(i18n("account.skin")), skinSelector);
                grid.addRow(2, new Label(i18n("account.cape")), capeSelector);
            }
        };

        listener.changed(null, null, skinTypeItem.getSelectedData());
        skinTypeItem.selectedDataProperty().addListener(listener);

        super.skinManagePane.leftRegion.getChildren().addAll(skinTypeItem, grid);

        InvalidationListener invalidationListener = (e) -> loadSkinPreview();

        skinTypeItem.selectedDataProperty().addListener(invalidationListener);
        modelCombobox.valueProperty().addListener(invalidationListener);
        skinSelector.valueProperty().addListener(invalidationListener);
        capeSelector.valueProperty().addListener(invalidationListener);

        loadSkinPreview();
    }

    private OfflineSkinConfig getConfig() {
        OfflineSkinConfig.Type type = skinTypeItem.getSelectedData();
        if (type == null) type = OfflineSkinConfig.Type.DEFAULT;
        SkinModel model = modelCombobox.getValue();

        var textureModel = switch (type) {
            case ALEX -> SkinModel.SLIM;
            case STEVE -> SkinModel.WIDE;
            case DEFAULT -> TexturesLoader.getDefaultModel(account.getProfileID());
            default -> model;
        };

        return new OfflineSkinConfig(type, textureModel, skinSelector.getValue(), capeSelector.getValue());
    }

    private void loadSkinPreview() {
        OfflineSkinConfig config = getConfig();
        config.load().whenComplete(Schedulers.javafx(), (loadedSkin, throwable) -> {
            if (throwable != null) {
                LOG.warning("Failed to load skin for preview", throwable);
                Controllers.showToast(i18n("message.failed"));
                return;
            }

            UUID uuid = account.getProfileID();
            SkinModel model = SkinModel.WIDE;
            Image skinTex = null;
            Image capeTex = null;

            if (loadedSkin != null) {
                model = loadedSkin.model();
                skinTex = loadedSkin.skin() != null ? loadedSkin.skin().image() : null;
                capeTex = loadedSkin.cape() != null ? loadedSkin.cape().image() : null;
            }

            if (skinTex == null) {
                skinTex = TexturesLoader.getDefaultSkin(uuid).image();
                model = TexturesLoader.getDefaultModel(uuid);
            }

            skinObjectProperty.set(new Skin(model, skinTex, capeTex));
        }).start();
    }

    @Override
    protected void onDrag(Path skin) {
        skinTypeItem.setSelectedData(OfflineSkinConfig.Type.LOCAL_FILE);
        skinSelector.setValue(FileUtils.getAbsolutePath(skin));
    }

    @Override
    protected void onSaveChanges() {
        super.onSaveChanges();
        account.setSkin(getConfig());
    }
}
