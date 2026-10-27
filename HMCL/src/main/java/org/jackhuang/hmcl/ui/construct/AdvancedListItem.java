// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.Nullable;

public class AdvancedListItem extends Control {
    public static final double LEFT_GRAPHIC_SIZE = 32;
    public static final double LEFT_ICON_SIZE = 20;
    public static final Insets LEFT_ICON_MARGIN = new Insets(0, 6, 0, 6);

    public static void setMargin(Node graphic, Insets margin) {
        BorderPane.setMargin(graphic, margin);
    }

    public static void setAlignment(Node graphic, Pos alignment) {
        BorderPane.setAlignment(graphic, alignment);
    }

    public AdvancedListItem() {
        getStyleClass().add("advanced-list-item");
        FXUtils.onClicked(this, () -> fireEvent(new ActionEvent()));
    }

    private final ObjectProperty<Node> leftGraphic = new SimpleObjectProperty<>(this, "leftGraphic");

    public ObjectProperty<Node> leftGraphicProperty() {
        return leftGraphic;
    }

    public Node getLeftGraphic() {
        return leftGraphic.get();
    }

    public void setLeftGraphic(Node leftGraphic) {
        this.leftGraphic.set(leftGraphic);
    }

    public void setLeftIcon(SVG svg) {
        Node icon = svg.createIcon(LEFT_ICON_SIZE);
        icon.setMouseTransparent(true);
        BorderPane.setMargin(icon, LEFT_ICON_MARGIN);
        BorderPane.setAlignment(icon, Pos.CENTER);
        leftGraphicProperty().set(icon);
    }

    private final ObjectProperty<Node> rightGraphic = new SimpleObjectProperty<>(this, "rightGraphic");

    public ObjectProperty<Node> rightGraphicProperty() {
        return rightGraphic;
    }

    public Node getRightGraphic() {
        return rightGraphic.get();
    }

    public void setRightGraphic(Node rightGraphic) {
        this.rightGraphic.set(rightGraphic);
    }

    public void setRightAction(SVG icon, Runnable action) {
        var button = FXUtils.newToggleButton4(icon, 14);
        button.setOnAction(e -> {
            action.run();
            e.consume();
        });
        setAlignment(button, Pos.CENTER);
        setRightGraphic(button);
    }

    /// The optional preparation action, retained independently of the default skin's lifetime.
    private @Nullable Runnable prepareAction;

    /// Returns the pending hover preparation action, or null if absent or already consumed.
    public @Nullable Runnable getPrepareAction() {
        return prepareAction;
    }

    /// Registers one-shot preparation with the default skin's [RipplerContainer].
    /// See [RipplerContainer#setPrepareAction(Runnable)] for timing and cancellation semantics.
    /// The action is retained before skin creation and across skin replacement, and cleared before
    /// execution. Null cancels it. Custom skins must explicitly support this action to execute it.
    /// This method must be called on the FX application thread.
    public void setPrepareAction(@Nullable Runnable action) {
        if (prepareAction == action)
            return;
        prepareAction = action;
        @Nullable Skin<?> skin = getSkin();
        if (skin instanceof AdvancedListItemSkin itemSkin)
            itemSkin.setPrepareAction(action);
    }

    private final StringProperty title = new SimpleStringProperty(this, "title");

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle");

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public void setSubtitle(String subtitle) {
        this.subtitle.set(subtitle);
    }

    private final BooleanProperty active = new SimpleBooleanProperty(this, "active");

    public BooleanProperty activeProperty() {
        return active;
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean active) {
        this.active.set(active);
    }

    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new SimpleObjectProperty<>(this, "onAction") {
        @Override
        protected void invalidated() {
            setEventHandler(ActionEvent.ACTION, get());
        }
    };

    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    public final void setOnAction(EventHandler<ActionEvent> value) {
        onActionProperty().set(value);
    }

    public final EventHandler<ActionEvent> getOnAction() {
        return onActionProperty().get();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AdvancedListItemSkin(this);
    }

}
