/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.account.AccountList;
import org.jackhuang.hmcl.ui.account.AddAccountPane;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorNavigatorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.profile.ProfileAdvancedListItem;
import org.jackhuang.hmcl.ui.profile.ProfileList;
import org.jackhuang.hmcl.ui.versions.GameAdvancedListItem;
import org.jackhuang.hmcl.ui.versions.GameList;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class RootPage extends DecoratorNavigatorPage {
    private MainPage mainPage = null;
    private SettingsPage settingsPage = null;
    private GameList gameListPage = null;
    private AccountList accountListPage = null;
    private ProfileList profileListPage = null;

    public RootPage() {
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(event -> onRefreshedVersions((HMCLGameRepository) event.getSource()));

        Profile profile = Profiles.getSelectedProfile();
        if (profile != null && profile.getRepository().isLoaded())
            onRefreshedVersions(Profiles.selectedProfileProperty().get().getRepository());
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    private MainPage getMainPage() {
        if (mainPage == null) {
            MainPage mainPage = new MainPage();
            FXUtils.applyDragListener(mainPage, it -> "zip".equals(FileUtils.getExtension(it)), modpacks -> {
                File modpack = modpacks.get(0);
                Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), modpack), i18n("install.modpack"));
            });

            FXUtils.onChangeAndOperate(Profiles.selectedVersionProperty(), mainPage::setCurrentGame);
            mainPage.showUpdateProperty().bind(UpdateChecker.outdatedProperty());
            mainPage.latestVersionProperty().bind(
                    BindingMapping.of(UpdateChecker.latestVersionProperty())
                            .map(version -> version == null ? "" : i18n("update.bubble.title", version.getVersion())));

            Profiles.registerVersionsListener(profile -> {
                HMCLGameRepository repository = profile.getRepository();
                List<Version> children = repository.getVersions().parallelStream()
                        .filter(version -> !version.isHidden())
                        .sorted(Comparator.comparing((Version version) -> version.getReleaseTime() == null ? new Date(0L) : version.getReleaseTime())
                                .thenComparing(a -> VersionNumber.asVersion(a.getId())))
                        .collect(Collectors.toList());
                runInFX(() -> {
                    if (profile == Profiles.getSelectedProfile())
                        mainPage.initVersions(profile, children);
                });
            });
            this.mainPage = mainPage;
        }
        return mainPage;
    }

    private SettingsPage getSettingsPage() {
        if (settingsPage == null)
            settingsPage = new SettingsPage();
        return settingsPage;
    }

    private GameList getGameListPage() {
        if (gameListPage == null) {
            gameListPage = new GameList();
            FXUtils.applyDragListener(gameListPage, it -> "zip".equals(FileUtils.getExtension(it)), modpacks -> {
                File modpack = modpacks.get(0);
                Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), modpack), i18n("install.modpack"));
            });
        }
        return gameListPage;
    }

    private AccountList getAccountListPage() {
        if (accountListPage == null) {
            accountListPage = new AccountList();
            accountListPage.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
            accountListPage.accountsProperty().bindContent(Accounts.accountsProperty());
        }
        return accountListPage;
    }

    private ProfileList getProfileListPage() {
        if (profileListPage == null) {
            profileListPage = new ProfileList();
            profileListPage.selectedProfileProperty().bindBidirectional(Profiles.selectedProfileProperty());
            profileListPage.profilesProperty().bindContent(Profiles.profilesProperty());
        }
        return profileListPage;
    }

    private static class Skin extends SkinBase<RootPage> {

        protected Skin(RootPage control) {
            super(control);

            // first item in left sidebar
            AccountAdvancedListItem accountListItem = new AccountAdvancedListItem();
            accountListItem.setOnAction(e -> getSkinnable().navigate(getSkinnable().getAccountListPage(), ContainerAnimations.FADE.getAnimationProducer()));
            accountListItem.accountProperty().bind(Accounts.selectedAccountProperty());

            // second item in left sidebar
            GameAdvancedListItem gameListItem = new GameAdvancedListItem();
            gameListItem.actionButtonVisibleProperty().bind(Profiles.selectedVersionProperty().isNotNull());
            gameListItem.setOnAction(e -> {
                Profile profile = Profiles.getSelectedProfile();
                String version = Profiles.getSelectedVersion();
                if (version == null) {
                    getSkinnable().navigate(getSkinnable().getGameListPage(), ContainerAnimations.FADE.getAnimationProducer());
                } else {
                    Versions.modifyGameSettings(profile, version);
                }
            });

            // third item in left sidebar
            AdvancedListItem gameItem = new AdvancedListItem();
            gameItem.setImage(newImage("/assets/img/bookshelf.png"));
            gameItem.setTitle(i18n("version.manage"));
            gameItem.setOnAction(e -> getSkinnable().navigate(getSkinnable().getGameListPage(), ContainerAnimations.FADE.getAnimationProducer()));

            // forth item in left sidebar
            ProfileAdvancedListItem profileListItem = new ProfileAdvancedListItem();
            profileListItem.setOnAction(e -> getSkinnable().navigate(getSkinnable().getProfileListPage(), ContainerAnimations.FADE.getAnimationProducer()));
            profileListItem.profileProperty().bind(Profiles.selectedProfileProperty());

            // fifth item in left sidebar
            AdvancedListItem launcherSettingsItem = new AdvancedListItem();
            launcherSettingsItem.setImage(newImage("/assets/img/command.png"));
            launcherSettingsItem.setTitle(i18n("settings.launcher"));
            launcherSettingsItem.setOnAction(e -> getSkinnable().navigate(getSkinnable().getSettingsPage(), ContainerAnimations.FADE.getAnimationProducer()));

            // the left sidebar
            AdvancedListBox sideBar = new AdvancedListBox()
                    .startCategory(i18n("account").toUpperCase())
                    .add(accountListItem)
                    .startCategory(i18n("version").toUpperCase())
                    .add(gameListItem)
                    .add(gameItem)
                    .startCategory(i18n("profile.title").toUpperCase())
                    .add(profileListItem)
                    .startCategory(i18n("launcher").toUpperCase())
                    .add(launcherSettingsItem);

            // the root page, with the sidebar in left, navigator in center.
            BorderPane root = new BorderPane();

            {
                StackPane drawerContainer = new StackPane();
                FXUtils.setLimitWidth(drawerContainer, 200);
                drawerContainer.getStyleClass().add("gray-background");
                drawerContainer.getChildren().setAll(sideBar);
                FXUtils.setOverflowHidden(drawerContainer, 8);

                StackPane wrapper = new StackPane(drawerContainer);
                wrapper.setPadding(new Insets(4, 0, 4, 4));
                root.setLeft(wrapper);
            }

            {
                control.navigator.getStyleClass().add("jfx-decorator-content-container");
                control.navigator.init(getSkinnable().getMainPage());
                FXUtils.setOverflowHidden(control.navigator, 8);
                StackPane wrapper = new StackPane(control.navigator);
                wrapper.setPadding(new Insets(4));
                root.setCenter(wrapper);
            }

            getChildren().setAll(root);
        }

    }

    // ==== Accounts ====
    public void checkAccount() {
        if (Accounts.getAccounts().isEmpty())
            Platform.runLater(this::addNewAccount);
    }

    private void addNewAccount() {
        Controllers.dialog(new AddAccountPane());
    }
    // ====

    private boolean checkedModpack = false;

    private void onRefreshedVersions(HMCLGameRepository repository) {
        runInFX(() -> {
            if (!checkedModpack) {
                checkedModpack = true;

                if (repository.getVersionCount() == 0) {
                    File modpackFile = new File("modpack.zip").getAbsoluteFile();
                    if (modpackFile.exists()) {
                        Task.supplyAsync(() -> CompressingUtils.findSuitableEncoding(modpackFile.toPath()))
                                .thenApplyAsync(encoding -> ModpackHelper.readModpackManifest(modpackFile.toPath(), encoding))
                                .thenApplyAsync(modpack -> ModpackHelper.getInstallTask(repository.getProfile(), modpackFile, modpack.getName(), modpack)
                                        .withRunAsync(Schedulers.javafx(), this::checkAccount).executor())
                                .thenAcceptAsync(Schedulers.javafx(), executor -> {
                                    Controllers.taskDialog(executor, i18n("modpack.installing"));
                                    executor.start();
                                }).start();
                    }
                }
            }

            checkAccount();
        });
    }
}
