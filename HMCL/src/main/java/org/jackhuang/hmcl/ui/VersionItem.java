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
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.jackhuang.hmcl.setting.Theme;

import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionItem extends StackPane {
    @FXML
    private Pane icon;
    @FXML
    private VBox content;
    @FXML
    private StackPane header;
    @FXML
    private StackPane body;
    @FXML
    private JFXButton btnSettings;
    @FXML
    private JFXButton btnUpdate;
    @FXML
    private JFXButton btnLaunch;
    @FXML
    private JFXButton btnScript;
    @FXML
    private Label lblVersionName;
    @FXML
    private Label lblGameVersion;
    @FXML
    private Label lblLibraries;
    @FXML
    private ImageView iconView;

    private EventHandler<? super MouseEvent> launchClickedHandler = null;

    private void initializeComponents() {
        setPickOnBounds(false);
        FXUtils.setLimitWidth(this, 160);
        FXUtils.setLimitHeight(this, 149);

        content = new VBox();
        {
            header = new StackPane();
            VBox.setVgrow(header, Priority.ALWAYS);
            header.setPickOnBounds(false);
            header.setPadding(new Insets(8));
            header.setStyle("-fx-background-radius: 2 2 0 0; -fx-background-color: rgb(255,255,255,0.87);");
            {
                VBox headerContent = new VBox();
                headerContent.setPadding(new Insets(8, 8, 0, 8));
                {
                    lblVersionName = new Label();
                    lblVersionName.setStyle("-fx-font-size: 15;");
                    lblVersionName.setTextAlignment(TextAlignment.JUSTIFY);
                    lblVersionName.setWrapText(true);
                    headerContent.getChildren().add(lblVersionName);

                    lblGameVersion = new Label();
                    lblGameVersion.setStyle("-fx-font-size: 11;");
                    lblGameVersion.setTextAlignment(TextAlignment.JUSTIFY);
                    lblGameVersion.setWrapText(true);
                    headerContent.getChildren().add(lblGameVersion);

                    lblLibraries = new Label();
                    lblLibraries.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");
                    lblLibraries.setTextAlignment(TextAlignment.JUSTIFY);
                    lblLibraries.setWrapText(true);
                    headerContent.getChildren().add(lblLibraries);
                }
                header.getChildren().add(headerContent);
            }
            content.getChildren().add(header);

            body = new StackPane();
            body.setStyle("-fx-background-radius: 0 0 2 2; -fx-background-color: rgb(255,255,255,0.87); -fx-padding: 8;");
            body.setMinHeight(40);
            body.setPickOnBounds(false);
            {
                BorderPane bodyContent = new BorderPane();
                {
                    HBox hbox = new HBox();
                    hbox.setSpacing(8);
                    {
                        btnSettings = new JFXButton();
                        btnSettings.getStyleClass().add("toggle-icon4");
                        FXUtils.setLimitWidth(btnSettings, 30);
                        FXUtils.setLimitHeight(btnSettings, 30);
                        hbox.getChildren().add(btnSettings);

                        btnUpdate = new JFXButton();
                        btnUpdate.getStyleClass().add("toggle-icon4");
                        FXUtils.setLimitWidth(btnUpdate, 30);
                        FXUtils.setLimitHeight(btnUpdate, 30);
                        hbox.getChildren().add(btnUpdate);
                    }
                    bodyContent.setLeft(hbox);
                }
                {
                    HBox hbox = new HBox();
                    hbox.setSpacing(8);
                    {
                        btnScript = new JFXButton();
                        btnScript.getStyleClass().add("toggle-icon4");
                        FXUtils.setLimitWidth(btnScript, 30);
                        FXUtils.setLimitHeight(btnScript, 30);
                        hbox.getChildren().add(btnScript);

                        btnLaunch = new JFXButton();
                        btnLaunch.getStyleClass().add("toggle-icon4");
                        FXUtils.setLimitWidth(btnLaunch, 30);
                        FXUtils.setLimitHeight(btnLaunch, 30);
                        hbox.getChildren().add(btnLaunch);
                    }
                    bodyContent.setRight(hbox);
                }
                body.getChildren().setAll(bodyContent);
            }
            content.getChildren().add(body);
        }
        getChildren().add(content);

        icon = new StackPane();
        StackPane.setAlignment(icon, Pos.TOP_RIGHT);
        icon.setPickOnBounds(false);
        {
            iconView = new ImageView();
            StackPane.setAlignment(iconView, Pos.CENTER_RIGHT);
            StackPane.setMargin(iconView, new Insets(0, 12, 0, 0));
            iconView.setImage(new Image("/assets/img/icon.png"));
            icon.getChildren().add(iconView);
        }
        getChildren().add(icon);
    }

    public VersionItem() {
        initializeComponents();
        setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 5.0, 0.12, -1.0, 1.0));
        btnSettings.setGraphic(SVG.gear(Theme.blackFillBinding(), 15, 15));
        btnUpdate.setGraphic(SVG.update(Theme.blackFillBinding(), 15, 15));
        btnLaunch.setGraphic(SVG.launch(Theme.blackFillBinding(), 15, 15));
        btnScript.setGraphic(SVG.script(Theme.blackFillBinding(), 15, 15));

        JFXUtilities.runInFX(() -> {
            FXUtils.installTooltip(btnSettings, i18n("version.settings"));
            FXUtils.installTooltip(btnUpdate, i18n("version.update"));
            FXUtils.installTooltip(btnLaunch, i18n("version.launch"));
            FXUtils.installTooltip(btnScript, i18n("version.launch_script"));
        });

        icon.translateYProperty().bind(Bindings.createDoubleBinding(() -> header.getBoundsInParent().getHeight() - icon.getHeight() / 2 - 16, header.boundsInParentProperty(), icon.heightProperty()));
        FXUtils.limitSize(iconView, 32, 32);

        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
                if (e.getClickCount() == 2)
                    Optional.ofNullable(launchClickedHandler).ifPresent(h -> h.handle(e));
        });
    }

    public void setUpdate(boolean update) {
        btnUpdate.setVisible(update);
    }

    public void setVersionName(String versionName) {
        lblVersionName.setText(versionName);
    }

    public void setGameVersion(String gameVersion) {
        lblGameVersion.setText(gameVersion);
    }

    public void setLibraries(String libraries) {
        lblLibraries.setText(libraries);
    }

    public void setImage(Image image) {
        iconView.setImage(image);
    }

    public void setOnSettingsButtonClicked(EventHandler<? super MouseEvent> handler) {
        btnSettings.setOnMouseClicked(handler);
    }

    public void setOnScriptButtonClicked(EventHandler<? super MouseEvent> handler) {
        btnScript.setOnMouseClicked(handler);
    }

    public void setOnLaunchButtonClicked(EventHandler<? super MouseEvent> handler) {
        launchClickedHandler = handler;
        btnLaunch.setOnMouseClicked(handler);
    }

    public void setOnUpdateButtonClicked(EventHandler<? super MouseEvent> handler) {
        btnUpdate.setOnMouseClicked(handler);
    }
}
