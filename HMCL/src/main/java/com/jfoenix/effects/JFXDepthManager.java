/*
 * Copyright (c) 2016 JFoenix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
public final class JFXDepthManager {
    private JFXDepthManager() {
        throw new AssertionError();
    }

    private static final DropShadow[] depth = new DropShadow[] {
            new DropShadow(BlurType.GAUSSIAN, Color.TRANSPARENT, 0, 0, 0, 0),
            new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.12), 4, 0, 0, 1),
            new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.16), 6, 0, 0, 2),
            new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.20), 10, 0, 0, 3),
            new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.24), 14, 0, 0, 4),
            new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.28), 20, 0, 0, 6)
    };

    /**
     * this method is used to add shadow effect to the node,
     * however the shadow is not real ( gets affected with node transformations)
     * <p>
     * use {@link #createMaterialNode(Node, int)} instead to generate a real shadow
     */
    public static void setDepth(Node control, int level) {
        level = level < 0 ? 0 : level;
        level = level > 5 ? 5 : level;
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
        Node container = new Pane(control) {
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
        level = level < 0 ? 0 : level;
        level = level > 5 ? 5 : level;
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

