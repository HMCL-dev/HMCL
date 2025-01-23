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
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TabControl;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.VersionSettingsPage;

import java.util.Locale;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LauncherSettingsPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("settings")));
    private final TabHeader tab;
    private final TabHeader.Tab<VersionSettingsPage> gameTab = new TabHeader.Tab<>("versionSettingsPage");
    private final TabControl.Tab<JavaManagementPage> javaManagementTab = new TabControl.Tab<>("javaManagementPage");
    private final TabHeader.Tab<SettingsPage> settingsTab = new TabHeader.Tab<>("settingsPage");
    private final TabHeader.Tab<PersonalizationPage> personalizationTab = new TabHeader.Tab<>("personalizationPage");
    private final TabHeader.Tab<DownloadSettingsPage> downloadTab = new TabHeader.Tab<>("downloadSettingsPage");
    private final TabHeader.Tab<HelpPage> helpTab = new TabHeader.Tab<>("helpPage");
    private final TabHeader.Tab<AboutPage> aboutTab = new TabHeader.Tab<>("aboutPage");
    private final TabHeader.Tab<FeedbackPage> feedbackTab = new TabHeader.Tab<>("feedbackPage");
    private final TransitionPane transitionPane = new TransitionPane();

    public LauncherSettingsPage() {
        gameTab.setNodeSupplier(() -> new VersionSettingsPage(true));
        javaManagementTab.setNodeSupplier(JavaManagementPage::new);
        settingsTab.setNodeSupplier(SettingsPage::new);
        personalizationTab.setNodeSupplier(PersonalizationPage::new);
        downloadTab.setNodeSupplier(DownloadSettingsPage::new);
        helpTab.setNodeSupplier(HelpPage::new);
        feedbackTab.setNodeSupplier(FeedbackPage::new);
        aboutTab.setNodeSupplier(AboutPage::new);
        tab = new TabHeader(gameTab, javaManagementTab, settingsTab, personalizationTab, downloadTab, helpTab, feedbackTab, aboutTab);

        tab.select(gameTab);
        gameTab.initializeIfNeeded();
        gameTab.getNode().loadVersion(Profiles.getSelectedProfile(), null);
        FXUtils.onChangeAndOperate(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE);
        });

        {
            AdvancedListBox sideBar = new AdvancedListBox()
                    .addNavigationDrawerItem(settingsItem -> {
                        settingsItem.setTitle(i18n("settings.type.global.manage"));
                        settingsItem.setLeftGraphic(wrap(SVG.GAMEPAD));
                        settingsItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(gameTab));
                        settingsItem.setOnAction(e -> tab.select(gameTab));
                    })
                    .addNavigationDrawerItem(javaItem -> {
                        javaItem.setTitle(i18n("java.management"));
                        javaItem.setLeftGraphic(wrap(SVG.WRENCH_OUTLINE));
                        javaItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(javaManagementTab));
                        javaItem.setOnAction(e -> tab.select(javaManagementTab));
                    })
                    .startCategory(i18n("launcher").toUpperCase(Locale.ROOT))
                    .addNavigationDrawerItem(settingsItem -> {
                        settingsItem.setTitle(i18n("settings.launcher.general"));
                        settingsItem.setLeftGraphic(wrap(SVG.APPLICATION_OUTLINE));
                        settingsItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(settingsTab));
                        settingsItem.setOnAction(e -> tab.select(settingsTab));
                    })
                    .addNavigationDrawerItem(personalizationItem -> {
                        personalizationItem.setTitle(i18n("settings.launcher.appearance"));
                        personalizationItem.setLeftGraphic(wrap(SVG.STYLE_OUTLINE));
                        personalizationItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(personalizationTab));
                        personalizationItem.setOnAction(e -> tab.select(personalizationTab));
                    })
                    .addNavigationDrawerItem(downloadItem -> {
                        downloadItem.setTitle(i18n("download"));
                        downloadItem.setLeftGraphic(wrap(SVG.DOWNLOAD_OUTLINE));
                        downloadItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(downloadTab));
                        downloadItem.setOnAction(e -> tab.select(downloadTab));
                    })
                    .startCategory(i18n("help").toUpperCase(Locale.ROOT))
                    .addNavigationDrawerItem(helpItem -> {
                        helpItem.setTitle(i18n("help"));
                        helpItem.setLeftGraphic(wrap(SVG.HELP_CIRCLE_OUTLINE));
                        helpItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(helpTab));
                        helpItem.setOnAction(e -> tab.select(helpTab));
                    })
                    .addNavigationDrawerItem(feedbackItem -> {
                        feedbackItem.setTitle(i18n("feedback"));
                        feedbackItem.setLeftGraphic(wrap(SVG.MESSAGE_ALERT_OUTLINE));
                        feedbackItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(feedbackTab));
                        feedbackItem.setOnAction(e -> tab.select(feedbackTab));
                    })
                    .addNavigationDrawerItem(aboutItem -> {
                        aboutItem.setTitle(i18n("about"));
                        aboutItem.setLeftGraphic(wrap(SVG.INFORMATION_OUTLINE));
                        aboutItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(aboutTab));
                        aboutItem.setOnAction(e -> tab.select(aboutTab));
                    });
            FXUtils.setLimitWidth(sideBar, 200);
            setLeft(sideBar);
        }

        setCenter(transitionPane);
    }

    @Override
    public void onPageShown() {
        tab.onPageShown();
    }

    @Override
    public void onPageHidden() {
        tab.onPageHidden();
    }

    public void showGameSettings(Profile profile) {
        gameTab.getNode().loadVersion(profile, null);
        tab.select(gameTab);
    }

    public void showFeedback() {
        tab.select(feedbackTab);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
