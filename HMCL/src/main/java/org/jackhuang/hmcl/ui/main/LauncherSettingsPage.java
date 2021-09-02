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
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.VersionSettingsPage;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LauncherSettingsPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("settings"), -1));
    private final TabHeader tab;
    private final TabHeader.Tab<VersionSettingsPage> gameTab = new TabHeader.Tab<>("versionSettingsPage");
    private final TabHeader.Tab<SettingsPage> settingsTab = new TabHeader.Tab<>("settingsPage");
    private final TabHeader.Tab<PersonalizationPage> personalizationTab = new TabHeader.Tab<>("personalizationPage");
    private final TabHeader.Tab<DownloadSettingsPage> downloadTab = new TabHeader.Tab<>("downloadSettingsPage");
    private final TabHeader.Tab<HelpPage> helpTab = new TabHeader.Tab<>("helpPage");
    private final TabHeader.Tab<AboutPage> aboutTab = new TabHeader.Tab<>("aboutPage");
    private final TabHeader.Tab<SponsorPage> sponsorTab = new TabHeader.Tab<>("sponsorPage");
    private final TransitionPane transitionPane = new TransitionPane();

    public LauncherSettingsPage() {
        gameTab.setNodeSupplier(VersionSettingsPage::new);
        settingsTab.setNodeSupplier(SettingsPage::new);
        personalizationTab.setNodeSupplier(PersonalizationPage::new);
        downloadTab.setNodeSupplier(DownloadSettingsPage::new);
        helpTab.setNodeSupplier(HelpPage::new);
        sponsorTab.setNodeSupplier(SponsorPage::new);
        aboutTab.setNodeSupplier(AboutPage::new);
        tab = new TabHeader(gameTab, settingsTab, personalizationTab, downloadTab, helpTab, sponsorTab, aboutTab);

        tab.getSelectionModel().select(gameTab);
        gameTab.initializeIfNeeded();
        gameTab.getNode().loadVersion(Profiles.getSelectedProfile(), null);
        FXUtils.onChangeAndOperate(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            newValue.initializeIfNeeded();
            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE.getAnimationProducer());
        });

        {
            AdvancedListBox sideBar = new AdvancedListBox()
                    .addNavigationDrawerItem(settingsItem -> {
                        settingsItem.setTitle(i18n("settings.game.current"));
                        settingsItem.setLeftGraphic(wrap(SVG.gamepad(null, 20, 20)));
                        settingsItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(gameTab));
                        settingsItem.setOnAction(e -> tab.getSelectionModel().select(gameTab));
                    })
                    .startCategory(i18n("launcher"))
                    .addNavigationDrawerItem(settingsItem -> {
                        settingsItem.setTitle(i18n("settings.launcher.general"));
                        settingsItem.setLeftGraphic(wrap(SVG.applicationOutline(null, 20, 20)));
                        settingsItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(settingsTab));
                        settingsItem.setOnAction(e -> tab.getSelectionModel().select(settingsTab));
                    })
                    .addNavigationDrawerItem(personalizationItem -> {
                        personalizationItem.setTitle(i18n("settings.launcher.appearance"));
                        personalizationItem.setLeftGraphic(wrap(SVG.styleOutline(null, 20, 20)));
                        personalizationItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(personalizationTab));
                        personalizationItem.setOnAction(e -> tab.getSelectionModel().select(personalizationTab));
                    })
                    .addNavigationDrawerItem(downloadItem -> {
                        downloadItem.setTitle(i18n("download"));
                        downloadItem.setLeftGraphic(wrap(SVG.downloadOutline(null, 20, 20)));
                        downloadItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(downloadTab));
                        downloadItem.setOnAction(e -> tab.getSelectionModel().select(downloadTab));
                    })
                    .startCategory(i18n("help"))
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

    public void showGameSettings(Profile profile) {
        gameTab.getNode().loadVersion(profile, null);
        tab.getSelectionModel().select(gameTab);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
