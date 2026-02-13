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
import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.beans.property.ReadOnlyBooleanProperty;
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
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;

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
        this.box.setMinSize(18.0, 18.0);
        this.box.setPrefSize(18.0, 18.0);
        this.box.setMaxSize(18.0, 18.0);
        this.box.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, new CornerRadii(2.0), Insets.EMPTY)));
        this.box.setBorder(new Border(new BorderStroke(Themes.getColorScheme().getOnSurfaceVariant(), BorderStrokeStyle.SOLID, new CornerRadii(2.0), new BorderWidths(this.lineThick))));
        StackPane boxContainer = new StackPane();
        boxContainer.getChildren().add(this.box);
        boxContainer.setPadding(new Insets(this.padding));
        this.rippler = new JFXRippler(boxContainer, RipplerMask.CIRCLE, RipplerPos.BACK);
        this.updateRippleColor();
        SVGPath shape = new SVGPath();
        shape.setContent("M384 690l452-452 60 60-512 512-238-238 60-60z");
        this.mark.setShape(shape);
        this.mark.setMaxSize(15.0, 12.0);
        this.mark.setStyle("-fx-background-color:-monet-on-primary; -fx-border-color:-monet-on-primary; -fx-border-width:2px;");
        this.mark.setVisible(false);
        this.mark.setScaleX(0.0);
        this.mark.setScaleY(0.0);
        boxContainer.getChildren().add(this.mark);
        this.container.getChildren().add(this.rippler);
        AnchorPane.setRightAnchor(this.rippler, this.labelOffset);
        control.selectedProperty().addListener((o, oldVal, newVal) -> {
            this.updateRippleColor();
            this.playSelectAnimation(newVal);
        });

        ReadOnlyBooleanProperty focusVisibleProperty = FXUtils.focusVisibleProperty(control);
        if (focusVisibleProperty == null)
            focusVisibleProperty = control.focusedProperty();
        focusVisibleProperty.addListener((o, oldVal, newVal) -> {
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
        this.registerChangeListener(control.checkedColorProperty(), ignored -> {
            if (select != null) {
                select.stop();
            }
            this.createFillTransition();
            updateColors();
        });
        this.registerChangeListener(control.unCheckedColorProperty(), ignored -> updateColors());
        this.transition = new CheckBoxTransition();
        this.createFillTransition();
    }

    private void updateRippleColor() {
        var control = (JFXCheckBox) this.getSkinnable();
        this.rippler.setRipplerFill(control.isSelected()
                ? control.getCheckedColor()
                : control.getUnCheckedColor());
    }

    private void updateColors() {
        var control = (JFXCheckBox) getSkinnable();
        boolean isSelected = control.isSelected();
        JFXNodeUtils.updateBackground(box.getBackground(), box, isSelected ? control.getCheckedColor() : Color.TRANSPARENT);
        rippler.setRipplerFill(isSelected ? control.getCheckedColor() : control.getUnCheckedColor());
        final BorderStroke borderStroke = box.getBorder().getStrokes().get(0);
        box.setBorder(new Border(new BorderStroke(
                isSelected ? control.getCheckedColor() : Themes.getColorScheme().getOnSurfaceVariant(),
                borderStroke.getTopStyle(),
                borderStroke.getRadii(),
                borderStroke.getWidths())));
    }

    protected void updateChildren() {
        super.updateChildren();
        if (this.container != null) {
            this.getChildren().remove(1);
            this.getChildren().add(this.container);
        }
    }

    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) + this.snapSizeX(this.box.minWidth(-1.0)) + this.labelOffset + 2.0 * this.padding;
    }

    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) + this.snapSizeY(this.box.prefWidth(-1.0)) + this.labelOffset + 2.0 * this.padding;
    }

    protected void layoutChildren(double x, double y, double w, double h) {
        CheckBox checkBox = this.getSkinnable();
        double boxWidth = this.snapSizeX(this.container.prefWidth(-1.0));
        double boxHeight = this.snapSizeY(this.container.prefHeight(-1.0));
        double computeWidth = Math.min(checkBox.prefWidth(-1.0), checkBox.minWidth(-1.0)) + this.labelOffset + 2.0 * this.padding;
        double labelWidth = Math.min(computeWidth - boxWidth, w - this.snapSizeX(boxWidth)) + this.labelOffset + 2.0 * this.padding;
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
        this.positionInArea(this.container, xOffset, yOffset, boxWidth, maxHeight, 0.0, checkBox.getAlignment().getHpos(), checkBox.getAlignment().getVpos());
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
            case TOP -> 0.0;
            case CENTER -> (height - contentHeight) / 2.0;
            case BOTTOM -> height - contentHeight;
            default -> 0.0;
        };
    }

    private void playSelectAnimation(Boolean selection) {
        if (selection == null) {
            selection = false;
        }

        JFXCheckBox control = (JFXCheckBox) this.getSkinnable();
        this.transition.setRate(selection ? 1.0 : -1.0);
        this.select.setRate(selection ? 1.0 : -1.0);
        this.transition.play();
        this.select.play();
        this.box.setBorder(new Border(new BorderStroke(
                selection ? control.getCheckedColor() : Themes.getColorScheme().getOnSurfaceVariant(),
                BorderStrokeStyle.SOLID,
                new CornerRadii(2.0),
                new BorderWidths(this.lineThick))));
    }

    private void createFillTransition() {
        this.select = new JFXFillTransition(Duration.millis(120.0), this.box, Color.TRANSPARENT,
                (Color) ((JFXCheckBox) this.getSkinnable()).getCheckedColor());
        this.select.setInterpolator(Interpolator.EASE_OUT);
    }

    private final class CheckBoxTransition extends CachedTransition {
        CheckBoxTransition() {
            super(JFXCheckBoxSkin.this.mark,
                    new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(JFXCheckBoxSkin.this.mark.visibleProperty(), false, Interpolator.EASE_OUT),
                                    new KeyValue(JFXCheckBoxSkin.this.mark.scaleXProperty(), (double) 0.5F, Interpolator.EASE_OUT),
                                    new KeyValue(JFXCheckBoxSkin.this.mark.scaleYProperty(), (double) 0.5F, Interpolator.EASE_OUT)),
                            new KeyFrame(Duration.millis(400.0),
                                    new KeyValue(JFXCheckBoxSkin.this.mark.visibleProperty(), true, Interpolator.EASE_OUT),
                                    new KeyValue(JFXCheckBoxSkin.this.mark.scaleXProperty(), (double) 0.5F, Interpolator.EASE_OUT),
                                    new KeyValue(JFXCheckBoxSkin.this.mark.scaleYProperty(), (double) 0.5F, Interpolator.EASE_OUT)),
                            new KeyFrame(Duration.millis(1000.0),
                                    new KeyValue(JFXCheckBoxSkin.this.mark.visibleProperty(), true, Interpolator.EASE_OUT),
                                    new KeyValue(JFXCheckBoxSkin.this.mark.scaleXProperty(), 1, Interpolator.EASE_OUT),
                                    new KeyValue(JFXCheckBoxSkin.this.mark.scaleYProperty(), 1, Interpolator.EASE_OUT))
                    )
            );
            this.setCycleDuration(Duration.seconds(0.12));
            this.setDelay(Duration.seconds(0.05));
        }
    }
}
