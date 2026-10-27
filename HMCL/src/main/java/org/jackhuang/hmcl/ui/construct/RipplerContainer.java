// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.*;
import javafx.css.converter.PaintConverter;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Wraps content with a region for hover, focus, press, and ripple feedback.
/// Feedback covers the entire pane, including padding, without replacing its background.
/// Construction and all subsequent changes must occur on the JavaFX application thread.
/// Only background presses trigger feedback in [FeedbackPosition#BACK] mode; content events
/// continue to bubble normally, but do not initiate outer press feedback.
/// Hover feedback remains available in an unfocused window. Losing window focus cancels
/// transient press feedback and suppresses keyboard focus feedback.
@NotNullByDefault
public class RipplerContainer extends StackPane {
    /// The CSS class assigned to this pane.
    private static final String DEFAULT_STYLE_CLASS = "rippler-container";

    /// The fallback paint before a stylesheet supplies a theme color.
    private static final Color DEFAULT_FEEDBACK_FILL = Color.BLACK;

    /// The content supplied by the caller.
    private final Node container;

    /// The sole feedback node, excluded from preferred-size calculations.
    private final InteractionLayer interactionLayer = new InteractionLayer();

    /// Updates feedback when control availability or window visibility changes.
    private final InvalidationListener availabilityListener = observable -> updateVisualState();

    /// Cancels transient presses on window focus loss without disabling subsequent hover feedback.
    private final InvalidationListener windowFocusListener = observable -> updateVisualState(!isWindowFocused());

    /// Reattaches window listeners when the scene moves between windows.
    private final InvalidationListener windowListener = observable -> observeWindow();

    /// The scene whose window property is currently observed.
    private @Nullable Scene observedScene;

    /// The window whose showing and focus properties are currently observed.
    private @Nullable Window observedWindow;

    /// Tracks pointer presence for direct entered/exited events as well as normal picking.
    private boolean pointerInside;

    /// Creates a wrapper with feedback behind its content.
    /// Sets the content's `pickOnBounds` to false so uncovered regions can receive input.
    ///
    /// @param container the content to wrap
    /// @throws NullPointerException if the content is null
    public RipplerContainer(Node container) {
        this.container = Objects.requireNonNull(container);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setPickOnBounds(false);
        interactionLayer.setManaged(false);
        interactionLayer.setFill(getFeedbackFill());
        updateChildren();

        interactionLayer.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && isFeedbackAvailable() && isPressFeedbackEnabled())
                interactionLayer.press(event.getX(), event.getY());
        });
        interactionLayer.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY)
                interactionLayer.release();
        });
        addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            pointerInside = true;
            updateVisualState();
            if (preparation != null)
                preparation.run();
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            pointerInside = false;
            updateVisualState();
            interactionLayer.release();
        });
        focusedProperty().addListener(observable -> updateVisualState());
        disabledProperty().addListener(availabilityListener);
        visibleProperty().addListener(availabilityListener);
        sceneProperty().addListener(observable -> observeScene());
        observeScene();
    }

    /// Removes listeners from the previous scene before observing the current one.
    private void observeScene() {
        if (observedScene != null)
            observedScene.windowProperty().removeListener(windowListener);
        observedScene = getScene();
        if (observedScene != null)
            observedScene.windowProperty().addListener(windowListener);
        observeWindow();
    }

    /// Transfers showing and focus listeners to the current scene's window.
    private void observeWindow() {
        if (observedWindow != null) {
            observedWindow.showingProperty().removeListener(availabilityListener);
            observedWindow.focusedProperty().removeListener(windowFocusListener);
        }
        observedWindow = observedScene == null ? null : observedScene.getWindow();
        if (observedWindow != null) {
            observedWindow.showingProperty().addListener(availabilityListener);
            observedWindow.focusedProperty().addListener(windowFocusListener);
        }
        updateVisualState(true);
    }

    /// Returns whether a visible, enabled wrapper belongs to a showing window, regardless of focus.
    private boolean isFeedbackAvailable() {
        return !isDisabled() && isVisible() && observedWindow != null && observedWindow.isShowing();
    }

    /// Returns whether the observed window currently owns keyboard focus.
    private boolean isWindowFocused() {
        return observedWindow != null && observedWindow.isFocused();
    }

    /// Supplies hover and focus states independently of whether press feedback is enabled.
    private void updateVisualState() {
        updateVisualState(false);
    }

    /// Updates persistent states and cancels presses when requested or feedback is unavailable.
    private void updateVisualState(boolean cancelPress) {
        boolean available = isFeedbackAvailable();
        interactionLayer.setHovered(available && pointerInside);
        interactionLayer.setFocusActive(available && isWindowFocused() && isFocused());
        if (cancelPress || !available)
            interactionLayer.reset();
    }

    /// Lays out content using normal StackPane constraints and fills the entire pane with feedback.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        interactionLayer.resize(getWidth(), getHeight());
        interactionLayer.relocate(0, 0);
    }

    /// Arranges the two existing children without introducing layout or clipping nodes.
    protected void updateChildren() {
        if (getPosition() == FeedbackPosition.BACK) {
            container.setPickOnBounds(false);
            getChildren().setAll(interactionLayer, container);
        } else {
            getChildren().setAll(container, interactionLayer);
        }
    }

    /// Selects whether feedback is above or below the wrapped content.
    private final ObjectProperty<FeedbackPosition> position = new SimpleObjectProperty<>(this, "position", FeedbackPosition.BACK) {
        /// Rejects null assignments before changing the stacking policy.
        @Override
        public void set(FeedbackPosition value) {
            super.set(Objects.requireNonNull(value));
        }

        /// Reorders existing children when the stacking policy changes.
        @Override
        protected void invalidated() {
            Objects.requireNonNull(get(), "Position must not be null");
            updateChildren();
        }
    };

    /// Returns the stacking policy property. Values, including bound values, must not be null.
    public ObjectProperty<FeedbackPosition> positionProperty() {
        return position;
    }

    /// Returns the stacking policy, initially [FeedbackPosition#BACK].
    public FeedbackPosition getPosition() {
        return position.get();
    }

    /// Places all feedback in front of or behind content. Assigning the existing value is a no-op.
    ///
    /// @throws NullPointerException if the position is null
    public void setPosition(FeedbackPosition value) {
        position.set(Objects.requireNonNull(value));
    }

    /// Returns the original content, which remains a direct child of this wrapper.
    public Node getContainer() {
        return container;
    }

    /// The optional action property, allocated only when requested or assigned a non-null handler.
    private @Nullable ObjectProperty<@Nullable EventHandler<ActionEvent>> onAction;

    /// Returns the handler property for actions originating from this wrapper, initially null.
    /// A non-null handler enables primary-click actions on the feedback background. Content
    /// nodes retain their own input handling; mouse-transparent content is picked through.
    /// Handled mouse clicks are consumed, including repeated clicks, while the resulting
    /// [ActionEvent] bubbles normally unless consumed. Actions from descendants do not invoke
    /// this handler. Clearing the handler removes the background click listener.
    ///
    /// Clicks invoke [#fire()] without waiting for animations or preparation. Disabling press
    /// feedback does not disable actions. Keyboard activation is left to the enclosing control.
    public ObjectProperty<@Nullable EventHandler<ActionEvent>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<>() {
                /// Returns the wrapper owning this property.
                @Override
                public Object getBean() {
                    return RipplerContainer.this;
                }

                /// Returns the action property name.
                @Override
                public String getName() {
                    return "onAction";
                }

                /// Installs or removes action dispatch only when a handler is present.
                @Override
                protected void invalidated() {
                    boolean enabled = get() != null;
                    interactionLayer.setOnMouseClicked(enabled ? RipplerContainer.this::handleBackgroundClick : null);
                    setEventHandler(ActionEvent.ACTION, enabled ? RipplerContainer.this::handleAction : null);
                }
            };
        }
        return onAction;
    }

    /// Returns the action handler, or null without allocating the property when unused.
    public @Nullable EventHandler<ActionEvent> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /// Sets the action handler; null removes it without allocating an unused property.
    public void setOnAction(@Nullable EventHandler<ActionEvent> handler) {
        if (onAction != null || handler != null)
            onActionProperty().set(handler);
    }

    /// Fires an action with this wrapper as its source and target unless disabled.
    /// Does not change visual feedback or run pending preparation.
    public void fire() {
        if (!isDisabled())
            fireEvent(new ActionEvent(this, this));
    }

    /// Consumes primary background clicks before dispatching an immediate action.
    private void handleBackgroundClick(MouseEvent event) {
        if (event.getTarget() == interactionLayer && event.getButton() == MouseButton.PRIMARY && !isDisabled()) {
            event.consume();
            fire();
        }
    }

    /// Delivers this wrapper's actions without treating descendant actions as background activation.
    private void handleAction(ActionEvent event) {
        @Nullable EventHandler<ActionEvent> handler = getOnAction();
        if (event.getTarget() == this && handler != null)
            handler.handle(event);
    }

    /// The optional one-shot preparation state; absent on wrappers that do not use prewarming.
    private @Nullable HoverPreparation preparation;

    /// Returns the pending preparation action, or null if none is registered or it was consumed.
    public @Nullable Runnable getPrepareAction() {
        return preparation == null ? null : preparation.action;
    }

    /// Registers an action to run once on the FX thread after a hovered state has settled.
    /// Replaces any pending action; null cancels it. The registration is cleared before invocation,
    /// including when the action throws. Assigning the same action reference is a no-op.
    ///
    /// Execution is deferred to the FX event queue; it does not wait for waveforms or guarantee
    /// that a frame has been presented by the display. With animations disabled or an already
    /// settled state, execution is also deferred.
    ///
    /// Leaving before the state settles prevents scheduling until a subsequent mouse entry.
    /// Once queued, the action remains scheduled regardless of hover, visibility, or feedback
    /// changes; only replacing or clearing the registration cancels it. Registering while already
    /// hovered checks the current state immediately. Scene-graph construction still occupies the FX thread.
    public void setPrepareAction(@Nullable Runnable action) {
        if (action == getPrepareAction())
            return;
        if (preparation != null) {
            interactionLayer.setOnStateSettled(null);
            preparation = null;
        }
        if (action != null) {
            preparation = new HoverPreparation(action);
            interactionLayer.setOnStateSettled(preparation);
            preparation.run();
        }
    }

    /// The CSS-configurable paint shared by all visual feedback states.
    private final StyleableObjectProperty<@Nullable Paint> feedbackFill = new StyleableObjectProperty<>(DEFAULT_FEEDBACK_FILL) {
        /// Returns the wrapper owning this property.
        @Override
        public Object getBean() {
            return RipplerContainer.this;
        }

        /// Returns the JavaFX property name.
        @Override
        public String getName() {
            return "feedbackFill";
        }

        /// Returns the stylesheet metadata for the feedback paint.
        @Override
        public CssMetaData<? extends Styleable, @Nullable Paint> getCssMetaData() {
            return StyleableProperties.FEEDBACK_FILL;
        }

        /// Repaints existing states and waveforms with the new paint.
        @Override
        protected void invalidated() {
            interactionLayer.setFill(get());
        }
    };

    /// Returns the paint property shared by hover, focus, press, and existing waveforms.
    /// Null hides feedback without changing event delivery.
    public StyleableObjectProperty<@Nullable Paint> feedbackFillProperty() {
        return feedbackFill;
    }

    /// Returns the shared paint, or null if feedback drawing is suppressed.
    public @Nullable Paint getFeedbackFill() {
        return feedbackFill.get();
    }

    /// Sets the shared paint. Its own transparency multiplies each state's opacity.
    public void setFeedbackFill(@Nullable Paint value) {
        feedbackFill.set(value);
    }

    /// Enables press feedback without disabling hover, focus, or event delivery.
    private final BooleanProperty pressFeedbackEnabled = new SimpleBooleanProperty(this, "pressFeedbackEnabled", true) {
        /// Cancels any existing press feedback when it becomes disabled.
        @Override
        protected void invalidated() {
            if (!get()) {
                interactionLayer.reset();
            }
        }
    };

    /// Returns the press feedback switch, initially true. Disabling it clears all waveforms
    /// and the held press, preserving hover and focus feedback and normal event delivery.
    public BooleanProperty pressFeedbackEnabledProperty() {
        return pressFeedbackEnabled;
    }

    /// Returns whether background presses may initiate visual feedback.
    public boolean isPressFeedbackEnabled() {
        return pressFeedbackEnabled.get();
    }

    /// Enables press feedback or immediately cancels it; enabling does not replay an existing press.
    public void setPressFeedbackEnabled(boolean value) {
        pressFeedbackEnabled.set(value);
    }

    /// Clears all held and fading waveforms and immediately restores the current hover/focus state.
    /// Suitable for clearing transient feedback when a virtualized cell is reused.
    public void resetFeedback() {
        interactionLayer.reset();
    }

    /// Schedules optional one-shot preparation when the hovered feedback state settles.
    @NotNullByDefault
    private final class HoverPreparation implements Runnable {
        /// The action retained until it is consumed or replaced.
        private final Runnable action;

        /// Whether this registration has already queued its action on the FX event queue.
        private boolean queued;

        /// Retains the action until it is consumed or replaced.
        private HoverPreparation(Runnable action) {
            this.action = action;
        }

        /// Queues preparation once after a hovered state settles, including without a transition.
        @Override
        public void run() {
            if (!queued && pointerInside && isFeedbackAvailable() && !interactionLayer.isStateTransitioning()) {
                queued = true;
                interactionLayer.setOnStateSettled(null);
                Platform.runLater(this::prepare);
            }
        }

        /// Consumes the registration before invocation, unless it has been replaced or cleared.
        private void prepare() {
            if (preparation == this) {
                setPrepareAction(null);
                action.run();
            }
        }
    }

    /// Returns this wrapper's stylesheet metadata, including inherited region properties.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    /// Returns the immutable stylesheet metadata shared by all wrappers.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /// Stores shared stylesheet metadata without allocating it for each wrapper.
    private static final class StyleableProperties {
        /// Maps the feedback paint to the `-jfx-feedback-fill` stylesheet property.
        private static final CssMetaData<RipplerContainer, @Nullable Paint> FEEDBACK_FILL = new CssMetaData<>("-jfx-feedback-fill", PaintConverter.getInstance(), DEFAULT_FEEDBACK_FILL) {
            /// Reports whether CSS can assign this paint property.
            @Override
            public boolean isSettable(RipplerContainer styleable) {
                return styleable.feedbackFill == null || !styleable.feedbackFill.isBound();
            }

            /// Returns the paint property receiving stylesheet values.
            @Override
            public StyleableProperty<@Nullable Paint> getStyleableProperty(RipplerContainer styleable) {
                return styleable.feedbackFillProperty();
            }
        };

        /// The inherited region metadata followed by the feedback paint metadata.
        private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            var styleables = new ArrayList<>(StackPane.getClassCssMetaData());
            styleables.add(FEEDBACK_FILL);
            STYLEABLES = List.copyOf(styleables);
        }
    }
}
