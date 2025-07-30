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
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.setting.FontManager;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Locale;
import java.util.Optional;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class PersonalizationPage extends StackPane {

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
            BorderPane themePane = new BorderPane();
            themeList.getContent().add(themePane);

            Label left = new Label(i18n("settings.launcher.theme"));
            BorderPane.setAlignment(left, Pos.CENTER_LEFT);
            themePane.setLeft(left);

            StackPane themeColorPickerContainer = new StackPane();
            themeColorPickerContainer.setMinHeight(30);
            themePane.setRight(themeColorPickerContainer);

            ColorPicker picker = new ColorPicker(Color.web(Theme.getTheme().getColor()));
            picker.getCustomColors().setAll(Theme.SUGGESTED_COLORS);
            picker.setOnAction(e ->
                    config().setTheme(Theme.custom(Theme.getColorDisplayName(picker.getValue()))));
            themeColorPickerContainer.getChildren().setAll(picker);
            Platform.runLater(() -> JFXDepthManager.setDepth(picker, 0));
        }
        {
            OptionToggleButton titleTransparentButton = new OptionToggleButton();
            themeList.getContent().add(titleTransparentButton);
            titleTransparentButton.selectedProperty().bindBidirectional(config().titleTransparentProperty());
            titleTransparentButton.setTitle(i18n("settings.launcher.title_transparent"));
        }
        {
            OptionToggleButton animationButton = new OptionToggleButton();
            themeList.getContent().add(animationButton);
            animationButton.selectedProperty().bindBidirectional(config().animationDisabledProperty());
            animationButton.setTitle(i18n("settings.launcher.turn_off_animations"));
        }
        content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.appearance")), themeList);

        {
            ComponentList componentList = new ComponentList();

            MultiFileItem<EnumBackgroundImage> backgroundItem = new MultiFileItem<>();
            ComponentSublist backgroundSublist = new ComponentSublist();
            backgroundSublist.getContent().add(backgroundItem);
            backgroundSublist.setTitle(i18n("launcher.background"));
            backgroundSublist.setHasSubtitle(true);

            backgroundItem.loadChildren(Arrays.asList(
                    new MultiFileItem.Option<>(i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
                            .setTooltip(i18n("launcher.background.default.tooltip")),
                    new MultiFileItem.Option<>(i18n("launcher.background.classic"), EnumBackgroundImage.CLASSIC),
                    new MultiFileItem.Option<>(i18n("launcher.background.translucent"), EnumBackgroundImage.TRANSLUCENT),
                    new MultiFileItem.FileOption<>(i18n("settings.custom"), EnumBackgroundImage.CUSTOM)
                            .setChooserTitle(i18n("launcher.background.choose"))
                            .addExtensionFilter(FXUtils.getImageExtensionFilter())
                            .bindBidirectional(config().backgroundImageProperty()),
                    new MultiFileItem.StringOption<>(i18n("launcher.background.network"), EnumBackgroundImage.NETWORK)
                            .setValidators(new URLValidator(true))
                            .bindBidirectional(config().backgroundImageUrlProperty())
            ));
            backgroundItem.selectedDataProperty().bindBidirectional(config().backgroundImageTypeProperty());
            backgroundSublist.subtitleProperty().bind(
                    new When(backgroundItem.selectedDataProperty().isEqualTo(EnumBackgroundImage.DEFAULT))
                            .then(i18n("launcher.background.default"))
                            .otherwise(config().backgroundImageProperty()));

            componentList.getContent().add(backgroundItem);
            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("launcher.background")), componentList);
        }

        {
            VBox bgSettings = new VBox(16);
            bgSettings.getStyleClass().add("card-non-transparent");
            {
                HBox hbox = new HBox(8);
                hbox.setAlignment(Pos.CENTER);
                hbox.setPadding(new Insets(0, 0, 0, 10));

                Label label1 = new Label(i18n("settings.launcher.background.settings.opacity"));
                Label label2 = new Label("%");

                double opa = config().getBackgroundImageOpacity();
                JFXSlider slider = new JFXSlider(0, 1, opa);
                HBox.setHgrow(slider, Priority.ALWAYS);

                JFXTextField textOpacity = new JFXTextField();
                textOpacity.setText(BigDecimal.valueOf(opa * 100).setScale(2, RoundingMode.HALF_UP).toString());
                textOpacity.setPrefWidth(60);

                AtomicReference<Double> lastValidOpacity = new AtomicReference<>(slider.getValue());
                slider.valueProperty().addListener((observable, oldValue, newValue) -> {
                    double opacity = newValue.doubleValue();
                    textOpacity.setText(BigDecimal.valueOf(opacity * 100).setScale(2, RoundingMode.HALF_UP).toString());
                    lastValidOpacity.set(opacity);
                    config().setBackgroundImageOpacity(opacity);
                });
                textOpacity.focusedProperty().addListener((observable, oldValue, newValue) -> {
                    if (!newValue){
                        try {
                            String text = textOpacity.getText().trim();
                            double opacity = Double.parseDouble(text) / 100;
                            if (opacity >= 0 && opacity <= 1) {
                                slider.setValue(opacity);
                            } else if (opacity < 0) {
                                slider.setValue(0);
                                textOpacity.setText("0.00");
                            } else if (opacity > 1) {
                                slider.setValue(1);
                                textOpacity.setText("100.00");
                            }
                        } catch (NumberFormatException ignored) {
                            slider.setValue(lastValidOpacity.get());
                            textOpacity.setText(BigDecimal.valueOf(lastValidOpacity.get() * 100).setScale(2, RoundingMode.HALF_UP).toString());
                        }
                    }
                });

                slider.setValueFactory(slider1 -> Bindings.createStringBinding(() -> String.format("%.2f", slider1.getValue()), slider1.valueProperty()));

                HBox.setMargin(label2, new Insets(0, 10, 0, 0));

                hbox.getChildren().setAll(label1, slider, textOpacity, label2);
                bgSettings.getChildren().add(hbox);
            }
            //hide the opacity setting when selecting a translucency type
            config().backgroundImageTypeProperty().addListener((observable, oldValue, newValue) -> bgSettings.setVisible(newValue != EnumBackgroundImage.TRANSLUCENT));
            content.getChildren().add(bgSettings);
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

                        FontComboBox cboLogFont = new FontComboBox();
                        cboLogFont.valueProperty().bindBidirectional(config().fontFamilyProperty());

                        JFXTextField txtLogFontSize = new JFXTextField();
                        FXUtils.setLimitWidth(txtLogFontSize, 50);
                        FXUtils.bind(txtLogFontSize, config().fontSizeProperty(), SafeStringConverter.fromFiniteDouble()
                                .restrict(it -> it > 0)
                                .fallbackTo(12.0)
                                .asPredicate(Validator.addTo(txtLogFontSize)));

                        hBox.getChildren().setAll(cboLogFont, txtLogFontSize);

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
                        hBox.setSpacing(8);

                        FontComboBox cboFont = new FontComboBox();
                        cboFont.setValue(config().getLauncherFontFamily());
                        FXUtils.onChange(cboFont.valueProperty(), FontManager::setFontFamily);

                        JFXButton clearButton = new JFXButton();
                        clearButton.getStyleClass().add("toggle-icon4");
                        clearButton.setGraphic(SVG.RESTORE.createIcon(Theme.blackFill(), -1));
                        clearButton.setOnAction(e -> cboFont.setValue(null));

                        hBox.getChildren().setAll(cboFont, clearButton);

                        borderPane.setRight(hBox);
                    }
                }

                vbox.getChildren().add(new Label("Hello Minecraft! Launcher"));

                fontPane.getContent().add(vbox);
            }

            {
                BorderPane fontAntiAliasingPane = new BorderPane();
                {
                    Label left = new Label(i18n("settings.launcher.font.anti_aliasing"));
                    BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                    fontAntiAliasingPane.setLeft(left);
                }

                {
                    @SuppressWarnings("unchecked")
                    JFXComboBox<Optional<FontSmoothingType>> cboAntiAliasing = new JFXComboBox<>(FXCollections.observableArrayList(
                            Optional.empty(),
                            Optional.of(FontSmoothingType.LCD),
                            Optional.of(FontSmoothingType.GRAY)
                    ));
                    String fontAntiAliasing = globalConfig().getFontAntiAliasing();
                    if ("lcd".equalsIgnoreCase(fontAntiAliasing)) {
                        cboAntiAliasing.setValue(Optional.of(FontSmoothingType.LCD));
                    } else if ("gray".equalsIgnoreCase(fontAntiAliasing)) {
                        cboAntiAliasing.setValue(Optional.of(FontSmoothingType.GRAY));
                    } else {
                        cboAntiAliasing.setValue(Optional.empty());
                    }
                    cboAntiAliasing.setConverter(FXUtils.stringConverter(value -> {
                        if (value.isPresent()) {
                            return i18n("settings.launcher.font.anti_aliasing." + value.get().name().toLowerCase(Locale.ROOT));
                        } else {
                            return i18n("settings.launcher.font.anti_aliasing.auto");
                        }
                    }));
                    FXUtils.onChange(cboAntiAliasing.valueProperty(), value ->
                            globalConfig().setFontAntiAliasing(value.map(it -> it.name().toLowerCase(Locale.ROOT))
                                    .orElse(null)));

                    fontAntiAliasingPane.setRight(cboAntiAliasing);
                }

                fontPane.getContent().add(fontAntiAliasingPane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.font")), fontPane);
        }
    }
}
