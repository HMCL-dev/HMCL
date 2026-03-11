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

import javafx.beans.property.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

/// @author Glavo
public class LineButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-button";

    private static final int IDX_TRAILING_TEXT = IDX_TRAILING;
    private static final int IDX_TRAILING_ICON = IDX_TRAILING + 1;

    public static LineButton createNavigationButton() {
        var button = new LineButton();
        button.setTrailingIcon(SVG.ARROW_FORWARD);
        return button;
    }

    public static LineButton createExternalLinkButton(String url) {
        var button = new LineButton();
        button.setTrailingIcon(SVG.OPEN_IN_NEW);
        if (url != null) {
            button.setOnAction(event -> FXUtils.openLink(url));
        }
        return button;
    }

    public LineButton() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        container.setMouseTransparent(true);
    }

    private ObjectProperty<EventHandler<ActionEvent>> onAction;

    public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return LineButton.this;
                }

                @Override
                public String getName() {
                    return "onAction";
                }

                @Override
                protected void invalidated() {
                    setEventHandler(ActionEvent.ACTION, get());
                }
            };
        }
        return onAction;
    }

    public EventHandler<ActionEvent> getOnAction() {
        return onActionProperty().get();
    }

    public void setOnAction(EventHandler<ActionEvent> value) {
        onActionProperty().set(value);
    }

    private StringProperty trailingText;

    public StringProperty trailingTextProperty() {
        if (trailingText == null) {
            trailingText = new StringPropertyBase() {
                private Label trailingTextLabel;

                @Override
                public Object getBean() {
                    return LineButton.this;
                }

                @Override
                public String getName() {
                    return "trailingText";
                }

                @Override
                protected void invalidated() {
                    String message = get();
                    if (message != null && !message.isEmpty()) {
                        if (trailingTextLabel == null) {
                            trailingTextLabel = new Label();
                            trailingTextLabel.getStyleClass().add("trailing-label");
                            trailingTextLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
                        }
                        trailingTextLabel.setText(message);
                        setNode(IDX_TRAILING_TEXT, trailingTextLabel);
                    } else if (trailingTextLabel != null) {
                        trailingTextLabel.setText("");
                        setNode(IDX_TRAILING_TEXT, null);
                    }
                }
            };
        }

        return trailingText;
    }

    public String getTrailingText() {
        return trailingText != null ? trailingText.get() : null;
    }

    public void setTrailingText(String trailingText) {
        trailingTextProperty().set(trailingText);
    }

    private ObjectProperty<Node> trailingIcon;

    public ObjectProperty<Node> trailingIconProperty() {
        if (trailingIcon == null)
            trailingIcon = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return LineButton.this;
                }

                @Override
                public String getName() {
                    return "trailingIcon";
                }

                @Override
                protected void invalidated() {
                    setNode(IDX_TRAILING_ICON, get());
                }
            };

        return trailingIcon;
    }

    public Node getTrailingIcon() {
        return trailingIcon != null ? trailingIcon.get() : null;
    }

    public void setTrailingIcon(Node trailingIcon) {
        trailingIconProperty().set(trailingIcon);
    }

    public void setTrailingIcon(SVG rightIcon) {
        setTrailingIcon(rightIcon, 20);
    }

    public void setTrailingIcon(SVG rightIcon, double size) {
        Node rightIconNode = rightIcon.createIcon(size);
        rightIconNode.getStyleClass().add("trailing-icon");
        setTrailingIcon(rightIconNode);
    }
}
