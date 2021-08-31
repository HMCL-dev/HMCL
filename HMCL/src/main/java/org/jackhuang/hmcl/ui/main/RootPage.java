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

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
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
import org.jackhuang.hmcl.ui.account.AddAccountPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorTabPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.versions.GameAdvancedListItem;
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

public class RootPage extends DecoratorTabPage {
    private MainPage mainPage = null;

    private final TabHeader.Tab<MainPage> mainTab = new TabHeader.Tab<>("main");

    public RootPage() {
        setLeftPaneWidth(200);

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class)
                .register(event -> onRefreshedVersions((HMCLGameRepository) event.getSource()));

        Profile profile = Profiles.getSelectedProfile();
        if (profile != null && profile.getRepository().isLoaded())
            onRefreshedVersions(Profiles.selectedProfileProperty().get().getRepository());

        mainTab.setNodeSupplier(this::getMainPage);
        getTabs().setAll(mainTab);
    }

    @Override
    public boolean back() {
        if (mainTab.isSelected())
            return true;
        else {
            getSelectionModel().select(mainTab);
            return false;
        }
    }

    @Override
    protected void onNavigated(Node to) {
        backableProperty().set(!(to instanceof MainPage));

        super.onNavigated(to);
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
                Controllers.getDecorator().startWizard(
                        new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), modpack),
                        i18n("install.modpack"));
            });

            FXUtils.onChangeAndOperate(Profiles.selectedVersionProperty(), mainPage::setCurrentGame);
            mainPage.showUpdateProperty().bind(UpdateChecker.outdatedProperty());
            mainPage.latestVersionProperty().bind(BindingMapping.of(UpdateChecker.latestVersionProperty())
                    .map(version -> version == null ? "" : i18n("update.bubble.title", version.getVersion())));

            Profiles.registerVersionsListener(profile -> {
                HMCLGameRepository repository = profile.getRepository();
                List<Version> children = repository.getVersions().parallelStream()
                        .filter(version -> !version.isHidden())
                        .sorted(Comparator
                                .comparing((Version version) -> version.getReleaseTime() == null ? new Date(0L)
                                        : version.getReleaseTime())
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

    private static class Skin extends SkinBase<RootPage> {

        protected Skin(RootPage control) {
            super(control);

            // first item in left sidebar
            AccountAdvancedListItem accountListItem = new AccountAdvancedListItem();
            accountListItem.setOnAction(e -> {
                Controllers.navigate(Controllers.getAccountListPage());

                if (Accounts.getAccounts().isEmpty()) {
                    Controllers.dialog(new AddAccountPane());
                }
            });
            accountListItem.accountProperty().bind(Accounts.selectedAccountProperty());

            // second item in left sidebar
            GameAdvancedListItem gameListItem = new GameAdvancedListItem();
            gameListItem.setOnAction(e -> {
                Profile profile = Profiles.getSelectedProfile();
                String version = Profiles.getSelectedVersion();
                if (version == null) {
                    Controllers.navigate(Controllers.getGameListPage());
                } else {
                    Versions.modifyGameSettings(profile, version);
                }
            });

            // third item in left sidebar
            AdvancedListItem gameItem = new AdvancedListItem();
            gameItem.setLeftGraphic(AdvancedListItem.createImageView(newImage("/assets/img/bookshelf.png")).getKey());
            gameItem.setTitle(i18n("version.manage"));
            gameItem.setOnAction(e -> Controllers.navigate(Controllers.getGameListPage()));

            // forth item in left sidebar
            AdvancedListItem downloadItem = new AdvancedListItem();
            downloadItem
                    .setLeftGraphic(AdvancedListItem.createImageView(newImage("/assets/img/chest.png")).getKey());
            downloadItem.setActionButtonVisible(false);
            downloadItem.setTitle(i18n("download"));
            downloadItem.setOnAction(e -> Controllers.navigate(Controllers.getDownloadPage()));

            // fifth item in left sidebar
            AdvancedListItem launcherSettingsItem = new AdvancedListItem();
            launcherSettingsItem
                    .setLeftGraphic(AdvancedListItem.createImageView(newImage("/assets/img/command.png")).getKey());
            launcherSettingsItem.setActionButtonVisible(false);
            launcherSettingsItem.setTitle(i18n("settings"));
            launcherSettingsItem.setOnAction(e -> Controllers.navigate(Controllers.getSettingsPage()));

            // the left sidebar
            AdvancedListBox sideBar = new AdvancedListBox()
                    .startCategory(i18n("account").toUpperCase())
                    .add(accountListItem)
                    .startCategory(i18n("version").toUpperCase())
                    .add(gameListItem)
                    .add(gameItem)
                    .add(downloadItem)
                    .add(launcherSettingsItem);

            // the root page, with the sidebar in left, navigator in center.
            BorderPane root = new BorderPane();
            sideBar.setPrefWidth(200);
            root.setLeft(sideBar);

            {
                control.transitionPane.getStyleClass().add("jfx-decorator-content-container");
                control.transitionPane.getChildren().setAll(getSkinnable().getMainPage());
                FXUtils.setOverflowHidden(control.transitionPane, 8);
                root.setCenter(control.transitionPane);
            }

            getChildren().setAll(root);
        }

    }

    // ==== Accounts ====

    private boolean checkedAccont = false;

    public void checkAccount() {
        if (checkedAccont)
            return;
        checkedAccont = true;
        checkAccountForcibly();
    }

    public void checkAccountForcibly() {
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
                                .thenApplyAsync(
                                        encoding -> ModpackHelper.readModpackManifest(modpackFile.toPath(), encoding))
                                .thenApplyAsync(modpack -> ModpackHelper
                                        .getInstallTask(repository.getProfile(), modpackFile, modpack.getName(),
                                                modpack)
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
