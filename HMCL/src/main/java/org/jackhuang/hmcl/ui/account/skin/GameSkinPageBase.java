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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.skin.Skin;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.VersionSettingsPage;

import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class GameSkinPageBase extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    protected final Account account;
    private final Map<String, String> urls;
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final TabHeader tab;
    private final TabHeader.Tab<VersionSettingsPage> manageTab = new TabHeader.Tab<>("manageTab");
    private final TransitionPane transitionPane = new TransitionPane();

    public GameSkinPageBase(Account account, Map<String, String> urls) {
        this.urls = urls;
        this.account = account;

        tab = new TabHeader(transitionPane, manageTab);
        tab.select(manageTab);

        BorderPane left = new BorderPane();
        FXUtils.setLimitWidth(left, 200);
        VBox.setVgrow(left, Priority.ALWAYS);
        setLeft(left);

        AdvancedListBox sideBar = new AdvancedListBox().addNavigationDrawerTab(tab, manageTab, i18n("account.skin.manage"), SVG.CHECKROOM);
        left.setTop(sideBar);

        PopupMenu saveList = new PopupMenu();
        JFXPopup savePopup = new JFXPopup(saveList);
        saveList.getContent().setAll(
                new IconedMenuItem(SVG.APPAREL, i18n("account.skin"), ()->{}, savePopup),
                new IconedMenuItem(SVG.CROP_9_16, i18n("version.launch_script"), ()->{}, savePopup)
        );

        AdvancedListBox toolbar = new AdvancedListBox()
                .addNavigationDrawerItem(i18n("go"), SVG.OUTPUT, () -> {

                });
        BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));
        left.setBottom(toolbar);

        setCenter(transitionPane);

        this.state.set(State.fromTitle(i18n("account.skin.manage", account.getIdentifier())));
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    protected abstract ReadOnlyObjectProperty<Skin> skinObjectProperty();

    protected abstract Task<Void> uploadSkin(Skin skin);
}
