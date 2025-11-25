/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.skins;

import com.jfoenix.effects.JFXDepthManager;
import com.jfoenix.transitions.CachedTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.ArrayList;

import static javafx.animation.Interpolator.EASE_BOTH;


/**
 * @author Shadi Shaheen
 */
class JFXCustomColorPicker extends Pane {

    ObjectProperty<RecentColorPath> selectedPath = new SimpleObjectProperty<>();
    private MoveTo startPoint;
    private CubicCurveTo curve0To;
    private CubicCurveTo outerCircleCurveTo;
    private CubicCurveTo curve1To;
    private CubicCurveTo innerCircleCurveTo;
    private ArrayList<CubicCurve> curves = new ArrayList<>();

    private double distance = 200;
    private double centerX = distance;
    private double centerY = distance;
    private double radius = 110;

    private int shapesNumber = 13;
    private ArrayList<RecentColorPath> shapes = new ArrayList<>();
    private CachedTransition showAnimation;
    private JFXColorPickerUI hslColorPicker;

    public JFXCustomColorPicker() {
        this.setPickOnBounds(false);
        this.setMinSize(distance * 2, distance * 2);

        final DoubleProperty rotationAngle = new SimpleDoubleProperty(2.1);

        // draw recent colors shape using cubic curves
        init(rotationAngle, centerX + 53, centerY + 162);

        hslColorPicker = new JFXColorPickerUI((int) distance);
        hslColorPicker.setLayoutX(centerX - distance / 2);
        hslColorPicker.setLayoutY(centerY - distance / 2);
        this.getChildren().add(hslColorPicker);
        // add recent colors shapes
        final int shapesStartIndex = this.getChildren().size();
        final int shapesEndIndex = shapesStartIndex + shapesNumber;
        for (int i = 0; i < shapesNumber; i++) {
            final double angle = 2 * i * Math.PI / shapesNumber;
            final RecentColorPath path = new RecentColorPath(startPoint,
                curve0To,
                outerCircleCurveTo,
                curve1To,
                innerCircleCurveTo);
            shapes.add(path);
            path.setPickOnBounds(false);
            final Rotate rotate = new Rotate(Math.toDegrees(angle), centerX, centerY);
            path.getTransforms().add(rotate);
            this.getChildren().add(shapesStartIndex, path);
            path.setFill(Color.valueOf(getDefaultColor(i)));
            path.setFocusTraversable(true);
            path.addEventHandler(MouseEvent.MOUSE_CLICKED, (event) -> {
                path.requestFocus();
                selectedPath.set(path);
            });
        }

        // add selection listeners
        selectedPath.addListener((o, oldVal, newVal) -> {
            if (oldVal != null) {
                hslColorPicker.removeColorSelectionNode(oldVal);
                oldVal.playTransition(-1);
            }
            // re-arrange children
            while (this.getChildren().indexOf(newVal) != shapesEndIndex - 1) {
                final Node temp = this.getChildren().get(shapesEndIndex - 1);
                this.getChildren().remove(shapesEndIndex - 1);
                this.getChildren().add(shapesStartIndex, temp);
            }
            // update path fill according to the color picker
            newVal.setStroke(Color.rgb(255, 255, 255, 0.87));
            newVal.playTransition(1);
            hslColorPicker.moveToColor((Color) newVal.getFill());
            hslColorPicker.addColorSelectionNode(newVal);
        });
        // init selection
        selectedPath.set((RecentColorPath) this.getChildren().get(shapesStartIndex));
    }


    public int getShapesNumber() {
        return shapesNumber;
    }

    public int getSelectedIndex() {
        if (selectedPath.get() != null) {
            return shapes.indexOf(selectedPath.get());
        }
        return -1;
    }

    public void setColor(final Color color) {
        shapes.get(getSelectedIndex()).setFill(color);
        hslColorPicker.moveToColor(color);
    }

    public Color getColor(final int index) {
        if (index >= 0 && index < shapes.size()) {
            return (Color) shapes.get(index).getFill();
        } else {
            return Color.WHITE;
        }
    }


    public void preAnimate() {
        final CubicCurve firstCurve = curves.get(0);
        final double x = firstCurve.getStartX();
        final double y = firstCurve.getStartY();
        firstCurve.setStartX(centerX);
        firstCurve.setStartY(centerY);

        final CubicCurve secondCurve = curves.get(1);
        final double x1 = secondCurve.getStartX();
        final double y1 = secondCurve.getStartY();
        secondCurve.setStartX(centerX);
        secondCurve.setStartY(centerY);

        final double cx1 = firstCurve.getControlX1();
        final double cy1 = firstCurve.getControlY1();
        firstCurve.setControlX1(centerX + radius);
        firstCurve.setControlY1(centerY + radius / 2);

        final KeyFrame keyFrame = new KeyFrame(Duration.millis(1000),
            new KeyValue(firstCurve.startXProperty(), x, EASE_BOTH),
            new KeyValue(firstCurve.startYProperty(), y, EASE_BOTH),
            new KeyValue(secondCurve.startXProperty(), x1, EASE_BOTH),
            new KeyValue(secondCurve.startYProperty(), y1, EASE_BOTH),
            new KeyValue(firstCurve.controlX1Property(), cx1, EASE_BOTH),
            new KeyValue(firstCurve.controlY1Property(), cy1, EASE_BOTH)
        );
        final Timeline timeline = new Timeline(keyFrame);
        showAnimation = new CachedTransition(this, timeline) {
            {
                setCycleDuration(Duration.millis(240));
                setDelay(Duration.millis(0));
            }
        };
    }

    public void animate() {
        showAnimation.play();
    }

    private void init(final DoubleProperty rotationAngle, final double initControlX1, final double initControlY1) {

        final Circle innerCircle = new Circle(centerX, centerY, radius, Color.TRANSPARENT);
        final Circle outerCircle = new Circle(centerX, centerY, radius * 2, Color.web("blue", 0.5));

        // Create a composite shape of 4 cubic curves
        // create 2 cubic curves of the shape
        createQuadraticCurve(rotationAngle, initControlX1, initControlY1);

        // inner circle curve
        final CubicCurve innerCircleCurve = new CubicCurve();
        innerCircleCurve.startXProperty().bind(curves.get(0).startXProperty());
        innerCircleCurve.startYProperty().bind(curves.get(0).startYProperty());
        innerCircleCurve.endXProperty().bind(curves.get(1).startXProperty());
        innerCircleCurve.endYProperty().bind(curves.get(1).startYProperty());
        curves.get(0).startXProperty().addListener((o, oldVal, newVal) -> {
            final Point2D controlPoint = makeControlPoint(newVal.doubleValue(),
                curves.get(0).getStartY(),
                innerCircle,
                shapesNumber,
                -1);
            innerCircleCurve.setControlX1(controlPoint.getX());
            innerCircleCurve.setControlY1(controlPoint.getY());
        });
        curves.get(0).startYProperty().addListener((o, oldVal, newVal) -> {
            final Point2D controlPoint = makeControlPoint(curves.get(0).getStartX(),
                newVal.doubleValue(),
                innerCircle,
                shapesNumber,
                -1);
            innerCircleCurve.setControlX1(controlPoint.getX());
            innerCircleCurve.setControlY1(controlPoint.getY());
        });
        curves.get(1).startXProperty().addListener((o, oldVal, newVal) -> {
            final Point2D controlPoint = makeControlPoint(newVal.doubleValue(),
                curves.get(1).getStartY(),
                innerCircle,
                shapesNumber,
                1);
            innerCircleCurve.setControlX2(controlPoint.getX());
            innerCircleCurve.setControlY2(controlPoint.getY());
        });
        curves.get(1).startYProperty().addListener((o, oldVal, newVal) -> {
            final Point2D controlPoint = makeControlPoint(curves.get(1).getStartX(),
                newVal.doubleValue(),
                innerCircle,
                shapesNumber,
                1);
            innerCircleCurve.setControlX2(controlPoint.getX());
            innerCircleCurve.setControlY2(controlPoint.getY());
        });
        Point2D controlPoint = makeControlPoint(curves.get(0).getStartX(),
            curves.get(0).getStartY(),
            innerCircle,
            shapesNumber,
            -1);
        innerCircleCurve.setControlX1(controlPoint.getX());
        innerCircleCurve.setControlY1(controlPoint.getY());
        controlPoint = makeControlPoint(curves.get(1).getStartX(),
            curves.get(1).getStartY(),
            innerCircle,
            shapesNumber,
            1);
        innerCircleCurve.setControlX2(controlPoint.getX());
        innerCircleCurve.setControlY2(controlPoint.getY());

        // outer circle curve
        final CubicCurve outerCircleCurve = new CubicCurve();
        outerCircleCurve.startXProperty().bind(curves.get(0).endXProperty());
        outerCircleCurve.startYProperty().bind(curves.get(0).endYProperty());
        outerCircleCurve.endXProperty().bind(curves.get(1).endXProperty());
        outerCircleCurve.endYProperty().bind(curves.get(1).endYProperty());
        controlPoint = makeControlPoint(curves.get(0).getEndX(),
            curves.get(0).getEndY(),
            outerCircle,
            shapesNumber,
            -1);
        outerCircleCurve.setControlX1(controlPoint.getX());
        outerCircleCurve.setControlY1(controlPoint.getY());
        controlPoint = makeControlPoint(curves.get(1).getEndX(), curves.get(1).getEndY(), outerCircle, shapesNumber, 1);
        outerCircleCurve.setControlX2(controlPoint.getX());
        outerCircleCurve.setControlY2(controlPoint.getY());

        startPoint = new MoveTo();
        startPoint.xProperty().bind(curves.get(0).startXProperty());
        startPoint.yProperty().bind(curves.get(0).startYProperty());

        curve0To = new CubicCurveTo();
        curve0To.controlX1Property().bind(curves.get(0).controlX1Property());
        curve0To.controlY1Property().bind(curves.get(0).controlY1Property());
        curve0To.controlX2Property().bind(curves.get(0).controlX2Property());
        curve0To.controlY2Property().bind(curves.get(0).controlY2Property());
        curve0To.xProperty().bind(curves.get(0).endXProperty());
        curve0To.yProperty().bind(curves.get(0).endYProperty());

        outerCircleCurveTo = new CubicCurveTo();
        outerCircleCurveTo.controlX1Property().bind(outerCircleCurve.controlX1Property());
        outerCircleCurveTo.controlY1Property().bind(outerCircleCurve.controlY1Property());
        outerCircleCurveTo.controlX2Property().bind(outerCircleCurve.controlX2Property());
        outerCircleCurveTo.controlY2Property().bind(outerCircleCurve.controlY2Property());
        outerCircleCurveTo.xProperty().bind(outerCircleCurve.endXProperty());
        outerCircleCurveTo.yProperty().bind(outerCircleCurve.endYProperty());

        curve1To = new CubicCurveTo();
        curve1To.controlX1Property().bind(curves.get(1).controlX2Property());
        curve1To.controlY1Property().bind(curves.get(1).controlY2Property());
        curve1To.controlX2Property().bind(curves.get(1).controlX1Property());
        curve1To.controlY2Property().bind(curves.get(1).controlY1Property());
        curve1To.xProperty().bind(curves.get(1).startXProperty());
        curve1To.yProperty().bind(curves.get(1).startYProperty());

        innerCircleCurveTo = new CubicCurveTo();
        innerCircleCurveTo.controlX1Property().bind(innerCircleCurve.controlX2Property());
        innerCircleCurveTo.controlY1Property().bind(innerCircleCurve.controlY2Property());
        innerCircleCurveTo.controlX2Property().bind(innerCircleCurve.controlX1Property());
        innerCircleCurveTo.controlY2Property().bind(innerCircleCurve.controlY1Property());
        innerCircleCurveTo.xProperty().bind(innerCircleCurve.startXProperty());
        innerCircleCurveTo.yProperty().bind(innerCircleCurve.startYProperty());
    }


    private void createQuadraticCurve(final DoubleProperty rotationAngle, final double initControlX1, final double initControlY1) {
        for (int i = 0; i < 2; i++) {

            double angle = 2 * i * Math.PI / shapesNumber;
            double xOffset = radius * Math.cos(angle);
            double yOffset = radius * Math.sin(angle);
            final double startx = centerX + xOffset;
            final double starty = centerY + yOffset;

            final double diffStartCenterX = startx - centerX;
            final double diffStartCenterY = starty - centerY;
            final double sinRotAngle = Math.sin(rotationAngle.get());
            final double cosRotAngle = Math.cos(rotationAngle.get());
            final double startXR = cosRotAngle * diffStartCenterX - sinRotAngle * diffStartCenterY + centerX;
            final double startYR = sinRotAngle * diffStartCenterX + cosRotAngle * diffStartCenterY + centerY;

            angle = 2 * i * Math.PI / shapesNumber;
            xOffset = distance * Math.cos(angle);
            yOffset = distance * Math.sin(angle);

            final double endx = centerX + xOffset;
            final double endy = centerY + yOffset;

            final CubicCurve curvedLine = new CubicCurve();
            curvedLine.setStartX(startXR);
            curvedLine.setStartY(startYR);
            curvedLine.setControlX1(startXR);
            curvedLine.setControlY1(startYR);
            curvedLine.setControlX2(endx);
            curvedLine.setControlY2(endy);
            curvedLine.setEndX(endx);
            curvedLine.setEndY(endy);
            curvedLine.setStroke(Color.FORESTGREEN);
            curvedLine.setStrokeWidth(1);
            curvedLine.setStrokeLineCap(StrokeLineCap.ROUND);
            curvedLine.setFill(Color.TRANSPARENT);
            curvedLine.setMouseTransparent(true);
            rotationAngle.addListener((o, oldVal, newVal) -> {
                final double newstartXR = ((cosRotAngle * diffStartCenterX) - (sinRotAngle * diffStartCenterY)) + centerX;
                final double newstartYR = (sinRotAngle * diffStartCenterX) + (cosRotAngle * diffStartCenterY) + centerY;
                curvedLine.setStartX(newstartXR);
                curvedLine.setStartY(newstartYR);
            });

            curves.add(curvedLine);

            if (i == 0) {
                curvedLine.setControlX1(initControlX1);
                curvedLine.setControlY1(initControlY1);
            } else {
                final CubicCurve firstCurve = curves.get(0);
                final double curveTheta = 2 * curves.indexOf(curvedLine) * Math.PI / shapesNumber;

                curvedLine.controlX1Property().bind(Bindings.createDoubleBinding(() -> {
                    final double a = firstCurve.getControlX1() - centerX;
                    final double b = Math.sin(curveTheta) * (firstCurve.getControlY1() - centerY);
                    return ((Math.cos(curveTheta) * a) - b) + centerX;
                }, firstCurve.controlX1Property(), firstCurve.controlY1Property()));

                curvedLine.controlY1Property().bind(Bindings.createDoubleBinding(() -> {
                    final double a = Math.sin(curveTheta) * (firstCurve.getControlX1() - centerX);
                    final double b = Math.cos(curveTheta) * (firstCurve.getControlY1() - centerY);
                    return a + b + centerY;
                }, firstCurve.controlX1Property(), firstCurve.controlY1Property()));


                curvedLine.controlX2Property().bind(Bindings.createDoubleBinding(() -> {
                    final double a = firstCurve.getControlX2() - centerX;
                    final double b = firstCurve.getControlY2() - centerY;
                    return ((Math.cos(curveTheta) * a) - (Math.sin(curveTheta) * b)) + centerX;
                }, firstCurve.controlX2Property(), firstCurve.controlY2Property()));

                curvedLine.controlY2Property().bind(Bindings.createDoubleBinding(() -> {
                    final double a = Math.sin(curveTheta) * (firstCurve.getControlX2() - centerX);
                    final double b = Math.cos(curveTheta) * (firstCurve.getControlY2() - centerY);
                    return a + b + centerY;
                }, firstCurve.controlX2Property(), firstCurve.controlY2Property()));
            }
        }
    }

    private String getDefaultColor(final int i) {
        String color = "#FFFFFF";
        switch (i) {
            case 0:
                color = "#8F3F7E";
                break;
            case 1:
                color = "#B5305F";
                break;
            case 2:
                color = "#CE584A";
                break;
            case 3:
                color = "#DB8D5C";
                break;
            case 4:
                color = "#DA854E";
                break;
            case 5:
                color = "#E9AB44";
                break;
            case 6:
                color = "#FEE435";
                break;
            case 7:
                color = "#99C286";
                break;
            case 8:
                color = "#01A05E";
                break;
            case 9:
                color = "#4A8895";
                break;
            case 10:
                color = "#16669B";
                break;
            case 11:
                color = "#2F65A5";
                break;
            case 12:
                color = "#4E6A9C";
                break;
            default:
                break;
        }
        return color;
    }

    private Point2D rotate(final Point2D a, final Point2D center, final double angle) {
        final double resultX = center.getX() + (a.getX() - center.getX()) * Math.cos(angle) - (a.getY() - center.getY()) * Math
            .sin(angle);
        final double resultY = center.getY() + (a.getX() - center.getX()) * Math.sin(angle) + (a.getY() - center.getY()) * Math
            .cos(angle);
        return new Point2D(resultX, resultY);
    }

    private Point2D makeControlPoint(final double endX, final double endY, final Circle circle, final int numSegments, int direction) {
        final double controlPointDistance = (4.0 / 3.0) * Math.tan(Math.PI / (2 * numSegments)) * circle.getRadius();
        final Point2D center = new Point2D(circle.getCenterX(), circle.getCenterY());
        final Point2D end = new Point2D(endX, endY);
        Point2D perp = rotate(center, end, direction * Math.PI / 2.);
        Point2D diff = perp.subtract(end);
        diff = diff.normalize();
        diff = scale(diff, controlPointDistance);
        return end.add(diff);
    }

    private Point2D scale(final Point2D a, final double scale) {
        return new Point2D(a.getX() * scale, a.getY() * scale);
    }

    final class RecentColorPath extends Path {
        PathClickTransition transition;

        RecentColorPath(final PathElement... elements) {
            super(elements);
            this.setStrokeLineCap(StrokeLineCap.ROUND);
            this.setStrokeWidth(0);
            this.setStrokeType(StrokeType.CENTERED);
            this.setCache(true);
            JFXDepthManager.setDepth(this, 2);
            this.transition = new PathClickTransition(this);
        }

        void playTransition(final double rate) {
            transition.setRate(rate);
            transition.play();
        }
    }

    private final class PathClickTransition extends CachedTransition {
        PathClickTransition(final Path path) {
            super(JFXCustomColorPicker.this, new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(((DropShadow) path.getEffect()).radiusProperty(),
                            JFXDepthManager.getShadowAt(2).radiusProperty().get(),
                            EASE_BOTH),
                        new KeyValue(((DropShadow) path.getEffect()).spreadProperty(),
                            JFXDepthManager.getShadowAt(2).spreadProperty().get(),
                            EASE_BOTH),
                        new KeyValue(((DropShadow) path.getEffect()).offsetXProperty(),
                            JFXDepthManager.getShadowAt(2).offsetXProperty().get(),
                            EASE_BOTH),
                        new KeyValue(((DropShadow) path.getEffect()).offsetYProperty(),
                            JFXDepthManager.getShadowAt(2).offsetYProperty().get(),
                            EASE_BOTH),
                        new KeyValue(path.strokeWidthProperty(), 0, EASE_BOTH)
                    ),
                    new KeyFrame(Duration.millis(1000),
                        new KeyValue(((DropShadow) path.getEffect()).radiusProperty(),
                            JFXDepthManager.getShadowAt(5).radiusProperty().get(),
                            EASE_BOTH),
                        new KeyValue(((DropShadow) path.getEffect()).spreadProperty(),
                            JFXDepthManager.getShadowAt(5).spreadProperty().get(),
                            EASE_BOTH),
                        new KeyValue(((DropShadow) path.getEffect()).offsetXProperty(),
                            JFXDepthManager.getShadowAt(5).offsetXProperty().get(),
                            EASE_BOTH),
                        new KeyValue(((DropShadow) path.getEffect()).offsetYProperty(),
                            JFXDepthManager.getShadowAt(5).offsetYProperty().get(),
                            EASE_BOTH),
                        new KeyValue(path.strokeWidthProperty(), 2, EASE_BOTH)
                    )
                )
            );
            // reduce the number to increase the shifting , increase number to reduce shifting
            setCycleDuration(Duration.millis(120));
            setDelay(Duration.seconds(0));
            setAutoReverse(false);
        }
    }
}
