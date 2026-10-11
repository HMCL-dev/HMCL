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
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Objects;
import java.util.function.Consumer;

/// Tracks content bounds independently of the stage's native frame and the root's shadow padding.
///
/// All access must occur on the JavaFX application thread. The scene and root must remain attached to the stage
/// until [#close()] is called. Normal bounds are sampled after layout, and once more before hiding or detaching.
/// Maximized bounds under [WindowState]'s policy, full-screen bounds, and iconified bounds are not reported
/// as normal bounds. Transparent macOS windows retain normal bounds despite native zoom notifications.
/// Minimum sizes are configured separately after showing or a display-scale or resizability change;
/// ordinary geometry tracking does not write window constraints.
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

    /// Whether the native minimum needs configuration once normal geometry has stopped changing between pulses.
    private boolean minimumSizePending;

    /// Whether this tracker has released its listeners.
    private boolean closed;

    /// Marks geometry for sampling after layout without assuming an order for stage and scene notifications.
    private final ChangeListener<Object> boundsListener = (observable, oldValue, newValue) -> invalidate();

    /// Compensates for native offsets discovered while creating the window peer.
    private final InvalidationListener offsetListener = observable -> restorePosition();

    /// Requests new native minimum dimensions when the display scale or resizability changes.
    private final ChangeListener<Object> minimumSizeListener = (observable, oldValue, newValue) -> invalidateMinimumSize();

    /// Establishes the normal restore rectangle before the native peer becomes visible.
    private final EventHandler<WindowEvent> showingHandler = event -> restoreBounds();

    /// Completes initial positioning and enables geometry sampling.
    private final EventHandler<WindowEvent> shownHandler = event -> {
        restorePosition();
        restoringPosition = false;
        shown = true;
        invalidateMinimumSize();
    };

    /// Samples geometry before JavaFX clears the hidden scene's native offsets.
    private final EventHandler<WindowEvent> hidingHandler = event -> {
        updateBounds();
        shown = false;
        restoringPosition = false;
    };

    /// Samples geometry and separately processes pending minimum-size configuration after layout.
    private final Runnable pulseListener = this::onPulse;

    /// The properties observed for geometry, state, and padding changes.
    private final ObservableValue<?> @Unmodifiable [] observed;

    /// The properties that can change the normal native frame dimensions.
    private final ObservableValue<?> @Unmodifiable [] minimumSizeObserved;

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
        observed = new ObservableValue<?>[] {
                stage.xProperty(), stage.yProperty(), stage.widthProperty(), stage.heightProperty(),
                stage.maximizedProperty(), stage.fullScreenProperty(), stage.iconifiedProperty(),
                scene.xProperty(), scene.yProperty(), scene.widthProperty(), scene.heightProperty(),
                root.paddingProperty()
        };
        for (ObservableValue<?> observable : observed) {
            observable.addListener(boundsListener);
        }
        scene.xProperty().addListener(offsetListener);
        scene.yProperty().addListener(offsetListener);
        minimumSizeObserved = new ObservableValue<?>[] {
                stage.outputScaleXProperty(), stage.outputScaleYProperty(), stage.resizableProperty()
        };
        for (ObservableValue<?> observable : minimumSizeObserved) {
            observable.addListener(minimumSizeListener);
        }
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
            invalidateMinimumSize();
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

    /// Schedules native minimum-size configuration independently of ordinary geometry sampling.
    private void invalidateMinimumSize() {
        minimumSizePending = true;
        invalidate();
    }

    /// Samples dirty geometry, then configures pending minimums on a subsequent quiet layout pulse.
    private void onPulse() {
        if (closed) {
            return;
        }
        if (dirty) {
            updateBounds();
            if (minimumSizePending && isNormal()) {
                Platform.requestNextPulse();
            }
        } else if (minimumSizePending && isNormal()) {
            updateMinimumSize();
        }
    }

    /// Returns whether the visible stage can supply normal window geometry.
    private boolean isNormal() {
        return shown && !restoringPosition && !stage.isIconified()
                && !WindowState.isMaximized(stage) && !stage.isFullScreen();
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
        if (WindowState.isMaximized(stage) || stage.isFullScreen()) {
            return;
        }

        normalBounds = new Rectangle2D(
                stage.getX() + scene.getX() + insets.getLeft(),
                stage.getY() + scene.getY() + insets.getTop(),
                Math.max(minWidth, width), Math.max(minHeight, height));
        saveBounds.accept(normalBounds);
    }

    /// Measures the normal native frame and applies content minimums including frame and shadow padding.
    private void updateMinimumSize() {
        // Stage and Scene sizes arrive separately. Only measure after pending geometry notifications
        // have been processed, never from an individual size listener or an ordinary resize pulse.
        boolean hasFrame = stage.getStyle() != StageStyle.TRANSPARENT && stage.getStyle() != StageStyle.UNDECORATED;
        double frameWidth = hasFrame ? Math.max(0, stage.getWidth() - scene.getWidth()) : 0;
        double frameHeight = hasFrame ? Math.max(0, stage.getHeight() - scene.getHeight()) : 0;
        minimumSizePending = false;
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
        for (ObservableValue<?> observable : observed) {
            observable.removeListener(boundsListener);
        }
        scene.xProperty().removeListener(offsetListener);
        scene.yProperty().removeListener(offsetListener);
        for (ObservableValue<?> observable : minimumSizeObserved) {
            observable.removeListener(minimumSizeListener);
        }
        scene.removePostLayoutPulseListener(pulseListener);
        stage.removeEventHandler(WindowEvent.WINDOW_SHOWING, showingHandler);
        stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, shownHandler);
        stage.removeEventHandler(WindowEvent.WINDOW_HIDING, hidingHandler);
    }
}
