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
import javafx.css.PseudoClass;
import javafx.css.StyleOrigin;
import javafx.css.StyleableProperty;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.skin.ToggleButtonSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

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
    private final Region track = new Region();
    /// Circular hover, focus, and pressed overlay behind the handle.
    private final Region stateLayer = new Region();
    /// Circular handle, independently styled from the track.
    private final Region handle = new Region();
    /// Optional check or cross, centered on the handle.
    private final SVGPath icon = new SVGPath();
    /// Current logical handle center measured from the track's leading edge.
    private final DoubleProperty center = new SimpleDoubleProperty(16);
    /// Current handle diameter in pixels.
    private final DoubleProperty diameter = new SimpleDoubleProperty(16);
    /// Interruptible movement and resize animation.
    private final Timeline geometryTransition = new Timeline();
    /// CSS-controlled paints sharing one selection transition.
    private final @Unmodifiable List<CssColor<?>> colors;
    /// Advances all changing paints together without allocating a timer for each part.
    private final Transition colorTransition = new Transition() {
        {
            setCycleDuration(Motion.SHORT4);
            setInterpolator(Motion.STANDARD);
            setOnFinished(event -> finishColors());
        }

        /// Updates only paints with a pending CSS target.
        @Override
        protected void interpolate(double fraction) {
            colors.forEach(color -> color.interpolate(fraction));
        }
    };
    /// True only while resolving CSS for a selection change.
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
        colors = List.of(backgroundColor(track), backgroundColor(handle), backgroundColor(stateLayer), borderColor(track),
                new CssColor<Paint>(icon.fillProperty(), paint -> paint instanceof Color color ? color : null,
                        (paint, color) -> color));
        artwork.getStyleClass().setAll("switch-artwork");
        track.getStyleClass().setAll("switch-track");
        stateLayer.getStyleClass().setAll("switch-state-layer");
        handle.getStyleClass().setAll("switch-handle");
        icon.getStyleClass().setAll("switch-icon");
        artwork.setManaged(false);
        artwork.setMouseTransparent(true);
        artwork.getTransforms().add(artworkScale);
        artwork.getChildren().setAll(track, stateLayer, handle, icon);
        artwork.getChildren().forEach(node -> node.setManaged(false));
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
        registerChangeListener(control.disabledProperty(), value -> cancelDrag());
        registerChangeListener(control.showSelectedIconProperty(), value -> update(false));
        registerChangeListener(control.showUnselectedIconProperty(), value -> update(false));
        registerChangeListener(control.disableAnimationProperty(), value -> {
            finishColors();
            update(true);
        });
        registerChangeListener(control.sceneProperty(), value -> cancelDrag());
        registerChangeListener(control.sizeProperty(), value -> control.requestLayout());
        registerChangeListener(center, value -> layoutHandle());
        registerChangeListener(diameter, value -> layoutHandle());
        control.addEventFilter(MouseEvent.ANY, dragHandler);
        control.pseudoClassStateChanged(VISUAL_FOCUSED, focusIndicator.get() && !control.isPressed());
        update(true);
    }

    /// Retargets geometry and resolves selection colors once, preserving any visible intermediate colors.
    private void updateSelection() {
        update(false);
        if (control.isDisableAnimation() || control.isDisabled() || control.getScene() == null)
            return;
        colorTransition.stop();
        colors.forEach(CssColor::capture);
        animateColors = true;
        try {
            control.applyCss();
        } finally {
            animateColors = false;
        }
        if (colors.stream().anyMatch(color -> color.target != null))
            colorTransition.playFromStart();
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

    /// Animates a single solid CSS fill while preserving its radii and insets.
    /// Complex backgrounds retain their normal immediate CSS behavior.
    private CssColor<Background> backgroundColor(Region region) {
        return new CssColor<>(region.backgroundProperty(), background ->
                background.getFills().size() == 1 && background.getImages().isEmpty()
                        && background.getFills().get(0).getFill() instanceof Color color ? color : null,
                (background, color) -> {
                    BackgroundFill fill = background.getFills().get(0);
                    return new Background(new BackgroundFill(color, fill.getRadii(), fill.getInsets()));
                });
    }

    /// Animates a uniform solid border, preserving its shape, widths, and insets.
    private CssColor<Border> borderColor(Region region) {
        return new CssColor<>(region.borderProperty(), border -> {
            if (border.getStrokes().size() != 1 || !border.getImages().isEmpty())
                return null;
            BorderStroke stroke = border.getStrokes().get(0);
            return stroke.isStrokeUniform() && stroke.getTopStroke() instanceof Color color ? color : null;
        }, (border, color) -> {
            BorderStroke stroke = border.getStrokes().get(0);
            return new Border(new BorderStroke(color, stroke.getTopStyle(), stroke.getRadii(),
                    stroke.getWidths(), stroke.getInsets()));
        });
    }

    /// Settles pending colors when animation is disabled or pointer interaction is canceled.
    private void finishColors() {
        colorTransition.stop();
        colors.forEach(CssColor::finish);
    }

    /// Interpolates a solid paint without changing the property's CSS precedence.
    /// CSS changes outside a selection update take effect immediately, including theme changes.
    private final class CssColor<T> {
        /// The rendered CSS property, also observed for new targets.
        private final ObjectProperty<@Nullable T> property;
        /// Reads a supported solid color, or null for complex paints.
        private final Function<T, @Nullable Color> colorOf;
        /// Replaces only the color, preserving the target's geometry.
        private final BiFunction<T, Color, T> recolor;
        /// Listener removed when this skin is disposed.
        private final ChangeListener<@Nullable T> listener = (observable, previous, next) -> retarget(previous, next);
        /// Most recent CSS value; null means no transition is pending.
        private @Nullable T target;
        /// CSS origin retained during animation so stylesheets can still override it.
        private @Nullable StyleOrigin origin;
        /// Starting color, captured from the currently displayed value on each reversal.
        private Color from = Color.TRANSPARENT;
        /// Destination color extracted from the current CSS target.
        private Color to = Color.TRANSPARENT;

        /// Observes a styleable property for the shared color transition.
        private CssColor(ObjectProperty<@Nullable T> property, Function<T, @Nullable Color> colorOf,
                         BiFunction<T, Color, T> recolor) {
            this.property = property;
            this.colorOf = colorOf;
            this.recolor = recolor;
            property.addListener(listener);
        }

        /// Preserves an unchanged CSS target when a selection interrupts the shared transition.
        private void capture() {
            @Nullable T current = property.get();
            @Nullable Color color = current == null ? null : colorOf.apply(current);
            if (color != null)
                from = color;
        }

        /// Starts from the visible color, or accepts unsupported and non-selection changes immediately.
        private void retarget(@Nullable T previous, @Nullable T next) {
            if (applyingColors)
                return;
            // CSS may reapply the same target while the displayed value is still interpolating.
            if (target != null && target.equals(next) && previous != null) {
                apply(previous);
                return;
            }
            target = null;
            @Nullable Color start = previous == null ? null : colorOf.apply(previous);
            @Nullable Color end = next == null ? null : colorOf.apply(next);
            if (!animateColors || start == null || end == null || start.equals(end))
                return;
            from = start;
            to = end;
            target = next;
            origin = ((StyleableProperty<?>) property).getStyleOrigin();
            interpolate(0);
        }

        /// Applies a frame without promoting the value to a user-set CSS override.
        private void interpolate(double fraction) {
            if (target != null)
                apply(fraction == 1 ? target : recolor.apply(target, from.interpolate(to, fraction)));
        }

        /// Writes a CSS value while suppressing this transition's own listener.
        @SuppressWarnings("unchecked")
        private void apply(T value) {
            applyingColors = true;
            try {
                ((StyleableProperty<T>) property).applyStyle(origin, value);
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
            property.removeListener(listener);
        }
    }

    /// Retargets an in-progress transition from its current geometry, or settles immediately.
    private void update(boolean immediate) {
        if (dragging)
            return;
        double currentCenter = center.get();
        double currentDiameter = diameter.get();
        geometryTransition.stop();
        boolean selected = control.isSelected();
        boolean pressed = control.isArmed() && !control.isDisabled();
        boolean hasIcon = selected ? control.isShowSelectedIcon() : control.isShowUnselectedIcon();
        double targetDiameter = pressed ? 28 : selected || hasIcon ? 24 : 16;
        double targetCenter = selected ? 36 : 16;
        icon.setVisible(hasIcon);
        icon.setContent(selected
                ? "M 2 8 L 6 12 L 14 4 L 12.6 2.6 L 6 9.2 L 3.4 6.6 Z"
                : "M 4 2.6 L 8 6.6 L 12 2.6 L 13.4 4 L 9.4 8 L 13.4 12 L 12 13.4 L 8 9.4 L 4 13.4 L 2.6 12 L 6.6 8 L 2.6 4 Z");
        if (immediate || control.isDisableAnimation() || control.getScene() == null) {
            center.set(targetCenter);
            diameter.set(targetDiameter);
        } else {
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
        track.resizeRelocate(0, 8, 52, 32);
        layoutHandle();
    }

    /// Updates unmanaged artwork directly, without laying out the label or settings row each frame.
    private void layoutHandle() {
        double cx = center.get();
        double size = diameter.get();
        stateLayer.resizeRelocate(cx - 20, 4, 40, 40);
        handle.resizeRelocate(cx - size / 2, 24 - size / 2, size, size);
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
        colors.forEach(CssColor::dispose);
        control.pseudoClassStateChanged(VISUAL_FOCUSED, false);
        super.dispose();
    }
}
