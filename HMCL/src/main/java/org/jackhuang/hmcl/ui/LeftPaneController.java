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
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.binding.When;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItemViewModel;
import org.jackhuang.hmcl.ui.account.AddAccountPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.versions.GameAdvancedListItemViewModel;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.Lang;

import java.io.File;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LeftPaneController {
    private final AdvancedListBox leftPane;
    private final VBox profilePane = new VBox();
    private final VBox accountPane = new VBox();

    public LeftPaneController(AdvancedListBox leftPane) {
        this.leftPane = leftPane;

        AdvancedListItem2 accountListItem = new AdvancedListItem2(new AccountAdvancedListItemViewModel());
        AdvancedListItem2 gameListItem = new AdvancedListItem2(new GameAdvancedListItemViewModel());

        IconedItem launcherSettingsItem = new IconedItem(SVG.gear(Theme.blackFillBinding(), 20, 20));

        launcherSettingsItem.getLabel().textProperty().bind(
                new When(UpdateChecker.outdatedProperty())
                        .then(i18n("update.found"))
                        .otherwise(i18n("settings.launcher")));

        launcherSettingsItem.getLabel().textFillProperty().bind(
                new When(UpdateChecker.outdatedProperty())
                        .then(Color.RED)
                        .otherwise(Color.BLACK));

        launcherSettingsItem.maxWidthProperty().bind(leftPane.widthProperty());
        launcherSettingsItem.setOnMouseClicked(e -> Controllers.navigate(Controllers.getSettingsPage()));

        leftPane
                .startCategory(i18n("account").toUpperCase())
                .add(accountListItem)
                .startCategory(i18n("version").toUpperCase())
                .add(gameListItem)
                .startCategory(i18n("launcher").toUpperCase())
                .add(launcherSettingsItem)
                .add(new ClassTitle(i18n("profile.title").toUpperCase(), Lang.apply(new JFXButton(), button -> {
                    button.setGraphic(SVG.plus(Theme.blackFillBinding(), 10, 10));
                    button.getStyleClass().add("toggle-icon-tiny");
                    button.setOnMouseClicked(e ->
                            Controllers.getDecorator().showPage(new ProfilePage(null)));
                })))
                .add(profilePane);

        EventBus.EVENT_BUS.channel(ProfileLoadingEvent.class).register(this::onProfilesLoading);
        EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).register(this::onProfileChanged);
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(this::onRefreshedVersions);
    }

    // ==== Accounts ====
    private Optional<Account> getAccountFromItem(RipplerContainer accountItem) {
        return Optional.ofNullable(accountItem.getProperties().get("account"))
                .map(Account.class::cast);
    }

    public void checkAccount() {
        if (Accounts.getAccounts().isEmpty())
            addNewAccount();
    }

    private void addNewAccount() {
        Controllers.dialog(new AddAccountPane());
    }
    // ====

    private void onProfileChanged(ProfileChangedEvent event) {
        Profile profile = event.getProfile();

        Platform.runLater(() -> {
            for (Node node : profilePane.getChildren()) {
                if (node instanceof RipplerContainer && node.getProperties().get("profile") instanceof String) {
                    boolean current = Objects.equals(node.getProperties().get("profile"), profile.getName());
                    ((RipplerContainer) node).setSelected(current);
                    ((AdvancedListItem) ((RipplerContainer) node).getContainer()).setSubtitle(current ? i18n("profile.selected") : "");
                }
            }
        });
    }

    private void onProfilesLoading() {
        LinkedList<RipplerContainer> list = new LinkedList<>();
        for (Profile profile : Settings.instance().getProfiles()) {
            AdvancedListItem item = new AdvancedListItem(Profiles.getProfileDisplayName(profile));
            RipplerContainer ripplerContainer = new RipplerContainer(item);
            item.setOnSettingsButtonClicked(e -> Controllers.getDecorator().showPage(new ProfilePage(profile)));
            ripplerContainer.setOnMouseClicked(e -> Settings.instance().setSelectedProfile(profile));
            ripplerContainer.getProperties().put("profile", profile.getName());
            ripplerContainer.maxWidthProperty().bind(leftPane.widthProperty());
            list.add(ripplerContainer);
        }
        Platform.runLater(() -> profilePane.getChildren().setAll(list));
    }

    private boolean checkedModpack = false;
    private static boolean showNewAccount = true;

    private void onRefreshedVersions(RefreshedVersionsEvent event) {
        JFXUtilities.runInFX(() -> {
            HMCLGameRepository repository = (HMCLGameRepository) event.getSource();
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
