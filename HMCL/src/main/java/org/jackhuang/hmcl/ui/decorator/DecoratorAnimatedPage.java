// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.decorator;

import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Lays out a fixed-width sidebar and a growing content area as independent animation targets.
@NotNullByDefault
public class DecoratorAnimatedPage extends Region {

    /// The vertically stacked sidebar, initially constrained to 200 pixels wide.
    protected final VBox left = new VBox();
    /// The content area, with overlapping children sized by a stack layout.
    protected final StackPane center = new StackPane();

    /// Creates both animation targets without requiring CSS or skin initialization.
    public DecoratorAnimatedPage() {
        getStyleClass().add("gray-background");
        FXUtils.setLimitWidth(left, 200);
        getChildren().setAll(left, center);
    }

    /// Replaces the sidebar contents in vertical order.
    protected void setLeft(Node... children) {
        left.getChildren().setAll(children);
    }

    /// Replaces the overlapping content nodes.
    protected void setCenter(Node... children) {
        center.getChildren().setAll(children);
    }

    /// Returns the sidebar container used by navigation animations.
    public VBox getLeft() {
        return left;
    }

    /// Returns the content container used by navigation animations.
    public StackPane getCenter() {
        return center;
    }

    /// Propagates content sizing dependencies, preferring width-dependent content.
    @Override
    public @Nullable Orientation getContentBias() {
        @Nullable Orientation centerBias = center.isManaged() ? center.getContentBias() : null;
        @Nullable Orientation leftBias = left.isManaged() ? left.getContentBias() : null;
        if (centerBias == Orientation.HORIZONTAL || leftBias == Orientation.HORIZONTAL) {
            return Orientation.HORIZONTAL;
        }
        return centerBias != null ? centerBias : leftBias;
    }

    /// Includes both managed columns and the horizontal insets in the minimum width.
    @Override
    protected double computeMinWidth(double height) {
        double contentHeight = height < 0 ? -1 : Math.max(0, height - snappedTopInset() - snappedBottomInset());
        return snappedLeftInset() + snappedRightInset()
                + (left.isManaged() ? snapSizeX(left.minWidth(contentHeight)) : 0)
                + (center.isManaged() ? snapSizeX(center.minWidth(contentHeight)) : 0);
    }

    /// Includes both managed columns and the horizontal insets in the preferred width.
    @Override
    protected double computePrefWidth(double height) {
        double contentHeight = height < 0 ? -1 : Math.max(0, height - snappedTopInset() - snappedBottomInset());
        return snappedLeftInset() + snappedRightInset() + sidebarWidth(contentHeight)
                + (center.isManaged() ? snapSizeX(center.prefWidth(contentHeight)) : 0);
    }

    /// Uses the taller column's minimum height at its allocated width.
    @Override
    protected double computeMinHeight(double width) {
        double sidebarWidth = sidebarWidth(-1);
        double contentWidth = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset() - sidebarWidth);
        return snappedTopInset() + snappedBottomInset() + Math.max(
                left.isManaged() ? snapSizeY(left.minHeight(sidebarWidth)) : 0,
                center.isManaged() ? snapSizeY(center.minHeight(contentWidth)) : 0);
    }

    /// Uses the taller column's preferred height at its allocated width.
    @Override
    protected double computePrefHeight(double width) {
        double sidebarWidth = sidebarWidth(-1);
        double contentWidth = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset() - sidebarWidth);
        return snappedTopInset() + snappedBottomInset() + Math.max(
                left.isManaged() ? snapSizeY(left.prefHeight(sidebarWidth)) : 0,
                center.isManaged() ? snapSizeY(center.prefHeight(contentWidth)) : 0);
    }

    /// Returns the sidebar's preferred width within its size constraints, or zero when unmanaged.
    private double sidebarWidth(double height) {
        return left.isManaged() ? snapSizeX(Math.max(left.minWidth(height), Math.min(left.prefWidth(height), left.maxWidth(height)))) : 0;
    }

    /// Fills the available height and gives the content the space remaining beside the sidebar.
    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        // Preserve child minimum sizes even if an ancestor allocates less than the page minimum.
        if (getContentBias() == Orientation.VERTICAL) {
            height = Math.max(height, minHeight(-1));
            width = Math.max(width, minWidth(height));
        } else {
            width = Math.max(width, minWidth(-1));
            height = Math.max(height, minHeight(getContentBias() == Orientation.HORIZONTAL ? width : -1));
        }
        double x = snappedLeftInset();
        double y = snappedTopInset();
        double contentWidth = Math.max(0, width - x - snappedRightInset());
        double contentHeight = Math.max(0, height - y - snappedBottomInset());
        double sidebarWidth = sidebarWidth(contentHeight);
        if (left.isManaged()) {
            layoutInArea(left, x, y, sidebarWidth, contentHeight, 0, HPos.LEFT, VPos.TOP);
        }
        if (center.isManaged()) {
            layoutInArea(center, x + sidebarWidth, y, Math.max(0, contentWidth - sidebarWidth), contentHeight,
                    0, HPos.CENTER, VPos.CENTER);
        }
    }
}
