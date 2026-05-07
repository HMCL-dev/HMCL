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

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.MathUtils;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.hardware.PhysicalMemoryStatus;

import java.lang.ref.WeakReference;

/// @author Glavo
public final class MemoryStatusBar extends Control {

    private static final String DEFAULT_STYLE_CLASS = "memory-status-bar";

    public MemoryStatusBar() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        setPrefWidth(200);
    }

    private final ReadOnlyObjectProperty<PhysicalMemoryStatus> memoryStatus = UpdateMemoryStatus.memoryStatusProperty();

    /// The memory status of the system.
    ///
    /// The property will be automatically updated.
    ///
    /// The property has no bean, because it may share between multiple MemoryStatusBar instances.
    public ReadOnlyObjectProperty<PhysicalMemoryStatus> memoryStatusProperty() {
        return memoryStatus;
    }

    public PhysicalMemoryStatus getMemoryStatus() {
        return memoryStatus.get();
    }

    private final DoubleProperty memoryAllocated = new SimpleDoubleProperty();

    public DoubleProperty memoryAllocatedProperty() {
        return memoryAllocated;
    }

    public double getMemoryAllocated() {
        return memoryAllocated.get();
    }

    public void setMemoryAllocated(double memoryAllocated) {
        this.memoryAllocated.set(memoryAllocated);
    }

    @Override
    protected SkinBase<MemoryStatusBar> createDefaultSkin() {
        return new Skin(this);
    }

    private static final class Skin extends SkinBase<MemoryStatusBar> {
        private static final int HEIGHT = 4;

        private final StackPane track;
        private final Rectangle memoryUsed;
        private final Rectangle memoryAllocate;

        private double contentWidth = -1;

        Skin(MemoryStatusBar control) {
            super(control);

            track = new StackPane();
            track.getStyleClass().add("track");
            track.setPadding(new Insets(HEIGHT, 0, 0, 0));

            memoryUsed = new Rectangle();
            memoryUsed.setManaged(false);
            memoryUsed.setArcWidth(HEIGHT);
            memoryUsed.setArcHeight(HEIGHT);
            memoryUsed.getStyleClass().add("memory-used");

            memoryAllocate = new Rectangle();
            memoryAllocate.setManaged(false);
            memoryAllocate.setArcWidth(HEIGHT);
            memoryAllocate.setArcHeight(HEIGHT);
            memoryAllocate.getStyleClass().add("memory-allocate");

            this.getChildren().setAll(track, memoryAllocate, memoryUsed);

            registerInvalidationListener(control.memoryStatusProperty(), it -> updateBar());
            registerInvalidationListener(control.memoryAllocatedProperty(), it -> updateBar());
        }

        private void updateBar() {
            if (contentWidth <= 0) {
                // not yet layout
                return;
            }

            PhysicalMemoryStatus status = getSkinnable().getMemoryStatus();
            double total = status.getTotal();
            double used = status.getUsed();
            double allocated = getSkinnable().getMemoryAllocated();

            if (total <= 0) {
                memoryUsed.setWidth(contentWidth);
                memoryAllocate.setWidth(contentWidth);
                return;
            }

            double usedWidth = MathUtils.clamp(contentWidth * (used / total), 0, contentWidth);
            double allocatedWidth = MathUtils.clamp(contentWidth * (allocated / total) + usedWidth, 0, contentWidth);

            memoryUsed.setWidth(usedWidth);
            memoryAllocate.setWidth(allocatedWidth);
        }

        @Override
        protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
            track.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
            memoryUsed.relocate(contentX, contentY);
            memoryAllocate.relocate(contentX, contentY);

            this.contentWidth = contentWidth;

            memoryUsed.setHeight(contentHeight);
            memoryAllocate.setHeight(contentHeight);

            updateBar();
        }

        @Override
        public double computeBaselineOffset(double topInset, double rightInset, double bottomInset, double leftInset) {
            return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
        }

        @Override
        protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
            return leftInset + track.prefWidth(height) + rightInset;
        }

        @Override
        protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
            return topInset + HEIGHT + bottomInset;
        }

        @Override
        protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
            return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
        }
    }

    private static final class UpdateMemoryStatus extends Thread {

        private static final int UPDATE_INTERVAL = 3000;

        @FXThread
        private static WeakReference<ObjectProperty<PhysicalMemoryStatus>> memoryStatusPropertyCache;

        @FXThread
        static ReadOnlyObjectProperty<PhysicalMemoryStatus> memoryStatusProperty() {
            if (memoryStatusPropertyCache != null) {
                var property = memoryStatusPropertyCache.get();
                if (property != null) {
                    return property;
                }
            }

            ObjectProperty<PhysicalMemoryStatus> property = new SimpleObjectProperty<>(PhysicalMemoryStatus.INVALID);
            memoryStatusPropertyCache = new WeakReference<>(property);
            new UpdateMemoryStatus(memoryStatusPropertyCache).start();
            return property;
        }

        private final WeakReference<ObjectProperty<PhysicalMemoryStatus>> memoryStatusPropertyRef;

        UpdateMemoryStatus(WeakReference<ObjectProperty<PhysicalMemoryStatus>> memoryStatusPropertyRef) {
            this.memoryStatusPropertyRef = memoryStatusPropertyRef;

            setName("UpdateMemoryStatus");
            setDaemon(true);
            setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run() {
            while (true) {
                PhysicalMemoryStatus status = SystemInfo.getPhysicalMemoryStatus();

                var memoryStatusProperty = memoryStatusPropertyRef.get();
                if (memoryStatusProperty == null)
                    return;

                if (Controllers.isStopped())
                    return;

                Platform.runLater(() -> memoryStatusProperty.set(status));

                try {
                    //noinspection BusyWait
                    Thread.sleep(UPDATE_INTERVAL);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
