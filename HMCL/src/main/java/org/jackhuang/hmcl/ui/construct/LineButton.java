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
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

/// @author Glavo
public final class LineButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-button";

    private static final int IDX_RIGHT_MESSAGE = LineComponentContainer.IDX_RIGHT;
    private static final int IDX_RIGHT_ICON = IDX_RIGHT_MESSAGE + 1;

    public static LineButton createNavigationButton() {
        var button = new LineButton();
        button.setRightIcon(SVG.ARROW_FORWARD);
        return button;
    }

    public LineButton() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        root.setMouseTransparent(true);

        FXUtils.onClicked(ripplerContainer, this::fire);
    }

    public void fire() {
        fireEvent(new ActionEvent());
    }

    private ObjectProperty<EventHandler<ActionEvent>> onAction;

    public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<>() {

                @Override
                protected void invalidated() {
                    setEventHandler(ActionEvent.ACTION, get());
                }

                @Override
                public Object getBean() {
                    return LineButton.this;
                }

                @Override
                public String getName() {
                    return "onAction";
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

    private StringProperty message;

    public StringProperty messageProperty() {
        if (message == null) {
            message = new StringPropertyBase() {
                private Label messageLabel;

                @Override
                public Object getBean() {
                    return LineButton.this;
                }

                @Override
                public String getName() {
                    return "message";
                }

                @Override
                protected void invalidated() {
                    String message = get();
                    if (message != null && !message.isEmpty()) {
                        if (messageLabel == null) {
                            messageLabel = new Label();
                            messageLabel.getStyleClass().add("subtitle-label");
                        }
                        messageLabel.setText(message);
                        root.setNode(IDX_RIGHT_MESSAGE, messageLabel);
                    } else if (messageLabel != null) {
                        messageLabel.setText("");
                        root.setNode(IDX_RIGHT_MESSAGE, null);
                    }
                }
            };
        }

        return message;
    }

    public String getMessage() {
        return message == null ? "" : message.get();
    }

    public void setMessage(String message) {
        messageProperty().set(message);
    }

    private ObjectProperty<Node> rightIcon;

    public ObjectProperty<Node> rightIconProperty() {
        if (rightIcon == null)
            rightIcon = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return LineButton.this;
                }

                @Override
                public String getName() {
                    return "rightIcon";
                }

                @Override
                protected void invalidated() {
                    root.setNode(IDX_RIGHT_ICON, get());
                }
            };


        return rightIcon;
    }

    public Node getRightIcon() {
        return rightIcon != null ? rightIcon.get() : null;
    }

    public void setRightIcon(Node rightIcon) {
        rightIconProperty().set(rightIcon);
    }

    public void setRightIcon(SVG rightIcon) {
        setRightIcon(rightIcon, SVG.DEFAULT_SIZE);
    }

    public void setRightIcon(SVG rightIcon, double size) {
        Node rightIconNode = rightIcon.createIcon(size);
        LineButton.setMargin(rightIconNode, new Insets(0, 8, 0, 8));
        setRightIcon(rightIconNode);
    }
}
