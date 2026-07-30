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

import com.jfoenix.controls.JFXPopup;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.game.skin.Skin;
import org.jackhuang.hmcl.game.skin.TextureModel;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.skin.SkinCanvas;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniRunning;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniWavingArms;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.SwingFXUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class SkinPageBase<T extends Account> extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    protected final T account;
    protected final BooleanProperty loadingProperty = new SimpleBooleanProperty(true);
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final TabHeader tab;
    private final TabHeader.Tab<SkinManagePane> manageTab = new TabHeader.Tab<>("manageTab");
    private final TransitionPane transitionPane = new TransitionPane();

    protected final SkinManagePane skinManagePane;

    protected SkinPageBase(T account) {
        this.account = account;

        tab = new TabHeader(transitionPane, manageTab);
        skinManagePane = new SkinManagePane();
        manageTab.setNodeSupplier(() -> skinManagePane);
        tab.select(manageTab);

        BorderPane left = new BorderPane();
        FXUtils.setLimitWidth(left, 200);
        VBox.setVgrow(left, Priority.ALWAYS);
        setLeft(left);

        AdvancedListBox sideBar = new AdvancedListBox().addNavigationDrawerTab(tab, manageTab, i18n("account.skin"), SVG.CHECKROOM);
        left.setTop(sideBar);

        AdvancedListBox toolbar = new AdvancedListBox().addNavigationDrawerItem(i18n("button.save"), SVG.OUTPUT, null, item -> item.setOnAction(e -> onSaveTexture(item.getWidth())));
        BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));
        left.setBottom(toolbar);

        skinManagePane.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                Path file = e.getDragboard().getFiles().get(0).toPath();
                if (FileUtils.getName(file).endsWith(".png")) {
                    e.acceptTransferModes(TransferMode.COPY);
                }
            }
        });
        skinManagePane.setOnDragDropped(e -> {
            if (e.isAccepted()) {
                onDrag(e.getDragboard().getFiles().get(0).toPath());
            }
        });

        setCenter(transitionPane);

        this.state.set(State.fromTitle(i18n("account.skin.manage", account.getProfileName())));
    }

    protected abstract void onDrag(Path skin);

    public void savePng(RenderedImage image, String name) throws IOException {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("button.save_as"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("file"), "*.png"));
        fileChooser.setInitialFileName(name + ".png");
        File target = fileChooser.showSaveDialog(Controllers.getStage());
        if (target == null) return;
        ImageIO.write(image, "png", target);
    }

    public void onSaveTexture(double w) {
        PopupMenu saveTextureList = new PopupMenu();
        JFXPopup saveTexturePopup = new JFXPopup(saveTextureList);

        var capeItem = new IconedMenuItem(SVG.CROP_9_16, i18n("account.skin.manage.save.cape"), () -> {
            var fxCapeImage = Objects.requireNonNull(skinObjectProperty().get().cape()).image();
            var bufferedCapeImage = SwingFXUtils.fromFXImage(fxCapeImage, null);
            try {
                savePng(bufferedCapeImage, "cape");
            } catch (Exception e) {
                LOG.warning("Failed to export skin img", e);
                Controllers.dialog(i18n("message.failed") + "\n" + StringUtils.getStackTrace(e), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
            }
        }, saveTexturePopup);

        saveTextureList.getContent().setAll(new IconedMenuItem(SVG.APPAREL, i18n("account.skin.manage.save.skin"), () -> {
            var fxSkinImage = skinObjectProperty().get().skin().image();
            var bufferedSkinImage = SwingFXUtils.fromFXImage(fxSkinImage, null);
            try {
                savePng(bufferedSkinImage, "skin");
            } catch (Exception e) {
                LOG.warning("Failed to export skin img", e);
                Controllers.dialog(i18n("message.failed") + "\n" + StringUtils.getStackTrace(e), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
            }
        }, saveTexturePopup), capeItem);

        capeItem.setDisable(skinObjectProperty().get().cape() == null);
        saveTexturePopup.show(this, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT, w, -10);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    protected abstract ReadOnlyObjectProperty<Skin> skinObjectProperty();

    protected final class SkinManagePane extends HBox {
        protected VBox leftRegion = new VBox(20);
        private final BorderPane rightRegion = new BorderPane();

        private SkinManagePane() {
            setSpacing(10);
            setPadding(new Insets(10, 10, 10, 10));

            leftRegion.getStyleClass().add("card-non-transparent");
            leftRegion.setAlignment(Pos.CENTER);
            HBox.setHgrow(leftRegion, Priority.ALWAYS);

            rightRegion.getStyleClass().add("card-non-transparent");
            FXUtils.setLimitWidth(rightRegion, 250);


            var uuid = account.getProfileID();
            var skin = TexturesLoader.getDefaultSkin(uuid).image();
            var slim = TexturesLoader.getDefaultModel(uuid) == TextureModel.SLIM;

            SpinnerPane spinnerPane = new SpinnerPane();
            spinnerPane.loadingProperty().bind(loadingProperty);
            spinnerPane.setPrefWidth(300);

            SkinCanvas canvas = new SkinCanvas(skin, 250, 400, true);
            canvas.getScale().setX(1.25);
            canvas.getScale().setY(1.25);
            canvas.updateSkin(skin, slim, null);
            skinObjectProperty().addListener((obs, oldSkin, newSkin) -> canvas.updateSkin(newSkin.skin().image(), newSkin.model().isSlim(), newSkin.cape() != null ? newSkin.cape().image() : null));

            spinnerPane.setContent(canvas);
            rightRegion.setCenter(spinnerPane);

            canvas.getAnimationPlayer().addSkinAnimation(new SkinAniWavingArms(100, 2000, 7.5, canvas), new SkinAniRunning(100, 100, 30, canvas));
            canvas.enableRotation(.5);

            getChildren().setAll(leftRegion, rightRegion);
        }
    }
}
