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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Objects;
import java.util.function.Consumer;

/// Tracks content bounds independently of the stage's native frame and the root's shadow padding.
///
/// All access must occur on the JavaFX application thread. The scene and root must remain attached to the stage
/// until [#close()] is called. Normal bounds are sampled after layout, and once more before hiding or detaching.
/// Maximized, full-screen, and iconified bounds are not reported as normal bounds.
@NotNullByDefault
final class WindowBounds implements AutoCloseable {
    /// The stage whose outer geometry is managed.
    private final Stage stage;

    /// The scene providing client dimensions and offsets within the native window.
    private final Scene scene;

    /// The root whose padding reserves space for custom shadows.
    private final Region root;

    /// The padding used in the normal window state, even when maximized padding is empty.
    private final Insets normalInsets;

    /// The minimum content width in JavaFX screen coordinates.
    private final double minWidth;

    /// The minimum content height in JavaFX screen coordinates.
    private final double minHeight;

    /// Receives normal content bounds in JavaFX screen coordinates.
    private final Consumer<Rectangle2D> saveBounds;

    /// The latest normal content rectangle, retained through hiding and non-normal window states.
    private Rectangle2D normalBounds;

    /// The last native frame width observed with a normal client-size notification.
    private double frameWidth;

    /// The last native frame height observed with a normal client-size notification.
    private double frameHeight;

    /// The horizontal display scale at which the frame was last initialized.
    private double frameScaleX;

    /// The vertical display scale at which the frame was last initialized.
    private double frameScaleY;

    /// The content width excluding both native borders and custom shadow padding.
    private final ReadOnlyDoubleWrapper contentWidth = new ReadOnlyDoubleWrapper();

    /// The content height excluding both native borders and custom shadow padding.
    private final ReadOnlyDoubleWrapper contentHeight = new ReadOnlyDoubleWrapper();

    /// Whether the current show operation is still resolving native client offsets.
    private boolean restoringPosition;

    /// Whether native geometry can be sampled, including during WINDOW_HIDING.
    private boolean shown;

    /// Whether geometry must be sampled after the next layout pulse.
    private boolean dirty;

    /// Whether this tracker has released its listeners.
    private boolean closed;

    /// Marks geometry for sampling after stage and scene notifications have both completed.
    private final InvalidationListener boundsListener = observable -> invalidate();

    /// Compensates for native offsets discovered while creating the window peer.
    private final InvalidationListener offsetListener = observable -> restorePosition();

    /// Samples frame extents only when the corresponding native client dimension has been updated.
    private final InvalidationListener sceneSizeListener = this::updateFrameSize;

    /// Establishes the normal restore rectangle before the native peer becomes visible.
    private final EventHandler<WindowEvent> showingHandler = event -> restoreBounds();

    /// Completes initial positioning and enables geometry sampling.
    private final EventHandler<WindowEvent> shownHandler = event -> {
        restorePosition();
        restoringPosition = false;
        shown = true;
        initializeFrameSize();
        invalidate();
    };

    /// Samples geometry before JavaFX clears the hidden scene's native offsets.
    private final EventHandler<WindowEvent> hidingHandler = event -> {
        updateBounds();
        shown = false;
        restoringPosition = false;
    };

    /// Samples a coherent set of stage, scene, and padding values after layout.
    private final Runnable pulseListener = () -> {
        if (dirty) {
            updateBounds();
        }
    };

    /// The properties observed for geometry, state, padding, and display-scale changes.
    private final Observable @Unmodifiable [] observed;

    /// Initializes client sizing and installs listeners on an attached stage and scene.
    ///
    /// The root's current padding must be its normal-state shadow padding. Initial bounds exclude that padding
    /// and native decoration. [Stage#sizeToScene()] lets JavaFX determine the required native outer size.
    ///
    /// @param stage the stage with `root` installed as its scene root
    /// @param root the scene root, whose minimum and preferred sizes are managed by this tracker
    /// @param initialBounds the normal content rectangle to restore
    /// @param minWidth the minimum content width
    /// @param minHeight the minimum content height
    /// @param saveBounds the callback receiving sampled normal content bounds
    WindowBounds(Stage stage, Region root, Rectangle2D initialBounds,
                 double minWidth, double minHeight, Consumer<Rectangle2D> saveBounds) {
        this.stage = stage;
        this.scene = Objects.requireNonNull(stage.getScene());
        this.root = root;
        this.normalInsets = root.getPadding();
        this.normalBounds = initialBounds;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.saveBounds = saveBounds;
        // Enforce minimum content size on the stage, independently of the current page's layout minimum.
        root.setMinSize(0, 0);
        contentWidth.set(initialBounds.getWidth());
        contentHeight.set(initialBounds.getHeight());
        observed = new Observable[] {
                stage.xProperty(), stage.yProperty(), stage.widthProperty(), stage.heightProperty(),
                stage.maximizedProperty(), stage.fullScreenProperty(), stage.iconifiedProperty(),
                stage.outputScaleXProperty(), stage.outputScaleYProperty(),
                scene.xProperty(), scene.yProperty(), scene.widthProperty(), scene.heightProperty(),
                root.paddingProperty()
        };
        for (Observable observable : observed) {
            observable.addListener(boundsListener);
        }
        scene.xProperty().addListener(offsetListener);
        scene.yProperty().addListener(offsetListener);
        scene.widthProperty().addListener(sceneSizeListener);
        scene.heightProperty().addListener(sceneSizeListener);
        scene.addPostLayoutPulseListener(pulseListener);
        stage.addEventHandler(WindowEvent.WINDOW_SHOWING, showingHandler);
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, shownHandler);
        stage.addEventHandler(WindowEvent.WINDOW_HIDING, hidingHandler);
        stage.setMinWidth(minWidth + normalInsets.getLeft() + normalInsets.getRight());
        stage.setMinHeight(minHeight + normalInsets.getTop() + normalInsets.getBottom());
        restoreBounds();
        if (stage.isShowing()) {
            restoringPosition = false;
            shown = true;
            initializeFrameSize();
            invalidate();
        }
    }

    /// Returns the content width, excluding native borders and the root's current padding.
    ReadOnlyDoubleProperty contentWidthProperty() {
        return contentWidth.getReadOnlyProperty();
    }

    /// Returns the content height, excluding native borders and the root's current padding.
    ReadOnlyDoubleProperty contentHeightProperty() {
        return contentHeight.getReadOnlyProperty();
    }

    /// Requests one layout pulse to coalesce geometry notifications.
    private void invalidate() {
        dirty = true;
        Platform.requestNextPulse();
    }

    /// Requests the normal client size and starts compensating for native client offsets.
    private void restoreBounds() {
        restoringPosition = true;
        root.setPrefSize(normalBounds.getWidth() + normalInsets.getLeft() + normalInsets.getRight(),
                normalBounds.getHeight() + normalInsets.getTop() + normalInsets.getBottom());
        restorePosition();
        stage.sizeToScene();
    }

    /// Aligns the content origin while the native peer is being initialized.
    private void restorePosition() {
        if (restoringPosition) {
            stage.setX(normalBounds.getMinX() - scene.getX() - normalInsets.getLeft());
            stage.setY(normalBounds.getMinY() - scene.getY() - normalInsets.getTop());
        }
    }

    /// Updates content dimensions and saves normal bounds when native geometry is available.
    private void updateBounds() {
        dirty = false;
        if (!shown || restoringPosition || stage.isIconified()) {
            return;
        }

        Insets insets = root.getPadding();
        double width = scene.getWidth() - insets.getLeft() - insets.getRight();
        double height = scene.getHeight() - insets.getTop() - insets.getBottom();
        if (width <= 0 || height <= 0) {
            return;
        }
        contentWidth.set(width);
        contentHeight.set(height);
        if (stage.isMaximized() || stage.isFullScreen()) {
            return;
        }

        if (frameScaleX != stage.getOutputScaleX() || frameScaleY != stage.getOutputScaleY()) {
            initializeFrameSize();
        } else {
            updateMinimumSize();
        }

        normalBounds = new Rectangle2D(
                stage.getX() + scene.getX() + insets.getLeft(),
                stage.getY() + scene.getY() + insets.getTop(),
                Math.max(minWidth, width), Math.max(minHeight, height));
        saveBounds.accept(normalBounds);
    }

    /// Samples the initial frame after peer creation and establishes native minimum dimensions.
    private void initializeFrameSize() {
        if (!stage.isMaximized() && !stage.isFullScreen() && !stage.isIconified()) {
            frameWidth = Math.max(0, stage.getWidth() - scene.getWidth());
            frameHeight = Math.max(0, stage.getHeight() - scene.getHeight());
            frameScaleX = stage.getOutputScaleX();
            frameScaleY = stage.getOutputScaleY();
            updateMinimumSize();
        }
    }

    /// Samples the native frame extent corresponding to a changed client dimension.
    ///
    /// @param observable the scene width or height property updated by JavaFX
    private void updateFrameSize(Observable observable) {
        if (shown && !restoringPosition && !stage.isMaximized() && !stage.isFullScreen() && !stage.isIconified()) {
            if (observable == scene.widthProperty()) {
                frameWidth = Math.max(0, stage.getWidth() - scene.getWidth());
            } else {
                frameHeight = Math.max(0, stage.getHeight() - scene.getHeight());
            }
        }
    }

    /// Applies the normal content minimum with the most recently observed native frame extents.
    private void updateMinimumSize() {
        // Stage dimensions can already contain a resize request that the scene has not received yet.
        // Reuse frame extents captured with client notifications instead of subtracting those mixed sizes.
        stage.setMinWidth(minWidth + normalInsets.getLeft() + normalInsets.getRight() + frameWidth);
        stage.setMinHeight(minHeight + normalInsets.getTop() + normalInsets.getBottom() + frameHeight);
    }

    /// Saves the last available bounds and removes all listeners without hiding or detaching the stage.
    /// Repeated calls have no effect.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        updateBounds();
        closed = true;
        for (Observable observable : observed) {
            observable.removeListener(boundsListener);
        }
        scene.xProperty().removeListener(offsetListener);
        scene.yProperty().removeListener(offsetListener);
        scene.widthProperty().removeListener(sceneSizeListener);
        scene.heightProperty().removeListener(sceneSizeListener);
        scene.removePostLayoutPulseListener(pulseListener);
        stage.removeEventHandler(WindowEvent.WINDOW_SHOWING, showingHandler);
        stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, shownHandler);
        stage.removeEventHandler(WindowEvent.WINDOW_HIDING, hidingHandler);
    }
}
