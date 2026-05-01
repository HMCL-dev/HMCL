package org.jackhuang.hmcl.ui.account.skin;

import com.jfoenix.controls.JFXComboBox;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineSkinConfig;
import org.jackhuang.hmcl.game.skin.Skin;
import org.jackhuang.hmcl.game.skin.TextureModel;
import org.jackhuang.hmcl.game.skin.TextureObject;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.FileSelector;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;

import java.util.Arrays;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class OfflineAccountSkinPage extends SkinPageBase<OfflineAccount> {
    private ReadOnlyObjectWrapper<Skin> skinProperty;

    private final MultiFileItem<OfflineSkinConfig.Type> skinItem = new MultiFileItem<>();
    private final JFXComboBox<TextureModel> modelCombobox = new JFXComboBox<>();
    private final FileSelector skinSelector = new FileSelector();
    private final FileSelector capeSelector = new FileSelector();

    public OfflineAccountSkinPage(OfflineAccount account) {
        super(account, null);

        skinItem.loadChildren(Arrays.asList(
                new MultiFileItem.Option<>(i18n("message.default"), OfflineSkinConfig.Type.DEFAULT),
                new MultiFileItem.Option<>(i18n("account.skin.type.steve"), OfflineSkinConfig.Type.STEVE),
                new MultiFileItem.Option<>(i18n("account.skin.type.alex"), OfflineSkinConfig.Type.ALEX),
                new MultiFileItem.Option<>(i18n("account.skin.type.local_file"), OfflineSkinConfig.Type.LOCAL_FILE)
        ));

        modelCombobox.setConverter(FXUtils.stringConverter(model -> i18n("account.skin.model." + model.modelName)));
        modelCombobox.getItems().setAll(TextureModel.WIDE, TextureModel.SLIM);

        OfflineSkinConfig config = account.getSkin();
        if (config == null) {
            skinItem.setSelectedData(OfflineSkinConfig.Type.DEFAULT);
            modelCombobox.setValue(TextureModel.WIDE);
        } else {
            skinItem.setSelectedData(config.type());
            modelCombobox.setValue(config.textureModel() != null ? config.textureModel() : TextureModel.WIDE);
            skinSelector.setValue(config.localSkinPath());
            capeSelector.setValue(config.localCapePath());
        }

        StackPane contentPane = super.skinManage.leftRegion;

        VBox settingsBox = new VBox(20);
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(16);
        grid.setVgap(10);

        skinItem.selectedDataProperty().addListener((obs, oldVal, newVal) -> {
            grid.getChildren().clear();
            if (newVal == OfflineSkinConfig.Type.LOCAL_FILE) {
                grid.addRow(0, new Label(i18n("account.skin.model")), modelCombobox);
                grid.addRow(1, new Label(i18n("account.skin")), skinSelector);
                grid.addRow(2, new Label(i18n("account.cape")), capeSelector);
            }
        });

        settingsBox.getChildren().addAll(skinItem, grid);
        contentPane.getChildren().setAll(settingsBox);
        StackPane.setAlignment(settingsBox, Pos.CENTER);
        settingsBox.setAlignment(Pos.CENTER);

        FXUtils.observeWeak(this::loadSkinPreview, skinItem.selectedDataProperty(), modelCombobox.valueProperty(),
                skinSelector.valueProperty(), capeSelector.valueProperty());

        loadSkinPreview();
    }

    private void loadSkinPreview() {
        OfflineSkinConfig config = getConfig();
        config.load().whenComplete(Schedulers.javafx(), (loadedSkin, throwable) -> {
            if (throwable == null && loadedSkin != null) {
                TextureObject skinTex = loadedSkin.skin() != null
                        ? new TextureObject(loadedSkin.skin().image(), "") : null;
                TextureObject capeTex = loadedSkin.cape() != null
                        ? new TextureObject(loadedSkin.cape().image(), "") : null;

                if (skinTex != null || capeTex != null) {
                    skinProperty.set(new Skin(loadedSkin.model(), skinTex, capeTex));
                }
            }
        }).start();
    }

    private OfflineSkinConfig getConfig() {
        OfflineSkinConfig.Type type = skinItem.getSelectedData();
        if (type == OfflineSkinConfig.Type.LOCAL_FILE) {
            return new OfflineSkinConfig(type, modelCombobox.getValue(), skinSelector.getValue(), capeSelector.getValue());
        }
        return new OfflineSkinConfig(type, null, null, null);
    }

    @Override
    protected ReadOnlyObjectProperty<Skin> skinObjectProperty() {
        if (skinProperty == null) skinProperty = new ReadOnlyObjectWrapper<>();
        return skinProperty.getReadOnlyProperty();
    }

    @Override
    protected Task<Void> setSkin(Skin skin) {
        return Task.supplyAsync(() -> {
            account.setSkin(getConfig());
            return null;
        });
    }
}
