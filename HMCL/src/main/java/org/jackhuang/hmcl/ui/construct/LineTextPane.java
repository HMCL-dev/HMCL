/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.ui.FXUtils;

/// @author Glavo
public final class LineTextPane extends LinePane {

    private static final String DEFAULT_STYLE_CLASS = "line-label-pane";

    public LineTextPane() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    private StringProperty text;

    public StringProperty textProperty() {
        if (text == null) {
            text = new StringPropertyBase() {
                private static final Insets LABEL_MARGIN = new Insets(0, 8, 0, 16);

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
                    if (text == null || text.isEmpty()) {
                        if (rightLabel != null)
                            rightLabel.setText(null);
                        LineTextPane.this.setRight(null);
                    } else {
                        if (rightLabel == null) {
                            rightLabel = FXUtils.newSafeTruncatedLabel();
                            FXUtils.copyOnDoubleClick(rightLabel);
                            BorderPane.setMargin(rightLabel, LABEL_MARGIN);
                            BorderPane.setAlignment(rightLabel, Pos.CENTER_RIGHT);
                        }
                        rightLabel.setText(text);
                        LineTextPane.this.setRight(rightLabel);
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
