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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.AnnouncementCard;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.GameItem;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jackhuang.hmcl.util.platform.JavaVersion;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.SINE;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MainPage extends StackPane implements DecoratorPage {
    private static final String ANNOUNCEMENT = "announcement";

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final PopupMenu menu = new PopupMenu();
    private final JFXPopup popup = new JFXPopup(menu);

    private final StringProperty currentGame = new SimpleStringProperty(this, "currentGame");
    private final BooleanProperty showUpdate = new SimpleBooleanProperty(this, "showUpdate");
    private final ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>(this, "latestVersion");
    private final ObservableList<Version> versions = FXCollections.observableArrayList();
    private final ObservableList<Node> versionNodes;
    private Profile profile;

    private VBox announcementPane;
    private final StackPane updatePane;
    private final JFXButton menuButton;

    {
        HBox titleNode = new HBox(8);
        titleNode.setPadding(new Insets(0, 0, 0, 2));
        titleNode.setAlignment(Pos.CENTER_LEFT);

        ImageView titleIcon = new ImageView();
        if (JavaVersion.CURRENT_JAVA.getParsedVersion() < 9) {
            // JavaFX 8 has some problems with @2x images
            // Force load the original icon
            try (InputStream is = MainPage.class.getResourceAsStream("/assets/img/icon.png")) {
                titleIcon.setImage(new Image(is, 20, 20, false, false));
            } catch (IOException ignored) {
            }
        } else {
            titleIcon.setImage(new Image("/assets/img/icon.png", 20, 20, false, false));
        }

        Label titleLabel = new Label(Metadata.FULL_TITLE);
        titleLabel.getStyleClass().add("jfx-decorator-title");
        titleNode.getChildren().setAll(titleIcon, titleLabel);

        state.setValue(new State(null, titleNode, false, false, true));

        setPadding(new Insets(20));

        if (Metadata.isNightly() || (Metadata.isDev() && !Objects.equals(Metadata.VERSION, config().getShownTips().get(ANNOUNCEMENT)))) {
            announcementPane = new VBox(16);
            if (Metadata.isNightly()) {
                announcementPane.getChildren().add(new AnnouncementCard(i18n("update.channel.nightly.title"), i18n("update.channel.nightly.hint")));
            } else if (Metadata.isDev()) {
                announcementPane.getChildren().add(new AnnouncementCard(i18n("update.channel.dev.title"), i18n("update.channel.dev.hint")));
            }
            getChildren().add(announcementPane);
        }

        updatePane = new StackPane();
        updatePane.setVisible(false);
        updatePane.getStyleClass().add("bubble");
        FXUtils.setLimitWidth(updatePane, 230);
        FXUtils.setLimitHeight(updatePane, 55);
        StackPane.setAlignment(updatePane, Pos.TOP_RIGHT);
        updatePane.setOnMouseClicked(e -> onUpgrade());
        FXUtils.onChange(showUpdateProperty(), this::showUpdate);

        {
            HBox hBox = new HBox();
            hBox.setSpacing(12);
            hBox.setAlignment(Pos.CENTER_LEFT);
            StackPane.setAlignment(hBox, Pos.CENTER_LEFT);
            StackPane.setMargin(hBox, new Insets(9, 12, 9, 16));
            {
                Label lblIcon = new Label();
                lblIcon.setGraphic(SVG.update(Theme.whiteFillBinding(), 20, 20));

                TwoLineListItem prompt = new TwoLineListItem();
                prompt.setSubtitle(i18n("update.bubble.subtitle"));
                prompt.setPickOnBounds(false);
                prompt.titleProperty().bind(BindingMapping.of(latestVersionProperty()).map(latestVersion ->
                        latestVersion == null ? "" : i18n("update.bubble.title", latestVersion.getVersion())));

                hBox.getChildren().setAll(lblIcon, prompt);
            }

            JFXButton closeUpdateButton = new JFXButton();
            closeUpdateButton.setGraphic(SVG.close(Theme.whiteFillBinding(), 10, 10));
            StackPane.setAlignment(closeUpdateButton, Pos.TOP_RIGHT);
            closeUpdateButton.getStyleClass().add("toggle-icon-tiny");
            StackPane.setMargin(closeUpdateButton, new Insets(5));
            closeUpdateButton.setOnMouseClicked(e -> closeUpdateBubble());

            updatePane.getChildren().setAll(hBox, closeUpdateButton);
        }

        StackPane launchPane = new StackPane();
        launchPane.getStyleClass().add("launch-pane");
        launchPane.setMaxWidth(230);
        launchPane.setMaxHeight(55);
        launchPane.setOnScroll(event -> {
            int index = IntStream.range(0, versions.size())
                    .filter(i -> versions.get(i).getId().equals(getCurrentGame()))
                    .findFirst().orElse(-1);
            if (index < 0) return;
            if (event.getDeltaY() > 0) {
                index--;
            } else {
                index++;
            }
            profile.setSelectedVersion(versions.get((index + versions.size()) % versions.size()).getId());
        });
        StackPane.setAlignment(launchPane, Pos.BOTTOM_RIGHT);
        {
            JFXButton launchButton = new JFXButton();
            launchButton.setPrefWidth(230);
            launchButton.setPrefHeight(55);
            //launchButton.setButtonType(JFXButton.ButtonType.RAISED);
            launchButton.setOnAction(e -> launch());
            launchButton.setDefaultButton(true);
            launchButton.setClip(new Rectangle(-100, -100, 310, 200));
            {
                VBox graphic = new VBox();
                graphic.setAlignment(Pos.CENTER);
                graphic.setTranslateX(-7);
                graphic.setMaxWidth(200);
                Label launchLabel = new Label(i18n("version.launch"));
                launchLabel.setStyle("-fx-font-size: 16px;");
                Label currentLabel = new Label();
                currentLabel.setStyle("-fx-font-size: 12px;");
                currentLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                    if (getCurrentGame() == null) {
                        return i18n("version.empty");
                    } else {
                        return getCurrentGame();
                    }
                }, currentGameProperty()));
                graphic.getChildren().setAll(launchLabel, currentLabel);

                launchButton.setGraphic(graphic);
            }

            Rectangle separator = new Rectangle();
            separator.setWidth(1);
            separator.setHeight(57);
            separator.setTranslateX(95);
            separator.setMouseTransparent(true);

            menuButton = new JFXButton();
            menuButton.setPrefHeight(55);
            menuButton.setPrefWidth(230);
            //menuButton.setButtonType(JFXButton.ButtonType.RAISED);
            menuButton.setStyle("-fx-font-size: 15px;");
            menuButton.setOnMouseClicked(e -> onMenu());
            menuButton.setClip(new Rectangle(211, -100, 100, 200));
            StackPane graphic = new StackPane();
            Node svg = SVG.triangle(Theme.foregroundFillBinding(), 10, 10);
            StackPane.setAlignment(svg, Pos.CENTER_RIGHT);
            graphic.getChildren().setAll(svg);
            graphic.setTranslateX(12);
            runInFX(() -> FXUtils.installFastTooltip(menuButton, i18n("version.switch")));
            menuButton.setGraphic(graphic);

            launchPane.getChildren().setAll(launchButton, separator, menuButton);
        }

        getChildren().addAll(updatePane, launchPane);

        menu.setMaxHeight(365);
        menu.setMaxWidth(545);
        menu.setAlwaysShowingVBar(true);
        menu.setOnMouseClicked(e -> popup.hide());
        versionNodes = MappedObservableList.create(versions, version -> {
            Node node = PopupMenu.wrapPopupMenuItem(new GameItem(profile, version.getId()));
            node.setOnMouseClicked(e -> profile.setSelectedVersion(version.getId()));
            return node;
        });
        Bindings.bindContent(menu.getContent(), versionNodes);
    }

    private void showUpdate(boolean show) {
        doAnimation(show);

        if (show && getLatestVersion() != null && !Objects.equals(config().getPromptedVersion(), getLatestVersion().getVersion())) {
            Controllers.dialog("", i18n("update.bubble.title", getLatestVersion().getVersion()), MessageDialogPane.MessageType.INFO, () -> {
                config().setPromptedVersion(getLatestVersion().getVersion());
                onUpgrade();
            });
        }
    }

    private void doAnimation(boolean show) {
        if (AnimationUtils.isAnimationEnabled()) {
            Duration duration = Duration.millis(320);
            Timeline nowAnimation = new Timeline();
            nowAnimation.getKeyFrames().addAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(updatePane.translateXProperty(), show ? 260 : 0, SINE)),
                    new KeyFrame(duration,
                            new KeyValue(updatePane.translateXProperty(), show ? 0 : 260, SINE)));
            if (show) nowAnimation.getKeyFrames().add(
                    new KeyFrame(Duration.ZERO, e -> updatePane.setVisible(true)));
            else nowAnimation.getKeyFrames().add(
                    new KeyFrame(duration, e -> updatePane.setVisible(false)));
            nowAnimation.play();
        } else {
            updatePane.setVisible(show);
        }
    }

    private void launch() {
        Versions.launch(Profiles.getSelectedProfile());
    }

    private void onMenu() {
        popup.show(menuButton, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.RIGHT, 0, -menuButton.getHeight());
    }

    private void onUpgrade() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    private void closeUpdateBubble() {
        showUpdate.unbind();
        showUpdate.set(false);
    }

    public void hideAnnouncementPane() {
        if (announcementPane != null) {
            config().getShownTips().put(ANNOUNCEMENT, Metadata.VERSION);
            Pane parent = (Pane) announcementPane.getParent();
            if (parent != null)
                parent.getChildren().remove(announcementPane);
            announcementPane = null;
        }
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public String getCurrentGame() {
        return currentGame.get();
    }

    public StringProperty currentGameProperty() {
        return currentGame;
    }

    public void setCurrentGame(String currentGame) {
        this.currentGame.set(currentGame);
    }

    public boolean isShowUpdate() {
        return showUpdate.get();
    }

    public BooleanProperty showUpdateProperty() {
        return showUpdate;
    }

    public void setShowUpdate(boolean showUpdate) {
        this.showUpdate.set(showUpdate);
    }

    public RemoteVersion getLatestVersion() {
        return latestVersion.get();
    }

    public ObjectProperty<RemoteVersion> latestVersionProperty() {
        return latestVersion;
    }

    public void setLatestVersion(RemoteVersion latestVersion) {
        this.latestVersion.set(latestVersion);
    }

    public void initVersions(Profile profile, List<Version> versions) {
        FXUtils.checkFxUserThread();
        this.profile = profile;
        this.versions.setAll(versions);
    }
}
