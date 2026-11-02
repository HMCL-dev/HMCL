// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.skins;

import com.jfoenix.controls.JFXSwitch;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.skin.ToggleButtonSkin;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Renders a Material Design 3 switch while retaining standard toggle-button behavior.
/// Label content remains separate from the switch artwork and is mirrored by JavaFX in RTL layouts.
@NotNullByDefault
public final class JFXSwitchSkin extends ToggleButtonSkin {
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
    private final Timeline transition = new Timeline();

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
        icon.getStyleClass().setAll("switch-icon");
        artwork.setManaged(false);
        artwork.setMouseTransparent(true);
        artwork.getTransforms().add(artworkScale);
        artwork.getChildren().setAll(track, stateLayer, handle, icon);
        artwork.getChildren().forEach(node -> node.setManaged(false));
        getChildren().add(artwork);
        registerChangeListener(control.selectedProperty(), value -> update(false));
        registerChangeListener(control.armedProperty(), value -> update(false));
        registerChangeListener(control.disabledProperty(), value -> update(true));
        registerChangeListener(control.showSelectedIconProperty(), value -> update(false));
        registerChangeListener(control.showUnselectedIconProperty(), value -> update(false));
        registerChangeListener(control.disableAnimationProperty(), value -> update(true));
        registerChangeListener(control.sceneProperty(), value -> update(true));
        registerChangeListener(control.sizeProperty(), value -> control.requestLayout());
        registerChangeListener(center, value -> control.requestLayout());
        registerChangeListener(diameter, value -> control.requestLayout());
        update(true);
    }

    /// Retargets an in-progress transition from its current geometry, or settles immediately.
    private void update(boolean immediate) {
        transition.stop();
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
            transition.getKeyFrames().setAll(new KeyFrame(Duration.millis(150),
                    new KeyValue(center, targetCenter, Interpolator.EASE_BOTH),
                    new KeyValue(diameter, targetDiameter, Interpolator.EASE_BOTH)));
            transition.play();
        }
        control.requestLayout();
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
        transition.stop();
        super.dispose();
    }
}
