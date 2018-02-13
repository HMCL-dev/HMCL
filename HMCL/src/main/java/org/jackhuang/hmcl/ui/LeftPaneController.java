/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.ProfileChangedEvent;
import org.jackhuang.hmcl.event.ProfileLoadingEvent;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.AccountHelper;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.ClassTitle;
import org.jackhuang.hmcl.ui.construct.IconedItem;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;

import java.io.File;
import java.util.LinkedList;
import java.util.Objects;

public final class LeftPaneController {
    private final AdvancedListBox leftPane;
    private final VBox profilePane = new VBox();
    private final VersionListItem accountItem = new VersionListItem("", "");

    public LeftPaneController(AdvancedListBox leftPane) {
        this.leftPane = leftPane;

        leftPane.startCategory(Main.i18n("account").toUpperCase())
                .add(Lang.apply(new RipplerContainer(accountItem), rippler -> {
                    rippler.setOnMouseClicked(e -> Controllers.navigate(new AccountsPage()));
                    accountItem.setOnSettingsButtonClicked(() -> Controllers.navigate(new AccountsPage()));
                }))
                .startCategory(Main.i18n("launcher").toUpperCase())
                .add(Lang.apply(new IconedItem(SVG.gear("black", 20, 20), Main.i18n("settings.launcher")), iconedItem -> {
                    iconedItem.prefWidthProperty().bind(leftPane.widthProperty());
                    iconedItem.setOnMouseClicked(e -> Controllers.navigate(Controllers.getSettingsPage()));
                }))
                .add(new ClassTitle(Lang.apply(new BorderPane(), borderPane -> {
                    borderPane.setLeft(Lang.apply(new VBox(), vBox -> {
                        vBox.getChildren().setAll(new Text(Main.i18n("profile.title").toUpperCase()));
                    }));
                    JFXButton addProfileButton = new JFXButton();
                    addProfileButton.setGraphic(SVG.plus("black", 10, 10));
                    addProfileButton.getStyleClass().add("toggle-icon-tiny");
                    addProfileButton.setOnMouseClicked(e ->
                            Controllers.getDecorator().showPage(new ProfilePage(null)));
                    borderPane.setRight(addProfileButton);
                })))
                .add(profilePane);

        EventBus.EVENT_BUS.channel(ProfileLoadingEvent.class).register(this::onProfilesLoading);
        EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).register(this::onProfileChanged);
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(this::onRefreshedVersions);

        FXUtils.onChangeAndOperate(Settings.INSTANCE.selectedAccountProperty(), it -> {
            if (it == null) {
                accountItem.setVersionName(Main.i18n("account.missing"));
                accountItem.setGameVersion(Main.i18n("message.unknown"));
            } else {
                accountItem.setVersionName(Accounts.getCurrentCharacter(it));
                accountItem.setGameVersion(AccountsPage.accountType(it));
            }

            if (it instanceof YggdrasilAccount) {
                Image image = AccountHelper.getSkin((YggdrasilAccount) it, 4);
                accountItem.setImage(image, AccountHelper.getViewport(4));
            } else
                accountItem.setImage(AccountHelper.getDefaultSkin(it, 4), AccountHelper.getViewport(4));
        });
    }

    private void onProfileChanged(ProfileChangedEvent event) {
        Profile profile = event.getProfile();

        Platform.runLater(() -> {
            for (Node node : profilePane.getChildren()) {
                if (node instanceof RipplerContainer && node.getProperties().get("profile") instanceof Pair<?, ?>) {
                    ((RipplerContainer) node).setSelected(Objects.equals(((Pair) node.getProperties().get("profile")).getKey(), profile.getName()));
                }
            }
        });
    }

    private void onProfilesLoading() {
        LinkedList<RipplerContainer> list = new LinkedList<>();
        for (Profile profile : Settings.INSTANCE.getProfiles()) {
            VersionListItem item = new VersionListItem(profile.getName());
            RipplerContainer ripplerContainer = new RipplerContainer(item);
            item.setOnSettingsButtonClicked(() -> Controllers.getDecorator().showPage(new ProfilePage(profile)));
            ripplerContainer.setRipplerFill(Paint.valueOf("#757de8"));
            ripplerContainer.setOnMouseClicked(e -> {
                // clean selected property
                for (Node node : profilePane.getChildren())
                    if (node instanceof RipplerContainer)
                        ((RipplerContainer) node).setSelected(false);
                ripplerContainer.setSelected(true);
                Settings.INSTANCE.setSelectedProfile(profile);
            });
            ripplerContainer.getProperties().put("profile", new Pair<>(profile.getName(), item));
            ripplerContainer.maxWidthProperty().bind(leftPane.widthProperty());
            list.add(ripplerContainer);
        }
        Platform.runLater(() -> profilePane.getChildren().setAll(list));
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
                                            })).executor(),
                                    Main.i18n("modpack.installing"), "", null);
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

    private void checkAccount() {
        if (Settings.INSTANCE.getAccounts().isEmpty())
            Controllers.navigate(new AccountsPage());
    }
}
