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
package org.jackhuang.hmcl.ui.main;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LauncherSettingsPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(new State(i18n("settings.launcher"), null, true, false, true, false, 200));
    private final TabHeader tab;
    private final TabHeader.Tab<SettingsPage> settingsTab = new TabHeader.Tab<>("settingsPage");
    private final TabHeader.Tab<AboutPage> aboutTab = new TabHeader.Tab<>("aboutPage");
    private final TabHeader.Tab<SponsorPage> sponsorTab = new TabHeader.Tab<>("sponsorPage");
    private final TransitionPane transitionPane = new TransitionPane();

    public LauncherSettingsPage() {
        settingsTab.setNodeSupplier(SettingsPage::new);
        sponsorTab.setNodeSupplier(SponsorPage::new);
        aboutTab.setNodeSupplier(AboutPage::new);
        tab = new TabHeader(settingsTab, sponsorTab, aboutTab);

        tab.getSelectionModel().select(settingsTab);
        FXUtils.onChangeAndOperate(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            newValue.initializeIfNeeded();
            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE.getAnimationProducer());
        });

        {
            AdvancedListItem settingsItem = new AdvancedListItem();
            settingsItem.getStyleClass().add("navigation-drawer-item");
            settingsItem.setTitle(i18n("settings.launcher"));
            settingsItem.setLeftGraphic(wrap(SVG.gearOutline(null, 20, 20)));
            settingsItem.setActionButtonVisible(false);
            settingsItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(settingsTab));
            settingsItem.setOnAction(e -> tab.getSelectionModel().select(settingsTab));

            AdvancedListItem sponsorItem = new AdvancedListItem();
            sponsorItem.getStyleClass().add("navigation-drawer-item");
            sponsorItem.setTitle(i18n("sponsor"));
            sponsorItem.setLeftGraphic(wrap(SVG.handHearOutline(null, 20, 20)));
            sponsorItem.setActionButtonVisible(false);
            sponsorItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(sponsorTab));
            sponsorItem.setOnAction(e -> tab.getSelectionModel().select(sponsorTab));

            AdvancedListItem aboutItem = new AdvancedListItem();
            aboutItem.getStyleClass().add("navigation-drawer-item");
            aboutItem.setTitle(i18n("about"));
            aboutItem.setLeftGraphic(wrap(SVG.informationOutline(null, 20, 20)));
            aboutItem.setActionButtonVisible(false);
            aboutItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(aboutTab));
            aboutItem.setOnAction(e -> tab.getSelectionModel().select(aboutTab));

            AdvancedListBox sideBar = new AdvancedListBox()
                    .add(settingsItem)
                    .add(sponsorItem)
                    .add(aboutItem);
            FXUtils.setLimitWidth(sideBar, 200);
            setLeft(sideBar);
        }

        setCenter(transitionPane);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
