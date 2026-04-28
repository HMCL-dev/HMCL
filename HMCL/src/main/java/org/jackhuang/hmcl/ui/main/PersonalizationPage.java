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

import com.jfoenix.controls.*;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import org.jackhuang.hmcl.setting.FontManager;
import org.jackhuang.hmcl.theme.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.util.Locale;
import java.util.Optional;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class PersonalizationPage extends StackPane {

    private static int snapOpacity(double val) {
        if (val <= 0) {
            return 0;
        } else if (Double.isNaN(val) || val >= 100.) {
            return 100;
        }

        int prevTick = (int) (val / 5);
        int prevTickValue = prevTick * 5;
        int nextTickValue = (prevTick + 1) * 5;

        return (val - prevTickValue) > (nextTickValue - val) ? nextTickValue : prevTickValue;
    }

    public PersonalizationPage() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);

        ComponentList themeList = new ComponentList();
        {
            var brightnessPane = new LineSelectButton<String>();
            brightnessPane.setTitle(i18n("settings.launcher.brightness"));
            brightnessPane.setConverter(name -> i18n("settings.launcher.brightness." + name));
            brightnessPane.setItems("auto", "light", "dark");
            brightnessPane.valueProperty().bindBidirectional(config().themeBrightnessProperty());

            themeList.getContent().add(brightnessPane);
        }
        {
            LineToggleButton titleTransparentButton = new LineToggleButton();
            themeList.getContent().add(titleTransparentButton);
            titleTransparentButton.selectedProperty().bindBidirectional(config().titleTransparentProperty());
            titleTransparentButton.setTitle(i18n("settings.launcher.title_transparent"));
        }
        {
            LineToggleButton animationButton = new LineToggleButton();
            themeList.getContent().add(animationButton);
            animationButton.selectedProperty().bindBidirectional(config().animationDisabledProperty());
            animationButton.setTitle(i18n("settings.launcher.turn_off_animations"));
            animationButton.setSubtitle(i18n("settings.take_effect_after_restart"));
        }
        {
            var themeSublist = new LineSelectButton<Theme>();
        }

        content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.appearance")), themeList);

        {
            ComponentList logPane = new ComponentList();

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

                        FontComboBox cboLogFont = new FontComboBox();
                        cboLogFont.valueProperty().bindBidirectional(config().fontFamilyProperty());

                        JFXTextField txtLogFontSize = new JFXTextField();
                        FXUtils.setLimitWidth(txtLogFontSize, 50);
                        FXUtils.bind(txtLogFontSize, config().fontSizeProperty(), SafeStringConverter.fromFiniteDouble()
                                .restrict(it -> it > 0)
                                .fallbackTo(12.0)
                                .asPredicate(Validator.addTo(txtLogFontSize)));

                        JFXButton clearButton = FXUtils.newToggleButton4(SVG.RESTORE);
                        clearButton.setOnAction(e -> cboLogFont.setValue(null));

                        FXUtils.installFastTooltip(clearButton, i18n("button.reset"));

                        hBox.getChildren().setAll(cboLogFont, txtLogFontSize, clearButton);

                        borderPane.setRight(hBox);
                    }
                }

                Label lblLogFontDisplay = new Label("[23:33:33] [Client Thread/INFO] [WaterPower]: Loaded mod WaterPower.");
                lblLogFontDisplay.fontProperty().bind(Bindings.createObjectBinding(
                        () -> Font.font(Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT), config().getFontSize()),
                        config().fontFamilyProperty(), config().fontSizeProperty()));

                fontPane.getChildren().add(lblLogFontDisplay);

                logPane.getContent().add(fontPane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.log")), logPane);
        }

        {
            ComponentList fontPane = new ComponentList();

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
                        hBox.setSpacing(8);

                        FontComboBox cboFont = new FontComboBox();
                        cboFont.setValue(config().getLauncherFontFamily());
                        FXUtils.onChange(cboFont.valueProperty(), FontManager::setFontFamily);

                        JFXButton clearButton = FXUtils.newToggleButton4(SVG.RESTORE);
                        clearButton.setOnAction(e -> cboFont.setValue(null));

                        FXUtils.installFastTooltip(clearButton, i18n("button.reset"));

                        hBox.getChildren().setAll(cboFont, clearButton);

                        borderPane.setRight(hBox);
                    }
                }

                vbox.getChildren().add(new Label("Hello Minecraft! Launcher"));

                fontPane.getContent().add(vbox);
            }

            {
                var fontAntiAliasingPane = new LineSelectButton<Optional<FontSmoothingType>>();
                fontAntiAliasingPane.setTitle(i18n("settings.launcher.font.anti_aliasing"));
                fontAntiAliasingPane.setSubtitle(i18n("settings.take_effect_after_restart"));
                fontAntiAliasingPane.setConverter(value ->
                        value.isPresent()
                                ? i18n("settings.launcher.font.anti_aliasing." + value.get().name().toLowerCase(Locale.ROOT))
                                : i18n("settings.launcher.font.anti_aliasing.auto")
                );
                fontAntiAliasingPane.setItems(
                        Optional.empty(),
                        Optional.of(FontSmoothingType.LCD),
                        Optional.of(FontSmoothingType.GRAY)
                );

                String fontAntiAliasing = globalConfig().getFontAntiAliasing();
                if ("lcd".equalsIgnoreCase(fontAntiAliasing)) {
                    fontAntiAliasingPane.setValue(Optional.of(FontSmoothingType.LCD));
                } else if ("gray".equalsIgnoreCase(fontAntiAliasing)) {
                    fontAntiAliasingPane.setValue(Optional.of(FontSmoothingType.GRAY));
                } else {
                    fontAntiAliasingPane.setValue(Optional.empty());
                }

                FXUtils.onChange(fontAntiAliasingPane.valueProperty(), value ->
                        globalConfig().setFontAntiAliasing(value.map(it -> it.name().toLowerCase(Locale.ROOT))
                                .orElse(null)));

                fontPane.getContent().add(fontAntiAliasingPane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.font")), fontPane);
        }
    }
}
