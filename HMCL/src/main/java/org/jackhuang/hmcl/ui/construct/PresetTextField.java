// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.glavo.m3fx.controls.M3ComboBox;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Objects;

/// A free-text editor with an exposed dropdown of suggested values.
@NotNullByDefault
public final class PresetTextField extends StackPane {
    /// Accepts arbitrary input and fires an action on Enter or when a preset replaces the text.
    private final TextField editor = new TextField();

    /// Creates a field with the given suggestions in display order.
    public PresetTextField(List<String> presets, String accessibleText) {
        var choices = new M3ComboBox<String>();
        choices.getItems().setAll(presets);
        choices.setMaxWidth(Double.MAX_VALUE);
        choices.setAccessibleText(accessibleText);
        choices.setFocusTraversable(false);
        editor.setAccessibleText(accessibleText);
        editor.setMaxHeight(Double.MAX_VALUE);
        getStyleClass().add("preset-text-field");
        setMargin(editor, new Insets(0, 40, 0, 0));
        getChildren().setAll(choices, editor);

        editor.textProperty().addListener((obs, old, text) -> choices.setValue(text));
        choices.valueProperty().addListener((obs, old, preset) -> {
            if (!Objects.equals(editor.getText(), preset)) {
                editor.setText(preset);
                editor.positionCaret(editor.getLength());
                editor.fireEvent(new ActionEvent());
            }
        });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP
                    || choices.isShowing() && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.ESCAPE)) {
                choices.fireEvent(event.copyFor(choices, choices));
                event.consume();
            }
        });
        editor.focusedProperty().addListener((obs, old, focused) -> {
            choices.pseudoClassStateChanged(PseudoClass.getPseudoClass("editor-focused"), focused);
            if (!focused) choices.hide();
        });
    }

    /// Returns the editor for reading, validating, and committing custom values.
    public TextField getEditor() {
        return editor;
    }
}
