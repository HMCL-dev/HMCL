// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3ColorField;
import org.glavo.m3fx.controls.M3ColorPicker;
import org.glavo.m3fx.controls.M3ColorSwatch;
import org.glavo.m3fx.controls.M3ColorSwatchSize;
import org.glavo.m3fx.controls.M3Dialog;
import org.glavo.m3fx.controls.M3RgbColor;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays a color swatch and opens a dialog that commits changes only on confirmation.
@NotNullByDefault
public final class ColorPickerButton extends StackPane {
    /// The color displayed in the settings row, or null when no solid color is set.
    private final ObjectProperty<@Nullable Color> value = new SimpleObjectProperty<>(this, "value");
    /// The setting edited by the dialog.
    private final String title;
    /// Whether the setting supports translucent colors.
    private final boolean allowAlpha;
    /// Suggested colors in display order.
    private final @Unmodifiable List<Color> presets;

    /// Creates a color button with the given dialog title, alpha policy, and optional presets.
    public ColorPickerButton(String title, boolean allowAlpha, List<Color> presets) {
        this.title = title;
        this.allowAlpha = allowAlpha;
        this.presets = List.copyOf(presets);
        var swatch = new M3ColorSwatch();
        swatch.setSize(M3ColorSwatchSize.EXTRA_SMALL);
        swatch.setMouseTransparent(true);
        swatch.colorProperty().bind(value.map(M3RgbColor::new));
        var button = new M3Button();
        button.setVariant(M3ButtonVariant.OUTLINED);
        button.setGraphic(swatch);
        button.textProperty().bind(value.map(color -> hex(color, false)).orElse(i18n("color.custom")));
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
        var picker = new M3ColorPicker(new M3RgbColor(Objects.requireNonNullElse(getValue(), Color.WHITE)));
        picker.setShowAlpha(allowAlpha);
        picker.getPresets().setAll(presets.stream().map(M3RgbColor::new).toList());

        var dialog = new M3Dialog();
        var pane = dialog.getDialogPane();
        pane.setHeadline(title);
        pane.setContent(picker);
        var cancel = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
        cancel.setCancelButton(true);
        var ok = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
        ok.setDefaultButton(true);
        ok.setOnAction(event -> {
            if (picker.lookup(".m3-color-field") instanceof M3ColorField field) {
                // Commit a pending text edit without rounding an untouched color to eight-bit channels.
                String committed = hex(field.getValue().toFxColor(), field.isIncludeAlpha());
                if (!committed.equals(field.getText()) && !field.commit()) {
                    event.consume();
                    return;
                }
            }
            Color chosen = picker.getValue().toFxColor();
            setValue(allowAlpha ? chosen : Color.color(chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
        });
        pane.getActions().setAll(cancel, ok);
        return dialog;
    }

    /// Formats opaque colors as RGB and includes alpha for translucent colors or RGBA input.
    private static String hex(Color color, boolean includeAlpha) {
        return includeAlpha || color.getOpacity() < 1
                ? ThemeColor.getColorDisplayNameWithOpacity(color, color.getOpacity())
                : ThemeColor.getColorDisplayName(color);
    }
}
