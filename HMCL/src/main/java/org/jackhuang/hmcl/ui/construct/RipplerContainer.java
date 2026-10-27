// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
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
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;

/// Wraps content with a region for hover, focus, press, and ripple feedback.
/// Feedback covers the entire pane, including padding, without replacing its background.
/// Construction and all subsequent changes must occur on the JavaFX application thread.
/// Only background presses trigger feedback in [FeedbackPosition#BACK] mode; content events
/// continue to bubble normally, but do not initiate outer press feedback.
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
    /// The settings instance observed by this wrapper.
    private final LauncherSettings launcherSettings = settings();
    /// Retains the delegate of the weak global settings listener.
    private final InvalidationListener animationListener = observable -> updateAnimationSetting();
    /// Updates visual availability after window state changes.
    private final InvalidationListener availabilityListener = observable -> updateAvailability();
    /// Reattaches window listeners when the scene moves between windows.
    private final InvalidationListener windowListener = observable -> observeWindow();
    /// The scene whose window property is currently observed.
    private @Nullable Scene observedScene;
    /// The window whose showing and focus properties are currently observed.
    private @Nullable Window observedWindow;
    /// Whether visual interaction is currently permitted.
    private boolean feedbackAvailable;
    /// Tracks pointer presence for direct entered/exited events as well as normal picking.
    private boolean pointerInside;
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
    /// Enables press feedback without disabling hover, focus, or event delivery.
    private final BooleanProperty pressFeedbackEnabled = new SimpleBooleanProperty(this, "pressFeedbackEnabled", true) {
        /// Cancels any existing press feedback when it becomes disabled.
        @Override
        protected void invalidated() {
            if (!get())
                interactionLayer.reset();
        }
    };
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

    /// Creates a wrapper with feedback behind its content.
    /// Sets the content's `pickOnBounds` to false so uncovered regions can receive input.
    /// The animation setting is observed immediately and remains live thereafter.
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
        launcherSettings.animationDisabledProperty().addListener(new WeakInvalidationListener(animationListener));
        updateAnimationSetting();

        interactionLayer.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && feedbackAvailable && isPressFeedbackEnabled())
                interactionLayer.press(event.getX(), event.getY());
        });
        interactionLayer.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY)
                interactionLayer.release();
        });
        addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            pointerInside = true;
            updateVisualState();
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

    /// Applies the effective animation setting, including the system default for a null setting.
    private void updateAnimationSetting() {
        interactionLayer.setAnimationEnabled(!launcherSettings.isAnimationDisabled());
    }

    /// Removes listeners from the previous scene before observing the current one.
    private void observeScene() {
        interactionLayer.reset();
        if (observedScene != null)
            observedScene.windowProperty().removeListener(windowListener);
        observedScene = getScene();
        if (observedScene != null)
            observedScene.windowProperty().addListener(windowListener);
        observeWindow();
    }

    /// Transfers showing and focus listeners to the current scene's window.
    private void observeWindow() {
        interactionLayer.reset();
        if (observedWindow != null) {
            observedWindow.showingProperty().removeListener(availabilityListener);
            observedWindow.focusedProperty().removeListener(availabilityListener);
        }
        observedWindow = observedScene == null ? null : observedScene.getWindow();
        if (observedWindow != null) {
            observedWindow.showingProperty().addListener(availabilityListener);
            observedWindow.focusedProperty().addListener(availabilityListener);
        }
        updateAvailability();
    }

    /// Cancels transient feedback whenever the wrapper cannot receive visual interaction.
    private void updateAvailability() {
        feedbackAvailable = !isDisabled() && isVisible() && observedWindow != null
                && observedWindow.isShowing() && observedWindow.isFocused();
        updateVisualState();
        if (!feedbackAvailable)
            interactionLayer.reset();
    }

    /// Supplies hover and focus states independently of whether press feedback is enabled.
    private void updateVisualState() {
        interactionLayer.setHovered(feedbackAvailable && pointerInside);
        interactionLayer.setFocusActive(feedbackAvailable && isFocused());
        if (!feedbackAvailable)
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
