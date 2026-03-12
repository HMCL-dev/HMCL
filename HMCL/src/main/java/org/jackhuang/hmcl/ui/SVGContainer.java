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
package org.jackhuang.hmcl.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Parent;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.Motion;

/// A lightweight wrapper for displaying [SVG] icons.
///
/// @author Glavo
public final class SVGContainer extends Parent {

    private static final String DEFAULT_STYLE_CLASS = "svg-container";

    private final SVGPath path = new SVGPath();
    private SVG icon = SVG.NONE;
    private double iconSize = SVG.DEFAULT_SIZE;
    private SVGPath tempPath;
    private Timeline timeline;

    {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
        this.path.getStyleClass().add("svg");
    }

    /// Creates an SVGContainer with the default icon and the default icon size.
    public SVGContainer() {
        this(SVG.NONE, SVG.DEFAULT_SIZE);
    }

    /// Creates an SVGContainer showing the given icon using the default icon size.
    ///
    /// @param icon the [SVG] icon to display
    public SVGContainer(SVG icon) {
        this(icon, SVG.DEFAULT_SIZE);
    }

    /// Creates an SVGContainer with a custom icon size. The initial icon is
    /// [SVG#NONE].
    ///
    /// @param iconSize the icon size
    public SVGContainer(double iconSize) {
        this(SVG.NONE, iconSize);
    }

    /// Creates an SVGContainer with the specified icon and size.
    ///
    /// @param icon     the [SVG] icon to display
    /// @param iconSize the icon size
    public SVGContainer(SVG icon, double iconSize) {
        setIconSizeImpl(iconSize);
        setIcon(icon);
    }

    public double getIconSize() {
        return iconSize;
    }

    private void setIconSizeImpl(double newSize) {
        this.iconSize = newSize;
        SVG.setSize(path, newSize);
        if (tempPath != null)
            SVG.setSize(tempPath, newSize);
    }

    public void setIconSize(double newSize) {
        setIconSizeImpl(newSize);
        requestLayout();
    }

    /// Gets the currently displayed icon.
    public SVG getIcon() {
        return icon;
    }

    /// Sets the icon to display without animation.
    public void setIcon(SVG newIcon) {
        setIcon(newIcon, Duration.ZERO);
    }

    /// Sets the icon to display with a cross-fade animation.
    public void setIcon(SVG newIcon, Duration animationDuration) {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }

        SVG oldIcon = this.icon;
        this.icon = newIcon;

        if (animationDuration.equals(Duration.ZERO)) {
            path.setContent(newIcon.getPath());
            path.setOpacity(1);
            if (getChildren().size() != 1)
                getChildren().setAll(path);
        } else {
            if (tempPath == null) {
                tempPath = new SVGPath();
                tempPath.getStyleClass().add("svg");
                SVG.setSize(tempPath, iconSize);
            } else
                tempPath.setOpacity(1);

            tempPath.setContent(oldIcon.getPath());
            getChildren().setAll(path, tempPath);

            path.setOpacity(0);
            path.setContent(newIcon.getPath());

            timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(path.opacityProperty(), 0, Motion.LINEAR),
                            new KeyValue(tempPath.opacityProperty(), 1, Motion.LINEAR)
                    ),
                    new KeyFrame(animationDuration,
                            new KeyValue(path.opacityProperty(), 1, Motion.LINEAR),
                            new KeyValue(tempPath.opacityProperty(), 0, Motion.LINEAR)
                    )
            );
            timeline.setOnFinished(e -> {
                getChildren().setAll(path);
                timeline = null;
            });
            timeline.play();
        }
    }

    // Parent

    @Override
    public double prefWidth(double height) {
        return iconSize;
    }

    @Override
    public double prefHeight(double width) {
        return iconSize;
    }

    @Override
    public double minHeight(double width) {
        return iconSize;
    }

    @Override
    public double minWidth(double height) {
        return iconSize;
    }

    @Override
    protected void layoutChildren() {
        // Do nothing
    }

}
