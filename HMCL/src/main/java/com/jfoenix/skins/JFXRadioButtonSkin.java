//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jfoenix.skins;

import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXRippler;
import com.jfoenix.controls.JFXRippler.RipplerMask;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.RadioButton;
import javafx.scene.control.skin.RadioButtonSkin;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class JFXRadioButtonSkin extends RadioButtonSkin {
    private static final double PADDING = 15.0;

    private boolean invalid = true;
    private final JFXRippler rippler;
    private final Circle radio;
    private final Circle dot;
    private Timeline timeline;
    private final AnchorPane container = new AnchorPane();
    private final double labelOffset = -10.0;

    public JFXRadioButtonSkin(JFXRadioButton control) {
        super(control);
        double radioRadius = 7.0;
        this.radio = new Circle(radioRadius);
        this.radio.getStyleClass().setAll("radio");
        this.radio.setStrokeWidth(2.0);
        this.radio.setFill(Color.TRANSPARENT);

        this.dot = new Circle();
        this.dot.getStyleClass().setAll("dot");
        this.dot.setRadius(radioRadius);
        this.dot.fillProperty().bind(control.selectedColorProperty());
        this.dot.setScaleX(0.0);
        this.dot.setScaleY(0.0);

        StackPane boxContainer = new StackPane();
        boxContainer.getChildren().addAll(this.radio, this.dot);
        boxContainer.setPadding(new Insets(PADDING));
        this.rippler = new JFXRippler(boxContainer, RipplerMask.CIRCLE);
        this.container.getChildren().add(this.rippler);
        AnchorPane.setRightAnchor(this.rippler, this.labelOffset);

        this.updateChildren();
        control.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                if (!this.getSkinnable().isPressed()) {
                    this.rippler.showOverlay();
                }
            } else {
                this.rippler.hideOverlay();
            }

        });
        control.pressedProperty().addListener((o, oldVal, newVal) -> this.rippler.hideOverlay());
        this.registerChangeListener(control.selectedColorProperty(), ignored -> {
            this.updateAnimation();
            boolean isSelected = this.getSkinnable().isSelected();
            Color unSelectedColor = ((JFXRadioButton) this.getSkinnable()).getUnSelectedColor();
            Color selectedColor = ((JFXRadioButton) this.getSkinnable()).getSelectedColor();
            this.rippler.setRipplerFill(isSelected ? selectedColor : unSelectedColor);
            if (isSelected) {
                this.radio.strokeProperty().set(selectedColor);
            }
        });

        this.registerChangeListener(control.unSelectedColorProperty(), ignored -> {
            this.updateAnimation();
            boolean isSelected = this.getSkinnable().isSelected();
            Color unSelectedColor = ((JFXRadioButton) this.getSkinnable()).getUnSelectedColor();
            Color selectedColor = ((JFXRadioButton) this.getSkinnable()).getSelectedColor();
            this.rippler.setRipplerFill(isSelected ? selectedColor : unSelectedColor);
            if (!isSelected) {
                this.radio.strokeProperty().set(unSelectedColor);
            }

        });
        this.registerChangeListener(control.selectedProperty(), ignored -> {
            boolean isSelected = this.getSkinnable().isSelected();
            Color unSelectedColor = ((JFXRadioButton) this.getSkinnable()).getUnSelectedColor();
            Color selectedColor = ((JFXRadioButton) this.getSkinnable()).getSelectedColor();
            this.rippler.setRipplerFill(isSelected ? selectedColor : unSelectedColor);
            if (this.timeline == null) {
                this.updateAnimation();
            }

            this.playAnimation();
        });
    }

    protected void updateChildren() {
        super.updateChildren();
        if (this.radio != null) {
            this.removeRadio();
            this.getChildren().add(this.container);
        }
    }

    protected void layoutChildren(double x, double y, double w, double h) {
        RadioButton radioButton = this.getSkinnable();
        double contWidth = this.snapSizeX(this.container.prefWidth(-1.0)) + (double) (this.invalid ? 2 : 0);
        double contHeight = this.snapSizeY(this.container.prefHeight(-1.0)) + (double) (this.invalid ? 2 : 0);
        double computeWidth = Math.min(radioButton.prefWidth(-1.0), radioButton.minWidth(-1.0)) + this.labelOffset + 2.0 * this.PADDING;
        double labelWidth = Math.min(computeWidth - contWidth, w - this.snapSizeX(contWidth)) + this.labelOffset + 2.0 * PADDING;
        double labelHeight = Math.min(radioButton.prefHeight(labelWidth), h);
        double maxHeight = Math.max(contHeight, labelHeight);
        double xOffset = computeXOffset(w, labelWidth + contWidth, radioButton.getAlignment().getHpos()) + x;
        double yOffset = computeYOffset(h, maxHeight, radioButton.getAlignment().getVpos()) + x;
        if (this.invalid) {
            this.initializeComponents();
            this.invalid = false;
        }

        this.layoutLabelInArea(xOffset + contWidth, yOffset, labelWidth, maxHeight, radioButton.getAlignment());
        ((Text) this.getChildren().get(this.getChildren().get(0) instanceof Text ? 0 : 1)).textProperty().set(this.getSkinnable().textProperty().get());
        this.container.resize(this.snapSizeX(contWidth), this.snapSizeY(contHeight));
        this.positionInArea(this.container, xOffset, yOffset, contWidth, maxHeight, 0.0, radioButton.getAlignment().getHpos(), radioButton.getAlignment().getVpos());
    }

    private void initializeComponents() {
        Color unSelectedColor = ((JFXRadioButton) this.getSkinnable()).getUnSelectedColor();
        Color selectedColor = ((JFXRadioButton) this.getSkinnable()).getSelectedColor();
        this.radio.setStroke(unSelectedColor);
        this.rippler.setRipplerFill(this.getSkinnable().isSelected() ? selectedColor : unSelectedColor);
        this.updateAnimation();
        this.playAnimation();
    }

    private void playAnimation() {
        this.timeline.setRate(this.getSkinnable().isSelected() ? 1.0 : -1.0);
        this.timeline.play();
    }

    private void updateAnimation() {
        Color unSelectedColor = ((JFXRadioButton) this.getSkinnable()).getUnSelectedColor();
        Color selectedColor = ((JFXRadioButton) this.getSkinnable()).getSelectedColor();
        this.timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(this.dot.scaleXProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(this.dot.scaleYProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(this.radio.strokeProperty(), unSelectedColor, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(200.0),
                        new KeyValue(this.dot.scaleXProperty(), 0.6, Interpolator.EASE_BOTH),
                        new KeyValue(this.dot.scaleYProperty(), 0.6, Interpolator.EASE_BOTH),
                        new KeyValue(this.radio.strokeProperty(), selectedColor, Interpolator.EASE_BOTH))
        );
    }

    private void removeRadio() {
        this.getChildren().removeIf(node -> "radio".equals(node.getStyleClass().get(0)));
    }

    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) + this.snapSizeX(this.radio.minWidth(-1.0)) + this.labelOffset + 2.0 * PADDING;
    }

    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) + this.snapSizeX(this.radio.prefWidth(-1.0)) + this.labelOffset + 2.0 * PADDING;
    }

    static double computeXOffset(double width, double contentWidth, HPos hpos) {
        return switch (hpos) {
            case LEFT -> 0.0;
            case CENTER -> (width - contentWidth) / 2.0;
            case RIGHT -> width - contentWidth;
        };
    }

    static double computeYOffset(double height, double contentHeight, VPos vpos) {
        return switch (vpos) {
            case TOP, BASELINE -> 0.0;
            case CENTER -> (height - contentHeight) / 2.0;
            case BOTTOM -> height - contentHeight;
        };
    }
}
