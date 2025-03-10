//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.jfoenix.controls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.scene.Parent;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.util.Duration;

// Old
public class JFXSpinner extends StackPane {
    private static final Color GREEN_COLOR = Color.valueOf("#0F9D58");
    private static final Color RED_COLOR = Color.valueOf("#db4437");
    private static final Color YELLOW_COLOR = Color.valueOf("#f4b400");
    private static final Color BLUE_COLOR = Color.valueOf("#4285f4");

    private static final String DEFAULT_STYLE_CLASS = "jfx-spinner";

    private Timeline timeline;
    private Arc arc;
    private boolean initialized;

    public JFXSpinner() {
        this.getStyleClass().add("jfx-spinner");
        this.initialize();
    }

    private void initialize() {
        this.arc = new Arc(0.0, 0.0, 12.0, 12.0, 0.0, 360.0);
        this.arc.setFill(Color.TRANSPARENT);
        this.arc.setStrokeWidth(3.0);
        this.arc.getStyleClass().add("arc");
        this.arc.radiusXProperty().bindBidirectional(this.radius);
        this.arc.radiusYProperty().bindBidirectional(this.radius);
        this.getChildren().add(this.arc);
        this.minWidthProperty().bind(Bindings.createDoubleBinding(() -> this.getRadius() * 2.0 + this.arc.getStrokeWidth() + 5.0, this.radius, this.arc.strokeWidthProperty()));
        this.maxWidthProperty().bind(Bindings.createDoubleBinding(() -> this.getRadius() * 2.0 + this.arc.getStrokeWidth() + 5.0, this.radius, this.arc.strokeWidthProperty()));
        this.minHeightProperty().bind(Bindings.createDoubleBinding(() -> this.getRadius() * 2.0 + this.arc.getStrokeWidth() + 5.0, this.radius, this.arc.strokeWidthProperty()));
        this.maxHeightProperty().bind(Bindings.createDoubleBinding(() -> this.getRadius() * 2.0 + this.arc.getStrokeWidth() + 5.0, this.radius, this.arc.strokeWidthProperty()));
    }

    private KeyFrame[] getKeyFrames(double angle, double duration, Color color) {
        return new KeyFrame[]{
                new KeyFrame(Duration.seconds(duration),
                        new KeyValue(this.arc.lengthProperty(), 5, Interpolator.LINEAR),
                        new KeyValue(this.arc.startAngleProperty(), angle + 45.0 + this.getStartingAngle(), Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(duration + 0.4),
                        new KeyValue(this.arc.lengthProperty(), 250, Interpolator.LINEAR),
                        new KeyValue(this.arc.startAngleProperty(), angle + 90.0 + this.getStartingAngle(), Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(duration + 0.7),
                        new KeyValue(this.arc.lengthProperty(), 250, Interpolator.LINEAR),
                        new KeyValue(this.arc.startAngleProperty(), angle + 135.0 + this.getStartingAngle(), Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(duration + 1.1),
                        new KeyValue(this.arc.lengthProperty(), 5, Interpolator.LINEAR),
                        new KeyValue(this.arc.startAngleProperty(), angle + 435.0 + this.getStartingAngle(), Interpolator.LINEAR),
                        new KeyValue(this.arc.strokeProperty(), color, Interpolator.EASE_BOTH))
        };
    }

    protected void layoutChildren() {
        if (!this.initialized) {
            super.layoutChildren();
            Color initialColor = (Color) this.arc.getStroke();
            if (initialColor == null) {
                this.arc.setStroke(BLUE_COLOR);
            }

            KeyFrame[] blueFrame = this.getKeyFrames(0.0, 0.0, initialColor == null ? BLUE_COLOR : initialColor);
            KeyFrame[] redFrame = this.getKeyFrames(450.0, 1.4, initialColor == null ? RED_COLOR : initialColor);
            KeyFrame[] yellowFrame = this.getKeyFrames(900.0, 2.8, initialColor == null ? YELLOW_COLOR : initialColor);
            KeyFrame[] greenFrame = this.getKeyFrames(1350.0, 4.2, initialColor == null ? GREEN_COLOR : initialColor);
            KeyFrame endingFrame = new KeyFrame(Duration.seconds(5.6),
                    new KeyValue(this.arc.lengthProperty(), 5, Interpolator.LINEAR),
                    new KeyValue(this.arc.startAngleProperty(), 1845.0 + this.getStartingAngle(), Interpolator.LINEAR)
            );
            if (this.timeline != null) {
                this.timeline.stop();
            }

            this.timeline = new Timeline(
                    blueFrame[0], blueFrame[1], blueFrame[2], blueFrame[3],
                    redFrame[0], redFrame[1], redFrame[2], redFrame[3],
                    yellowFrame[0], yellowFrame[1], yellowFrame[2], yellowFrame[3],
                    greenFrame[0], greenFrame[1], greenFrame[2], greenFrame[3],
                    endingFrame
            );
            this.timeline.setCycleCount(-1);
            this.timeline.setRate(1.0);
            this.timeline.play();
            this.initialized = true;
        }

    }

    private final StyleableDoubleProperty radius = new SimpleStyleableDoubleProperty(StyleableProperties.RADIUS, this, "radius", 12.0);
    public final StyleableDoubleProperty radiusProperty() {
        return this.radius;
    }

    public final double getRadius() {
        return this.radiusProperty().get();
    }

    public final void setRadius(double radius) {
        this.radiusProperty().set(radius);
    }

    private final StyleableDoubleProperty startingAngle = new SimpleStyleableDoubleProperty(StyleableProperties.STARTING_ANGLE, this, "starting_angle", 360.0 - Math.random() * 720.0);

    public final StyleableDoubleProperty startingAngleProperty() {
        return this.startingAngle;
    }

    public final double getStartingAngle() {
        return this.startingAngleProperty().get();
    }

    public final void setStartingAngle(double startingAngle) {
        this.startingAngleProperty().set(startingAngle);
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.CHILD_STYLEABLES;
    }

    private static class StyleableProperties {
        private static final CssMetaData<JFXSpinner, Number> RADIUS =
                new CssMetaData<JFXSpinner, Number>("-jfx-radius", StyleConverter.getSizeConverter(), 12) {
                    public boolean isSettable(JFXSpinner control) {
                        return !control.radius.isBound();
                    }

                    public StyleableDoubleProperty getStyleableProperty(JFXSpinner control) {
                        return control.radius;
                    }
                };
        private static final CssMetaData<JFXSpinner, Number> STARTING_ANGLE =
                new CssMetaData<JFXSpinner, Number>("-jfx-starting-angle", StyleConverter.getSizeConverter(), 360.0 - Math.random() * 720.0) {
                    public boolean isSettable(JFXSpinner control) {
                        return !control.startingAngle.isBound();
                    }

                    public StyleableDoubleProperty getStyleableProperty(JFXSpinner control) {
                        return control.startingAngle;
                    }
                };
        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        private StyleableProperties() {
        }

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(ProgressIndicator.getClassCssMetaData());
            Collections.addAll(styleables, RADIUS, STARTING_ANGLE);
            CHILD_STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
