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

import com.jfoenix.controls.JFXScrollPane;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.util.Collections;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class PersonalizationPage extends StackPane {

    public PersonalizationPage() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        JFXScrollPane.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);

        {
            ComponentList themeList = new ComponentList();
            BorderPane themePane = new BorderPane();
            themeList.getContent().setAll(themePane);

            Label left = new Label(i18n("settings.launcher.theme"));
            BorderPane.setAlignment(left, Pos.CENTER_LEFT);
            themePane.setLeft(left);

            StackPane themeColorPickerContainer = new StackPane();
            themeColorPickerContainer.setMinHeight(30);
            themePane.setRight(themeColorPickerContainer);

            ColorPicker picker = new ColorPicker(Color.web(config().getTheme().getColor()));
            picker.getCustomColors().setAll(Theme.SUGGESTED_COLORS);
            picker.setOnAction(e -> {
                Theme theme = Theme.custom(Theme.getColorDisplayName(picker.getValue()));
                config().setTheme(theme);
                Controllers.getScene().getStylesheets().setAll(theme.getStylesheets());
            });
            themeColorPickerContainer.getChildren().setAll(picker);
            Platform.runLater(() -> JFXDepthManager.setDepth(picker, 0));

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.appearance")), themeList);
        }

        {
            StackPane componentList = new StackPane();

            MultiFileItem<EnumBackgroundImage> backgroundItem = new MultiFileItem<>(true);
            backgroundItem.setTitle(i18n("launcher.background"));
            backgroundItem.setChooserTitle(i18n("launcher.background.choose"));
            backgroundItem.setHasSubtitle(true);
            backgroundItem.setCustomText(i18n("settings.custom"));
            backgroundItem.setStyle("-fx-padding: 8 0 0 0");

            backgroundItem.loadChildren(Collections.singletonList(
                    backgroundItem.createChildren(i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
            ), EnumBackgroundImage.CUSTOM);
            backgroundItem.customTextProperty().bindBidirectional(config().backgroundImageProperty());
            backgroundItem.selectedDataProperty().bindBidirectional(config().backgroundImageTypeProperty());
            backgroundItem.subtitleProperty().bind(
                    new When(backgroundItem.selectedDataProperty().isEqualTo(EnumBackgroundImage.DEFAULT))
                            .then(i18n("launcher.background.default"))
                            .otherwise(config().backgroundImageProperty()));

            componentList.getChildren().add(backgroundItem);
            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("launcher.background")), componentList);
        }

        {
            ComponentList logPane = new ComponentSublist();
            logPane.setTitle(i18n("settings.launcher.log"));

            {
                VBox fontPane = new VBox();
                fontPane.setSpacing(5);

                {
                    BorderPane borderPane = new BorderPane();
                    fontPane.getChildren().add(borderPane);
                    {
                        Label left = new Label(i18n("settings.launcher.log.font"));
                        BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                        borderPane.setLeft(left);
                    }

                    {
                        HBox hBox = new HBox();
                        hBox.setSpacing(3);

                        FontComboBox cboLogFont = new FontComboBox(12);
                        cboLogFont.valueProperty().bindBidirectional(config().fontFamilyProperty());

                        JFXTextField txtLogFontSize = new JFXTextField();
                        FXUtils.setLimitWidth(txtLogFontSize, 50);
                        txtLogFontSize.textProperty().bindBidirectional(config().fontSizeProperty(),
                                SafeStringConverter.fromFiniteDouble()
                                        .restrict(it -> it > 0)
                                        .fallbackTo(12.0)
                                        .asPredicate(Validator.addTo(txtLogFontSize)));


                        hBox.getChildren().setAll(cboLogFont, txtLogFontSize);

                        borderPane.setRight(hBox);
                    }
                }

                Label lblLogFontDisplay = new Label("[23:33:33] [Client Thread/INFO] [WaterPower]: Loaded mod WaterPower.");
                lblLogFontDisplay.fontProperty().bind(Bindings.createObjectBinding(
                        () -> Font.font(config().getFontFamily(), config().getFontSize()),
                        config().fontFamilyProperty(), config().fontSizeProperty()));

                fontPane.getChildren().add(lblLogFontDisplay);

                logPane.getContent().add(fontPane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.log")), logPane);
        }

        {
            ComponentSublist fontPane = new ComponentSublist();
            fontPane.setTitle(i18n("settings.launcher.font"));

            {
                VBox vbox = new VBox();
                vbox.setSpacing(5);

                {
                    BorderPane borderPane = new BorderPane();
                    vbox.getChildren().add(borderPane);
                    {
                        Label left = new Label(i18n("settings.launcher.font"));
                        BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                        borderPane.setLeft(left);
                    }

                    {
                        HBox hBox = new HBox();
                        hBox.setSpacing(3);

                        FontComboBox cboFont = new FontComboBox(12);
                        cboFont.valueProperty().bindBidirectional(config().fontFamilyProperty());

                        JFXTextField txtFontSize = new JFXTextField();
                        FXUtils.setLimitWidth(txtFontSize, 50);
                        txtFontSize.textProperty().bindBidirectional(config().fontSizeProperty(),
                                SafeStringConverter.fromFiniteDouble()
                                        .restrict(it -> it > 0)
                                        .fallbackTo(12.0)
                                        .asPredicate(Validator.addTo(txtFontSize)));


                        hBox.getChildren().setAll(cboFont, txtFontSize);

                        borderPane.setRight(hBox);
                    }
                }

                Label lblFontDisplay = new Label("Hello Minecraft! Launcher");
                lblFontDisplay.fontProperty().bind(Bindings.createObjectBinding(
                        () -> Font.font(config().getFontFamily(), config().getFontSize()),
                        config().fontFamilyProperty(), config().fontSizeProperty()));

                vbox.getChildren().add(lblFontDisplay);

                fontPane.getContent().add(vbox);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.font")), fontPane);
        }
    }
}
