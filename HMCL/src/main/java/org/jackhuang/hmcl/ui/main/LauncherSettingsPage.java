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
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LauncherSettingsPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("settings.launcher"), 200));
    private final TabHeader tab;
    private final TabHeader.Tab<SettingsPage> settingsTab = new TabHeader.Tab<>("settingsPage");
    private final TabHeader.Tab<HelpPage> helpTab = new TabHeader.Tab<>("helpPage");
    private final TabHeader.Tab<AboutPage> aboutTab = new TabHeader.Tab<>("aboutPage");
    private final TabHeader.Tab<SponsorPage> sponsorTab = new TabHeader.Tab<>("sponsorPage");
    private final TransitionPane transitionPane = new TransitionPane();

    public LauncherSettingsPage() {
        settingsTab.setNodeSupplier(SettingsPage::new);
        helpTab.setNodeSupplier(HelpPage::new);
        sponsorTab.setNodeSupplier(SponsorPage::new);
        aboutTab.setNodeSupplier(AboutPage::new);
        tab = new TabHeader(settingsTab, helpTab, sponsorTab, aboutTab);

        tab.getSelectionModel().select(settingsTab);
        FXUtils.onChangeAndOperate(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            newValue.initializeIfNeeded();
            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE.getAnimationProducer());
        });

        {
            AdvancedListBox sideBar = new AdvancedListBox()
                    .addNavigationDrawerItem(settingsItem -> {
                        settingsItem.setTitle(i18n("settings.launcher"));
                        settingsItem.setLeftGraphic(wrap(SVG.gearOutline(null, 20, 20)));
                        settingsItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(settingsTab));
                        settingsItem.setOnAction(e -> tab.getSelectionModel().select(settingsTab));
                    })
                    .addNavigationDrawerItem(helpItem -> {
                        helpItem.setTitle(i18n("help"));
                        helpItem.setLeftGraphic(wrap(SVG.helpCircleOutline(null, 20, 20)));
                        helpItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(helpTab));
                        helpItem.setOnAction(e -> tab.getSelectionModel().select(helpTab));
                    })
                    .addNavigationDrawerItem(sponsorItem -> {
                        sponsorItem.setTitle(i18n("sponsor"));
                        sponsorItem.setLeftGraphic(wrap(SVG.handHearOutline(null, 20, 20)));
                        sponsorItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(sponsorTab));
                        sponsorItem.setOnAction(e -> tab.getSelectionModel().select(sponsorTab));
                    })
                    .addNavigationDrawerItem(aboutItem -> {
                        aboutItem.setTitle(i18n("about"));
                        aboutItem.setLeftGraphic(wrap(SVG.informationOutline(null, 20, 20)));
                        aboutItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(aboutTab));
                        aboutItem.setOnAction(e -> tab.getSelectionModel().select(aboutTab));
                    });
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
