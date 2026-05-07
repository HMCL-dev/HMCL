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

import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Window;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Shadi Shaheen
/// @version 1.0
/// @since 2017-02-11
public final class JFXNodeUtils {

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
            return String.format((Locale) null, "#%02X%02X%02X",
                    Math.round(c.getRed() * 255),
                    Math.round(c.getGreen() * 255),
                    Math.round(c.getBlue() * 255));
        } else {
            return null;
        }
    }

    private static final @NotNull Function<Node, ObservableBooleanValue> treeVisiblePropertyGetter = initTreeVisiblePropertyGetter();

    private static @NotNull Function<Node, ObservableBooleanValue> initTreeVisiblePropertyGetter() {

        MethodHandles.Lookup lookup;
        try {
            lookup = MethodHandles.privateLookupIn(Node.class, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            LOG.warning("Failed to get private lookup for Node", e);
            return JFXNodeUtils::defaultTreeVisibleProperty;
        }

        try {
            Method treeVisiblePropertyMethod = Node.class.getDeclaredMethod("treeVisibleProperty");
            if (!ObservableBooleanValue.class.isAssignableFrom(treeVisiblePropertyMethod.getReturnType())) {
                LOG.warning("Node.treeVisibleProperty() does not return ObservableBooleanValue: " + treeVisiblePropertyMethod.getReturnType());
                return JFXNodeUtils::defaultTreeVisibleProperty;
            }

            MethodHandle handle = lookup.unreflect(treeVisiblePropertyMethod)
                    .asType(MethodType.methodType(ObservableBooleanValue.class, Node.class));
            return item -> {
                try {
                    return (ObservableBooleanValue) handle.invokeExact((Node) item);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new AssertionError("Unreachable", e);
                }
            };
        } catch (Exception e) {
            LOG.warning("Failed to get method handle for Node.treeVisibleProperty()", e);
            return JFXNodeUtils::defaultTreeVisibleProperty;
        }
    }

    /// If `Node.treeVisibleProperty()` does not exist, use `Node.visibleProperty()` as a fallback
    private static @NotNull ObservableBooleanValue defaultTreeVisibleProperty(Node item) {
        return item.visibleProperty();
    }

    public static @NotNull ObservableBooleanValue treeVisibleProperty(Node item) {
        return treeVisiblePropertyGetter.apply(item);
    }

    public static boolean isTreeVisible(Node item) {
        return treeVisibleProperty(item).getValue();
    }

    public static boolean isTreeShowing(Node node) {
        if (node == null)
            return false;

        Scene scene = node.getScene();
        if (scene == null)
            return false;

        Window window = scene.getWindow();
        if (window == null || !window.isShowing())
            return false;

        return isTreeVisible(node);
    }

    private JFXNodeUtils() {
    }

}
