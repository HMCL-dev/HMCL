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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXMasonryPane;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.ProfileChangedEvent;
import org.jackhuang.hmcl.event.ProfileLoadingEvent;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.ui.download.DownloadWizardProvider;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public final class MainPage extends StackPane implements DecoratorPage {

    private final StringProperty title = new SimpleStringProperty(this, "title", Main.i18n("main_page"));

    @FXML
    private JFXButton btnRefresh;

    @FXML
    private JFXButton btnAdd;

    @FXML
    private JFXMasonryPane masonryPane;

    {
        FXUtils.loadFXML(this, "/assets/fxml/main.fxml");

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(() -> Platform.runLater(this::loadVersions));
        EventBus.EVENT_BUS.channel(ProfileLoadingEvent.class).register(this::onProfilesLoading);
        EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).register(this::onProfileChanged);

        btnAdd.setOnMouseClicked(e -> Controllers.getDecorator().startWizard(new DownloadWizardProvider(), Main.i18n("install")));
        btnRefresh.setOnMouseClicked(e -> Settings.INSTANCE.getSelectedProfile().getRepository().refreshVersions());
    }

    private Node buildNode(Profile profile, String version, String game) {
        VersionItem item = new VersionItem();
        item.setGameVersion(game);
        item.setVersionName(version);
        item.setOnLaunchButtonClicked(e -> {
            if (Settings.INSTANCE.getSelectedAccount() == null)
                Controllers.dialog(Main.i18n("login.no_Player007"));
            else
                LauncherHelper.INSTANCE.launch(version, null);
        });
        item.setOnScriptButtonClicked(e -> {
            if (Settings.INSTANCE.getSelectedAccount() == null)
                Controllers.dialog(Main.i18n("login.no_Player007"));
            else {
                Controllers.inputDialog(Main.i18n("mainwindow.enter_script_name"), file -> {
                    LauncherHelper.INSTANCE.launch(version, file);
                });
            }
        });
        item.setOnSettingsButtonClicked(e -> {
            Controllers.getDecorator().showPage(Controllers.getVersionPage());
            Controllers.getVersionPage().load(version, profile);
        });
        File iconFile = profile.getRepository().getVersionIcon(version);
        if (iconFile.exists())
            item.setImage(new Image("file:" + iconFile.getAbsolutePath()));
        return item;
    }

    public void onProfilesLoading() {
        // TODO: Profiles
    }

    public void onProfileChanged(ProfileChangedEvent event) {
        Platform.runLater(() -> loadVersions(event.getProfile()));
    }

    private void loadVersions() {
        loadVersions(Settings.INSTANCE.getSelectedProfile());
    }

    private void loadVersions(Profile profile) {
        List<Node> children = new LinkedList<>();
        for (Version version : profile.getRepository().getVersions()) {
            children.add(buildNode(profile, version.getId(), Lang.nonNull(GameVersion.minecraftVersion(profile.getRepository().getVersionJar(version.getId())), "Unknown")));
        }
        FXUtils.resetChildren(masonryPane, children);
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }
}
