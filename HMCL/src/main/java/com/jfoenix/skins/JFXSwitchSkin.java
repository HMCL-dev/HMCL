// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.skins;

import com.jfoenix.controls.JFXSwitch;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.css.StyleOrigin;
import javafx.css.StyleableProperty;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// Renders a Material Design 3 switch and handles input.
/// Geometry uses unscaled MD3 units and is mirrored in RTL layouts.
@NotNullByDefault
public final class JFXSwitchSkin extends SkinBase<JFXSwitch> {
    /// Track width in unscaled MD3 artwork coordinates.
    private static final double TRACK_WIDTH = 52;

    /// Track height corresponding to the control's default `-jfx-size`.
    private static final double TRACK_HEIGHT = 32;

    /// Unscaled interaction target height.
    private static final double TARGET_HEIGHT = 48;

    /// Vertical center shared by the track, handle, state layer, and icon.
    private static final double CENTER_Y = TARGET_HEIGHT / 2;

    /// Handle center at the leading end of the track.
    private static final double OFF_CENTER = TRACK_HEIGHT / 2;

    /// Handle center at the trailing end of the track.
    private static final double ON_CENTER = TRACK_WIDTH - OFF_CENTER;

    /// Diameter of the unselected handle without an icon.
    private static final double OFF_DIAMETER = 16;

    /// Diameter of a selected handle or a handle containing an icon.
    private static final double ON_DIAMETER = 24;

    /// Handle diameter while pressed or dragged.
    private static final double PRESSED_DIAMETER = 28;

    /// Radius of the hover, focus, and pressed overlay.
    private static final double STATE_LAYER_RADIUS = 20;

    /// Minimum pointer movement in control pixels before a press becomes a drag.
    private static final double DRAG_THRESHOLD = 3;

    /// Indicates visible keyboard focus.
    private static final PseudoClass VISUAL_FOCUSED = PseudoClass.getPseudoClass("visual-focused");

    /// The rendered switch.
    private final JFXSwitch control;

    /// Unmanaged switch artwork.
    private final Pane artwork = new Pane();

    /// Scales artwork from its leading top corner.
    private final Scale artworkScale = new Scale();

    /// Rounded track with its optional outline.
    private final Rectangle track = new Rectangle(0, (TARGET_HEIGHT - TRACK_HEIGHT) / 2, TRACK_WIDTH, TRACK_HEIGHT);

    /// Interaction feedback behind the handle.
    private final Circle stateLayer = new Circle(OFF_CENTER, CENTER_Y, STATE_LAYER_RADIUS);

    /// Switch handle.
    private final Circle handle = new Circle(OFF_CENTER, CENTER_Y, OFF_DIAMETER / 2);

    /// Optional handle icon.
    private @Nullable SVGPath icon;

    /// Current logical handle center measured from the track's leading edge.
    private final DoubleProperty center = new SimpleDoubleProperty(OFF_CENTER) {
        /// Updates handle position.
        @Override
        protected void invalidated() {
            layoutHandle();
        }
    };

    /// Unscaled handle diameter.
    private final DoubleProperty diameter = new SimpleDoubleProperty(OFF_DIAMETER) {
        /// Updates handle size.
        @Override
        protected void invalidated() {
            layoutHandle();
        }
    };

    /// Optional movement and resize animation.
    private @Nullable Timeline geometryTransition;

    /// Animated CSS paints.
    private final List<CssColor> colors = new ArrayList<>();

    /// Optional shared color animation.
    private @Nullable Transition colorTransition;

    /// Whether a selection color update is pending.
    private boolean animateColors;

    /// Prevents animation writes from being interpreted as new CSS targets.
    private boolean applyingColors;

    /// Pointer event handler.
    private final EventHandler<MouseEvent> mouseHandler = this::handleMouse;

    /// Keyboard event handler.
    private final EventHandler<KeyEvent> keyHandler = this::handleKey;

    /// Whether a primary press is awaiting click or drag completion.
    private boolean mouseDown;

    /// Whether the handle is following the pointer instead of an animation.
    private boolean dragging;

    /// Whether a Space press is awaiting release.
    private boolean keyDown;

    /// Pointer position in unscaled artwork coordinates at press time.
    private double pressX;

    /// Handle center at press time, preserving the pointer's grab offset.
    private double pressCenter;

    /// Creates a skin for the switch.
    public JFXSwitchSkin(JFXSwitch control) {
        super(control);
        this.control = control;
        artwork.getStyleClass().setAll("switch-artwork");
        track.getStyleClass().setAll("switch-track");
        stateLayer.getStyleClass().setAll("switch-state-layer");
        handle.getStyleClass().setAll("switch-handle");
        track.setArcWidth(TRACK_HEIGHT);
        track.setArcHeight(TRACK_HEIGHT);
        artwork.setManaged(false);
        artwork.setMouseTransparent(true);
        artwork.getTransforms().add(artworkScale);
        artwork.getChildren().setAll(track, stateLayer, handle);
        for (var node : artwork.getChildren())
            node.setManaged(false);
        getChildren().add(artwork);
        registerChangeListener(control.selectedProperty(), value -> updateSelection());
        registerChangeListener(control.armedProperty(), value -> update(false));
        @Nullable ReadOnlyBooleanProperty focusVisible = FXUtils.focusVisibleProperty(control);
        ReadOnlyBooleanProperty focusIndicator = focusVisible != null ? focusVisible : control.focusedProperty();
        registerChangeListener(focusIndicator, value ->
                control.pseudoClassStateChanged(VISUAL_FOCUSED, focusIndicator.get() && !control.isPressed()));
        // Match the previous toggle skin: pointer press and release both dismiss focus feedback.
        registerChangeListener(control.pressedProperty(), value ->
                control.pseudoClassStateChanged(VISUAL_FOCUSED, false));
        Consumer<ObservableValue<?>> cancelGesture = value -> cancelGesture();
        registerChangeListener(control.disabledProperty(), cancelGesture);
        registerChangeListener(control.sceneProperty(), cancelGesture);
        registerChangeListener(control.focusedProperty(), value -> {
            if (!control.isFocused())
                cancelGesture();
        });
        control.addEventFilter(MouseEvent.ANY, mouseHandler);
        control.addEventFilter(KeyEvent.ANY, keyHandler);
        control.pseudoClassStateChanged(VISUAL_FOCUSED, focusIndicator.get() && !control.isPressed());
        update(true);
    }

    /// Updates icon and animation options, optionally settling active animations.
    public void updateOptions(boolean finishAnimation) {
        if (finishAnimation)
            finishColors();
        update(finishAnimation);
    }

    /// Stops animations and removes event handlers and listeners.
    @Override
    public void dispose() {
        control.removeEventFilter(MouseEvent.ANY, mouseHandler);
        control.removeEventFilter(KeyEvent.ANY, keyHandler);
        cancelGesture();
        for (CssColor color : colors)
            color.dispose();
        control.pseudoClassStateChanged(VISUAL_FOCUSED, false);
        super.dispose();
    }

    /// Centers the scaled artwork within the control and starts pending color transitions.
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double scale = sizeScale();
        artworkScale.setX(scale);
        artworkScale.setY(scale);
        // Keep animation and CSS geometry in MD3 units; scale the entire artwork consistently.
        artwork.resizeRelocate(x + (w - TRACK_WIDTH * scale) / 2,
                y + (h - TARGET_HEIGHT * scale) / 2, TRACK_WIDTH, TARGET_HEIGHT);
        layoutHandle();
        // JavaFX applies CSS before layout, so all changed paints now share the same start time.
        if (animateColors) {
            animateColors = false;
            for (CssColor color : colors) {
                if (color.target != null) {
                    colorTransition().playFromStart();
                    break;
                }
            }
        }
    }

    /// Reserves the scaled track width and control insets.
    @Override
    protected double computeMinWidth(double h, double top, double right, double bottom, double left) {
        return left + TRACK_WIDTH * sizeScale() + right;
    }

    /// Reserves the scaled interaction target height and control insets.
    @Override
    protected double computeMinHeight(double w, double top, double right, double bottom, double left) {
        return top + TARGET_HEIGHT * sizeScale() + bottom;
    }

    /// Uses the same fixed width as the minimum size.
    @Override
    protected double computePrefWidth(double h, double top, double right, double bottom, double left) {
        return computeMinWidth(h, top, right, bottom, left);
    }

    /// Uses the same fixed height as the minimum size.
    @Override
    protected double computePrefHeight(double w, double top, double right, double bottom, double left) {
        return computeMinHeight(w, top, right, bottom, left);
    }

    /// Limits width to the preferred size.
    @Override
    protected double computeMaxWidth(double h, double top, double right, double bottom, double left) {
        return computePrefWidth(h, top, right, bottom, left);
    }

    /// Limits height to the preferred size.
    @Override
    protected double computeMaxHeight(double w, double top, double right, double bottom, double left) {
        return computePrefHeight(w, top, right, bottom, left);
    }

    /// Handles clicking and dragging.
    private void handleMouse(MouseEvent event) {
        if (event.getEventType() == MouseEvent.MOUSE_CLICKED && event.getButton() == MouseButton.PRIMARY) {
            // Activation happens on release; do not also activate the surrounding settings row.
            event.consume();
            return;
        }
        if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            if (event.getButton() != MouseButton.PRIMARY || keyDown
                    || event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown()
                    || event.isMiddleButtonDown() || event.isSecondaryButtonDown())
                return;
            if (control.isDisabled() || control.selectedProperty().isBound())
                return;
            if (control.isFocusTraversable())
                control.requestFocus();
            @Nullable Point2D point = artwork.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (point != null) {
                mouseDown = true;
                pressX = point.getX();
                pressCenter = center.get();
                control.arm();
            }
            event.consume();
        } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED && mouseDown && event.isPrimaryButtonDown()) {
            @Nullable Point2D point = artwork.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (point == null)
                return;
            if (!dragging && Math.abs(point.getX() - pressX) * sizeScale() < DRAG_THRESHOLD)
                return;
            dragging = true;
            if (geometryTransition != null)
                geometryTransition.stop();
            control.arm();
            diameter.set(PRESSED_DIAMETER);
            center.set(dragCenter(point.getX()));
            event.consume();
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED && event.getButton() == MouseButton.PRIMARY) {
            if (!mouseDown) {
                event.consume();
                return;
            }
            @Nullable Point2D point = dragging
                    ? artwork.sceneToLocal(event.getSceneX(), event.getSceneY())
                    : control.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (dragging && point != null)
                center.set(dragCenter(point.getX()));
            boolean activate = point != null && (dragging
                    ? (center.get() >= (OFF_CENTER + ON_CENTER) / 2) != control.isSelected()
                    : control.isArmed() && control.contains(point));
            mouseDown = false;
            dragging = false;
            control.disarm();
            event.consume();
            if (activate)
                control.fire();
            // Action handlers may replace or remove the skin synchronously.
            if (control.getSkin() == this)
                update(false);
        } else if (mouseDown && !dragging) {
            if (event.getEventType() == MouseEvent.MOUSE_ENTERED)
                control.arm();
            else if (event.getEventType() == MouseEvent.MOUSE_EXITED)
                control.disarm();
        }
    }

    /// Clamps pointer displacement to the track while preserving the initial grab offset.
    private double dragCenter(double pointerX) {
        return Math.max(OFF_CENTER, Math.min(ON_CENTER, pressCenter + pointerX - pressX));
    }

    /// Handles Space activation and Escape cancellation.
    private void handleKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE && event.getEventType() == KeyEvent.KEY_PRESSED) {
            if (mouseDown || keyDown) {
                boolean awaitingRelease = keyDown;
                cancelGesture();
                // Ignore auto-repeat until the canceled Space press is released.
                keyDown = awaitingRelease;
                event.consume();
            }
        } else if (event.getCode() == KeyCode.SPACE) {
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                if (event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown())
                    return;
                if (!keyDown) {
                    if (mouseDown || control.isDisabled() || control.selectedProperty().isBound())
                        return;
                    keyDown = true;
                    control.arm();
                }
                event.consume();
            } else if (event.getEventType() == KeyEvent.KEY_RELEASED && keyDown) {
                keyDown = false;
                boolean activate = control.isArmed();
                control.disarm();
                event.consume();
                if (activate)
                    control.fire();
            }
        }
    }

    /// Cancels input without changing selection.
    private void cancelGesture() {
        finishColors();
        mouseDown = false;
        keyDown = false;
        dragging = false;
        control.disarm();
        update(true);
    }

    /// Updates geometry and schedules selection colors for the next CSS pass.
    private void updateSelection() {
        update(false);
        if (control.isDisableAnimation() || control.isDisabled() || control.getScene() == null)
            return;
        if (colors.isEmpty()) {
            colors.add(new CssColor(track.fillProperty()));
            colors.add(new CssColor(handle.fillProperty()));
            colors.add(new CssColor(stateLayer.fillProperty()));
            colors.add(new CssColor(track.strokeProperty()));
            if (icon != null)
                colors.add(new CssColor(icon.fillProperty()));
        }
        if (colorTransition != null)
            colorTransition.stop();
        // A reversal before the first frame may reapply the visible paint without emitting a change event.
        for (CssColor color : colors)
            color.target = null;
        animateColors = true;
        control.requestLayout();
    }

    /// Updates handle geometry, with optional animation.
    private void update(boolean immediate) {
        if (dragging)
            return;
        double currentCenter = center.get();
        double currentDiameter = diameter.get();
        if (geometryTransition != null)
            geometryTransition.stop();
        boolean selected = control.isSelected();
        boolean pressed = control.isArmed() && !control.isDisabled();
        boolean hasIcon = selected ? control.isShowSelectedIcon() : control.isShowUnselectedIcon();
        double targetDiameter = pressed ? PRESSED_DIAMETER : selected || hasIcon ? ON_DIAMETER : OFF_DIAMETER;
        double targetCenter = selected ? ON_CENTER : OFF_CENTER;
        updateIcon(selected, hasIcon);
        if (immediate || control.isDisableAnimation() || control.getScene() == null
                || currentCenter == targetCenter && currentDiameter == targetDiameter) {
            center.set(targetCenter);
            diameter.set(targetDiameter);
        } else {
            if (geometryTransition == null)
                geometryTransition = new Timeline();
            // Preserve the visible geometry when a press or reversal interrupts a transition.
            center.set(currentCenter);
            diameter.set(currentDiameter);
            geometryTransition.getKeyFrames().setAll(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(center, currentCenter),
                            new KeyValue(diameter, currentDiameter)),
                    new KeyFrame(pressed ? Motion.SHORT2 : Motion.MEDIUM1,
                            new KeyValue(diameter, targetDiameter, pressed ? Motion.LINEAR : Motion.STANDARD)),
                    new KeyFrame(Motion.MEDIUM2,
                            new KeyValue(center, targetCenter, Motion.EASE_OUT_BACK)));
            geometryTransition.play();
        }
        layoutHandle();
    }

    /// Updates the handle icon, creating it if needed.
    private void updateIcon(boolean selected, boolean visible) {
        if (icon == null && visible) {
            icon = new SVGPath();
            icon.getStyleClass().setAll("switch-icon");
            icon.setManaged(false);
            // The first CSS fill should appear immediately, without a transition from default black.
            icon.setFill(null);
            if (!colors.isEmpty())
                colors.add(new CssColor(icon.fillProperty()));
            artwork.getChildren().add(icon);
        }
        if (icon != null) {
            icon.setVisible(visible);
            if (visible)
                icon.setContent(selected
                        ? "M2 8l4 4 8-8-1.4-1.4L6 9.2 3.4 6.6Z"
                        : "M4 2.6l4 4 4-4L13.4 4l-4 4 4 4-1.4 1.4-4-4-4 4L2.6 12l4-4-4-4Z");
        }
    }

    /// Positions and sizes the handle and its overlays.
    private void layoutHandle() {
        double cx = center.get();
        stateLayer.setCenterX(cx);
        handle.setCenterX(cx);
        handle.setRadius(diameter.get() / 2);
        if (icon != null && icon.isVisible())
            icon.relocate(cx - icon.getLayoutBounds().getWidth() / 2,
                    CENTER_Y - icon.getLayoutBounds().getHeight() / 2);
    }

    /// Returns the size scale, or 1 for an invalid size.
    private double sizeScale() {
        double size = control.getSize();
        return Double.isFinite(size) && size > 0 ? size / TRACK_HEIGHT : 1;
    }

    /// Returns the shared color animation, creating it if needed.
    private Transition colorTransition() {
        if (colorTransition == null) {
            colorTransition = new Transition() {
                {
                    setCycleDuration(Motion.SHORT4);
                    setInterpolator(Motion.STANDARD);
                }

                /// Updates only paints with a pending CSS target.
                @Override
                protected void interpolate(double fraction) {
                    for (CssColor color : colors)
                        color.interpolate(fraction);
                }
            };
            colorTransition.setOnFinished(event -> finishColors());
        }
        return colorTransition;
    }

    /// Stops color animation and applies pending targets.
    private void finishColors() {
        animateColors = false;
        if (colorTransition != null)
            colorTransition.stop();
        for (CssColor color : colors)
            color.finish();
    }

    /// Animates a solid paint while preserving CSS precedence.
    private final class CssColor implements ChangeListener<@Nullable Paint> {

        /// The animated paint property.
        private final ObjectProperty<@Nullable Paint> property;

        /// Most recent solid CSS target; null means no transition is pending.
        private @Nullable Color target;

        /// CSS origin of the target paint.
        private @Nullable StyleOrigin origin;

        /// Starting color.
        private Color from = Color.TRANSPARENT;

        /// Observes a styleable paint property.
        private CssColor(ObjectProperty<@Nullable Paint> property) {
            this.property = property;
            property.addListener(this);
        }

        /// Animates selection colors and applies other paint changes immediately.
        @Override
        public void changed(ObservableValue<? extends @Nullable Paint> observable,
                            @Nullable Paint previous, @Nullable Paint next) {
            if (applyingColors)
                return;
            // CSS may reapply the same target while the displayed value is still interpolating.
            if (target != null && target.equals(next) && previous != null) {
                apply(previous);
                return;
            }
            target = null;
            if (!animateColors || !(previous instanceof Color start) || !(next instanceof Color end) || start.equals(end))
                return;
            from = start;
            target = end;
            origin = ((StyleableProperty<?>) property).getStyleOrigin();
            interpolate(0);
        }

        /// Applies an animation frame.
        private void interpolate(double fraction) {
            if (target != null)
                apply(fraction == 1 ? target : from.interpolate(target, fraction));
        }

        /// Writes a CSS value while suppressing this transition's own listener.
        @SuppressWarnings("unchecked")
        private void apply(Paint value) {
            applyingColors = true;
            try {
                ((StyleableProperty<Paint>) property).applyStyle(origin, value);
            } finally {
                applyingColors = false;
            }
        }

        /// Applies and clears the pending target.
        private void finish() {
            if (target != null) {
                apply(target);
                target = null;
            }
        }

        /// Removes the paint listener.
        private void dispose() {
            property.removeListener(this);
        }
    }
}
