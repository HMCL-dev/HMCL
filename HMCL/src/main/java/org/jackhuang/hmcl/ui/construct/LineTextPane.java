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
