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
import javafx.animation.Animation.Status;
import javafx.animation.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Path;
import javafx.util.Duration;


/**
 * @author Shadi Shaheen & Bassel El Mabsout this UI allows the user to pick a color using HSL color system
 */
class JFXColorPickerUI extends Pane {

    private CachedTransition selectorTransition;
    private int pickerSize = 400;
    // sl circle selector size
    private int selectorSize = 20;
    private double centerX;
    private double centerY;
    private double huesRadius;
    private double slRadius;
    private double currentHue = 0;

    private ImageView huesCircleView;
    private ImageView slCircleView;
    private Pane colorSelector;
    private Pane selector;
    private CurveTransition colorsTransition;

    public JFXColorPickerUI(int pickerSize) {

        JFXDepthManager.setDepth(this, 1);

        this.pickerSize = pickerSize;
        this.centerX = (double) pickerSize / 2;
        this.centerY = (double) pickerSize / 2;
        final double pickerRadius = (double) pickerSize / 2;
        this.huesRadius = pickerRadius * 0.9;
        final double huesSmallR = pickerRadius * 0.8;
        final double huesLargeR = pickerRadius;
        this.slRadius = pickerRadius * 0.7;

        // Create Hues Circle
        huesCircleView = new ImageView(getHuesCircle(pickerSize, pickerSize));
        // clip to smooth the edges
        Circle outterCircle = new Circle(centerX, centerY, huesLargeR - 2);
        Circle innterCircle = new Circle(centerX, centerY, huesSmallR + 2);
        huesCircleView.setClip(Path.subtract(outterCircle, innterCircle));
        this.getChildren().add(huesCircleView);

        // create Hues Circle Selector
        Circle r1 = new Circle(pickerRadius - huesSmallR);
        Circle r2 = new Circle(pickerRadius - huesRadius);
        colorSelector = new Pane();
        colorSelector.setStyle(
            "-fx-border-color:#424242; -fx-border-width:1px; -fx-background-color:rgba(255, 255, 255, 0.87);");
        colorSelector.setPrefSize(pickerRadius - huesSmallR, pickerRadius - huesSmallR);
        colorSelector.setShape(Path.subtract(r1, r2));
        colorSelector.setCache(true);
        colorSelector.setMouseTransparent(true);
        colorSelector.setPickOnBounds(false);
        this.getChildren().add(colorSelector);

        // add Hues Selection Listeners
        huesCircleView.addEventHandler(MouseEvent.MOUSE_DRAGGED, (event) -> {
            if (colorsTransition != null) {
                colorsTransition.stop();
            }
            double dx = event.getX() - centerX;
            double dy = event.getY() - centerY;
            double theta = Math.atan2(dy, dx);
            double x = centerX + huesRadius * Math.cos(theta);
            double y = centerY + huesRadius * Math.sin(theta);
            colorSelector.setRotate(90 + Math.toDegrees(Math.atan2(dy, dx)));
            colorSelector.setTranslateX(x - colorSelector.getPrefWidth() / 2);
            colorSelector.setTranslateY(y - colorSelector.getPrefHeight() / 2);
        });
        huesCircleView.addEventHandler(MouseEvent.MOUSE_PRESSED, (event) -> {
            double dx = event.getX() - centerX;
            double dy = event.getY() - centerY;
            double theta = Math.atan2(dy, dx);
            double x = centerX + huesRadius * Math.cos(theta);
            double y = centerY + huesRadius * Math.sin(theta);
            colorsTransition = new CurveTransition(new Point2D(colorSelector.getTranslateX() + colorSelector.getPrefWidth() / 2,
                colorSelector.getTranslateY() + colorSelector.getPrefHeight() / 2),
                new Point2D(x, y));
            colorsTransition.play();
        });
        colorSelector.translateXProperty()
            .addListener((o, oldVal, newVal) -> updateHSLCircleColor((int) (newVal.intValue() + colorSelector.getPrefWidth() / 2),
                (int) (colorSelector.getTranslateY() + colorSelector
                    .getPrefHeight() / 2)));
        colorSelector.translateYProperty()
            .addListener((o, oldVal, newVal) -> updateHSLCircleColor((int) (colorSelector.getTranslateX() + colorSelector
                .getPrefWidth() / 2), (int) (newVal.intValue() + colorSelector.getPrefHeight() / 2)));


        // Create SL Circle
        slCircleView = new ImageView(getSLCricle(pickerSize, pickerSize));
        slCircleView.setClip(new Circle(centerX, centerY, slRadius - 2));
        slCircleView.setPickOnBounds(false);
        this.getChildren().add(slCircleView);

        // create SL Circle Selector
        selector = new Pane();
        Circle c1 = new Circle(selectorSize / 2);
        Circle c2 = new Circle((selectorSize / 2) * 0.5);
        selector.setShape(Path.subtract(c1, c2));
        selector.setStyle(
            "-fx-border-color:#424242; -fx-border-width:1px;-fx-background-color:rgba(255, 255, 255, 0.87);");
        selector.setPrefSize(selectorSize, selectorSize);
        selector.setMinSize(selectorSize, selectorSize);
        selector.setMaxSize(selectorSize, selectorSize);
        selector.setCache(true);
        selector.setMouseTransparent(true);
        this.getChildren().add(selector);


        // add SL selection Listeners
        slCircleView.addEventHandler(MouseEvent.MOUSE_DRAGGED, (event) -> {
            if (selectorTransition != null) {
                selectorTransition.stop();
            }
            if (Math.pow(event.getX() - centerX, 2) + Math.pow(event.getY() - centerY, 2) < Math.pow(slRadius - 2, 2)) {
                selector.setTranslateX(event.getX() - selector.getPrefWidth() / 2);
                selector.setTranslateY(event.getY() - selector.getPrefHeight() / 2);
            } else {
                double dx = event.getX() - centerX;
                double dy = event.getY() - centerY;
                double theta = Math.atan2(dy, dx);
                double x = centerX + (slRadius - 2) * Math.cos(theta);
                double y = centerY + (slRadius - 2) * Math.sin(theta);
                selector.setTranslateX(x - selector.getPrefWidth() / 2);
                selector.setTranslateY(y - selector.getPrefHeight() / 2);
            }
        });
        slCircleView.addEventHandler(MouseEvent.MOUSE_PRESSED, (event) -> {
            selectorTransition = new CachedTransition(selector, new Timeline(new KeyFrame(Duration.millis(1000),
                new KeyValue(selector.translateXProperty(),
                    event.getX() - selector
                        .getPrefWidth() / 2,
                    Interpolator.EASE_BOTH),
                new KeyValue(selector.translateYProperty(),
                    event.getY() - selector
                        .getPrefHeight() / 2,
                    Interpolator.EASE_BOTH)))) {{
                setCycleDuration(Duration.millis(160));
                setDelay(Duration.seconds(0));
            }};
            selectorTransition.play();
        });
        // add slCircleView listener
        selector.translateXProperty()
            .addListener((o, oldVal, newVal) -> setColorAtLocation(newVal.intValue() + selectorSize / 2,
                (int) selector.getTranslateY() + selectorSize / 2));
        selector.translateYProperty()
            .addListener((o, oldVal, newVal) -> setColorAtLocation((int) selector.getTranslateX() + selectorSize / 2,
                newVal.intValue() + selectorSize / 2));


        // initial color selection
        double dx = 20 - centerX;
        double dy = 20 - centerY;
        double theta = Math.atan2(dy, dx);
        double x = centerX + huesRadius * Math.cos(theta);
        double y = centerY + huesRadius * Math.sin(theta);
        colorSelector.setRotate(90 + Math.toDegrees(Math.atan2(dy, dx)));
        colorSelector.setTranslateX(x - colorSelector.getPrefWidth() / 2);
        colorSelector.setTranslateY(y - colorSelector.getPrefHeight() / 2);
        selector.setTranslateX(centerX - selector.getPrefWidth() / 2);
        selector.setTranslateY(centerY - selector.getPrefHeight() / 2);
    }

    /**
     * List of Color Nodes that needs to be updated when picking a color
     */
    private ObservableList<Node> colorNodes = FXCollections.observableArrayList();

    public void addColorSelectionNode(Node... nodes) {
        colorNodes.addAll(nodes);
    }

    public void removeColorSelectionNode(Node... nodes) {
        colorNodes.removeAll(nodes);
    }

    private void updateHSLCircleColor(int x, int y) {
        // transform color to HSL space
        Color color = huesCircleView.getImage().getPixelReader().getColor(x, y);
        double max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        double min = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
        double hue = 0;
        if (max != min) {
            double d = max - min;
            if (max == color.getRed()) {
                hue = (color.getGreen() - color.getBlue()) / d + (color.getGreen() < color.getBlue() ? 6 : 0);
            } else if (max == color.getGreen()) {
                hue = (color.getBlue() - color.getRed()) / d + 2;
            } else if (max == color.getBlue()) {
                hue = (color.getRed() - color.getGreen()) / d + 4;
            }
            hue /= 6;
        }
        currentHue = map(hue, 0, 1, 0, 255);

        // refresh the HSL circle
        refreshHSLCircle();
    }

    private void refreshHSLCircle() {
        ColorAdjust colorAdjust = new ColorAdjust();
        colorAdjust.setHue(map(currentHue + (currentHue < 127.5 ? 1 : -1) * 127.5, 0, 255, -1, 1));
        slCircleView.setEffect(colorAdjust);
        setColorAtLocation((int) selector.getTranslateX() + selectorSize / 2,
            (int) selector.getTranslateY() + selectorSize / 2);
    }


    /**
     * this method is used to move selectors to a certain color
     */
    private boolean allowColorChange = true;
    private ParallelTransition pTrans;

    public void moveToColor(Color color) {
        allowColorChange = false;
        double max = Math.max(color.getRed(),
            Math.max(color.getGreen(), color.getBlue())), min = Math.min(color.getRed(),
            Math.min(color.getGreen(),
                color.getBlue()));
        double hue = 0;
        double l = (max + min) / 2;
        double s = 0;
        if (max == min) {
            hue = s = 0; // achromatic
        } else {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == color.getRed()) {
                hue = (color.getGreen() - color.getBlue()) / d + (color.getGreen() < color.getBlue() ? 6 : 0);
            } else if (max == color.getGreen()) {
                hue = (color.getBlue() - color.getRed()) / d + 2;
            } else if (max == color.getBlue()) {
                hue = (color.getRed() - color.getGreen()) / d + 4;
            }
            hue /= 6;
        }
        currentHue = map(hue, 0, 1, 0, 255);

        // Animate Hue
        double theta = map(currentHue, 0, 255, -Math.PI, Math.PI);
        double x = centerX + huesRadius * Math.cos(theta);
        double y = centerY + huesRadius * Math.sin(theta);
        colorsTransition = new CurveTransition(new Point2D(colorSelector.getTranslateX() + colorSelector.getPrefWidth() / 2,
            colorSelector.getTranslateY() + colorSelector.getPrefHeight() / 2),
            new Point2D(x, y));


        // Animate SL
        s = map(s, 0, 1, 0, 255);
        l = map(l, 0, 1, 0, 255);
        Point2D point = getPointFromSL((int) s, (int) l, slRadius);
        double pX = centerX - point.getX();
        double pY = centerY - point.getY();

        double endPointX;
        double endPointY;
        if (Math.pow(pX - centerX, 2) + Math.pow(pY - centerY, 2) < Math.pow(slRadius - 2, 2)) {
            endPointX = pX - selector.getPrefWidth() / 2;
            endPointY = pY - selector.getPrefHeight() / 2;
        } else {
            double dx = pX - centerX;
            double dy = pY - centerY;
            theta = Math.atan2(dy, dx);
            x = centerX + (slRadius - 2) * Math.cos(theta);
            y = centerY + (slRadius - 2) * Math.sin(theta);
            endPointX = x - selector.getPrefWidth() / 2;
            endPointY = y - selector.getPrefHeight() / 2;
        }
        selectorTransition = new CachedTransition(selector, new Timeline(new KeyFrame(Duration.millis(1000),
            new KeyValue(selector.translateXProperty(),
                endPointX,
                Interpolator.EASE_BOTH),
            new KeyValue(selector.translateYProperty(),
                endPointY,
                Interpolator.EASE_BOTH)))) {{
            setCycleDuration(Duration.millis(160));
            setDelay(Duration.seconds(0));
        }};

        if (pTrans != null) {
            pTrans.stop();
        }
        pTrans = new ParallelTransition(colorsTransition, selectorTransition);
        pTrans.setOnFinished((finish) -> {
            if (pTrans.getStatus() == Status.STOPPED) {
                allowColorChange = true;
            }
        });
        pTrans.play();

        refreshHSLCircle();
    }

    private void setColorAtLocation(int x, int y) {
        if (allowColorChange) {
            Color color = getColorAtLocation(x, y);
            String colorString = "rgb(" + color.getRed() * 255 + "," + color.getGreen() * 255 + "," + color.getBlue() * 255 + ");";
            for (Node node : colorNodes)
                node.setStyle("-fx-background-color:" + colorString + "; -fx-fill:" + colorString+";");
        }
    }

    private Color getColorAtLocation(double x, double y) {
        double dy = x - centerX;
        double dx = y - centerY;
        return getColor(dx, dy);
    }

    private Image getHuesCircle(int width, int height) {
        WritableImage raster = new WritableImage(width, height);
        PixelWriter pixelWriter = raster.getPixelWriter();
        Point2D center = new Point2D((double) width / 2, (double) height / 2);
        double rsmall = 0.8 * width / 2;
        double rbig = (double) width / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dx = x - center.getX();
                double dy = y - center.getY();
                double distance = Math.sqrt((dx * dx) + (dy * dy));
                double o = Math.atan2(dy, dx);
                if (distance > rsmall && distance < rbig) {
                    double H = map(o, -Math.PI, Math.PI, 0, 255);
                    double S = 255;
                    double L = 152;
                    pixelWriter.setColor(x, y, HSL2RGB(H, S, L));
                }
            }
        }
        return raster;
    }

    private Image getSLCricle(int width, int height) {
        WritableImage raster = new WritableImage(width, height);
        PixelWriter pixelWriter = raster.getPixelWriter();
        Point2D center = new Point2D((double) width / 2, (double) height / 2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dy = x - center.getX();
                double dx = y - center.getY();
                pixelWriter.setColor(x, y, getColor(dx, dy));
            }
        }
        return raster;
    }

    private double clamp(double from, double small, double big) {
        return Math.min(Math.max(from, small), big);
    }

    private Color getColor(double dx, double dy) {
        double distance = Math.sqrt((dx * dx) + (dy * dy));
        double rverysmall = 0.65 * ((double) pickerSize / 2);
        Color pixelColor = Color.BLUE;

        if (distance <= rverysmall * 1.1) {
            double angle = -Math.PI / 2.;
            double angle1 = angle + 2 * Math.PI / 3.;
            double angle2 = angle1 + 2 * Math.PI / 3.;
            double x1 = rverysmall * Math.sin(angle1);
            double y1 = rverysmall * Math.cos(angle1);
            double x2 = rverysmall * Math.sin(angle2);
            double y2 = rverysmall * Math.cos(angle2);
            dx += 0.01;
            double[] circle = circleFrom3Points(new Point2D(x1, y1), new Point2D(x2, y2), new Point2D(dx, dy));
            double xArc = circle[0];
            double yArc = 0;
            double arcR = circle[2];
            double Arco = Math.atan2(dx - xArc, dy - yArc);
            double Arco1 = Math.atan2(x1 - xArc, y1 - yArc);
            double Arco2 = Math.atan2(x2 - xArc, y2 - yArc);

            double finalX = xArc > 0 ? xArc - arcR : xArc + arcR;

            double saturation = map(finalX, -rverysmall, rverysmall, 255, 0);

            double lightness = 255;
            double diffAngle = Arco2 - Arco1;
            double diffArco = Arco - Arco1;
            if (dx < x1) {
                diffAngle = diffAngle < 0 ? 2 * Math.PI + diffAngle : diffAngle;
                diffAngle = Math.abs(2 * Math.PI - diffAngle);
                diffArco = diffArco < 0 ? 2 * Math.PI + diffArco : diffArco;
                diffArco = Math.abs(2 * Math.PI - diffArco);
            }
            lightness = map(diffArco, 0, diffAngle, 0, 255);


            if (distance > rverysmall) {
                saturation = 255 - saturation;
                if (lightness < 0 && dy < 0) {
                    lightness = 255;
                }
            }
            lightness = clamp(lightness, 0, 255);
            if ((saturation < 10 && dx < x1) || (saturation > 240 && dx > x1)) {
                saturation = 255 - saturation;
            }
            saturation = clamp(saturation, 0, 255);
            pixelColor = HSL2RGB(currentHue, saturation, lightness);
        }
        return pixelColor;
    }


    /***************************************************************************
     *                                                                         *
     * Hues Animation                                                          *
     *                                                                         *
     **************************************************************************/

    private final class CurveTransition extends Transition {
        Point2D from;
        double fromTheta;
        double toTheta;

        public CurveTransition(Point2D from, Point2D to) {
            this.from = from;
            double fromDx = from.getX() - centerX;
            double fromDy = from.getY() - centerY;
            fromTheta = Math.atan2(fromDy, fromDx);
            double toDx = to.getX() - centerX;
            double toDy = to.getY() - centerY;
            toTheta = Math.atan2(toDy, toDx);
            setInterpolator(Interpolator.EASE_BOTH);
            setDelay(Duration.millis(0));
            setCycleDuration(Duration.millis(240));
        }

        @Override
        protected void interpolate(double frac) {
            double dif = Math.min(Math.abs(toTheta - fromTheta), 2 * Math.PI - Math.abs(toTheta - fromTheta));
            if (dif == 2 * Math.PI - Math.abs(toTheta - fromTheta)) {
                int dir = -1;
                if (toTheta < fromTheta) {
                    dir = 1;
                }
                dif = dir * dif;
            } else {
                dif = toTheta - fromTheta;
            }

            Point2D newP = rotate(from, new Point2D(centerX, centerY), frac * dif);
            colorSelector.setRotate(90 + Math.toDegrees(Math.atan2(newP.getY() - centerY, newP.getX() - centerX)));
            colorSelector.setTranslateX(newP.getX() - colorSelector.getPrefWidth() / 2);
            colorSelector.setTranslateY(newP.getY() - colorSelector.getPrefHeight() / 2);
        }
    }


    /***************************************************************************
     *                                                                         *
     * Util methods	                                                           *
     *                                                                         *
     **************************************************************************/

    private double map(double val, double min1, double max1, double min2, double max2) {
        return min2 + (max2 - min2) * ((val - min1) / (max1 - min1));
    }

    private Color HSL2RGB(double hue, double sat, double lum) {
        hue = map(hue, 0, 255, 0, 359);
        sat = map(sat, 0, 255, 0, 1);
        lum = map(lum, 0, 255, 0, 1);
        double v;
        double red, green, blue;
        double m;
        double sv;
        int sextant;
        double fract, vsf, mid1, mid2;

        red = lum;   // default to gray
        green = lum;
        blue = lum;
        v = (lum <= 0.5) ? (lum * (1.0 + sat)) : (lum + sat - lum * sat);
        m = lum + lum - v;
        sv = (v - m) / v;
        hue /= 60.0;  //get into range 0..6
        sextant = (int) Math.floor(hue);  // int32 rounds up or down.
        fract = hue - sextant;
        vsf = v * sv * fract;
        mid1 = m + vsf;
        mid2 = v - vsf;

        if (v > 0) {
            switch (sextant) {
                case 0:
                    red = v;
                    green = mid1;
                    blue = m;
                    break;
                case 1:
                    red = mid2;
                    green = v;
                    blue = m;
                    break;
                case 2:
                    red = m;
                    green = v;
                    blue = mid1;
                    break;
                case 3:
                    red = m;
                    green = mid2;
                    blue = v;
                    break;
                case 4:
                    red = mid1;
                    green = m;
                    blue = v;
                    break;
                case 5:
                    red = v;
                    green = m;
                    blue = mid2;
                    break;
            }
        }
        return new Color(red, green, blue, 1);
    }

    private double[] circleFrom3Points(Point2D a, Point2D b, Point2D c) {
        double ax, ay, bx, by, cx, cy, x1, y11, dx1, dy1, x2, y2, dx2, dy2, ox, oy, dx, dy, radius; // Variables Used and to Declared
        ax = a.getX();
        ay = a.getY(); //first Point X and Y
        bx = b.getX();
        by = b.getY(); // Second Point X and Y
        cx = c.getX();
        cy = c.getY(); // Third Point X and Y

        ////****************Following are Basic Procedure**********************///
        x1 = (bx + ax) / 2;
        y11 = (by + ay) / 2;
        dy1 = bx - ax;
        dx1 = -(by - ay);

        x2 = (cx + bx) / 2;
        y2 = (cy + by) / 2;
        dy2 = cx - bx;
        dx2 = -(cy - by);

        ox = (y11 * dx1 * dx2 + x2 * dx1 * dy2 - x1 * dy1 * dx2 - y2 * dx1 * dx2) / (dx1 * dy2 - dy1 * dx2);
        oy = (ox - x1) * dy1 / dx1 + y11;

        dx = ox - ax;
        dy = oy - ay;
        radius = Math.sqrt(dx * dx + dy * dy);
        return new double[] {ox, oy, radius};
    }


    private Point2D getPointFromSL(int saturation, int lightness, double radius) {
        double dy = map(saturation, 0, 255, -radius, radius);
        double angle = 0.;
        double angle1 = angle + 2 * Math.PI / 3.;
        double angle2 = angle1 + 2 * Math.PI / 3.;
        double x1 = radius * Math.sin(angle1);
        double y1 = radius * Math.cos(angle1);
        double x2 = radius * Math.sin(angle2);
        double y2 = radius * Math.cos(angle2);
        double dx = 0;
        double[] circle = circleFrom3Points(new Point2D(x1, y1), new Point2D(dx, dy), new Point2D(x2, y2));
        double xArc = circle[0];
        double yArc = circle[1];
        double arcR = circle[2];
        double Arco1 = Math.atan2(x1 - xArc, y1 - yArc);
        double Arco2 = Math.atan2(x2 - xArc, y2 - yArc);
        double ArcoFinal = map(lightness, 0, 255, Arco2, Arco1);
        double finalX = xArc + arcR * Math.sin(ArcoFinal);
        double finalY = yArc + arcR * Math.cos(ArcoFinal);
        if (dy < y1) {
            ArcoFinal = map(lightness, 0, 255, Arco1, Arco2 + 2 * Math.PI);
            finalX = -xArc - arcR * Math.sin(ArcoFinal);
            finalY = yArc + arcR * Math.cos(ArcoFinal);
        }
        return new Point2D(finalX, finalY);
    }

    private Point2D rotate(Point2D a, Point2D center, double angle) {
        double resultX = center.getX() + (a.getX() - center.getX()) * Math.cos(angle) - (a.getY() - center.getY()) * Math
            .sin(angle);
        double resultY = center.getY() + (a.getX() - center.getX()) * Math.sin(angle) + (a.getY() - center.getY()) * Math
            .cos(angle);
        return new Point2D(resultX, resultY);
    }

}
