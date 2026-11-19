// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.AccessibleAttribute;
import javafx.scene.control.ListView;
import javafx.scene.text.Font;
import org.glavo.m3fx.controls.M3ComboBox;
import org.glavo.m3fx.controls.M3ComboBoxInputMode;
import org.jetbrains.annotations.NotNullByDefault;

/// Creates searchable font pickers with previews of the installed families.
@NotNullByDefault
public final class FontComboBox {
    /// Creates a picker that loads system fonts when it first enters a scene.
    public static M3ComboBox<String> create() {
        var box = new M3ComboBox<String>();
        box.setMinWidth(260);
        box.setInputMode(M3ComboBoxInputMode.FILTERED_SELECTION);
        box.styleProperty().bind(box.valueProperty().map(FontComboBox::familyStyle).orElse(""));
        box.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null && box.getItems().isEmpty())
                box.getItems().setAll(Font.getFamilies());
        });
        box.showingProperty().addListener((obs, old, showing) -> {
            if (showing && box.queryAccessibleAttribute(AccessibleAttribute.FOCUS_NODE) instanceof ListView<?> options)
                installPreviews(options);
        });
        return box;
    }

    /// Applies the family to each virtualized option while retaining the control's label formatting.
    private static <T> void installPreviews(ListView<T> options) {
        if (options.getProperties().putIfAbsent(FontComboBox.class, true) != null)
            return;
        var factory = options.getCellFactory();
        options.setCellFactory(view -> {
            var cell = factory.call(view);
            cell.styleProperty().bind(cell.itemProperty().map(item -> familyStyle(item.toString())).orElse(""));
            return cell;
        });
    }

    /// Quotes a font family for an inline JavaFX CSS declaration.
    private static String familyStyle(String family) {
        return "-fx-font-family: \"" + family.replace("\\", "\\\\").replace("\"", "\\\"") + "\";";
    }

    /// Prevents instantiation.
    private FontComboBox() {}
}
