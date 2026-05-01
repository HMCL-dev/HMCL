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
import javafx.scene.layout.*;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.game.skin.Skin;
import org.jackhuang.hmcl.game.skin.TextureModel;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.skin.SkinCanvas;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniRunning;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniWavingArms;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class SkinPageBase<T extends Account> extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    protected final T account;
    @Nullable
    private final String url;
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loadingProperty = new SimpleBooleanProperty(true);
    private final TabHeader tab;
    private final TabHeader.Tab<SkinManage> manageTab = new TabHeader.Tab<>("manageTab");
    private final TransitionPane transitionPane = new TransitionPane();

    protected final SkinManage skinManage;

    protected SkinPageBase(T account, @Nullable String url) {
        this.url = url;
        this.account = account;

        tab = new TabHeader(transitionPane, manageTab);
        skinManage = new SkinManage();
        manageTab.setNodeSupplier(() -> skinManage);
        tab.select(manageTab);

        BorderPane left = new BorderPane();
        FXUtils.setLimitWidth(left, 200);
        VBox.setVgrow(left, Priority.ALWAYS);
        setLeft(left);

        AdvancedListBox sideBar = new AdvancedListBox().addNavigationDrawerTab(tab, manageTab, i18n("account.skin"), SVG.CHECKROOM);
        left.setTop(sideBar);

        PopupMenu saveList = new PopupMenu();
        JFXPopup savePopup = new JFXPopup(saveList);
        saveList.getContent().setAll(new IconedMenuItem(SVG.APPAREL, i18n("account.skin.manage.save.skin"), () -> {
        }, savePopup), new IconedMenuItem(SVG.CROP_9_16, i18n("account.skin.manage.save.cape"), () -> {
        }, savePopup));

        AdvancedListBox toolbar = new AdvancedListBox().addNavigationDrawerItem(i18n("button.save"), SVG.OUTPUT, null, item -> {
            item.setOnAction(e -> savePopup.show(item, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT, item.getWidth(), 0));
        });
        BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));
        left.setBottom(toolbar);

        setCenter(transitionPane);

        this.state.set(State.fromTitle(i18n("account.skin.manage", account.getUsername())));
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    protected abstract ReadOnlyObjectProperty<Skin> skinObjectProperty();

    protected abstract Task<Void> setSkin(Skin skin);

    protected final class SkinManage extends HBox {
        protected StackPane leftRegion = new StackPane();
        private BorderPane rightRegion = new BorderPane();

        private SkinManage() {
            setSpacing(10);
            setPadding(new Insets(10, 10, 10, 10));

            leftRegion.getStyleClass().add("card-non-transparent");
            HBox.setHgrow(leftRegion, Priority.ALWAYS);

            rightRegion.getStyleClass().add("card-non-transparent");
            FXUtils.setLimitWidth(rightRegion, 250);


            var uuid = account.getUUID();
            var skin = TexturesLoader.getDefaultSkin(uuid).image();
            var slim = TexturesLoader.getDefaultModel(uuid) == TextureModel.SLIM;

            SkinCanvas canvas = new SkinCanvas(skin, 250, 300, true);
            canvas.updateSkin(skin, slim, null);
            skinObjectProperty().addListener((obs, oldSkin, newSkin) -> {
                canvas.updateSkin(newSkin.skin().image(), newSkin.model().isSlim(), newSkin.cape() != null ? newSkin.cape().image() : null);
            });
            StackPane canvasPane = new StackPane(canvas);
            canvasPane.setPrefWidth(300);
            rightRegion.setCenter(canvasPane);
            canvas.getAnimationPlayer().addSkinAnimation(new SkinAniWavingArms(100, 2000, 7.5, canvas), new SkinAniRunning(100, 100, 30, canvas));
            canvas.enableRotation(.5);

            getChildren().setAll(leftRegion, rightRegion);
        }
    }
}
