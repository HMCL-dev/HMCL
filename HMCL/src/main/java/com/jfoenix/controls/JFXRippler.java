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

package com.jfoenix.controls;

import com.jfoenix.converters.RipplerMaskTypeConverter;
import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.*;
import javafx.beans.DefaultProperty;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.*;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Bounds;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JFXRippler is the material design implementation of a ripple effect.
 * the ripple effect can be applied to any node in the scene. JFXRippler is
 * a {@link StackPane} container that holds a specified node (control node) and a ripple generator.
 * <p>
 * UPDATE NOTES:
 * - fireEventProgrammatically(Event) method has been removed as the ripple controller is
 * the control itself, so you can trigger manual ripple by firing mouse event on the control
 * instead of JFXRippler
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
@DefaultProperty(value = "control")
public class JFXRippler extends StackPane {
    public enum RipplerPos {
        FRONT, BACK
    }

    public enum RipplerMask {
        CIRCLE, RECT, FIT
    }

    protected RippleGenerator rippler;
    protected Pane ripplerPane;
    protected Node control;

    protected static final double RIPPLE_MAX_RADIUS = 300;
    private static final Interpolator RIPPLE_INTERPOLATOR = Interpolator.SPLINE(0.0825,
            0.3025,
            0.0875,
            0.9975); //0.1, 0.54, 0.28, 0.95);

    private boolean forceOverlay = false;

    /// creates empty rippler node
    public JFXRippler() {
        this(null, RipplerMask.RECT, RipplerPos.FRONT);
    }

    /// creates a rippler for the specified control
    public JFXRippler(Node control) {
        this(control, RipplerMask.RECT, RipplerPos.FRONT);
    }

    /// creates a rippler for the specified control
    ///
    /// @param pos can be either FRONT/BACK (position the ripple effect infront of or behind the control)
    public JFXRippler(Node control, RipplerPos pos) {
        this(control, RipplerMask.RECT, pos);
    }

    /// creates a rippler for the specified control and apply the specified mask to it
    ///
    /// @param mask can be either rectangle/cricle
    public JFXRippler(Node control, RipplerMask mask) {
        this(control, mask, RipplerPos.FRONT);
    }

    /// creates a rippler for the specified control, mask and position.
    ///
    /// @param mask can be either rectangle/cricle
    /// @param pos  can be either FRONT/BACK (position the ripple effect infront of or behind the control)
    public JFXRippler(Node control, RipplerMask mask, RipplerPos pos) {
        initialize();

        setMaskType(mask);
        setPosition(pos);
        createRippleUI();
        setControl(control);

        // listen to control position changed
        position.addListener(observable -> updateControlPosition());

        setPickOnBounds(false);
        setCache(true);
        setCacheHint(CacheHint.SPEED);
        setCacheShape(true);
    }

    protected final void createRippleUI() {
        // create rippler panels
        rippler = new RippleGenerator();
        ripplerPane = new StackPane();
        ripplerPane.setMouseTransparent(true);
        ripplerPane.getChildren().add(rippler);
        getChildren().add(ripplerPane);
    }

    /***************************************************************************
     *                                                                         *
     * Setters / Getters                                                       *
     *                                                                         *
     **************************************************************************/

    public void setControl(Node control) {
        if (control != null) {
            this.control = control;
            // position control
            positionControl(control);
            // add control listeners to generate / release ripples
            initControlListeners();
        }
    }

    // Override this method to create JFXRippler for a control outside the ripple
    protected void positionControl(Node control) {
        if (this.position.get() == RipplerPos.BACK) {
            getChildren().add(control);
        } else {
            getChildren().add(0, control);
        }
    }

    protected void updateControlPosition() {
        if (this.position.get() == RipplerPos.BACK) {
            ripplerPane.toBack();
        } else {
            ripplerPane.toFront();
        }
    }

    public Node getControl() {
        return control;
    }

    // methods that can be changed by extending the rippler class

    /// generate the clipping mask
    ///
    /// @return the mask node
    protected Node getMask() {
        double borderWidth = ripplerPane.getBorder() != null ? ripplerPane.getBorder().getInsets().getTop() : 0;
        Bounds bounds = control.getBoundsInParent();
        double width = control.getLayoutBounds().getWidth();
        double height = control.getLayoutBounds().getHeight();
        double diffMinX = Math.abs(control.getBoundsInLocal().getMinX() - control.getLayoutBounds().getMinX());
        double diffMinY = Math.abs(control.getBoundsInLocal().getMinY() - control.getLayoutBounds().getMinY());
        double diffMaxX = Math.abs(control.getBoundsInLocal().getMaxX() - control.getLayoutBounds().getMaxX());
        double diffMaxY = Math.abs(control.getBoundsInLocal().getMaxY() - control.getLayoutBounds().getMaxY());
        Node mask;
        switch (getMaskType()) {
            case RECT:
                mask = new Rectangle(bounds.getMinX() + diffMinX - snappedLeftInset(),
                        bounds.getMinY() + diffMinY - snappedTopInset(),
                        width - 2 * borderWidth,
                        height - 2 * borderWidth); // -0.1 to prevent resizing the anchor pane
                break;
            case CIRCLE:
                double radius = Math.min((width / 2) - 2 * borderWidth, (height / 2) - 2 * borderWidth);
                mask = new Circle((bounds.getMinX() + diffMinX + bounds.getMaxX() - diffMaxX) / 2 - snappedLeftInset(),
                        (bounds.getMinY() + diffMinY + bounds.getMaxY() - diffMaxY) / 2 - snappedTopInset(),
                        radius,
                        Color.BLUE);
                break;
            case FIT:
                mask = new Region();
                if (control instanceof Shape) {
                    ((Region) mask).setShape((Shape) control);
                } else if (control instanceof Region) {
                    ((Region) mask).setShape(((Region) control).getShape());
                    JFXNodeUtils.updateBackground(((Region) control).getBackground(), (Region) mask);
                }
                mask.resize(width, height);
                mask.relocate(bounds.getMinX() + diffMinX, bounds.getMinY() + diffMinY);
                break;
            default:
                mask = new Rectangle(bounds.getMinX() + diffMinX - snappedLeftInset(),
                        bounds.getMinY() + diffMinY - snappedTopInset(),
                        width - 2 * borderWidth,
                        height - 2 * borderWidth); // -0.1 to prevent resizing the anchor pane
                break;
        }
        return mask;
    }

    /**
     * compute the ripple radius
     *
     * @return the ripple radius size
     */
    protected double computeRippleRadius() {
        double width2 = control.getLayoutBounds().getWidth() * control.getLayoutBounds().getWidth();
        double height2 = control.getLayoutBounds().getHeight() * control.getLayoutBounds().getHeight();
        return Math.min(Math.sqrt(width2 + height2), RIPPLE_MAX_RADIUS) * 1.1 + 5;
    }

    protected void setOverLayBounds(Rectangle overlay) {
        overlay.setWidth(control.getLayoutBounds().getWidth());
        overlay.setHeight(control.getLayoutBounds().getHeight());
    }

    /**
     * init mouse listeners on the control
     */
    protected void initControlListeners() {
        // if the control got resized the overlay rect must be rest
        control.layoutBoundsProperty().addListener(observable -> resetRippler());
        if (getChildren().contains(control)) {
            control.boundsInParentProperty().addListener(observable -> resetRippler());
        }
        control.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY)
                createRipple(event.getX(), event.getY());
        });
        // create fade out transition for the ripple
        control.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY)
                releaseRipple();
        });
    }

    /**
     * creates Ripple effect
     */
    protected void createRipple(double x, double y) {
        if (!isRipplerDisabled()) {
            rippler.setGeneratorCenterX(x);
            rippler.setGeneratorCenterY(y);
            rippler.createRipple();
        }
    }

    protected void releaseRipple() {
        rippler.releaseRipple();
    }

    /**
     * creates Ripple effect in the center of the control
     *
     * @return a runnable to release the ripple when needed
     */
    public Runnable createManualRipple() {
        if (!isRipplerDisabled()) {
            rippler.setGeneratorCenterX(control.getLayoutBounds().getWidth() / 2);
            rippler.setGeneratorCenterY(control.getLayoutBounds().getHeight() / 2);
            rippler.createRipple();
            return () -> {
                // create fade out transition for the ripple
                releaseRipple();
            };
        }
        return () -> {
        };
    }

    /// show/hide the ripple overlay
    ///
    /// @param forceOverlay used to hold the overlay after ripple action
    public void setOverlayVisible(boolean visible, boolean forceOverlay) {
        this.forceOverlay = forceOverlay;
        setOverlayVisible(visible);
    }

    /// show/hide the ripple overlay
    /// NOTE: setting overlay visibility to false will reset forceOverlay to false
    public void setOverlayVisible(boolean visible) {
        if (visible) {
            showOverlay();
        } else {
            forceOverlay = false;
            hideOverlay();
        }
    }

    /**
     * this method will be set to private in future versions of JFoenix,
     * user the method {@link #setOverlayVisible(boolean)}
     */
    public void showOverlay() {
        if (rippler.overlayRect != null) {
            rippler.overlayRect.outAnimation.stop();
        }
        rippler.createOverlay();
        rippler.overlayRect.inAnimation.play();
    }

    public void hideOverlay() {
        if (!forceOverlay) {
            if (rippler.overlayRect != null) {
                rippler.overlayRect.inAnimation.stop();
            }
            if (rippler.overlayRect != null) {
                rippler.overlayRect.outAnimation.play();
            }
        } else {
            System.err.println("Ripple Overlay is forced!");
        }
    }

    /**
     * Generates ripples on the screen every 0.3 seconds or whenever
     * the createRipple method is called. Ripples grow and fade out
     * over 0.6 seconds
     */
    protected final class RippleGenerator extends Group {

        private double generatorCenterX = 0;
        private double generatorCenterY = 0;
        private OverLayRipple overlayRect;
        private final AtomicBoolean generating = new AtomicBoolean(false);
        private boolean cacheRipplerClip = false;
        private boolean resetClip = false;
        private final Queue<Ripple> ripplesQueue = new LinkedList<>();

        RippleGenerator() {
            // improve in performance, by preventing
            // redrawing the parent when the ripple effect is triggered
            this.setManaged(false);
            this.setCache(true);
            this.setCacheHint(CacheHint.SPEED);
        }

        void createRipple() {
            if (!generating.getAndSet(true)) {
                // create overlay once then change its color later
                createOverlay();
                if (this.getClip() == null || (getChildren().size() == 1 && !cacheRipplerClip) || resetClip) {
                    this.setClip(getMask());
                }
                this.resetClip = false;

                // create the ripple effect
                final Ripple ripple = new Ripple(generatorCenterX, generatorCenterY);
                getChildren().add(ripple);
                ripplesQueue.add(ripple);

                // animate the ripple
                overlayRect.outAnimation.stop();
                overlayRect.inAnimation.play();
                ripple.inAnimation.play();
            }
        }

        private void releaseRipple() {
            Ripple ripple = ripplesQueue.poll();
            if (ripple != null) {
                ripple.inAnimation.stop();
                ripple.outAnimation = new Timeline(
                        new KeyFrame(Duration.millis(Math.min(800, (0.9 * 500) / ripple.getScaleX()))
                                , ripple.outKeyValues));
                ripple.outAnimation.setOnFinished((event) -> getChildren().remove(ripple));
                ripple.outAnimation.play();
                if (generating.getAndSet(false)) {
                    if (overlayRect != null) {
                        overlayRect.inAnimation.stop();
                        if (!forceOverlay) {
                            overlayRect.outAnimation.play();
                        }
                    }
                }
            }
        }

        void cacheRippleClip(boolean cached) {
            cacheRipplerClip = cached;
        }

        void createOverlay() {
            if (overlayRect == null) {
                overlayRect = new OverLayRipple();
                overlayRect.setClip(getMask());
                getChildren().add(0, overlayRect);
                overlayRect.fillProperty().bind(Bindings.createObjectBinding(() -> {
                    if (getRipplerFill() instanceof Color fill) {
                        return new Color(fill.getRed(),
                                fill.getGreen(),
                                fill.getBlue(),
                                0.2);
                    } else {
                        return Color.TRANSPARENT;
                    }
                }, ripplerFillProperty()));
            }
        }

        void setGeneratorCenterX(double generatorCenterX) {
            this.generatorCenterX = generatorCenterX;
        }

        void setGeneratorCenterY(double generatorCenterY) {
            this.generatorCenterY = generatorCenterY;
        }

        private final class OverLayRipple extends Rectangle {
            // Overlay ripple animations
            Animation inAnimation = new Timeline(new KeyFrame(Duration.millis(300),
                    new KeyValue(opacityProperty(), 1, Interpolator.EASE_IN)));

            Animation outAnimation = new Timeline(new KeyFrame(Duration.millis(300),
                    new KeyValue(opacityProperty(), 0, Interpolator.EASE_OUT)));

            OverLayRipple() {
                super();
                setOverLayBounds(this);
                this.getStyleClass().add("jfx-rippler-overlay");
                // update initial position
                if (JFXRippler.this.getChildrenUnmodifiable().contains(control)) {
                    double diffMinX = Math.abs(control.getBoundsInLocal().getMinX() - control.getLayoutBounds().getMinX());
                    double diffMinY = Math.abs(control.getBoundsInLocal().getMinY() - control.getLayoutBounds().getMinY());
                    Bounds bounds = control.getBoundsInParent();
                    this.setX(bounds.getMinX() + diffMinX - snappedLeftInset());
                    this.setY(bounds.getMinY() + diffMinY - snappedTopInset());
                }
                // set initial attributes
                setOpacity(0);
                setCache(true);
                setCacheHint(CacheHint.SPEED);
                setCacheShape(true);
                setManaged(false);
            }
        }

        private final class Ripple extends Circle {

            KeyValue[] outKeyValues;
            Animation outAnimation = null;
            Animation inAnimation = null;

            private Ripple(double centerX, double centerY) {
                super(centerX,
                        centerY,
                        getRipplerRadius() == Region.USE_COMPUTED_SIZE ?
                                computeRippleRadius() : getRipplerRadius(), null);
                setCache(true);
                setCacheHint(CacheHint.SPEED);
                setCacheShape(true);
                setManaged(false);
                setSmooth(true);

                KeyValue[] inKeyValues = new KeyValue[isRipplerRecenter() ? 4 : 2];
                outKeyValues = new KeyValue[isRipplerRecenter() ? 5 : 3];

                inKeyValues[0] = new KeyValue(scaleXProperty(), 0.9, RIPPLE_INTERPOLATOR);
                inKeyValues[1] = new KeyValue(scaleYProperty(), 0.9, RIPPLE_INTERPOLATOR);

                outKeyValues[0] = new KeyValue(this.scaleXProperty(), 1, RIPPLE_INTERPOLATOR);
                outKeyValues[1] = new KeyValue(this.scaleYProperty(), 1, RIPPLE_INTERPOLATOR);
                outKeyValues[2] = new KeyValue(this.opacityProperty(), 0, RIPPLE_INTERPOLATOR);

                if (isRipplerRecenter()) {
                    double dx = (control.getLayoutBounds().getWidth() / 2 - centerX) / 1.55;
                    double dy = (control.getLayoutBounds().getHeight() / 2 - centerY) / 1.55;
                    inKeyValues[2] = outKeyValues[3] = new KeyValue(translateXProperty(),
                            Math.signum(dx) * Math.min(Math.abs(dx),
                                    this.getRadius() / 2),
                            RIPPLE_INTERPOLATOR);
                    inKeyValues[3] = outKeyValues[4] = new KeyValue(translateYProperty(),
                            Math.signum(dy) * Math.min(Math.abs(dy),
                                    this.getRadius() / 2),
                            RIPPLE_INTERPOLATOR);
                }
                inAnimation = new Timeline(new KeyFrame(Duration.ZERO,
                        new KeyValue(scaleXProperty(),
                                0,
                                RIPPLE_INTERPOLATOR),
                        new KeyValue(scaleYProperty(),
                                0,
                                RIPPLE_INTERPOLATOR),
                        new KeyValue(translateXProperty(),
                                0,
                                RIPPLE_INTERPOLATOR),
                        new KeyValue(translateYProperty(),
                                0,
                                RIPPLE_INTERPOLATOR),
                        new KeyValue(opacityProperty(),
                                1,
                                RIPPLE_INTERPOLATOR)
                ), new KeyFrame(Duration.millis(900), inKeyValues));

                setScaleX(0);
                setScaleY(0);
                if (getRipplerFill() instanceof Color fill) {
                    Color circleColor = new Color(fill.getRed(),
                            fill.getGreen(),
                            fill.getBlue(),
                            0.3);
                    setStroke(circleColor);
                    setFill(circleColor);
                } else {
                    setStroke(getRipplerFill());
                    setFill(getRipplerFill());
                }
            }
        }

        public void clear() {
            getChildren().clear();
            rippler.overlayRect = null;
            generating.set(false);
        }
    }

    private void resetOverLay() {
        if (rippler.overlayRect != null) {
            rippler.overlayRect.inAnimation.stop();
            final RippleGenerator.OverLayRipple oldOverlay = rippler.overlayRect;
            rippler.overlayRect.outAnimation.setOnFinished((finish) -> rippler.getChildren().remove(oldOverlay));
            rippler.overlayRect.outAnimation.play();
            rippler.overlayRect = null;
        }
    }

    private void resetClip() {
        this.rippler.resetClip = true;
    }

    protected void resetRippler() {
        resetOverLay();
        resetClip();
    }

    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    /**
     * Initialize the style class to 'jfx-rippler'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "jfx-rippler";

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * the ripple recenter property, by default it's false.
     * if true the ripple effect will show gravitational pull to the center of its control
     */
    private StyleableBooleanProperty ripplerRecenter;

    public boolean isRipplerRecenter() {
        return ripplerRecenter != null && ripplerRecenter.get();
    }

    public StyleableBooleanProperty ripplerRecenterProperty() {
        if (this.ripplerRecenter == null) {
            this.ripplerRecenter = new SimpleStyleableBooleanProperty(
                    StyleableProperties.RIPPLER_RECENTER,
                    JFXRippler.this,
                    "ripplerRecenter",
                    false);
        }
        return this.ripplerRecenter;
    }

    public void setRipplerRecenter(boolean recenter) {
        ripplerRecenterProperty().set(recenter);
    }

    /**
     * the ripple radius size, by default it will be automatically computed.
     */
    private StyleableDoubleProperty ripplerRadius;

    public double getRipplerRadius() {
        return ripplerRadius == null ? Region.USE_COMPUTED_SIZE : ripplerRadius.get();
    }

    public StyleableDoubleProperty ripplerRadiusProperty() {
        if (this.ripplerRadius == null) {
            this.ripplerRadius = new SimpleStyleableDoubleProperty(
                    StyleableProperties.RIPPLER_RADIUS,
                    JFXRippler.this,
                    "ripplerRadius",
                    Region.USE_COMPUTED_SIZE);
        }
        return this.ripplerRadius;
    }

    public void setRipplerRadius(double radius) {
        ripplerRadiusProperty().set(radius);
    }

    private static final Color DEFAULT_RIPPLER_FILL = Color.rgb(0, 200, 255);

    /**
     * the default color of the ripple effect
     */
    private StyleableObjectProperty<Paint> ripplerFill;

    public Paint getRipplerFill() {
        return ripplerFill == null ? DEFAULT_RIPPLER_FILL : ripplerFill.get();
    }

    public StyleableObjectProperty<Paint> ripplerFillProperty() {
        if (this.ripplerFill == null) {
            this.ripplerFill = new SimpleStyleableObjectProperty<>(StyleableProperties.RIPPLER_FILL,
                    JFXRippler.this,
                    "ripplerFill",
                    DEFAULT_RIPPLER_FILL);
        }
        return this.ripplerFill;
    }

    public void setRipplerFill(Paint color) {
        ripplerFillProperty().set(color);
    }

    /// mask property used for clipping the rippler.
    /// can be either CIRCLE/RECT
    private StyleableObjectProperty<RipplerMask> maskType;

    public RipplerMask getMaskType() {
        return maskType == null ? RipplerMask.RECT : maskType.get();
    }

    public StyleableObjectProperty<RipplerMask> maskTypeProperty() {
        if (this.maskType == null) {
            this.maskType = new SimpleStyleableObjectProperty<>(
                    StyleableProperties.MASK_TYPE,
                    JFXRippler.this,
                    "maskType",
                    RipplerMask.RECT);
        }
        return this.maskType;
    }

    public void setMaskType(RipplerMask type) {
        if (this.maskType != null || type != RipplerMask.RECT)
            maskTypeProperty().set(type);
    }

    /**
     * the ripple disable, by default it's false.
     * if true the ripple effect will be hidden
     */
    private StyleableBooleanProperty ripplerDisabled;

    public boolean isRipplerDisabled() {
        return ripplerDisabled != null && ripplerDisabled.get();
    }

    public StyleableBooleanProperty ripplerDisabledProperty() {
        if (this.ripplerDisabled == null) {
            this.ripplerDisabled = new SimpleStyleableBooleanProperty(
                    StyleableProperties.RIPPLER_DISABLED,
                    JFXRippler.this,
                    "ripplerDisabled",
                    false);
        }
        return this.ripplerDisabled;
    }

    public void setRipplerDisabled(boolean disabled) {
        ripplerDisabledProperty().set(disabled);
    }

    /**
     * indicates whether the ripple effect is infront of or behind the node
     */
    protected ObjectProperty<RipplerPos> position = new SimpleObjectProperty<>();

    public void setPosition(RipplerPos pos) {
        this.position.set(pos);
    }

    public RipplerPos getPosition() {
        return position == null ? RipplerPos.FRONT : position.get();
    }

    public ObjectProperty<RipplerPos> positionProperty() {
        return this.position;
    }

    private static final class StyleableProperties {
        private static final CssMetaData<JFXRippler, Boolean> RIPPLER_RECENTER =
                new CssMetaData<>("-jfx-rippler-recenter",
                        BooleanConverter.getInstance(), false) {
                    @Override
                    public boolean isSettable(JFXRippler control) {
                        return control.ripplerRecenter == null || !control.ripplerRecenter.isBound();
                    }

                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(JFXRippler control) {
                        return control.ripplerRecenterProperty();
                    }
                };
        private static final CssMetaData<JFXRippler, Boolean> RIPPLER_DISABLED =
                new CssMetaData<>("-jfx-rippler-disabled",
                        BooleanConverter.getInstance(), false) {
                    @Override
                    public boolean isSettable(JFXRippler control) {
                        return control.ripplerDisabled == null || !control.ripplerDisabled.isBound();
                    }

                    @Override
                    public StyleableProperty<Boolean> getStyleableProperty(JFXRippler control) {
                        return control.ripplerDisabledProperty();
                    }
                };
        private static final CssMetaData<JFXRippler, Paint> RIPPLER_FILL =
                new CssMetaData<>("-jfx-rippler-fill",
                        PaintConverter.getInstance(), DEFAULT_RIPPLER_FILL) {
                    @Override
                    public boolean isSettable(JFXRippler control) {
                        return control.ripplerFill == null || !control.ripplerFill.isBound();
                    }

                    @Override
                    public StyleableProperty<Paint> getStyleableProperty(JFXRippler control) {
                        return control.ripplerFillProperty();
                    }
                };
        private static final CssMetaData<JFXRippler, Number> RIPPLER_RADIUS =
                new CssMetaData<>("-jfx-rippler-radius",
                        SizeConverter.getInstance(), Region.USE_COMPUTED_SIZE) {
                    @Override
                    public boolean isSettable(JFXRippler control) {
                        return control.ripplerRadius == null || !control.ripplerRadius.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(JFXRippler control) {
                        return control.ripplerRadiusProperty();
                    }
                };
        private static final CssMetaData<JFXRippler, RipplerMask> MASK_TYPE =
                new CssMetaData<>("-jfx-mask-type",
                        RipplerMaskTypeConverter.getInstance(), RipplerMask.RECT) {
                    @Override
                    public boolean isSettable(JFXRippler control) {
                        return control.maskType == null || !control.maskType.isBound();
                    }

                    @Override
                    public StyleableProperty<RipplerMask> getStyleableProperty(JFXRippler control) {
                        return control.maskTypeProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(StackPane.getClassCssMetaData());
            Collections.addAll(styleables,
                    RIPPLER_RECENTER,
                    RIPPLER_RADIUS,
                    RIPPLER_FILL,
                    MASK_TYPE,
                    RIPPLER_DISABLED
            );
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }
}
