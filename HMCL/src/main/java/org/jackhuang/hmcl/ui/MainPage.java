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
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.VersionNumber;

import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MainPage extends StackPane implements DecoratorPage {
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", i18n("main_page"));

    private final VBox menu = new VBox();
    private final JFXPopup popup = new JFXPopup(menu);

    @FXML
    private StackPane main;
    @FXML
    private JFXButton btnLaunch;
    @FXML
    private JFXButton btnMenu;
    @FXML
    private Label lblCurrentGame;

    private Profile profile;
    {
        FXUtils.loadFXML(this, "/assets/fxml/main.fxml");

        FXUtils.onChangeAndOperate(ConfigHolder.config().enableMainPageGameListProperty(), newValue -> {
            if (newValue)
                getChildren().setAll(new GameVersionListPage());
            else
                getChildren().setAll(main);
        });

        btnLaunch.setClip(new Rectangle(-100, -100, 280, 200));
        btnMenu.setClip(new Rectangle(180, -100, 100, 200));
        menu.setMinWidth(200);

        StackPane graphic = new StackPane();
        Node svg = SVG.triangle(Theme.whiteFillBinding(), 10, 10);
        StackPane.setAlignment(svg, Pos.CENTER_RIGHT);
        graphic.getChildren().setAll(svg);
        graphic.setTranslateX(11);
        btnMenu.setGraphic(graphic);

        Profiles.selectedVersionProperty().addListener((o, a, version) -> {
            if (version != null) {
                lblCurrentGame.setText(version);
            } else {
                lblCurrentGame.setText(i18n("version.empty"));
            }
        });

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                loadVersions((HMCLGameRepository) event.getSource());
        });
        Profiles.selectedProfileProperty().addListener((a, b, newValue) -> profile = newValue);

        profile = Profiles.getSelectedProfile();
        if (profile != null) {
            if (profile.getRepository().isLoaded())
                loadVersions(profile.getRepository());
            else
                profile.getRepository().refreshVersionsAsync().start();
        }
    }

    private void loadVersions(HMCLGameRepository repository) {
        List<IconedMenuItem> children = repository.getVersions().parallelStream()
                .filter(version -> !version.isHidden())
                .sorted((a, b) -> VersionNumber.COMPARATOR.compare(VersionNumber.asVersion(a.getId()), VersionNumber.asVersion(b.getId())))
                .map(version -> new IconedMenuItem(null, version.getId(), () -> {
                    repository.getProfile().setSelectedVersion(version.getId());
                    Versions.launch(repository.getProfile(), version.getId());
                    popup.hide();
                }))
                .collect(Collectors.toList());
        JFXUtilities.runInFX(() -> {
            if (profile == repository.getProfile())
                menu.getChildren().setAll(children);
        });
    }

    @FXML
    private void launch() {
        Profile profile = Profiles.getSelectedProfile();
        Versions.launch(profile, profile.getSelectedVersion());
    }

    @FXML
    private void onMenu() {
        popup.show(btnMenu, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.RIGHT, 0, -btnMenu.getHeight());
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }
}
