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

package com.jfoenix.utils;

import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2017-02-11
 */
public class JFXNodeUtils {


    public static void updateBackground(Background newBackground, Region nodeToUpdate) {
        updateBackground(newBackground, nodeToUpdate, Color.BLACK);
    }

    public static void updateBackground(Background newBackground, Region nodeToUpdate, Paint fill) {
        if (newBackground != null && !newBackground.getFills().isEmpty()) {
            final BackgroundFill[] fills = new BackgroundFill[newBackground.getFills().size()];
            for (int i = 0; i < newBackground.getFills().size(); i++) {
                BackgroundFill bf = newBackground.getFills().get(i);
                fills[i] = new BackgroundFill(fill, bf.getRadii(), bf.getInsets());
            }
            nodeToUpdate.setBackground(new Background(fills));
        }
    }

    public static String colorToHex(Color c) {
        if (c != null) {
            return String.format((Locale) null, "#%02x%02x%02x",
                Math.round(c.getRed() * 255),
                Math.round(c.getGreen() * 255),
                Math.round(c.getBlue() * 255)).toUpperCase();
        } else {
            return null;
        }
    }

    public static void addPressAndHoldHandler(Node node, Duration holdTime,
                                              EventHandler<MouseEvent> handler) {
        Wrapper<MouseEvent> eventWrapper = new Wrapper<>();
        PauseTransition holdTimer = new PauseTransition(holdTime);
        holdTimer.setOnFinished(event -> handler.handle(eventWrapper.content));
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            eventWrapper.content = event;
            holdTimer.playFromStart();
        });
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> holdTimer.stop());
        node.addEventHandler(MouseEvent.DRAG_DETECTED, event -> holdTimer.stop());
    }

    public static void addPressAndHoldFilter(Node node, Duration holdTime,
                                             EventHandler<MouseEvent> handler) {
        Wrapper<MouseEvent> eventWrapper = new Wrapper<>();
        PauseTransition holdTimer = new PauseTransition(holdTime);
        holdTimer.setOnFinished(event -> handler.handle(eventWrapper.content));
        node.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            eventWrapper.content = event;
            holdTimer.playFromStart();
        });
        node.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> holdTimer.stop());
        node.addEventFilter(MouseEvent.DRAG_DETECTED, event -> holdTimer.stop());
    }

    public static <T> InvalidationListener addDelayedPropertyInvalidationListener(ObservableValue<T> property,
                                                                                  Duration delayTime,
                                                                                  Consumer<T> consumer) {
        Wrapper<T> eventWrapper = new Wrapper<>();
        PauseTransition holdTimer = new PauseTransition(delayTime);
        holdTimer.setOnFinished(event -> consumer.accept(eventWrapper.content));
        final InvalidationListener invalidationListener = observable -> {
            eventWrapper.content = property.getValue();
            holdTimer.playFromStart();
        };
        property.addListener(invalidationListener);
        return invalidationListener;
    }

    public static <T> InvalidationListener addDelayedPropertyInvalidationListener(ObservableValue<T> property,
                                                                                  Duration delayTime,
                                                                                  BiConsumer<T, InvalidationListener> consumer) {
        Wrapper<T> eventWrapper = new Wrapper<>();
        PauseTransition holdTimer = new PauseTransition(delayTime);
        final InvalidationListener invalidationListener = observable -> {
            eventWrapper.content = property.getValue();
            holdTimer.playFromStart();
        };
        holdTimer.setOnFinished(event -> consumer.accept(eventWrapper.content, invalidationListener));
        property.addListener(invalidationListener);
        return invalidationListener;
    }


    public static <T> InvalidationListener addDelayedPropertyInvalidationListener(ObservableValue<T> property,
                                                                                  Duration delayTime,
                                                                                  Consumer<T> justInTimeConsumer,
                                                                                  Consumer<T> delayedConsumer) {
        Wrapper<T> eventWrapper = new Wrapper<>();
        PauseTransition holdTimer = new PauseTransition(delayTime);
        holdTimer.setOnFinished(event -> delayedConsumer.accept(eventWrapper.content));
        final InvalidationListener invalidationListener = observable -> {
            eventWrapper.content = property.getValue();
            justInTimeConsumer.accept(eventWrapper.content);
            holdTimer.playFromStart();
        };
        property.addListener(invalidationListener);
        return invalidationListener;
    }


    public static <T extends Event> EventHandler<? super T> addDelayedEventHandler(Node control, Duration delayTime,
                                                                                   final EventType<T> eventType,
                                                                                   final EventHandler<? super T> eventHandler) {
        Wrapper<T> eventWrapper = new Wrapper<>();
        PauseTransition holdTimer = new PauseTransition(delayTime);
        holdTimer.setOnFinished(finish -> eventHandler.handle(eventWrapper.content));
        final EventHandler<? super T> eventEventHandler = event -> {
            eventWrapper.content = event;
            holdTimer.playFromStart();
        };
        control.addEventHandler(eventType, eventEventHandler);
        return eventEventHandler;
    }

    private static class Wrapper<T> {
        T content;
    }
}
