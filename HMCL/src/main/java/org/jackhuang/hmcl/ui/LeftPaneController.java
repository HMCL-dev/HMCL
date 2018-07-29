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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

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
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.IconedItem;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.MappedObservableList;

import java.io.File;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static javafx.collections.FXCollections.singletonObservableList;
import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LeftPaneController {
    private final AdvancedListBox leftPane;
    private final VBox profilePane = new VBox();
    private final VBox accountPane = new VBox();
    private final IconedItem launcherSettingsItem;

    private ListProperty<RipplerContainer> accountItems = new SimpleListProperty<>();
    private ObjectProperty<Account> selectedAccount = new SimpleObjectProperty<Account>() {
        {
            accountItems.addListener(onInvalidating(this::invalidated));
        }

        @Override
        protected void invalidated() {
            Account selected = get();
            accountItems.forEach(item -> item.setSelected(
                    getAccountFromItem(item)
                            .map(it -> it == selected)
                            .orElse(false)));
        }
    };

    public LeftPaneController(AdvancedListBox leftPane) {
        this.leftPane = leftPane;

        this.launcherSettingsItem = Lang.apply(new IconedItem(SVG.gear(Theme.blackFillBinding(), 20, 20), i18n("settings.launcher")), iconedItem -> {
            iconedItem.prefWidthProperty().bind(leftPane.widthProperty());
            iconedItem.setOnMouseClicked(e -> Controllers.navigate(Controllers.getSettingsPage()));
        });

        leftPane
                .add(new ClassTitle(i18n("account").toUpperCase(), Lang.apply(new JFXButton(), button -> {
                    button.setGraphic(SVG.plus(Theme.blackFillBinding(), 10, 10));
                    button.getStyleClass().add("toggle-icon-tiny");
                    button.setOnMouseClicked(e -> addNewAccount());
                })))
                .add(accountPane)
                .startCategory(i18n("launcher").toUpperCase())
                .add(launcherSettingsItem)
                .add(new ClassTitle(i18n("profile.title").toUpperCase(), Lang.apply(new JFXButton(), button -> {
                    button.setGraphic(SVG.plus(Theme.blackFillBinding(), 10, 10));
                    button.getStyleClass().add("toggle-icon-tiny");
                    button.setOnMouseClicked(e ->
                            Controllers.getDecorator().showPage(new ProfilePage(null)));
                })))
                .add(profilePane);

        // ==== Accounts ====
        // Missing account item
        VersionListItem missingAccountItem = new VersionListItem(i18n("account.missing"), i18n("message.unknown"));
        RipplerContainer missingAccountRippler = new RipplerContainer(missingAccountItem);
        missingAccountItem.setOnSettingsButtonClicked(e -> addNewAccount());
        missingAccountRippler.setOnMouseClicked(e -> addNewAccount());

        accountItems.bind(
                new When(Accounts.accountsProperty().emptyProperty())
                        .then(singletonObservableList(missingAccountRippler))
                        .otherwise(MappedObservableList.create(Accounts.getAccounts(), this::createAccountItem)));
        Bindings.bindContent(accountPane.getChildren(), accountItems);

        selectedAccount.bindBidirectional(Accounts.selectedAccountProperty());
        // ====

        EventBus.EVENT_BUS.channel(ProfileLoadingEvent.class).register(this::onProfilesLoading);
        EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).register(this::onProfileChanged);
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(this::onRefreshedVersions);
    }

    // ==== Accounts ====
    private Optional<Account> getAccountFromItem(RipplerContainer accountItem) {
        return Optional.ofNullable(accountItem.getProperties().get("account"))
                .map(Account.class::cast);
    }

    private static String accountSubtitle(Account account) {
        if (account instanceof OfflineAccount)
            return i18n("account.methods.offline");
        else if (account instanceof YggdrasilAccount)
            return account.getUsername();
        else
            return "";
    }

    private RipplerContainer createAccountItem(Account account) {
        VersionListItem item = new VersionListItem(account.getCharacter(), accountSubtitle(account));
        RipplerContainer rippler = new RipplerContainer(item);
        item.setOnSettingsButtonClicked(e -> {
            AccountPage accountPage = new AccountPage(account, item);
            JFXPopup popup = new JFXPopup(accountPage);
            accountPage.setOnDelete(popup::hide);
            popup.show((Node) e.getSource(), JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, e.getX(), e.getY());
        });
        rippler.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                selectedAccount.set(account);
            }
        });
        rippler.getProperties().put("account", account);
        rippler.maxWidthProperty().bind(leftPane.widthProperty());

        if (account instanceof YggdrasilAccount) {
            Image image = AccountHelper.getSkin((YggdrasilAccount) account, 4);
            item.setImage(image, AccountHelper.getViewport(4));
        } else {
            item.setImage(AccountHelper.getDefaultSkin(account.getUUID(), 4), AccountHelper.getViewport(4));
        }

        if (account instanceof AuthlibInjectorAccount) {
            FXUtils.installTooltip(rippler, 500, 5000, 0, new Tooltip(((AuthlibInjectorAccount) account).getServer().getName()));
        }

        // update skin
        if (account instanceof YggdrasilAccount) {
            AccountHelper.refreshSkinAsync((YggdrasilAccount) account)
                    .subscribe(Schedulers.javafx(), () -> {
                        Image image = AccountHelper.getSkin((YggdrasilAccount) account, 4);
                        item.setImage(image, AccountHelper.getViewport(4));
                    });
        }

        return rippler;
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
                    ((VersionListItem) ((RipplerContainer) node).getContainer()).setGameVersion(current ? i18n("profile.selected") : "");
                }
            }
        });
    }

    private void onProfilesLoading() {
        LinkedList<RipplerContainer> list = new LinkedList<>();
        for (Profile profile : Settings.instance().getProfiles()) {
            VersionListItem item = new VersionListItem(Profiles.getProfileDisplayName(profile));
            RipplerContainer ripplerContainer = new RipplerContainer(item);
            item.setOnSettingsButtonClicked(e -> Controllers.getDecorator().showPage(new ProfilePage(profile)));
            ripplerContainer.setOnMouseClicked(e -> Settings.instance().setSelectedProfile(profile));
            ripplerContainer.getProperties().put("profile", profile.getName());
            ripplerContainer.maxWidthProperty().bind(leftPane.widthProperty());
            list.add(ripplerContainer);
        }
        Platform.runLater(() -> profilePane.getChildren().setAll(list));
    }

    public void showUpdate() {
        launcherSettingsItem.getLabel().setText(i18n("update.found"));
        launcherSettingsItem.getLabel().setTextFill(Color.RED);
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
