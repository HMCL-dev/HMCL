// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.scene.control.Label;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.Nullable;

/// @author Glavo
public final class LineTextPane extends LineComponent {

    private static final String DEFAULT_STYLE_CLASS = "line-text-pane";

    public LineTextPane() {
        this.getStyleClass().addAll(DEFAULT_STYLE_CLASS);
    }

    /// The lazily created text displayed to the right of the title.
    private @Nullable StringProperty text;

    /// Returns the right-side text property; null and empty text omit the label.
    public StringProperty textProperty() {
        if (text == null) {
            text = new StringPropertyBase() {
                /// The reusable right-side label, created when nonempty text is assigned.
                private @Nullable Label rightLabel;

                /// Returns the pane owning this property.
                @Override
                public Object getBean() {
                    return LineTextPane.this;
                }

                /// Returns the property name.
                @Override
                public String getName() {
                    return "text";
                }

                /// Updates the label and attaches it directly to the row while nonempty.
                @Override
                protected void invalidated() {
                    @Nullable String text = get();
                    if (text != null && !text.isEmpty()) {
                        if (rightLabel == null) {
                            rightLabel = FXUtils.newSafeTruncatedLabel();
                            FXUtils.copyOnDoubleClick(rightLabel);
                        }
                        rightLabel.setText(text);
                        container.setTrailing(rightLabel);
                    } else {
                        if (rightLabel != null)
                            rightLabel.setText(null);

                        container.setTrailing(null);
                    }
                }
            };
        }
        return text;
    }

    /// Returns the right-side text, or null if none is assigned.
    public @Nullable String getText() {
        return textProperty().get();
    }

    /// Sets the right-side text; null and empty text omit the label.
    public void setText(@Nullable String text) {
        textProperty().set(text);
    }
}
