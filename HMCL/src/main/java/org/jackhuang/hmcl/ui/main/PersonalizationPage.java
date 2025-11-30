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
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.binding.When;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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

import java.util.Arrays;
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
            BorderPane themePane = new BorderPane();
            themeList.getContent().add(themePane);

            Label left = new Label(i18n("settings.launcher.theme"));
            BorderPane.setAlignment(left, Pos.CENTER_LEFT);
            themePane.setLeft(left);

            StackPane themeColorPickerContainer = new StackPane();
            themeColorPickerContainer.setMinHeight(30);
            themePane.setRight(themeColorPickerContainer);

            ColorPicker picker = new JFXColorPicker(Color.web(Theme.getTheme().getColor()));
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
                    new MultiFileItem.FileOption<>(i18n("settings.custom"), EnumBackgroundImage.CUSTOM)
                            .setChooserTitle(i18n("launcher.background.choose"))
                            .addExtensionFilter(FXUtils.getImageExtensionFilter())
                            .bindBidirectional(config().backgroundImageProperty()),
                    new MultiFileItem.StringOption<>(i18n("launcher.background.network"), EnumBackgroundImage.NETWORK)
                            .setValidators(new URLValidator(true))
                            .bindBidirectional(config().backgroundImageUrlProperty()),
                    new MultiFileItem.PaintOption<>(i18n("launcher.background.paint"), EnumBackgroundImage.PAINT)
                            .bindBidirectional(config().backgroundPaintProperty())
            ));
            backgroundItem.selectedDataProperty().bindBidirectional(config().backgroundImageTypeProperty());
            backgroundSublist.subtitleProperty().bind(
                    new When(backgroundItem.selectedDataProperty().isEqualTo(EnumBackgroundImage.DEFAULT))
                            .then(i18n("launcher.background.default"))
                            .otherwise(config().backgroundImageProperty()));

            HBox opacityItem = new HBox(8);
            {
                opacityItem.setAlignment(Pos.CENTER);

                Label label = new Label(i18n("settings.launcher.background.settings.opacity"));

                JFXSlider slider = new JFXSlider(0, 100,
                        config().getBackgroundImageType() != EnumBackgroundImage.TRANSLUCENT
                                ? config().getBackgroundImageOpacity() : 50);
                slider.setShowTickMarks(true);
                slider.setMajorTickUnit(10);
                slider.setMinorTickCount(1);
                slider.setBlockIncrement(5);
                slider.setSnapToTicks(true);
                HBox.setHgrow(slider, Priority.ALWAYS);

                if (config().getBackgroundImageType() == EnumBackgroundImage.TRANSLUCENT) {
                    slider.setDisable(true);
                    config().backgroundImageTypeProperty().addListener(new ChangeListener<>() {
                        @Override
                        public void changed(ObservableValue<? extends EnumBackgroundImage> observable, EnumBackgroundImage oldValue, EnumBackgroundImage newValue) {
                            if (newValue != EnumBackgroundImage.TRANSLUCENT) {
                                config().backgroundImageTypeProperty().removeListener(this);
                                slider.setDisable(false);
                                slider.setValue(100);
                            }
                        }
                    });
                }

                Label textOpacity = new Label();
                FXUtils.setLimitWidth(textOpacity, 35);

                StringBinding valueBinding = Bindings.createStringBinding(() -> ((int) slider.getValue()) + "%", slider.valueProperty());
                textOpacity.textProperty().bind(valueBinding);
                slider.setValueFactory(s -> valueBinding);

                slider.valueProperty().addListener((observable, oldValue, newValue) ->
                        config().setBackgroundImageOpacity(snapOpacity(newValue.doubleValue())));

                opacityItem.getChildren().setAll(label, slider, textOpacity);
            }

            componentList.getContent().setAll(backgroundItem, opacityItem);
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
