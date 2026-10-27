// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.game.GameSettingsPage;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LauncherSettingsPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("settings")));
    private final TabHeader tab;
    /// The tab for editing game settings presets.
    private final TabItem<GameSettingsPage<GameSettings.Preset>> gameTab = new TabItem<>();
    /// The tab whose content manages installed Java runtimes.
    private final TabItem<JavaManagementPage> javaManagementTab = new TabItem<>();
    /// The tab for general launcher settings.
    private final TabItem<SettingsPage> settingsTab = new TabItem<>();
    /// The tab for launcher appearance settings.
    private final TabItem<PersonalizationPage> personalizationTab = new TabItem<>();
    /// The tab for download settings.
    private final TabItem<DownloadSettingsPage> downloadTab = new TabItem<>();
    /// The tab displaying launcher help.
    private final TabItem<HelpPage> helpTab = new TabItem<>();
    /// The tab displaying launcher information.
    private final TabItem<AboutPage> aboutTab = new TabItem<>();
    /// The tab displaying feedback and contact information.
    private final TabItem<FeedbackPage> feedbackTab = new TabItem<>();
    private final TransitionPane transitionPane = new TransitionPane();

    public LauncherSettingsPage() {
        gameTab.setNodeSupplier(() -> new GameSettingsPage<>(GameSettings.Preset.class, null));
        javaManagementTab.setNodeSupplier(JavaManagementPage::new);
        settingsTab.setNodeSupplier(SettingsPage::new);
        personalizationTab.setNodeSupplier(PersonalizationPage::new);
        downloadTab.setNodeSupplier(DownloadSettingsPage::new);
        helpTab.setNodeSupplier(HelpPage::new);
        feedbackTab.setNodeSupplier(FeedbackPage::new);
        aboutTab.setNodeSupplier(AboutPage::new);
        tab = new TabHeader(transitionPane, gameTab, javaManagementTab, settingsTab, personalizationTab, downloadTab, helpTab, feedbackTab, aboutTab);

        tab.select(gameTab);
        addEventHandler(Navigator.NavigationEvent.NAVIGATED, event -> gameTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(GameDirectoryManager.getSelectedRepository())));

        AdvancedListBox sideBar = new AdvancedListBox()
                .addNavigationDrawerTab(tab, gameTab, i18n("settings.type.global.manage"), SVG.STADIA_CONTROLLER, SVG.STADIA_CONTROLLER_FILL)
                .addNavigationDrawerTab(tab, javaManagementTab, i18n("java.management"), SVG.LOCAL_CAFE, SVG.LOCAL_CAFE_FILL)
                .startCategory(i18n("launcher").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, settingsTab, i18n("settings.launcher.general"), SVG.TUNE)
                .addNavigationDrawerTab(tab, personalizationTab, i18n("settings.launcher.appearance"), SVG.STYLE, SVG.STYLE_FILL)
                .addNavigationDrawerTab(tab, downloadTab, i18n("download"), SVG.DOWNLOAD)
                .startCategory(i18n("help").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, helpTab, i18n("help"), SVG.HELP, SVG.HELP_FILL)
                .addNavigationDrawerTab(tab, feedbackTab, i18n("contact"), SVG.FEEDBACK, SVG.FEEDBACK_FILL)
                .addNavigationDrawerTab(tab, aboutTab, i18n("about"), SVG.INFO, SVG.INFO_FILL);
        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);

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

    public void showGameSettings(HMCLGameRepository repository) {
        gameTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(repository));
        tab.select(gameTab, false);
    }

    public void showFeedback() {
        tab.select(feedbackTab, false);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
