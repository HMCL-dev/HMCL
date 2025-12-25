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

import com.jfoenix.controls.events.JFXDialogEvent;
import com.jfoenix.converters.DialogTransitionConverter;
import com.jfoenix.effects.JFXDepthManager;
import com.jfoenix.transitions.CachedTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.Motion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Note: for JFXDialog to work properly, the root node **MUST**
/// be of type [StackPane]
///
/// @author Shadi Shaheen
/// @version 1.0
/// @since 2016-03-09
@DefaultProperty(value = "content")
public class JFXDialog extends StackPane {

    private static final double INITIAL_SCALE = 0.8;

    // public static enum JFXDialogLayout{PLAIN, HEADING, ACTIONS, BACKDROP};
    public enum DialogTransition {
        CENTER, NONE
    }

    private StackPane contentHolder;

    private double offsetX = 0;
    private double offsetY = 0;

    private StackPane dialogContainer;
    private Region content;
    private Transition animation;

    private final EventHandler<? super MouseEvent> closeHandler = e -> close();

    /// creates empty JFXDialog control with CENTER animation type
    public JFXDialog() {
        this(null, null, DialogTransition.CENTER);
    }

    /// creates empty JFXDialog control with a specified animation type
    public JFXDialog(DialogTransition transition) {
        this(null, null, transition);
    }

    /// creates JFXDialog control with a specified animation type, the animation type
    /// can be one of the following:
    ///
    ///   - CENTER
    ///   - TOP
    ///   - RIGHT
    ///   - BOTTOM
    ///   - LEFT
    ///
    /// @param dialogContainer is the parent of the dialog, it
    /// @param content         the content of dialog
    /// @param transitionType  the animation type
    public JFXDialog(StackPane dialogContainer, Region content, DialogTransition transitionType) {
        initialize();
        setContent(content);
        setDialogContainer(dialogContainer);
        this.transitionType.set(transitionType);
        // init change listeners
        initChangeListeners();
    }

    /// creates JFXDialog control with a specified animation type that
    /// is closed when clicking on the overlay, the animation type
    /// can be one of the following:
    ///
    ///   - CENTER
    ///   - TOP
    ///   - RIGHT
    ///   - BOTTOM
    ///   - LEFT
    public JFXDialog(StackPane dialogContainer, Region content, DialogTransition transitionType, boolean overlayClose) {
        setOverlayClose(overlayClose);
        initialize();
        setContent(content);
        setDialogContainer(dialogContainer);
        this.transitionType.set(transitionType);
        // init change listeners
        initChangeListeners();
    }

    private void initChangeListeners() {
        overlayCloseProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                this.addEventHandler(MouseEvent.MOUSE_PRESSED, closeHandler);
            } else {
                this.removeEventHandler(MouseEvent.MOUSE_PRESSED, closeHandler);
            }
        });
    }

    private void initialize() {
        this.setVisible(false);
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
        this.transitionType.addListener((o, oldVal, newVal) -> {
            animation = getShowAnimation(transitionType.get());
        });

        contentHolder = new StackPane();
        contentHolder.setBackground(new Background(new BackgroundFill(Color.WHITE, new CornerRadii(2), null)));
        JFXDepthManager.setDepth(contentHolder, 4);
        contentHolder.setPickOnBounds(false);
        // ensure stackpane is never resized beyond it's preferred size
        contentHolder.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        this.getChildren().add(contentHolder);
        this.getStyleClass().add("jfx-dialog-overlay-pane");
        StackPane.setAlignment(contentHolder, Pos.CENTER);
        this.setBackground(new Background(new BackgroundFill(Color.rgb(0, 0, 0, 0.1), null, null)));
        // close the dialog if clicked on the overlay pane
        if (overlayClose.get()) {
            this.addEventHandler(MouseEvent.MOUSE_PRESSED, closeHandler);
        }
        // prevent propagating the events to overlay pane
        contentHolder.addEventHandler(MouseEvent.ANY, Event::consume);
    }

    /***************************************************************************
     *                                                                         *
     * Setters / Getters                                                       *
     *                                                                         *
     **************************************************************************/

    /// @return the dialog container
    public StackPane getDialogContainer() {
        return dialogContainer;
    }

    /// set the dialog container
    /// Note: the dialog container must be StackPane, its the container for the dialog to be shown in.
    public void setDialogContainer(StackPane dialogContainer) {
        if (dialogContainer != null) {
            this.dialogContainer = dialogContainer;
            // FIXME: need to be improved to consider only the parent boundary
            offsetX = dialogContainer.getBoundsInLocal().getWidth();
            offsetY = dialogContainer.getBoundsInLocal().getHeight();
            animation = getShowAnimation(transitionType.get());
        }
    }

    /// @return dialog content node
    public Region getContent() {
        return content;
    }

    /// set the content of the dialog
    public void setContent(Region content) {
        if (content != null) {
            this.content = content;
            this.content.setPickOnBounds(false);
            contentHolder.getChildren().setAll(content);
        }
    }

    /// indicates whether the dialog will close when clicking on the overlay or not
    private final BooleanProperty overlayClose = new SimpleBooleanProperty(true);

    public final BooleanProperty overlayCloseProperty() {
        return this.overlayClose;
    }

    public final boolean isOverlayClose() {
        return this.overlayCloseProperty().get();
    }

    public final void setOverlayClose(final boolean overlayClose) {
        this.overlayCloseProperty().set(overlayClose);
    }

    /// if sets to true, the content of dialog container will be cached and replaced with an image
    /// when displaying the dialog (better performance).
    /// this is recommended if the content behind the dialog will not change during the showing
    /// period
    private final BooleanProperty cacheContainer = new SimpleBooleanProperty(false);

    public boolean isCacheContainer() {
        return cacheContainer.get();
    }

    public BooleanProperty cacheContainerProperty() {
        return cacheContainer;
    }

    public void setCacheContainer(boolean cacheContainer) {
        this.cacheContainer.set(cacheContainer);
    }

    /// it will show the dialog in the specified container
    public void show(StackPane dialogContainer) {
        this.setDialogContainer(dialogContainer);
        showDialog();
    }

    private ArrayList<Node> tempContent;

    /**
     * show the dialog inside its parent container
     */
    public void show() {
        this.setDialogContainer(dialogContainer);
        showDialog();
    }

    private void showDialog() {
        if (dialogContainer == null) {
            throw new RuntimeException("ERROR: JFXDialog container is not set!");
        }
        if (isCacheContainer()) {
            tempContent = new ArrayList<>(dialogContainer.getChildren());

            SnapshotParameters snapShotparams = new SnapshotParameters();
            snapShotparams.setFill(Color.TRANSPARENT);
            WritableImage temp = dialogContainer.snapshot(snapShotparams,
                    new WritableImage((int) dialogContainer.getWidth(),
                            (int) dialogContainer.getHeight()));
            ImageView tempImage = new ImageView(temp);
            tempImage.setCache(true);
            tempImage.setCacheHint(CacheHint.SPEED);
            dialogContainer.getChildren().setAll(tempImage, this);
        } else {
            //prevent error if opening an already opened dialog
            dialogContainer.getChildren().remove(this);
            tempContent = null;
            dialogContainer.getChildren().add(this);
        }

        if (animation != null) {
            animation.play();
        } else {
            setVisible(true);
            setOpacity(1);
            Event.fireEvent(JFXDialog.this, new JFXDialogEvent(JFXDialogEvent.OPENED));
        }
    }

    /**
     * close the dialog
     */
    public void close() {
        if (animation != null) {
            animation.setRate(-2);
            animation.play();
            animation.setOnFinished(e -> {
                closeDialog();
            });
        } else {
            setOpacity(0);
            setVisible(false);
            closeDialog();
        }
    }

    private void closeDialog() {
        resetProperties();
        Event.fireEvent(JFXDialog.this, new JFXDialogEvent(JFXDialogEvent.CLOSED));
        if (tempContent == null) {
            dialogContainer.getChildren().remove(this);
        } else {
            dialogContainer.getChildren().setAll(tempContent);
        }
    }

    /***************************************************************************
     *                                                                         *
     * Transitions                                                             *
     *                                                                         *
     **************************************************************************/

    private Transition getShowAnimation(DialogTransition transitionType) {
        Transition animation = null;
        if (contentHolder != null) {
            animation = switch (transitionType) {
                case CENTER -> {
                    contentHolder.setScaleX(INITIAL_SCALE);
                    contentHolder.setScaleY(INITIAL_SCALE);
                    yield new CenterTransition();
                }
                case NONE -> {
                    contentHolder.setScaleX(1);
                    contentHolder.setScaleY(1);
                    contentHolder.setTranslateX(0);
                    contentHolder.setTranslateY(0);
                    yield null;
                }
            };
        }
        if (animation != null) {
            animation.setOnFinished(finish ->
                    Event.fireEvent(JFXDialog.this, new JFXDialogEvent(JFXDialogEvent.OPENED)));
        }
        return animation;
    }

    private void resetProperties() {
        this.setVisible(false);
        contentHolder.setTranslateX(0);
        contentHolder.setTranslateY(0);
        contentHolder.setScaleX(1);
        contentHolder.setScaleY(1);
    }

    private final class CenterTransition extends CachedTransition {
        private static final Interpolator INTERPOLATOR = Motion.EMPHASIZED_DECELERATE;

        CenterTransition() {
            super(contentHolder, new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(contentHolder.scaleXProperty(), INITIAL_SCALE, INTERPOLATOR),
                            new KeyValue(contentHolder.scaleYProperty(), INITIAL_SCALE, INTERPOLATOR),
                            new KeyValue(JFXDialog.this.visibleProperty(), false, Motion.LINEAR)
                    ),
                    new KeyFrame(Duration.millis(10),
                            new KeyValue(JFXDialog.this.visibleProperty(), true, Motion.LINEAR),
                            new KeyValue(JFXDialog.this.opacityProperty(), 0, INTERPOLATOR)
                    ),
                    new KeyFrame(Motion.EXTRA_LONG4,
                            new KeyValue(contentHolder.scaleXProperty(), 1, INTERPOLATOR),
                            new KeyValue(contentHolder.scaleYProperty(), 1, INTERPOLATOR),
                            new KeyValue(JFXDialog.this.visibleProperty(), true, Motion.LINEAR),
                            new KeyValue(JFXDialog.this.opacityProperty(), 1, INTERPOLATOR)
                    ))
            );
            // reduce the number to increase the shifting , increase number to reduce shifting
            setCycleDuration(Duration.seconds(0.4));
            setDelay(Duration.ZERO);
        }
    }

    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    /// Initialize the style class to 'jfx-dialog'.
    ///
    /// This is the selector class from which CSS can be used to style
    /// this control.
    private static final String DEFAULT_STYLE_CLASS = "jfx-dialog";

    /// dialog transition type property, it can be one of the following:
    ///
    ///   - CENTER
    ///   - TOP
    ///   - RIGHT
    ///   - BOTTOM
    ///   - LEFT
    ///   - NONE
    private final StyleableObjectProperty<DialogTransition> transitionType = new SimpleStyleableObjectProperty<>(
            StyleableProperties.DIALOG_TRANSITION,
            JFXDialog.this,
            "dialogTransition",
            DialogTransition.CENTER);

    public DialogTransition getTransitionType() {
        return transitionType == null ? DialogTransition.CENTER : transitionType.get();
    }

    public StyleableObjectProperty<DialogTransition> transitionTypeProperty() {
        return this.transitionType;
    }

    public void setTransitionType(DialogTransition transition) {
        this.transitionType.set(transition);
    }

    private static final class StyleableProperties {
        private static final CssMetaData<JFXDialog, DialogTransition> DIALOG_TRANSITION =
                new CssMetaData<JFXDialog, DialogTransition>("-jfx-dialog-transition",
                        DialogTransitionConverter.getInstance(),
                        DialogTransition.CENTER) {
                    @Override
                    public boolean isSettable(JFXDialog control) {
                        return control.transitionType == null || !control.transitionType.isBound();
                    }

                    @Override
                    public StyleableProperty<DialogTransition> getStyleableProperty(JFXDialog control) {
                        return control.transitionTypeProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(StackPane.getClassCssMetaData());
            Collections.addAll(styleables,
                    DIALOG_TRANSITION
            );
            CHILD_STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.CHILD_STYLEABLES;
    }


    /***************************************************************************
     *                                                                         *
     * Custom Events                                                           *
     *                                                                         *
     **************************************************************************/

    private final ObjectProperty<EventHandler<? super JFXDialogEvent>> onDialogClosedProperty = new ObjectPropertyBase<EventHandler<? super JFXDialogEvent>>() {
        @Override
        protected void invalidated() {
            setEventHandler(JFXDialogEvent.CLOSED, get());
        }

        @Override
        public Object getBean() {
            return JFXDialog.this;
        }

        @Override
        public String getName() {
            return "onClosed";
        }
    };

    /**
     * Defines a function to be called when the dialog is closed.
     * Note: it will be triggered after the close animation is finished.
     */
    public ObjectProperty<EventHandler<? super JFXDialogEvent>> onDialogClosedProperty() {
        return onDialogClosedProperty;
    }

    public void setOnDialogClosed(EventHandler<? super JFXDialogEvent> handler) {
        onDialogClosedProperty().set(handler);
    }

    public EventHandler<? super JFXDialogEvent> getOnDialogClosed() {
        return onDialogClosedProperty().get();
    }

    private final ObjectProperty<EventHandler<? super JFXDialogEvent>> onDialogOpenedProperty = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(JFXDialogEvent.OPENED, get());
        }

        @Override
        public Object getBean() {
            return JFXDialog.this;
        }

        @Override
        public String getName() {
            return "onOpened";
        }
    };

    /**
     * Defines a function to be called when the dialog is opened.
     * Note: it will be triggered after the show animation is finished.
     */
    public ObjectProperty<EventHandler<? super JFXDialogEvent>> onDialogOpenedProperty() {
        return onDialogOpenedProperty;
    }

    public void setOnDialogOpened(EventHandler<? super JFXDialogEvent> handler) {
        onDialogOpenedProperty().set(handler);
    }

    public EventHandler<? super JFXDialogEvent> getOnDialogOpened() {
        return onDialogOpenedProperty().get();
    }
}
