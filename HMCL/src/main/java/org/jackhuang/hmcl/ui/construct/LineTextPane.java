// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.scene.control.Label;
import org.jackhuang.hmcl.ui.FXUtils;

/// @author Glavo
public final class LineTextPane extends LineComponent {

    private static final String DEFAULT_STYLE_CLASS = "line-text-pane";

    public LineTextPane() {
        this.getStyleClass().addAll(DEFAULT_STYLE_CLASS);
    }

    private StringProperty text;

    public StringProperty textProperty() {
        if (text == null) {
            text = new StringPropertyBase() {
                private Label rightLabel;

                @Override
                public Object getBean() {
                    return LineTextPane.this;
                }

                @Override
                public String getName() {
                    return "text";
                }

                @Override
                protected void invalidated() {
                    String text = get();
                    if (text != null && !text.isEmpty()) {
                        if (rightLabel == null) {
                            rightLabel = FXUtils.newSafeTruncatedLabel();
                            FXUtils.copyOnDoubleClick(rightLabel);
                        }
                        rightLabel.setText(text);
                        setNode(IDX_TRAILING, rightLabel);
                    } else {
                        if (rightLabel != null)
                            rightLabel.setText(null);

                        setNode(IDX_TRAILING, null);
                    }
                }
            };
        }
        return text;
    }

    public String getText() {
        return textProperty().get();
    }

    public void setText(String text) {
        textProperty().set(text);
    }
}
