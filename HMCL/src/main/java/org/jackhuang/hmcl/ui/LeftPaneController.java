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
import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.construct.IconedItem;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.Lang;

import java.io.File;
import java.util.LinkedList;
import java.util.Objects;

public final class LeftPaneController {
    private final AdvancedListBox leftPane;
    private final VBox profilePane = new VBox();
    private final VBox accountPane = new VBox();
    private final IconedItem launcherSettingsItem;
    private final VersionListItem missingAccountItem = new VersionListItem(Launcher.i18n("account.missing"), Launcher.i18n("message.unknown"));

    public LeftPaneController(AdvancedListBox leftPane) {
        this.leftPane = leftPane;

        this.launcherSettingsItem = Lang.apply(new IconedItem(SVG.gear(Theme.blackFillBinding(), 20, 20), Launcher.i18n("settings.launcher")), iconedItem -> {
            iconedItem.prefWidthProperty().bind(leftPane.widthProperty());
            iconedItem.setOnMouseClicked(e -> Controllers.navigate(Controllers.getSettingsPage()));
        });

        leftPane
                .add(new ClassTitle(Launcher.i18n("account").toUpperCase(), Lang.apply(new JFXButton(), button -> {
                    button.setGraphic(SVG.plus(Theme.blackFillBinding(), 10, 10));
                    button.getStyleClass().add("toggle-icon-tiny");
                    button.setOnMouseClicked(e -> addNewAccount());
                })))
                .add(accountPane)
                .startCategory(Launcher.i18n("launcher").toUpperCase())
                .add(launcherSettingsItem)
                .add(new ClassTitle(Launcher.i18n("profile.title").toUpperCase(), Lang.apply(new JFXButton(), button -> {
                    button.setGraphic(SVG.plus(Theme.blackFillBinding(), 10, 10));
                    button.getStyleClass().add("toggle-icon-tiny");
                    button.setOnMouseClicked(e ->
                            Controllers.getDecorator().showPage(new ProfilePage(null)));
                })))
                .add(profilePane);

        EventBus.EVENT_BUS.channel(AccountLoadingEvent.class).register(this::onAccountsLoading);
        EventBus.EVENT_BUS.channel(ProfileLoadingEvent.class).register(this::onProfilesLoading);
        EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).register(this::onProfileChanged);
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(this::onRefreshedVersions);

        FXUtils.onChangeAndOperate(Settings.INSTANCE.selectedAccountProperty(), this::onSelectedAccountChanged);
        onAccountsLoading();
    }

    private void addNewAccount() {
        Controllers.dialog(new AddAccountPane(Controllers::closeDialog));
    }

    private void onSelectedAccountChanged(Account newAccount) {
        Platform.runLater(() -> {
            for (Node node : accountPane.getChildren()) {
                if (node instanceof RipplerContainer && node.getProperties().get("account") instanceof Account) {
                    boolean current = Objects.equals(node.getProperties().get("account"), newAccount);
                    ((RipplerContainer) node).setSelected(current);
                }
            }
        });
    }

    private void onProfileChanged(ProfileChangedEvent event) {
        Profile profile = event.getProfile();

        Platform.runLater(() -> {
            for (Node node : profilePane.getChildren()) {
                if (node instanceof RipplerContainer && node.getProperties().get("profile") instanceof String) {
                    boolean current = Objects.equals(node.getProperties().get("profile"), profile.getName());
                    ((RipplerContainer) node).setSelected(current);
                    ((VersionListItem) ((RipplerContainer) node).getContainer()).setGameVersion(current ? Launcher.i18n("profile.selected") : "");
                }
            }
        });
    }

    private void onProfilesLoading() {
        LinkedList<RipplerContainer> list = new LinkedList<>();
        for (Profile profile : Settings.INSTANCE.getProfiles()) {
            VersionListItem item = new VersionListItem(Profiles.getProfileDisplayName(profile));
            RipplerContainer ripplerContainer = new RipplerContainer(item);
            item.setOnSettingsButtonClicked(e -> Controllers.getDecorator().showPage(new ProfilePage(profile)));
            ripplerContainer.setOnMouseClicked(e -> Settings.INSTANCE.setSelectedProfile(profile));
            ripplerContainer.getProperties().put("profile", profile.getName());
            ripplerContainer.maxWidthProperty().bind(leftPane.widthProperty());
            list.add(ripplerContainer);
        }
        Platform.runLater(() -> profilePane.getChildren().setAll(list));
    }

    private static String accountType(Account account) {
        if (account instanceof OfflineAccount) return Launcher.i18n("account.methods.offline");
        else if (account instanceof YggdrasilAccount) return account.getUsername();
        else throw new Error(Launcher.i18n("account.methods.no_method") + ": " + account);
    }

    private void onAccountsLoading() {
        LinkedList<RipplerContainer> list = new LinkedList<>();
        Account selectedAccount = Settings.INSTANCE.getSelectedAccount();
        for (Account account : Settings.INSTANCE.getAccounts()) {
            VersionListItem item = new VersionListItem(account.getCharacter(), accountType(account));
            RipplerContainer ripplerContainer = new RipplerContainer(item);
            item.setOnSettingsButtonClicked(e -> {
                AccountPage accountPage = new AccountPage(account, item);
                JFXPopup popup = new JFXPopup(accountPage);
                accountPage.setOnDelete(popup::hide);
                popup.show((Node) e.getSource(), JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, e.getX(), e.getY());
            });
            ripplerContainer.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                    Settings.INSTANCE.setSelectedAccount(account);
            });
            ripplerContainer.getProperties().put("account", account);
            ripplerContainer.maxWidthProperty().bind(leftPane.widthProperty());

            if (account instanceof YggdrasilAccount) {
                Image image = AccountHelper.getSkin((YggdrasilAccount) account, 4);
                item.setImage(image, AccountHelper.getViewport(4));
            } else
                item.setImage(AccountHelper.getDefaultSkin(account, 4), AccountHelper.getViewport(4));

            if (account instanceof AuthlibInjectorAccount)
                Accounts.getAuthlibInjectorServerNameAsync((AuthlibInjectorAccount) account)
                    .subscribe(Schedulers.javafx(), variables -> FXUtils.installTooltip(ripplerContainer, 500, 5000, 0, new Tooltip(variables.get("serverName"))));

            if (selectedAccount == account)
                ripplerContainer.setSelected(true);

            list.add(ripplerContainer);
        }

        if (Settings.INSTANCE.getAccounts().isEmpty()) {
            RipplerContainer container = new RipplerContainer(missingAccountItem);
            missingAccountItem.setOnSettingsButtonClicked(e -> addNewAccount());
            container.setOnMouseClicked(e -> addNewAccount());
            list.add(container);
        }

        Platform.runLater(() -> accountPane.getChildren().setAll(list));
    }

    public void showUpdate() {
        launcherSettingsItem.setText(Launcher.i18n("update.found"));
        launcherSettingsItem.setTextFill(Color.RED);
    }

    private boolean checkedModpack = false;

    private void onRefreshedVersions(RefreshedVersionsEvent event) {
        JFXUtilities.runInFX(() -> {
            boolean flag = true;
            HMCLGameRepository repository = (HMCLGameRepository) event.getSource();
            if (!checkedModpack) {
                checkedModpack = true;

                if (repository.getVersionCount() == 0) {
                    File modpackFile = new File("modpack.zip").getAbsoluteFile();
                    if (modpackFile.exists()) {
                        try {
                            Modpack modpack = ModpackHelper.readModpackManifest(modpackFile);
                            Controllers.taskDialog(ModpackHelper.getInstallTask(repository.getProfile(), modpackFile, modpack.getName(), modpack)
                                            .with(Task.of(Schedulers.javafx(), () -> {
                                                Controllers.closeDialog();
                                                checkAccount();
                                            })).executor(true),
                                    Launcher.i18n("modpack.installing"), "", null);
                            flag = false;
                        } catch (UnsupportedModpackException ignore) {
                        }
                    }
                }
            }

            if (flag)
                checkAccount();
        });
    }

    public void checkAccount() {
        if (Settings.INSTANCE.getAccounts().isEmpty())
            addNewAccount();
    }
}
