// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3ColorArea;
import org.glavo.m3fx.controls.M3ColorChannel;
import org.glavo.m3fx.controls.M3ColorSlider;
import org.glavo.m3fx.controls.M3ColorSwatch;
import org.glavo.m3fx.controls.M3ColorSwatchPicker;
import org.glavo.m3fx.controls.M3ColorSwatchRounding;
import org.glavo.m3fx.controls.M3ColorSwatchSize;
import org.glavo.m3fx.controls.M3Dialog;
import org.glavo.m3fx.controls.M3RgbColor;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays a color swatch and opens a dialog that commits changes only on confirmation.
@NotNullByDefault
public final class ColorPickerButton extends StackPane {
    /// Accepted hexadecimal RGB forms, with an optional leading hash.
    private static final Pattern RGB_HEX = Pattern.compile("#?(?:[A-Fa-f0-9]{3}){1,2}");

    /// The color displayed in the settings row, or null when no solid color is set.
    private final ObjectProperty<@Nullable Color> value = new SimpleObjectProperty<>(this, "value");
    /// The setting edited by the dialog.
    private final String title;
    /// Opaque suggested colors in display order.
    private final @Unmodifiable List<M3RgbColor> presets;

    /// Creates a button for choosing an opaque RGB color from the picker or optional presets.
    public ColorPickerButton(String title, List<Color> presets) {
        this.title = title;
        this.presets = presets.stream().map(ColorPickerButton::opaqueRgb).toList();
        var swatch = new M3ColorSwatch();
        swatch.setSize(M3ColorSwatchSize.EXTRA_SMALL);
        swatch.setMouseTransparent(true);
        swatch.colorProperty().bind(value.map(ColorPickerButton::opaqueRgb));
        var button = new M3Button();
        button.setVariant(M3ButtonVariant.OUTLINED);
        button.setGraphic(swatch);
        button.textProperty().bind(value.map(ThemeColor::getColorDisplayName).orElse(i18n("color.custom")));
        button.accessibleTextProperty().bind(button.textProperty().map(text -> title + ", " + text));
        button.setOnAction(event -> Controllers.dialog(createDialog()));
        getChildren().add(button);
    }

    /// Returns the committed color, or null for an empty swatch.
    public @Nullable Color getValue() {
        return value.get();
    }

    /// Updates the swatch to reflect the setting.
    public void setValue(@Nullable Color color) {
        value.set(color);
    }

    /// Returns the committed color property.
    public ObjectProperty<@Nullable Color> valueProperty() {
        return value;
    }

    /// Creates a fresh editing session from the current setting.
    M3Dialog createDialog() {
        var area = new M3ColorArea(opaqueRgb(Objects.requireNonNullElse(getValue(), Color.WHITE)));
        var hue = new M3ColorSlider(M3ColorChannel.HUE);
        hue.valueProperty().bindBidirectional(area.valueProperty());
        var adjustments = new VBox(4, area, hue);
        VBox.setVgrow(area, Priority.ALWAYS);

        var preview = new M3ColorSwatch();
        preview.colorProperty().bind(area.valueProperty());
        var field = FXUtils.newTextField();
        field.setPrefColumnCount(8);
        field.setPromptText("#RRGGBB");
        field.setAccessibleText("RGB");
        var rgb = Bindings.createObjectBinding(() -> parseRgb(field.getText()), field.textProperty());
        field.errorProperty().bind(rgb.isNull());
        var pickerHex = area.valueProperty().map(color -> ThemeColor.getColorDisplayName(color.toFxColor()));
        pickerHex.subscribe(hex -> {
            if (!hex.equals(ThemeColor.getColorDisplayName(rgb.get()))) {
                field.setText(hex);
            }
        });
        rgb.addListener((obs, old, color) -> {
            // Keep the picker's full precision when its rounded display value matches the text.
            if (color != null && !ThemeColor.getColorDisplayName(color).equals(pickerHex.getValue())) {
                area.setValue(new M3RgbColor(color));
            }
        });

        var valueRow = new HBox(8, preview, field);
        valueRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        var details = new VBox(12, valueRow);
        if (!presets.isEmpty()) {
            var palette = new M3ColorSwatchPicker();
            palette.setColumnCount(3);
            palette.setRounding(M3ColorSwatchRounding.DEFAULT);
            palette.setMaxWidth(Region.USE_PREF_SIZE);
            palette.setMinHeight(Region.USE_PREF_SIZE);
            palette.getItems().setAll(presets);
            area.valueProperty().subscribe(palette::selectColor);
            palette.selectedColorProperty().subscribe(color -> {
                if (color != null && !color.isEquivalentTo(area.getValue())) {
                    area.setValue(color);
                }
            });
            details.getChildren().add(palette);
        }
        var content = new HBox(16, adjustments, details);
        HBox.setHgrow(adjustments, Priority.ALWAYS);
        var dialog = new M3Dialog();
        var pane = dialog.getDialogPane();
        pane.setHeadline(title);
        pane.setContent(content);
        var cancel = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
        cancel.setCancelButton(true);
        var ok = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
        ok.setDefaultButton(true);
        ok.disableProperty().bind(field.errorProperty());
        ok.setOnAction(event -> setValue(area.getValue().toFxColor()));
        pane.getActions().setAll(cancel, ok);
        return dialog;
    }

    /// Returns the RGB channels at their original precision with full opacity.
    private static M3RgbColor opaqueRgb(Color color) {
        return new M3RgbColor(color.getRed(), color.getGreen(), color.getBlue());
    }

    /// Parses three or six hexadecimal digits, returning null for incomplete or invalid input.
    private static @Nullable Color parseRgb(String text) {
        String hex = text.strip();
        return RGB_HEX.matcher(hex).matches() ? Color.web(hex.startsWith("#") ? hex : "#" + hex) : null;
    }
}
