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
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.GameItem;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.javafx.MultiStepBinding;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MainPage extends StackPane implements DecoratorPage {
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", i18n("main_page"));

    private final PopupMenu menu = new PopupMenu();
    private final JFXPopup popup = new JFXPopup(menu);

    @FXML
    private StackPane main;
    @FXML
    private StackPane updatePane;
    @FXML
    private JFXButton btnLaunch;
    @FXML
    private JFXButton btnMenu;
    @FXML
    private JFXButton closeUpdateButton;
    @FXML
    private Label lblCurrentGame;
    @FXML
    private Label lblIcon;
    @FXML
    private TwoLineListItem lblLatestVersion;
    @FXML
    private Rectangle separator;

    {
        FXUtils.loadFXML(this, "/assets/fxml/main.fxml");

        btnLaunch.setClip(new Rectangle(-100, -100, 310, 200));
        btnMenu.setClip(new Rectangle(211, -100, 100, 200));
        menu.setMaxHeight(365);
        menu.setMinWidth(545);

        updatePane.visibleProperty().bind(UpdateChecker.outdatedProperty());
        closeUpdateButton.setGraphic(SVG.close(Theme.whiteFillBinding(), 10, 10));
        closeUpdateButton.setOnMouseClicked(event -> {
            Duration duration = Duration.millis(320);
            Timeline nowAnimation = new Timeline();
            nowAnimation.getKeyFrames().addAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(updatePane.translateXProperty(), 0, Interpolator.EASE_IN)),
                    new KeyFrame(duration,
                            new KeyValue(updatePane.translateXProperty(), 260, Interpolator.EASE_IN)),
                    new KeyFrame(duration, e -> {
                updatePane.visibleProperty().unbind();
                updatePane.setVisible(false);
            }));
            nowAnimation.play();
        });
        lblIcon.setGraphic(SVG.update(Theme.whiteFillBinding(), 20, 20));
        lblLatestVersion.titleProperty().bind(
                MultiStepBinding.of(UpdateChecker.latestVersionProperty())
                        .map(version -> version == null ? "" : i18n("update.bubble.title", version.getVersion())));

        StackPane graphic = new StackPane();
        Node svg = SVG.triangle(Theme.whiteFillBinding(), 10, 10);
        StackPane.setAlignment(svg, Pos.CENTER_RIGHT);
        graphic.getChildren().setAll(svg);
        graphic.setTranslateX(12);
        btnMenu.setGraphic(graphic);

        FXUtils.onChangeAndOperate(Profiles.selectedVersionProperty(), version -> {
            if (version != null) {
                lblCurrentGame.setText(version);
            } else {
                lblCurrentGame.setText(i18n("version.empty"));
            }
        });

        Profiles.registerVersionsListener(this::loadVersions);
    }

    private void loadVersions(Profile profile) {
        HMCLGameRepository repository = profile.getRepository();
        List<Node> children = repository.getVersions().parallelStream()
                .filter(version -> !version.isHidden())
                .sorted((a, b) -> VersionNumber.COMPARATOR.compare(VersionNumber.asVersion(a.getId()), VersionNumber.asVersion(b.getId())))
                .map(version -> {
                    StackPane pane = new StackPane();
                    GameItem item = new GameItem(profile, version.getId());
                    pane.getChildren().setAll(item);
                    pane.getStyleClass().setAll("menu-container");
                    item.setMouseTransparent(true);
                    RipplerContainer container = new RipplerContainer(pane);
                    container.setOnMouseClicked(e -> {
                        profile.setSelectedVersion(version.getId());
                        popup.hide();
                    });
                    return container;
                })
                .collect(Collectors.toList());
        JFXUtilities.runInFX(() -> {
            if (profile == Profiles.getSelectedProfile())
                menu.getContent().setAll(children);
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

    @FXML
    private void onUpgrade() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
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
