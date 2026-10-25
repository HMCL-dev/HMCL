/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.decorator;

import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Displays navigation content and prepares one hidden page in the same scene.
@NotNullByDefault
final class NavigationContentPane extends Region {
    /// The visible navigation viewport supplying preparation dimensions.
    private final Region navigator;

    /// The temporary preparation container, or `null` while no candidate is pending.
    private @Nullable StackPane preloadPane;

    /// Whether the current candidate still needs its first content layout pass.
    private boolean preloadLayoutPending;

    /// Creates a clipped content area containing the navigator.
    ///
    /// @param navigator the navigation viewport to display
    NavigationContentPane(Region navigator) {
        this.navigator = navigator;
        getStyleClass().add("jfx-decorator-content-container");
        FXUtils.setOverflowHidden(this);
        getChildren().setAll(navigator);
    }

    /// Replaces the candidate with an unowned page, using a snapshot of the navigator size.
    /// Reuses the pending hidden container when present. CSS and layout are deferred
    /// to scene pulses. The container survives the first content layout pass for
    /// each candidate and is removed and emptied on a later pass.
    ///
    /// Requires the JavaFX Application Thread and, for preparation to proceed,
    /// a scene that processes pulses. The caller must retain the page for reuse.
    ///
    /// @param page the page to prepare
    /// @return the supplied page
    /// @throws IllegalArgumentException if the page already belongs to a parent or scene
    <N extends Node> N preparePage(N page) {
        if (page.getParent() != null || page.getScene() != null) {
            throw new IllegalArgumentException("Page already belongs to a parent or scene");
        }
        if (preloadPane == null) {
            preloadPane = new StackPane();
            preloadPane.setVisible(false);
            preloadPane.setManaged(false);
            preloadPane.setMouseTransparent(true);
            getChildren().add(0, preloadPane);
        }
        preloadPane.getChildren().setAll(page);
        preloadPane.resize(navigator.getWidth(), navigator.getHeight());
        preloadLayoutPending = true;
        requestLayout();
        return page;
    }

    /// Returns the navigation viewport's sizing dependency.
    @Override
    public @Nullable Orientation getContentBias() {
        return navigator.getContentBias();
    }

    /// Returns the viewport's minimum width plus horizontal insets.
    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + navigator.minWidth(height < 0 ? -1
                : Math.max(0, height - snappedTopInset() - snappedBottomInset())) + snappedRightInset();
    }

    /// Returns the viewport's minimum height plus vertical insets.
    @Override
    protected double computeMinHeight(double width) {
        return snappedTopInset() + navigator.minHeight(width < 0 ? -1
                : Math.max(0, width - snappedLeftInset() - snappedRightInset())) + snappedBottomInset();
    }

    /// Returns the viewport's preferred width plus horizontal insets.
    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + navigator.prefWidth(height < 0 ? -1
                : Math.max(0, height - snappedTopInset() - snappedBottomInset())) + snappedRightInset();
    }

    /// Returns the viewport's preferred height plus vertical insets.
    @Override
    protected double computePrefHeight(double width) {
        return snappedTopInset() + navigator.prefHeight(width < 0 ? -1
                : Math.max(0, width - snappedLeftInset() - snappedRightInset())) + snappedBottomInset();
    }

    /// Lays out navigation content and removes a candidate retained from an earlier pass.
    @Override
    protected void layoutChildren() {
        double left = snappedLeftInset();
        double top = snappedTopInset();
        layoutInArea(navigator, left, top,
                getWidth() - left - snappedRightInset(), getHeight() - top - snappedBottomInset(),
                0, HPos.CENTER, VPos.CENTER);
        @Nullable StackPane candidate = preloadPane;
        if (candidate != null && getScene() != null) {
            if (preloadLayoutPending) {
                preloadLayoutPending = false;
            } else {
                disposePreloadPane(candidate);
            }
        }
    }

    /// Removes and empties the specified container without affecting a newer candidate.
    private void disposePreloadPane(StackPane candidate) {
        getChildren().remove(candidate);
        candidate.getChildren().clear();
        if (preloadPane == candidate) {
            preloadPane = null;
        }
    }
}
