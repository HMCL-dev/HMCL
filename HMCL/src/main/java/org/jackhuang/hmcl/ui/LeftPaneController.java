/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.concurrency.JFXUtilities;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.account.AddAccountPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.profile.ProfileAdvancedListItem;
import org.jackhuang.hmcl.ui.versions.GameAdvancedListItem;
import org.jackhuang.hmcl.ui.versions.Versions;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LeftPaneController extends AdvancedListBox {

    public LeftPaneController() {

        AccountAdvancedListItem accountListItem = new AccountAdvancedListItem();
        accountListItem.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));
        accountListItem.accountProperty().bind(Accounts.selectedAccountProperty());

        GameAdvancedListItem gameListItem = new GameAdvancedListItem();
        gameListItem.actionButtonVisibleProperty().bind(Profiles.selectedVersionProperty().isNotNull());
        gameListItem.setOnAction(e -> Versions.modifyGameSettings(Profiles.getSelectedProfile(), Profiles.getSelectedVersion()));

        ProfileAdvancedListItem profileListItem = new ProfileAdvancedListItem();
        profileListItem.setOnAction(e -> Controllers.navigate(Controllers.getProfileListPage()));
        profileListItem.profileProperty().bind(Profiles.selectedProfileProperty());

        AdvancedListItem gameItem = new AdvancedListItem();
        gameItem.setImage(new Image("/assets/img/bookshelf.png"));
        gameItem.setTitle(i18n("version.manage"));
        gameItem.setOnAction(e -> Controllers.navigate(Controllers.getGameListPage()));

        AdvancedListItem launcherSettingsItem = new AdvancedListItem();
        launcherSettingsItem.setImage(new Image("/assets/img/command.png"));
        launcherSettingsItem.setTitle(i18n("settings.launcher"));
        launcherSettingsItem.setOnAction(e -> Controllers.navigate(Controllers.getSettingsPage()));

        this
                .startCategory(i18n("account").toUpperCase())
                .add(accountListItem)
                .startCategory(i18n("version").toUpperCase())
                .add(gameListItem)
                .add(gameItem)
                .startCategory(i18n("profile.title").toUpperCase())
                .add(profileListItem)
                .startCategory(i18n("launcher").toUpperCase())
                .add(launcherSettingsItem);

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(event -> onRefreshedVersions((HMCLGameRepository) event.getSource()));

        Profile profile = Profiles.getSelectedProfile();
        if (profile != null && profile.getRepository().isLoaded())
            onRefreshedVersions(Profiles.selectedProfileProperty().get().getRepository());
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
    private static boolean showNewAccount = true;

    private void onRefreshedVersions(HMCLGameRepository repository) {
        JFXUtilities.runInFX(() -> {
            if (!checkedModpack) {
                checkedModpack = true;

                if (repository.getVersionCount() == 0) {
                    File modpackFile = new File("modpack.zip").getAbsoluteFile();
                    if (modpackFile.exists()) {
                        try {
                            AtomicReference<Region> region = new AtomicReference<>();
                            Modpack modpack = ModpackHelper.readModpackManifest(modpackFile);
                            TaskExecutor executor = ModpackHelper.getInstallTask(repository.getProfile(), modpackFile, modpack.getName(), modpack)
                                    .with(Task.of(Schedulers.javafx(), () -> {
                                        region.get().fireEvent(new DialogCloseEvent());
                                        checkAccount();
                                    })).executor();
                            region.set(Controllers.taskDialog(executor, i18n("modpack.installing"), ""));
                            executor.start();
                            showNewAccount = false;
                        } catch (UnsupportedModpackException ignore) {
                        }
                    }
                }
            }

            if (showNewAccount) {
                showNewAccount = false;
                checkAccount();
            }
        });
    }
}
