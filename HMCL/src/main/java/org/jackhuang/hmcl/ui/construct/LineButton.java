// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.Nullable;

/// @author Glavo
public class LineButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-button";

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
    }

    /// The private group containing only this button's trailing text and icon.
    private @Nullable HBox trailingBox;

    /// Creates the trailing group on first use and retains it across text and icon changes.
    private HBox getTrailingBox() {
        if (trailingBox == null) {
            HBox box = new HBox(SPACING);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPickOnBounds(false);
            trailingBox = box;
            updateTrailingBox();
            container.setTrailing(box);
        }
        return trailingBox;
    }

    /// Updates group participation from the text value and the icon's own state.
    private void updateTrailingBox() {
        if (trailingBox != null) {
            @Nullable String text = getTrailingText();
            boolean hasText = text != null && !text.isEmpty();
            @Nullable Node icon = getTrailingIcon();
            trailingBox.setManaged(hasText || icon != null && icon.isManaged());
            trailingBox.setVisible(hasText || icon != null && icon.isVisible());
        }
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
                    EventHandler<ActionEvent> handler = get();
                    setEventHandler(ActionEvent.ACTION, handler == null ? null : event -> {
                        if (event.getTarget() == LineButton.this) {
                            handler.handle(event);
                        }
                    });
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

    /// The lazily created trailing-text property.
    private @Nullable StringProperty trailingText;

    /// Returns the trailing-text property; null and empty text omit the label.
    public StringProperty trailingTextProperty() {
        if (trailingText == null) {
            trailingText = new StringPropertyBase() {
                /// The reusable label, retained when its text is cleared.
                private @Nullable Label trailingTextLabel;

                /// Returns the button owning this property.
                @Override
                public Object getBean() {
                    return LineButton.this;
                }

                /// Returns the property name.
                @Override
                public String getName() {
                    return "trailingText";
                }

                /// Updates the trailing label and places it before the icon when present.
                @Override
                protected void invalidated() {
                    @Nullable String message = get();
                    if (message != null && !message.isEmpty()) {
                        if (trailingTextLabel == null) {
                            trailingTextLabel = new Label();
                            trailingTextLabel.getStyleClass().add("trailing-label");
                            trailingTextLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
                            trailingTextLabel.setMouseTransparent(true);
                            trailingTextLabel.setMinWidth(50);
                            trailingTextLabel.setAlignment(Pos.CENTER_RIGHT);
                        }
                        trailingTextLabel.setText(message);
                        HBox box = getTrailingBox();
                        if (trailingTextLabel.getParent() != box)
                            box.getChildren().add(0, trailingTextLabel);
                    } else if (trailingTextLabel != null) {
                        trailingTextLabel.setText("");
                        getTrailingBox().getChildren().remove(trailingTextLabel);
                    }
                    updateTrailingBox();
                }
            };
        }

        return trailingText;
    }

    /// Returns the trailing text, or null if none is assigned.
    public @Nullable String getTrailingText() {
        return trailingText != null ? trailingText.get() : null;
    }

    /// Sets the trailing text; null and empty text omit the label.
    public void setTrailingText(@Nullable String trailingText) {
        trailingTextProperty().set(trailingText);
    }

    /// The lazily created trailing-icon property.
    private @Nullable ObjectProperty<@Nullable Node> trailingIcon;

    /// Returns the trailing-icon property; null removes the icon.
    public ObjectProperty<@Nullable Node> trailingIconProperty() {
        if (trailingIcon == null)
            trailingIcon = new ObjectPropertyBase<>() {
                /// The icon currently attached to the trailing group.
                private @Nullable Node previous;

                /// Tracks visibility and layout participation of the current icon only.
                private final InvalidationListener iconListener = observable -> updateTrailingBox();

                /// Returns the button owning this property.
                @Override
                public Object getBean() {
                    return LineButton.this;
                }

                /// Returns the property name.
                @Override
                public String getName() {
                    return "trailingIcon";
                }

                /// Replaces the icon at the end of the trailing group.
                @Override
                protected void invalidated() {
                    if (previous != null) {
                        previous.visibleProperty().removeListener(iconListener);
                        previous.managedProperty().removeListener(iconListener);
                        getTrailingBox().getChildren().remove(previous);
                    }
                    previous = get();
                    if (previous != null) {
                        previous.visibleProperty().addListener(iconListener);
                        previous.managedProperty().addListener(iconListener);
                        getTrailingBox().getChildren().add(previous);
                    }
                    updateTrailingBox();
                }
            };

        return trailingIcon;
    }

    /// Returns the trailing icon, or null if none is assigned.
    public @Nullable Node getTrailingIcon() {
        return trailingIcon != null ? trailingIcon.get() : null;
    }

    /// Sets the trailing icon; null removes it.
    public void setTrailingIcon(@Nullable Node trailingIcon) {
        trailingIconProperty().set(trailingIcon);
    }

    public void setTrailingIcon(SVG rightIcon) {
        setTrailingIcon(rightIcon, 20);
    }

    public void setTrailingIcon(SVG rightIcon, double size) {
        Node rightIconNode = rightIcon.createIcon(size);
        rightIconNode.getStyleClass().add("trailing-icon");
        rightIconNode.setMouseTransparent(true);
        setTrailingIcon(rightIconNode);
    }
}
