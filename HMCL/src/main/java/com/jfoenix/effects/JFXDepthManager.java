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

package com.jfoenix.effects;

import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * it will create a shadow effect for a given node and a specified depth level.
 * depth levels are {0,1,2,3,4,5}
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXDepthManager {

    private static final DropShadow[] depth = new DropShadow[] {
        new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0), 0, 0, 0, 0),
        new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 10, 0.12, -1, 2),
        new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 15, 0.16, 0, 4),
        new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 20, 0.19, 0, 6),
        new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 25, 0.25, 0, 8),
        new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 30, 0.30, 0, 10)};

    /**
     * this method is used to add shadow effect to the node,
     * however the shadow is not real ( gets affected with node transformations)
     * <p>
     * use {@link #createMaterialNode(Node, int)} instead to generate a real shadow
     */
    public static void setDepth(Node control, int level) {
        level = Math.max(level, 0);
        level = Math.min(level, 5);
        control.setEffect(new DropShadow(BlurType.GAUSSIAN,
            depth[level].getColor(),
            depth[level].getRadius(),
            depth[level].getSpread(),
            depth[level].getOffsetX(),
            depth[level].getOffsetY()));
    }

    public static int getLevels() {
        return depth.length;
    }

    public static DropShadow getShadowAt(int level) {
        return depth[level];
    }

    /**
     * this method will generate a new container node that prevent
     * control transformation to be applied to the shadow effect
     * (which makes it looks as a real shadow)
     */
    public static Node createMaterialNode(Node control, int level) {
        Node container = new Pane(control){
            @Override
            protected double computeMaxWidth(double height) {
                return computePrefWidth(height);
            }

            @Override
            protected double computeMaxHeight(double width) {
                return computePrefHeight(width);
            }

            @Override
            protected double computePrefWidth(double height) {
                return control.prefWidth(height);
            }

            @Override
            protected double computePrefHeight(double width) {
                return control.prefHeight(width);
            }
        };
        container.getStyleClass().add("depth-container");
        container.setPickOnBounds(false);
        level = Math.max(level, 0);
        level = Math.min(level, 5);
        container.setEffect(new DropShadow(BlurType.GAUSSIAN,
            depth[level].getColor(),
            depth[level].getRadius(),
            depth[level].getSpread(),
            depth[level].getOffsetX(),
            depth[level].getOffsetY()));
        return container;
    }

    public static void pop(Node control) {
        control.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.26), 5, 0.05, 0, 1));
    }

}
