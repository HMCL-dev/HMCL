//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jfoenix.skins;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXRippler;
import com.jfoenix.controls.JFXRippler.RipplerMask;
import com.jfoenix.controls.JFXRippler.RipplerPos;
import com.jfoenix.transitions.CachedTransition;
import com.jfoenix.transitions.JFXFillTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.skin.CheckBoxSkin;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class JFXCheckBoxSkin extends CheckBoxSkin {
    private final StackPane box = new StackPane();
    private final StackPane mark = new StackPane();
    private final double lineThick = 2.0;
    private final double padding = 10.0;
    private final JFXRippler rippler;
    private final AnchorPane container = new AnchorPane();
    private final double labelOffset = -8.0;
    private final Transition transition;
    private boolean invalid = true;
    private JFXFillTransition select;

    public JFXCheckBoxSkin(JFXCheckBox control) {
        super(control);
        this.box.setMinSize(18.0F, 18.0F);
        this.box.setPrefSize(18.0F, 18.0F);
        this.box.setMaxSize(18.0F, 18.0F);
        this.box.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, new CornerRadii(2.0F), Insets.EMPTY)));
        this.box.setBorder(new Border(new BorderStroke(control.getUnCheckedColor(), BorderStrokeStyle.SOLID, new CornerRadii(2.0F), new BorderWidths(this.lineThick))));
        StackPane boxContainer = new StackPane();
        boxContainer.getChildren().add(this.box);
        boxContainer.setPadding(new Insets(this.padding));
        this.rippler = new JFXRippler(boxContainer, RipplerMask.CIRCLE, RipplerPos.BACK);
        this.updateRippleColor();
        SVGPath shape = new SVGPath();
        shape.setContent("M384 690l452-452 60 60-512 512-238-238 60-60z");
        this.mark.setShape(shape);
        this.mark.setMaxSize(15.0F, 12.0F);
        this.mark.setStyle("-fx-background-color:WHITE; -fx-border-color:WHITE; -fx-border-width:2px;");
        this.mark.setVisible(false);
        this.mark.setScaleX(0.0F);
        this.mark.setScaleY(0.0F);
        boxContainer.getChildren().add(this.mark);
        this.container.getChildren().add(this.rippler);
        AnchorPane.setRightAnchor(this.rippler, this.labelOffset);
        control.selectedProperty().addListener((o, oldVal, newVal) -> {
            this.updateRippleColor();
            this.playSelectAnimation(newVal);
        });
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
        this.updateChildren();
        this.registerChangeListener(control.checkedColorProperty(), ignored -> this.createFillTransition());
        this.transition = new CheckBoxTransition();
        this.createFillTransition();
    }

    private void updateRippleColor() {
        this.rippler.setRipplerFill(this.getSkinnable().isSelected()
                ? ((JFXCheckBox) this.getSkinnable()).getCheckedColor()
                : ((JFXCheckBox) this.getSkinnable()).getUnCheckedColor());
    }

    protected void updateChildren() {
        super.updateChildren();
        if (this.container != null) {
            this.getChildren().remove(1);
            this.getChildren().add(this.container);
        }

    }

    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) + this.snapSizeX(this.box.minWidth(-1.0F)) + this.labelOffset + (double) 2.0F * this.padding;
    }

    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) + this.snapSizeY(this.box.prefWidth(-1.0F)) + this.labelOffset + (double) 2.0F * this.padding;
    }

    protected void layoutChildren(double x, double y, double w, double h) {
        CheckBox checkBox = this.getSkinnable();
        double boxWidth = this.snapSizeX(this.container.prefWidth(-1.0F));
        double boxHeight = this.snapSizeY(this.container.prefHeight(-1.0F));
        double computeWidth = Math.min(checkBox.prefWidth(-1.0F), checkBox.minWidth(-1.0F)) + this.labelOffset + (double) 2.0F * this.padding;
        double labelWidth = Math.min(computeWidth - boxWidth, w - this.snapSizeX(boxWidth)) + this.labelOffset + (double) 2.0F * this.padding;
        double labelHeight = Math.min(checkBox.prefHeight(labelWidth), h);
        double maxHeight = Math.max(boxHeight, labelHeight);
        double xOffset = computeXOffset(w, labelWidth + boxWidth, checkBox.getAlignment().getHpos()) + x;
        double yOffset = computeYOffset(h, maxHeight, checkBox.getAlignment().getVpos()) + x;
        if (this.invalid) {
            if (this.getSkinnable().isSelected()) {
                this.playSelectAnimation(true);
            }

            this.invalid = false;
        }

        this.layoutLabelInArea(xOffset + boxWidth, yOffset, labelWidth, maxHeight, checkBox.getAlignment());
        this.container.resize(boxWidth, boxHeight);
        this.positionInArea(this.container, xOffset, yOffset, boxWidth, maxHeight, 0.0F, checkBox.getAlignment().getHpos(), checkBox.getAlignment().getVpos());
    }

    static double computeXOffset(double width, double contentWidth, HPos hpos) {
        switch (hpos) {
            case LEFT:
                return 0.0F;
            case CENTER:
                return (width - contentWidth) / (double) 2.0F;
            case RIGHT:
                return width - contentWidth;
            default:
                return 0.0F;
        }
    }

    static double computeYOffset(double height, double contentHeight, VPos vpos) {
        switch (vpos) {
            case TOP:
                return 0.0F;
            case CENTER:
                return (height - contentHeight) / (double) 2.0F;
            case BOTTOM:
                return height - contentHeight;
            default:
                return 0.0F;
        }
    }

    private void playSelectAnimation(Boolean selection) {
        if (selection == null) {
            selection = false;
        }

        JFXCheckBox control = (JFXCheckBox) this.getSkinnable();
        this.transition.setRate(selection ? (double) 1.0F : (double) -1.0F);
        this.select.setRate(selection ? (double) 1.0F : (double) -1.0F);
        this.transition.play();
        this.select.play();
        this.box.setBorder(new Border(new BorderStroke(selection ? control.getCheckedColor() : control.getUnCheckedColor(), BorderStrokeStyle.SOLID, new CornerRadii(2.0F), new BorderWidths(this.lineThick))));
    }

    private void createFillTransition() {
        this.select = new JFXFillTransition(Duration.millis(120.0F), this.box, Color.TRANSPARENT, (Color) ((JFXCheckBox) this.getSkinnable()).getCheckedColor());
        this.select.setInterpolator(Interpolator.EASE_OUT);
    }

    private final class CheckBoxTransition extends CachedTransition {
        CheckBoxTransition() {
            super(JFXCheckBoxSkin.this.mark, new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(JFXCheckBoxSkin.this.mark.visibleProperty(), false, Interpolator.EASE_BOTH), new KeyValue(JFXCheckBoxSkin.this.mark.scaleXProperty(), (double) 0.5F, Interpolator.EASE_OUT), new KeyValue(JFXCheckBoxSkin.this.mark.scaleYProperty(), (double) 0.5F, Interpolator.EASE_OUT)), new KeyFrame(Duration.millis(400.0F), new KeyValue(JFXCheckBoxSkin.this.mark.visibleProperty(), true, Interpolator.EASE_OUT), new KeyValue(JFXCheckBoxSkin.this.mark.scaleXProperty(), (double) 0.5F, Interpolator.EASE_OUT), new KeyValue(JFXCheckBoxSkin.this.mark.scaleYProperty(), (double) 0.5F, Interpolator.EASE_OUT)), new KeyFrame(Duration.millis(1000.0F), new KeyValue(JFXCheckBoxSkin.this.mark.scaleXProperty(), 1, Interpolator.EASE_OUT), new KeyValue(JFXCheckBoxSkin.this.mark.scaleYProperty(), 1, Interpolator.EASE_OUT))));
            this.setCycleDuration(Duration.seconds(0.12));
            this.setDelay(Duration.seconds(0.05));
        }
    }
}
