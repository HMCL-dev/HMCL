// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
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
import javafx.scene.layout.Background;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
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

/// Wraps content with hover, focus, press, and ripple feedback behind it.
/// Feedback covers padding without replacing the background. Corners follow the first background
/// fill of this pane, then the wrapped [Region], otherwise remaining square. Fill insets are ignored;
/// percentage radii use this pane's dimensions.
/// Only background presses trigger press feedback. Window focus loss cancels press and focus
/// feedback, but hover remains available. Construction and changes must occur on the FX thread.
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

    /// Cancels press feedback when window focus is lost.
    private final InvalidationListener windowFocusListener = observable -> updateVisualState(!isWindowFocused());

    /// Reattaches window listeners when the scene moves between windows.
    private final InvalidationListener windowListener = observable -> observeWindow();

    /// The scene whose window property is currently observed.
    private @Nullable Scene observedScene;

    /// The window whose showing and focus properties are currently observed.
    private @Nullable Window observedWindow;

    /// Whether the pointer has entered without subsequently exiting.
    private boolean pointerInside;

    /// Wraps the content and sets its `pickOnBounds` to false for background input.
    ///
    /// @param container the content to wrap
    /// @throws NullPointerException if the content is null
    public RipplerContainer(Node container) {
        this.container = Objects.requireNonNull(container);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setPickOnBounds(false);
        interactionLayer.setManaged(false);
        interactionLayer.setFill(getFeedbackFill());
        container.setPickOnBounds(false);
        getChildren().setAll(interactionLayer, container);
        InvalidationListener backgroundListener = observable -> updateCornerRadii();
        backgroundProperty().addListener(backgroundListener);
        if (container instanceof Region region)
            region.backgroundProperty().addListener(backgroundListener);
        updateCornerRadii();

        interactionLayer.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && isFeedbackAvailable() && isPressFeedbackEnabled())
                interactionLayer.press(event.getX(), event.getY());
        });
        interactionLayer.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY)
                interactionLayer.release();
        });
        interactionLayer.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleBackgroundClick);
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

    /// Synchronizes feedback corners from the first available background fill without copying insets.
    private void updateCornerRadii() {
        @Nullable Background background = getBackground();
        if ((background == null || background.getFills().isEmpty()) && container instanceof Region region)
            background = region.getBackground();
        interactionLayer.setCornerRadii(background == null || background.getFills().isEmpty()
                ? CornerRadii.EMPTY : background.getFills().get(0).getRadii());
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

    /// Returns the original content, which remains a direct child of this wrapper.
    public Node getContainer() {
        return container;
    }

    /// The lazily allocated action handler property.
    private @Nullable ObjectProperty<@Nullable EventHandler<ActionEvent>> onAction;

    /// Returns the handler property for actions targeted at this wrapper, initially null.
    /// Primary background clicks invoke [#fire()] independently of the handler, without consuming
    /// the mouse event or waiting for animation or preparation. Descendant actions bypass this
    /// handler but follow normal event dispatch. Keyboard activation is not provided.
    public ObjectProperty<@Nullable EventHandler<ActionEvent>> onActionProperty() {
        if (onAction == null) {
            onAction = new SimpleObjectProperty<>(this, "onAction") {
                /// Updates only the property-backed action handler.
                @Override
                protected void invalidated() {
                    setEventHandler(ActionEvent.ACTION, get() == null ? null : RipplerContainer.this::handleAction);
                }
            };
        }
        return onAction;
    }

    /// Returns the handler in [#onActionProperty()], or null.
    public @Nullable EventHandler<ActionEvent> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /// Sets [#onActionProperty()]; null clears the handler.
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

    /// Dispatches primary background actions without consuming the original mouse event.
    private void handleBackgroundClick(MouseEvent event) {
        if (event.getTarget() == interactionLayer && event.getButton() == MouseButton.PRIMARY && !isDisabled()) {
            fire();
        }
    }

    /// Delivers this wrapper's actions without treating descendant actions as background activation.
    private void handleAction(ActionEvent event) {
        @Nullable EventHandler<ActionEvent> handler = getOnAction();
        if (event.getTarget() == this && handler != null)
            handler.handle(event);
    }

    /// The pending one-shot hover preparation.
    private @Nullable HoverPreparation preparation;

    /// Returns the pending preparation action, or null if none is registered or it was consumed.
    public @Nullable Runnable getPrepareAction() {
        return preparation == null ? null : preparation.action;
    }

    /// Registers an action to queue once on the FX thread when hovered feedback settles.
    /// An already-hovered state is checked immediately; execution is deferred even without animation.
    /// Assigning the same action reference is a no-op; replacement or null cancels pending or queued work.
    /// The registration is cleared before invocation.
    ///
    /// Leaving before settlement postpones scheduling until another entry. Once queued, only replacement
    /// or cancellation prevents invocation. Scheduling does not wait for waveforms or display presentation.
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

    /// Returns the feedback paint property, initially black and styled by `-jfx-feedback-fill`.
    /// Paint transparency multiplies feedback opacity; null hides feedback without affecting input.
    public StyleableObjectProperty<@Nullable Paint> feedbackFillProperty() {
        return feedbackFill;
    }

    /// Returns the paint in [#feedbackFillProperty()], or null.
    public @Nullable Paint getFeedbackFill() {
        return feedbackFill.get();
    }

    /// Sets [#feedbackFillProperty()].
    public void setFeedbackFill(@Nullable Paint value) {
        feedbackFill.set(value);
    }

    /// Whether background presses produce visual feedback.
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
    /// Enabling it does not replay an existing press.
    public BooleanProperty pressFeedbackEnabledProperty() {
        return pressFeedbackEnabled;
    }

    /// Returns whether press feedback is enabled.
    public boolean isPressFeedbackEnabled() {
        return pressFeedbackEnabled.get();
    }

    /// Sets [#pressFeedbackEnabledProperty()].
    public void setPressFeedbackEnabled(boolean value) {
        pressFeedbackEnabled.set(value);
    }

    /// Clears all held and fading waveforms and immediately restores the current hover/focus state.
    /// Suitable for clearing transient feedback when a virtualized cell is reused.
    public void resetFeedback() {
        interactionLayer.reset();
    }

    /// Queues one-shot hover preparation.
    @NotNullByDefault
    private final class HoverPreparation implements Runnable {
        /// The registered action.
        private final Runnable action;

        /// Whether the action has been queued.
        private boolean queued;

        /// Creates a pending registration.
        private HoverPreparation(Runnable action) {
            this.action = action;
        }

        /// Queues the action when hover feedback is available and settled.
        @Override
        public void run() {
            if (!queued && pointerInside && isFeedbackAvailable() && !interactionLayer.isStateTransitioning()) {
                queued = true;
                interactionLayer.setOnStateSettled(null);
                Platform.runLater(this::prepare);
            }
        }

        /// Consumes and invokes this registration if it is still current.
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

    /// Shared stylesheet metadata.
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
