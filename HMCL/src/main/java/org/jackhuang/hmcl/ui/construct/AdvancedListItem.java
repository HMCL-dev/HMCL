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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Displays a two-line item with optional graphics and an action supplied by its skin.
@NotNullByDefault
public class AdvancedListItem extends Control {
    /// The conventional size of a left graphic, in pixels.
    public static final double LEFT_GRAPHIC_SIZE = 32;
    /// The size of icons created by [#setLeftIcon(SVG)], in pixels.
    public static final double LEFT_ICON_SIZE = 20;
    /// The margin applied by [#setLeftIcon(SVG)].
    public static final Insets LEFT_ICON_MARGIN = new Insets(0, 6, 0, 6);

    /// Sets the graphic's [BorderPane] margin constraint; null removes the constraint.
    public static void setMargin(Node graphic, @Nullable Insets margin) {
        BorderPane.setMargin(graphic, margin);
    }

    /// Sets the graphic's [BorderPane] alignment constraint; null removes the constraint.
    public static void setAlignment(Node graphic, @Nullable Pos alignment) {
        BorderPane.setAlignment(graphic, alignment);
    }

    /// Creates an item whose default skin routes background clicks to [#fire()].
    public AdvancedListItem() {
        getStyleClass().add("advanced-list-item");
    }

    /// Fires this item's action unless disabled, without waiting for feedback or preparation.
    public void fire() {
        if (!isDisabled())
            fireEvent(new ActionEvent(this, this));
    }

    /// The optional graphic displayed to the left of the text, initially null.
    private final ObjectProperty<@Nullable Node> leftGraphic = new SimpleObjectProperty<>(this, "leftGraphic");

    /// Returns the left graphic property, whose value may be null.
    public ObjectProperty<@Nullable Node> leftGraphicProperty() {
        return leftGraphic;
    }

    /// Returns the left graphic, or null if absent.
    public @Nullable Node getLeftGraphic() {
        return leftGraphic.get();
    }

    /// Sets the left graphic; null removes it.
    public void setLeftGraphic(@Nullable Node leftGraphic) {
        this.leftGraphic.set(leftGraphic);
    }

    /// Replaces the left graphic with a centered, mouse-transparent icon using the standard size and margin.
    public void setLeftIcon(SVG svg) {
        Node icon = svg.createIcon(LEFT_ICON_SIZE);
        icon.setMouseTransparent(true);
        BorderPane.setMargin(icon, LEFT_ICON_MARGIN);
        BorderPane.setAlignment(icon, Pos.CENTER);
        leftGraphicProperty().set(icon);
    }

    /// The optional graphic displayed to the right of the text, initially null.
    private @Nullable ObjectProperty<@Nullable Node> rightGraphic;

    /// Returns the right graphic property, whose value may be null.
    public ObjectProperty<@Nullable Node> rightGraphicProperty() {
        if (rightGraphic == null)
            rightGraphic = new SimpleObjectProperty<>(this, "rightGraphic");
        return rightGraphic;
    }

    /// Returns the right graphic, or null if absent.
    public @Nullable Node getRightGraphic() {
        return rightGraphic != null ? rightGraphic.get() : null;
    }

    /// Sets the right graphic; null removes it.
    public void setRightGraphic(@Nullable Node rightGraphic) {
        if (this.rightGraphic != null || rightGraphic != null)
            rightGraphicProperty().set(rightGraphic);
    }

    /// Replaces the right graphic with a centered icon button that runs the action when activated.
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

    /// The title text, initially null.
    private final StringProperty title = new SimpleStringProperty(this, "title");

    /// Returns the title property, whose value may be null.
    public StringProperty titleProperty() {
        return title;
    }

    /// Returns the title text, or null if unset.
    public @Nullable String getTitle() {
        return title.get();
    }

    /// Sets the title text; null clears it.
    public void setTitle(@Nullable String title) {
        this.title.set(title);
    }

    /// The subtitle text, initially null.
    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle");

    /// Returns the subtitle property, whose value may be null.
    public StringProperty subtitleProperty() {
        return subtitle;
    }

    /// Returns the subtitle text, or null if unset.
    public @Nullable String getSubtitle() {
        return subtitle.get();
    }

    /// Sets the subtitle text; null removes the subtitle line in the default skin.
    public void setSubtitle(@Nullable String subtitle) {
        this.subtitle.set(subtitle);
    }

    /// The active state, initially false, reflected by the default skin's selected pseudo-class.
    private final BooleanProperty active = new SimpleBooleanProperty(this, "active");

    /// Returns the active-state property.
    public BooleanProperty activeProperty() {
        return active;
    }

    /// Returns whether the item is active.
    public boolean isActive() {
        return active.get();
    }

    /// Sets whether the item is active.
    public void setActive(boolean active) {
        this.active.set(active);
    }

    /// The handler for actions originating from this item, excluding descendant controls.
    private final ObjectProperty<@Nullable EventHandler<ActionEvent>> onAction = new SimpleObjectProperty<>(this, "onAction") {
        /// Routes only this item's actions to the registered handler.
        @Override
        protected void invalidated() {
            @Nullable EventHandler<ActionEvent> handler = get();
            setEventHandler(ActionEvent.ACTION, handler == null ? null : event -> {
                if (event.getTarget() == AdvancedListItem.this)
                    handler.handle(event);
            });
        }
    };

    /// Returns the handler property for this item's actions, initially null.
    public final ObjectProperty<@Nullable EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /// Sets the item action handler; null removes it.
    public final void setOnAction(@Nullable EventHandler<ActionEvent> value) {
        onActionProperty().set(value);
    }

    /// Returns the item action handler, or null if none is registered.
    public final @Nullable EventHandler<ActionEvent> getOnAction() {
        return onActionProperty().get();
    }

    /// Creates the skin that lays out the graphics and text and handles background actions.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new AdvancedListItemSkin(this);
    }

}
