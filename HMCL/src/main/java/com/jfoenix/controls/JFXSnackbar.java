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

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.Motion;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/// "Snackbars provide brief messages about app processes at the bottom of the screen"
/// (<a href="https://material.io/design/components/snackbars.html#">Material Design Guidelines</a>).
///
/// To show a snackbar you need to
/// <ol>
///   - Have a [Pane] (snackbarContainer) to show the snackbar on top of. Register it in
///     [the JFXSnackbar constructor][#JFXSnackbar(Pane)] or using the [#registerSnackbarContainer(Pane)] method.
///   - Have or create a [JFXSnackbar].  - Having one snackbar where you pass all your
///     [SnackbarEvents][JFXSnackbar.SnackbarEvent] will ensure that the [enqueuemethod][JFXSnackbar#enqueue(SnackbarEvent)] works as intended.
///
///   - Have something to show in the snackbar. A [JFXSnackbarLayout] is nice and pretty,
///     but any arbitrary [Node] will do.
///   - Create a [SnackbarEvent][JFXSnackbar.SnackbarEvent] specifying the contents and the
///     duration.
/// </ol>
///
/// Finally, with all those things prepared, show your snackbar using
/// [snackbar.enqueue(snackbarEvent);][JFXSnackbar#enqueue(SnackbarEvent)].
///
/// It's most convenient to create functions to do most of this (creating the layout and event) with the default
/// settings; that way all you need to do to show a snackbar is specify the message or just the message and the duration.
///
/// @see <a href="https://material.io/design/components/snackbars.html#"> The Material Design Snackbar</a>
public class JFXSnackbar extends Group {

    private static final String DEFAULT_STYLE_CLASS = "jfx-snackbar";

    private Pane snackbarContainer;
    private final ChangeListener<? super Number> sizeListener = (o, oldVal, newVal) -> refreshPopup();
    private final WeakChangeListener<? super Number> weakSizeListener = new WeakChangeListener<>(sizeListener);

    private final AtomicBoolean processingQueue = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<SnackbarEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap.KeySetView<Object, Boolean> eventsSet = ConcurrentHashMap.newKeySet();

    private final Pane content;
    private PseudoClass activePseudoClass = null;
    private PauseTransition pauseTransition;

    /// This constructor assumes that you will eventually call the [#registerSnackbarContainer(Pane)] method before
    /// calling the [#enqueue(SnackbarEvent)] method. Otherwise, how will the snackbar know where to show itself?
    ///
    ///
    /// "Snackbars provide brief messages about app processes at the bottom of the screen"
    /// (<a href="https://material.io/design/components/snackbars.html#">Material Design Guidelines</a>).
    ///
    /// To show a snackbar you need to
    ///
    /// - Have a [Pane] (snackbarContainer) to show the snackbar on top of. Register it in
    ///   [the JFXSnackbar constructor][#JFXSnackbar(Pane)] or using the [#registerSnackbarContainer(Pane)] method.
    /// - Have or create a [JFXSnackbar].  - Having one snackbar where you pass all your
    ///   [SnackbarEvents][JFXSnackbar.SnackbarEvent] will ensure that the [enqueuemethod][JFXSnackbar#enqueue(SnackbarEvent)] works as intended.
    /// - Have something to show in the snackbar. A [JFXSnackbarLayout] is nice and pretty,
    ///   but any arbitrary [Node] will do.
    /// - Create a [SnackbarEvent][JFXSnackbar.SnackbarEvent] specifying the contents and the
    ///   duration.
    ///
    /// Finally, with all those things prepared, show your snackbar using
    /// [snackbar.enqueue(snackbarEvent);][JFXSnackbar#enqueue(SnackbarEvent)].
    ///
    public JFXSnackbar() {
        this(null);
    }

    /// "Snackbars provide brief messages about app processes at the bottom of the screen"
    /// (<a href="https://material.io/design/components/snackbars.html#">Material Design Guidelines</a>).
    ///
    /// To show a snackbar you need to
    ///
    /// - Have a [Pane] (snackbarContainer) to show the snackbar on top of. Register it in
    ///   [the JFXSnackbar constructor][#JFXSnackbar(Pane)] or using the [#registerSnackbarContainer(Pane)] method.
    /// - Have or create a [JFXSnackbar].  - Having one snackbar where you pass all your
    ///   [SnackbarEvents][JFXSnackbar.SnackbarEvent] will ensure that the [enqueuemethod][JFXSnackbar#enqueue(SnackbarEvent)] works as intended.
    /// - Have something to show in the snackbar. A [JFXSnackbarLayout] is nice and pretty,
    ///   but any arbitrary [Node] will do.
    /// - Create a [SnackbarEvent][JFXSnackbar.SnackbarEvent] specifying the contents and the
    ///   duration.
    ///
    /// Finally, with all those things prepared, show your snackbar using
    /// [snackbar.enqueue(snackbarEvent);][JFXSnackbar#enqueue(SnackbarEvent)].
    ///
    /// @param snackbarContainer where the snackbar will appear. Using a single snackbar instead of many, will ensure that
    ///                                                                                                                              the [#enqueue(SnackbarEvent)] method works correctly.
    public JFXSnackbar(Pane snackbarContainer) {
        initialize();
        content = new StackPane();
        content.getStyleClass().add("jfx-snackbar-content");
        //wrap the content in a group so that the content is managed inside its own container
        //but the group is not managed in the snackbarContainer so it does not affect any layout calculations
        getChildren().add(content);
        setManaged(false);
        setVisible(false);

        // register the container before resizing it
        registerSnackbarContainer(snackbarContainer);

        // resize the popup if its layout has been changed
        layoutBoundsProperty().addListener((o, oldVal, newVal) -> refreshPopup());

        addEventHandler(SnackbarEvent.SNACKBAR, this::enqueue);
    }

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    // Setters / Getters

    public Pane getPopupContainer() {
        return snackbarContainer;
    }

    public void setPrefWidth(double width) {
        content.setPrefWidth(width);
    }

    public double getPrefWidth() {
        return content.getPrefWidth();
    }

    // Public API

    public void registerSnackbarContainer(Pane snackbarContainer) {
        if (snackbarContainer != null) {
            if (this.snackbarContainer != null) {
                //since listeners are added the container should be properly registered/unregistered
                throw new IllegalArgumentException("Snackbar Container already set");
            }
            this.snackbarContainer = snackbarContainer;
            this.snackbarContainer.getChildren().add(this);
            this.snackbarContainer.heightProperty().addListener(weakSizeListener);
            this.snackbarContainer.widthProperty().addListener(weakSizeListener);
        }
    }

    public void unregisterSnackbarContainer(Pane snackbarContainer) {
        if (snackbarContainer != null) {
            if (this.snackbarContainer == null) {
                throw new IllegalArgumentException("Snackbar Container not set");
            }
            this.snackbarContainer.getChildren().remove(this);
            this.snackbarContainer.heightProperty().removeListener(weakSizeListener);
            this.snackbarContainer.widthProperty().removeListener(weakSizeListener);
            this.snackbarContainer = null;
        }
    }

    private void show(SnackbarEvent event) {
        content.getChildren().setAll(event.getContent());
        openAnimation = getTimeline(event.getTimeout());
        if (event.getPseudoClass() != null) {
            activePseudoClass = event.getPseudoClass();
            content.pseudoClassStateChanged(activePseudoClass, true);
        }
        openAnimation.play();
    }

    private Timeline openAnimation = null;

    private Timeline getTimeline(Duration timeout) {
        Timeline animation;
        animation = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        e -> {
                            this.toBack();
                            this.setVisible(false);
                        },
                        new KeyValue(this.translateYProperty(), this.getLayoutBounds().getHeight(), Motion.EASE),
                        new KeyValue(this.opacityProperty(), 0, Motion.EASE)
                ),
                new KeyFrame(
                        Duration.millis(10),
                        e -> {
                            this.toFront();
                            this.setVisible(true);
                        }
                ),
                new KeyFrame(Duration.millis(300),
                        new KeyValue(this.opacityProperty(), 1, Motion.EASE),
                        new KeyValue(this.translateYProperty(), 0, Motion.EASE)
                )
        );
        animation.setCycleCount(1);
        pauseTransition = Duration.INDEFINITE.equals(timeout) ? null : new PauseTransition(timeout);
        if (pauseTransition != null) {
            animation.setOnFinished(finish -> {
                pauseTransition.setOnFinished(done -> {
                    pauseTransition = null;
                    eventsSet.remove(currentEvent);
                    currentEvent = eventQueue.peek();
                    close();
                });
                pauseTransition.play();
            });
        }
        return animation;
    }

    public void close() {
        if (openAnimation != null) {
            openAnimation.stop();
        }
        if (this.isVisible()) {
            Timeline closeAnimation = new Timeline(
                    new KeyFrame(
                            Duration.ZERO,
                            e -> this.toFront(),
                            new KeyValue(this.opacityProperty(), 1, Motion.EASE),
                            new KeyValue(this.translateYProperty(), 0, Motion.EASE)
                    ),
                    new KeyFrame(
                            Duration.millis(290),
                            e -> this.setVisible(true)
                    ),
                    new KeyFrame(Duration.millis(300),
                            e -> {
                                this.toBack();
                                this.setVisible(false);
                            },
                            new KeyValue(this.translateYProperty(), this.getLayoutBounds().getHeight(), Motion.EASE),
                            new KeyValue(this.opacityProperty(), 0, Motion.EASE)
                    )
            );
            closeAnimation.setCycleCount(1);
            closeAnimation.setOnFinished(e -> {
                resetPseudoClass();
                processSnackbar();
            });
            closeAnimation.play();
        }
    }

    private SnackbarEvent currentEvent = null;

    public SnackbarEvent getCurrentEvent() {
        return currentEvent;
    }

    /**
     * Shows {@link SnackbarEvent SnackbarEvents} one by one. The next event will be shown after the current event's duration.
     *
     * @param event the {@link SnackbarEvent event} to put in the queue.
     */
    public void enqueue(SnackbarEvent event) {
        synchronized (this) {
            if (!eventsSet.contains(event)) {
                eventsSet.add(event);
                eventQueue.offer(event);
            } else if (currentEvent == event && pauseTransition != null) {
                pauseTransition.playFromStart();
            }
        }
        if (processingQueue.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                currentEvent = eventQueue.poll();
                if (currentEvent != null) {
                    show(currentEvent);
                }
            });
        }
    }

    private void resetPseudoClass() {
        if (activePseudoClass != null) {
            content.pseudoClassStateChanged(activePseudoClass, false);
            activePseudoClass = null;
        }
    }

    private void processSnackbar() {
        currentEvent = eventQueue.poll();
        if (currentEvent != null) {
            eventsSet.remove(currentEvent);
            show(currentEvent);
        } else {
            //The enqueue method and this listener should be executed sequentially on the FX Thread so there
            //should not be a race condition
            processingQueue.getAndSet(false);
        }
    }

    private void refreshPopup() {
        if (snackbarContainer == null) {
            return;
        }
        Bounds contentBound = this.getLayoutBounds();
        double offsetX = Math.ceil(snackbarContainer.getWidth() / 2) - Math.ceil(contentBound.getWidth() / 2);
        double offsetY = snackbarContainer.getHeight() - contentBound.getHeight();
        this.setLayoutX(offsetX);
        this.setLayoutY(offsetY);

    }

    ///////////////////////////////////////////////////////////////////////////
    // Event API
    ///////////////////////////////////////////////////////////////////////////

    /// Specifies _what_ and _how long_ to show a [JFXSnackbar].
    ///
    /// The _what_ can be any arbitrary [Node]; the [JFXSnackbarLayout] is a great choice.
    ///
    /// The _how long_ is specified in the form of a [javafx.util.Duration][javafx.util.Duration], not to be
    /// confused with the [java.time.Duration].
    public static class SnackbarEvent extends Event {

        public static final EventType<SnackbarEvent> SNACKBAR = new EventType<>(Event.ANY, "SNACKBAR");

        /// The amount of time the snackbar will show for, if not otherwise specified.
        ///
        /// It's 1.5 seconds.
        public static Duration DEFAULT_DURATION = Duration.seconds(1.5);

        private final Node content;
        private final PseudoClass pseudoClass;
        private final Duration timeout;

        /// Creates a [SnackbarEvent] with the [default duration][#DEFAULT_DURATION] and no pseudoClass.
        ///
        /// @param content what you want shown in the snackbar; a [JFXSnackbarLayout] is a great choice.
        public SnackbarEvent(Node content) {
            this(content, DEFAULT_DURATION, null);
        }

        /// Creates a [SnackbarEvent] with the [default duration][#DEFAULT_DURATION]; you specify the contents and
        /// pseudoClass.
        ///
        /// @param content what you want shown in the snackbar; a [JFXSnackbarLayout] is a great choice.
        public SnackbarEvent(Node content, PseudoClass pseudoClass) {
            this(content, DEFAULT_DURATION, pseudoClass);
        }

        /// Creates a SnackbarEvent with no pseudoClass; you specify the contents and duration.
        /// pseudoClass.
        ///
        /// @param content what you want shown in the snackbar; a [JFXSnackbarLayout] is a great choice.
        /// @param timeout the amount of time you want the snackbar to show for.
        public SnackbarEvent(Node content, Duration timeout) {
            this(content, timeout, null);
        }

        /// Creates a SnackbarEvent; you specify the contents, duration and pseudoClass.
        ///
        /// If you don't need so much customization, try one of the other constructors.
        ///
        /// @param content what you want shown in the snackbar; a [JFXSnackbarLayout] is a great choice.
        /// @param timeout the amount of time you want the snackbar to show for.
        public SnackbarEvent(Node content, Duration timeout, PseudoClass pseudoClass) {
            super(SNACKBAR);
            this.content = content;
            this.pseudoClass = pseudoClass;
            this.timeout = timeout;
        }

        public Node getContent() {
            return content;
        }

        public PseudoClass getPseudoClass() {
            return pseudoClass;
        }

        public Duration getTimeout() {
            return timeout;
        }

        @Override
        @SuppressWarnings("unchecked")
        public EventType<? extends SnackbarEvent> getEventType() {
            return (EventType<? extends SnackbarEvent>) super.getEventType();
        }

        public boolean isPersistent() {
            return Duration.INDEFINITE.equals(getTimeout());
        }
    }
}

