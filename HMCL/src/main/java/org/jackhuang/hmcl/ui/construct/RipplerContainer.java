// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.utils.TreeShowingProperty;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.*;
import javafx.css.converter.PaintConverter;
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
            if (preparation != null)
                preparation.armed = true;
            updateVisualState();
            if (preparation != null)
                preparation.run();
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            pointerInside = false;
            cancelPreparation();
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
        if (cancelPress || !available)
            cancelPreparation();
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
    /// Execution is deferred through a post-layout pulse and then the FX event queue; it does not
    /// wait for waveforms. With animations disabled or an already settled state, the same deferral
    /// applies. This does not guarantee that a frame has been presented by the display.
    ///
    /// Leaving, hiding, disabling, window focus loss, scene changes, and [#resetFeedback()] cancel
    /// the current attempt without consuming the action. A subsequent mouse entry can retry.
    /// Registering while already hovered starts an attempt immediately. Scene-graph construction
    /// in the action still occupies the FX thread.
    public void setPrepareAction(@Nullable Runnable action) {
        if (action == getPrepareAction())
            return;
        if (preparation != null) {
            preparation.dispose();
            preparation = null;
        }
        if (action != null) {
            preparation = new HoverPreparation(action);
            interactionLayer.setOnStateSettled(preparation);
            preparation.armed = pointerInside;
            preparation.run();
        }
    }

    /// Cancels only the current hover attempt, retaining any registered preparation action.
    private void cancelPreparation() {
        if (preparation != null)
            preparation.cancel();
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
                cancelPreparation();
                interactionLayer.reset();
            }
        }
    };

    /// Returns the press feedback switch, initially true. Disabling it clears all waveforms
    /// and the held press, preserving hover and focus feedback and normal event delivery.
    /// It also cancels the current preparation attempt without consuming its registration.
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
    /// Cancels the current preparation attempt; its action remains available for a later mouse entry.
    public void resetFeedback() {
        cancelPreparation();
        interactionLayer.reset();
    }

    /// Owns optional preparation listeners and cancels attempts when any ancestor becomes hidden.
    @NotNullByDefault
    private final class HoverPreparation implements Runnable, InvalidationListener {
        /// The action retained until it is consumed or replaced.
        private final Runnable action;

        /// Observes visibility and attachment only for wrappers with a preparation action.
        private final TreeShowingProperty showing = new TreeShowingProperty(RipplerContainer.this);

        /// Whether the current pointer entry is still eligible to prepare.
        private boolean armed;

        /// The current pulse/event-queue attempt, or null while waiting for the state transition.
        private @Nullable PreparationTask pending;

        /// Registers the visibility listener without allocating any additional animation timer.
        private HoverPreparation(Runnable action) {
            this.action = action;
            showing.addListener(this);
            showing.get();
        }

        /// Returns whether the current hover can still prepare the component.
        private boolean isEligible() {
            return armed && pointerInside && isFeedbackAvailable() && showing.get();
        }

        /// Schedules one attempt after state completion, including when there was no transition.
        @Override
        public void run() {
            if (pending == null && isEligible() && !interactionLayer.isStateTransitioning()) {
                pending = new PreparationTask(Objects.requireNonNull(getScene()));
                pending.scene.addPostLayoutPulseListener(pending);
                Platform.requestNextPulse();
            }
        }

        /// Cancels the current attempt when this node or its ancestors stop showing.
        @Override
        public void invalidated(Observable observable) {
            if (!showing.get())
                cancel();
        }

        /// Invalidates queued work and removes any outstanding pulse listener.
        private void cancel() {
            armed = false;
            if (pending != null) {
                pending.scene.removePostLayoutPulseListener(pending);
                pending = null;
            }
        }

        /// Releases all listeners when the action is consumed, replaced, or cancelled explicitly.
        private void dispose() {
            cancel();
            interactionLayer.setOnStateSettled(null);
            showing.removeListener(this);
            showing.dispose();
        }

        /// Carries one attempt across the pulse boundary; cancelled attempts cannot be reused.
        @NotNullByDefault
        private final class PreparationTask implements Runnable {
            /// The scene owning this attempt's post-layout listener.
            private final Scene scene;

            /// Whether the post-layout callback has already queued the action for a later FX turn.
            private boolean queued;

            /// Creates an attempt for the current scene.
            private PreparationTask(Scene scene) {
                this.scene = scene;
            }

            /// Queues after layout, then rechecks eligibility and consumes the action before invoking it.
            @Override
            public void run() {
                if (preparation != HoverPreparation.this || pending != this)
                    return;
                if (!queued) {
                    scene.removePostLayoutPulseListener(this);
                    queued = true;
                    Platform.runLater(this);
                    return;
                }
                pending = null;
                if (getScene() == scene && isEligible() && !interactionLayer.isStateTransitioning()) {
                    setPrepareAction(null);
                    action.run();
                }
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
        /// Maps the feedback paint to its HMCL stylesheet property.
        private static final CssMetaData<RipplerContainer, @Nullable Paint> FEEDBACK_FILL = new CssMetaData<>("-hmcl-feedback-fill", PaintConverter.getInstance(), DEFAULT_FEEDBACK_FILL) {
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
