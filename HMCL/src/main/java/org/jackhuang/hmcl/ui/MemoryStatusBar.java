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
import javafx.scene.control.Control;
import javafx.scene.control.SkinBase;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.hardware.PhysicalMemoryStatus;

import java.lang.ref.WeakReference;

/// @author Glavo
public final class MemoryStatusBar extends Control {

    private static final String DEFAULT_STYLE_CLASS = "memory-status-bar";

    public MemoryStatusBar() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    private final ReadOnlyObjectProperty<PhysicalMemoryStatus> memoryStatus = UpdateMemoryStatus.memoryStatusProperty();

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
        private static final int HEIGHT = 24;

        private final Rectangle memoryTotal;
        private final Rectangle memoryUsed;
        private final Rectangle memoryAllocate;

        private double contentWidth = -1;

        Skin(MemoryStatusBar control) {
            super(control);

            memoryTotal = new Rectangle();
            memoryTotal.getStyleClass().add("memory-total");

            memoryUsed = new Rectangle();
            memoryUsed.getStyleClass().add("memory-used");

            memoryAllocate = new Rectangle();
            memoryAllocate.getStyleClass().add("memory-allocate");

            this.getChildren().setAll(memoryTotal, memoryUsed, memoryAllocate);

            registerInvalidationListener(control.memoryStatusProperty(), it -> updateBar());
            registerInvalidationListener(control.memoryAllocatedProperty(), it -> updateBar());
        }

        private void updateBar() {
            if (contentWidth < 0) {
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

            // TODO
        }

        @Override
        protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
            memoryTotal.relocate(contentX, contentY);
            memoryUsed.relocate(contentX, contentY);
            memoryAllocate.relocate(contentX, contentY);

            this.contentWidth = contentWidth;

            memoryTotal.setWidth(contentWidth);
            memoryTotal.setHeight(contentHeight);
            memoryUsed.setHeight(contentHeight);
            memoryAllocate.setHeight(contentHeight);

            updateBar();
        }
    }

    private static final class UpdateMemoryStatus extends Thread {

        private static final int UPDATE_INTERVAL = 5000;

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
