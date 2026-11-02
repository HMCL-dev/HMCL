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
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.skin.ToggleButtonSkin;
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

/// Renders a Material Design 3 switch while retaining standard toggle-button behavior.
/// Label content remains separate from the switch artwork and is mirrored by JavaFX in RTL layouts.
@NotNullByDefault
public final class JFXSwitchSkin extends ToggleButtonSkin {
    /// Marks focus acquired without a pointer press, independently of actual keyboard focus.
    private static final PseudoClass VISUAL_FOCUSED = PseudoClass.getPseudoClass("visual-focused");

    /// The control whose properties drive this skin.
    private final JFXSwitch control;

    /// Unmanaged artwork, laid out independently of the inherited label.
    private final Pane artwork = new Pane();

    /// Scales artwork from its leading top corner without scaling label content.
    private final Scale artworkScale = new Scale();

    /// Rounded track with its optional outline.
    private final Rectangle track = new Rectangle(0, 8, 52, 32);

    /// Circular hover, focus, and pressed overlay behind the handle.
    private final Circle stateLayer = new Circle(16, 24, 20);

    /// Circular handle, independently styled from the track.
    private final Circle handle = new Circle(16, 24, 8);

    /// Optional check or cross, created on first use and then reused across state changes.
    private @Nullable SVGPath icon;

    /// Current logical handle center measured from the track's leading edge.
    private final DoubleProperty center = new SimpleDoubleProperty(16) {
        /// Applies each position change directly to the unmanaged artwork.
        @Override
        protected void invalidated() {
            layoutHandle();
        }
    };

    /// Current handle diameter in pixels.
    private final DoubleProperty diameter = new SimpleDoubleProperty(16) {
        /// Applies each size change directly to the unmanaged artwork.
        @Override
        protected void invalidated() {
            layoutHandle();
        }
    };

    /// Movement and resize animation, allocated on the first animated geometry change.
    private @Nullable Timeline geometryTransition;

    /// Paint listeners installed on the first animated selection change; initially empty.
    private final List<CssColor> colors = new ArrayList<>();

    /// Shared color animation, allocated when CSS supplies the first transition target.
    private @Nullable Transition colorTransition;

    /// Whether selection colors are waiting for the next CSS and layout pass.
    private boolean animateColors;

    /// Prevents animation writes from being interpreted as new CSS targets.
    private boolean applyingColors;

    /// Filters pointer gestures before the inherited button behavior handles release.
    private final EventHandler<MouseEvent> dragHandler = this::handleDrag;

    /// Whether the current primary press on the control can begin a drag.
    private boolean dragCandidate;

    /// Whether the handle is following the pointer instead of an animation.
    private boolean dragging;

    /// Suppresses the click generated after a completed or canceled drag.
    private boolean suppressClick;

    /// Pointer position in unscaled artwork coordinates at press time.
    private double pressX;

    /// Handle center at press time, preserving the pointer's grab offset.
    private double pressCenter;

    /// Creates the artwork and installs listeners that are removed on disposal.
    ///
    /// @param control the switch to render
    public JFXSwitchSkin(JFXSwitch control) {
        super(control);
        this.control = control;
        artwork.getStyleClass().setAll("switch-artwork");
        track.getStyleClass().setAll("switch-track");
        stateLayer.getStyleClass().setAll("switch-state-layer");
        handle.getStyleClass().setAll("switch-handle");
        track.setArcWidth(32);
        track.setArcHeight(32);
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
        Consumer<ObservableValue<?>> cancelGesture = value -> cancelDrag();
        registerChangeListener(control.disabledProperty(), cancelGesture);
        registerChangeListener(control.sceneProperty(), cancelGesture);
        control.addEventFilter(MouseEvent.ANY, dragHandler);
        control.pseudoClassStateChanged(VISUAL_FOCUSED, focusIndicator.get() && !control.isPressed());
        update(true);
    }

    /// Called by the control when an optional icon or animation property changes.
    /// When requested, settles colors and geometry immediately after an animation-policy change.
    public void updateOptions(boolean finishAnimation) {
        if (finishAnimation)
            finishColors();
        update(finishAnimation);
    }

    /// Retargets geometry and lets the next CSS pass collect selection colors before layout.
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
        for (CssColor color : colors)
            color.capture();
        animateColors = true;
        control.requestLayout();
    }

    /// Drags in artwork coordinates so CSS scaling and RTL mirroring use the same state threshold.
    private void handleDrag(MouseEvent event) {
        if (event.getEventType() == MouseEvent.MOUSE_CLICKED && event.getButton() == MouseButton.PRIMARY && suppressClick) {
            suppressClick = false;
            event.consume();
            return;
        }
        if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            if (event.getButton() != MouseButton.PRIMARY)
                return;
            suppressClick = false;
            dragCandidate = false;
            if (control.isDisabled() || control.selectedProperty().isBound())
                return;
            @Nullable Point2D point = artwork.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (point != null) {
                dragCandidate = true;
                pressX = point.getX();
                pressCenter = center.get();
            }
        } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED && dragCandidate && event.isPrimaryButtonDown()) {
            @Nullable Point2D point = artwork.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (point == null)
                return;
            if (!dragging && Math.abs(point.getX() - pressX) * sizeScale() < 3)
                return;
            dragging = true;
            if (geometryTransition != null)
                geometryTransition.stop();
            // Disarm the button so its release cannot toggle a second time.
            control.disarm();
            diameter.set(28);
            center.set(Math.max(16, Math.min(36, pressCenter + point.getX() - pressX)));
            event.consume();
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED && event.getButton() == MouseButton.PRIMARY) {
            dragCandidate = false;
            if (!dragging) {
                // Re-entering after cancellation can re-arm the inherited button behavior.
                if (suppressClick) {
                    control.disarm();
                    event.consume();
                }
                return;
            }
            boolean selected = center.get() >= 26;
            dragging = false;
            suppressClick = true;
            control.disarm();
            event.consume();
            if (!control.isDisabled() && !control.selectedProperty().isBound() && selected != control.isSelected())
                control.fire();
            update(false);
        } else if (dragging && (event.getEventType() == MouseEvent.MOUSE_ENTERED
                || event.getEventType() == MouseEvent.MOUSE_EXITED)) {
            event.consume();
        }
    }

    /// Cancels pointer tracking without committing a state change and restores selected geometry.
    private void cancelDrag() {
        finishColors();
        dragCandidate = false;
        if (dragging) {
            dragging = false;
            suppressClick = true;
            control.disarm();
        }
        update(true);
    }

    /// Allocates the shared color animation on first use and reuses it for subsequent transitions.
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

    /// Settles pending colors without allocating animation objects during cancellation or disposal.
    private void finishColors() {
        animateColors = false;
        if (colorTransition != null)
            colorTransition.stop();
        for (CssColor color : colors)
            color.finish();
    }

    /// Interpolates a solid paint without changing the property's CSS precedence.
    /// CSS changes outside a selection update take effect immediately, including theme changes.
    private final class CssColor implements ChangeListener<@Nullable Paint> {

        /// The rendered CSS property, also observed for new targets.
        private final ObjectProperty<@Nullable Paint> property;

        /// Most recent solid CSS target; null means no transition is pending.
        private @Nullable Color target;

        /// CSS origin retained during animation so stylesheets can still override it.
        private @Nullable StyleOrigin origin;

        /// Starting color, captured from the currently displayed value on each reversal.
        private Color from = Color.TRANSPARENT;

        /// Observes a styleable property for the shared color transition.
        private CssColor(ObjectProperty<@Nullable Paint> property) {
            this.property = property;
            property.addListener(this);
        }

        /// Preserves an unchanged CSS target when a selection interrupts the shared transition.
        private void capture() {
            if (property.get() instanceof Color color)
                from = color;
        }

        /// Starts from the visible color, or accepts unsupported and non-selection changes immediately.
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

        /// Applies a frame without promoting the value to a user-set CSS override.
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

        /// Restores the exact latest CSS target and clears this paint's pending transition.
        private void finish() {
            if (target != null) {
                apply(target);
                target = null;
            }
        }

        /// Removes the CSS listener after the skin has settled the shared transition.
        private void dispose() {
            property.removeListener(this);
        }
    }

    /// Retargets an in-progress transition from its current geometry, or settles immediately.
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
        double targetDiameter = pressed ? 28 : selected || hasIcon ? 24 : 16;
        double targetCenter = selected ? 36 : 16;
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

    /// Creates the icon and its paint listener only when needed, retaining them for subsequent toggles.
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

    /// Restores artwork when the inherited skin rebuilds its label children.
    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The superclass constructor calls this method before field initialization.
        @Nullable Pane currentArtwork = artwork;
        if (currentArtwork != null)
            getChildren().add(currentArtwork);
    }

    /// Returns the horizontal space reserved before the inherited label.
    private double labelOffset() {
        boolean hasText = control.getContentDisplay() != ContentDisplay.GRAPHIC_ONLY
                && control.getText() != null && !control.getText().isEmpty();
        boolean hasGraphic = control.getContentDisplay() != ContentDisplay.TEXT_ONLY
                && control.getGraphic() != null;
        return 52 * sizeScale() + (hasText || hasGraphic ? 12 : 0);
    }

    /// Returns the artwork and target scale, falling back to MD3 dimensions for invalid sizes.
    private double sizeScale() {
        double size = control.getSize();
        return Double.isFinite(size) && size > 0 ? size / 32 : 1;
    }

    /// Positions the scaled artwork in its target and gives the label the remaining width.
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double offset = labelOffset();
        super.layoutChildren(x + offset, y, Math.max(0, w - offset), h);
        double scale = sizeScale();
        artworkScale.setX(scale);
        artworkScale.setY(scale);
        // Keep animation and CSS geometry in MD3 units; scale the entire artwork consistently.
        artwork.resizeRelocate(x, y + (h - 48 * scale) / 2, 52, 48);
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

    /// Updates unmanaged artwork directly, without laying out the label or settings row each frame.
    private void layoutHandle() {
        double cx = center.get();
        stateLayer.setCenterX(cx);
        handle.setCenterX(cx);
        handle.setRadius(diameter.get() / 2);
        if (icon != null && icon.isVisible())
            icon.relocate(cx - icon.getLayoutBounds().getWidth() / 2,
                    24 - icon.getLayoutBounds().getHeight() / 2);
    }

    /// Includes the switch and label gap in the minimum width.
    @Override
    protected double computeMinWidth(double h, double top, double right, double bottom, double left) {
        return labelOffset() + super.computeMinWidth(h, top, right, bottom, left);
    }

    /// Includes the switch and label gap in the preferred width.
    @Override
    protected double computePrefWidth(double h, double top, double right, double bottom, double left) {
        return labelOffset() + super.computePrefWidth(h, top, right, bottom, left);
    }

    /// Preserves the configured target height when the label is shorter than the switch.
    @Override
    protected double computeMinHeight(double w, double top, double right, double bottom, double left) {
        return Math.max(top + 48 * sizeScale() + bottom,
                super.computeMinHeight(w < 0 ? w : Math.max(0, w - labelOffset()), top, right, bottom, left));
    }

    /// Measures wrapped labels using the width remaining after the switch.
    @Override
    protected double computePrefHeight(double w, double top, double right, double bottom, double left) {
        return Math.max(top + 48 * sizeScale() + bottom,
                super.computePrefHeight(w < 0 ? w : Math.max(0, w - labelOffset()), top, right, bottom, left));
    }

    /// Stops animation and removes the listeners and behavior installed by this skin.
    @Override
    public void dispose() {
        control.removeEventFilter(MouseEvent.ANY, dragHandler);
        cancelDrag();
        for (CssColor color : colors)
            color.dispose();
        control.pseudoClassStateChanged(VISUAL_FOCUSED, false);
        super.dispose();
    }
}
