// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies cached flow measurements and configurable wheel speed independently of user preferences.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class ScrollAdapterTest {
    /// Repeated wheel input extends measurements linearly rather than rescanning queued cells.
    @Test
    void variableFlowReusesMeasuredWheelRange() throws Exception {
        onFxThread(() -> {
            for (boolean vertical : new boolean @Unmodifiable []{true, false}) {
                for (int inputs : new int @Unmodifiable []{8, 16, 32}) {
                    int[] measurements = {0};
                    ListView<String> list = new ListView<>();
                    list.setOrientation(vertical ? Orientation.VERTICAL : Orientation.HORIZONTAL);
                    list.setCellFactory(view -> {
                        ListCell<String> cell = new ListCell<>();
                        cell.setPrefSize(vertical ? 100 : 24, vertical ? 24 : 100);
                        cell.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                        cell.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                        cell.indexProperty().addListener(observable -> measurements[0]++);
                        return cell;
                    });
                    for (int i = 0; i < 10_000; i++)
                        list.getItems().add("Cell " + i);
                    layout(list);
                    VirtualFlow<?> flow = (VirtualFlow<?>) requireNonNull(list.lookup(".virtual-flow"));
                    ScrollUtils.addSmoothScrolling(flow);
                    boolean backwards = inputs == 16;
                    if (backwards) {
                        flow.setPosition(1);
                        flow.layout();
                    }
                    measurements[0] = 0;
                    for (int i = 0; i < inputs; i++) {
                        double delta = backwards ? 40 : -40;
                        sendWheel(flow, vertical ? 0 : delta, vertical ? delta : 0);
                    }
                    int measured = measurements[0];
                    requireNonNull(ScrollUtils.controller(flow)).wheel.cancel();
                    assertTrue(measured <= inputs * 6 + 20, "Repeated input must only measure newly reached cells: " + measured);
                }
            }
            return true;
        });
    }

    /// Same-size model replacements and layout changes invalidate cached offscreen cell lengths.
    @Test
    void variableFlowInvalidatesMeasuredRange() throws Exception {
        onFxThread(() -> {
            ListView<Integer> list = new ListView<>();
            double[] scale = {1};
            list.setCellFactory(view -> new ListCell<>() {
                /// Sizes each row from its model value, including reused measurement cells.
                @Override
                protected void updateItem(@Nullable Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    double length = (item == null ? 24 : item) * scale[0];
                    setMinHeight(length);
                    setPrefHeight(length);
                    setMaxHeight(length);
                }
            });
            for (int i = 0; i < 30; i++)
                list.getItems().add(24);
            layout(list);
            VirtualFlow<?> flow = (VirtualFlow<?>) requireNonNull(list.lookup(".virtual-flow"));
            ScrollUtils.addSmoothScrolling(flow);
            ScrollController controller = requireNonNull(ScrollUtils.controller(flow));
            double initial = controller.limit(false, 10_000);
            list.getItems().set(29, 200);
            assertEquals(initial + 176, controller.limit(false, 10_000), 0.001);
            list.getItems().set(29, 24);
            assertEquals(initial, controller.limit(false, 10_000), 0.001);
            var replacement = FXCollections.observableArrayList(list.getItems());
            replacement.set(29, 120);
            list.setItems(replacement);
            assertEquals(initial + 96, controller.limit(false, 10_000), 0.001);
            replacement.set(29, 24);
            assertEquals(initial, controller.limit(false, 10_000), 0.001);
            flow.resize(flow.getWidth(), flow.getHeight() + 20);
            flow.layout();
            assertEquals(initial - 20, controller.limit(false, 10_000), 0.001);
            flow.setPosition(1);
            flow.layout();
            assertEquals(-(initial - 20), controller.limit(false, -10_000), 0.001);
            flow.setPosition(0);
            flow.layout();
            assertEquals(initial - 20, controller.limit(false, 10_000), 0.001);
            scale[0] = 2;
            list.refresh();
            list.layout();
            assertEquals(30 * 48 - flow.getHeight(), controller.limit(false, 10_000), 0.001);
            return true;
        });
    }

    /// Tree value changes invalidate offscreen measurements even when the expanded row count stays unchanged.
    @Test
    void treeFlowInvalidatesMeasuredRange() throws Exception {
        onFxThread(() -> {
            TreeItem<Integer> root = new TreeItem<>(24);
            root.setExpanded(true);
            for (int i = 0; i < 30; i++)
                root.getChildren().add(new TreeItem<>(24));
            TreeView<Integer> tree = new TreeView<>(root);
            tree.setShowRoot(false);
            tree.setCellFactory(view -> new TreeCell<>() {
                /// Sizes each tree row from its model value.
                @Override
                protected void updateItem(@Nullable Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    double length = item == null ? 24 : item;
                    setMinHeight(length);
                    setPrefHeight(length);
                    setMaxHeight(length);
                }
            });
            layout(tree);
            VirtualFlow<?> flow = (VirtualFlow<?>) requireNonNull(tree.lookup(".virtual-flow"));
            ScrollUtils.addSmoothScrolling(flow);
            ScrollController controller = requireNonNull(ScrollUtils.controller(flow));
            double initial = controller.limit(false, 10_000);
            root.getChildren().get(29).setValue(200);
            assertEquals(initial + 176, controller.limit(false, 10_000), 0.001);
            root.getChildren().get(29).setValue(24);
            assertEquals(initial, controller.limit(false, 10_000), 0.001);
            return true;
        });
    }

    /// Custom speed parsing remains testable independently of the test JVM's forced default.
    @Test
    void wheelSpeedSupportsCustomFactorsAndPercentages() {
        assertEquals(3, ScrollAdapter.readWheelSpeed(null));
        assertEquals(3, ScrollAdapter.readWheelSpeed(""));
        assertEquals(3, ScrollAdapter.readWheelSpeed("1"));
        assertEquals(3, ScrollAdapter.readWheelSpeed("100%"));
        assertEquals(1.5, ScrollAdapter.readWheelSpeed("0.5"));
        assertEquals(1.5, ScrollAdapter.readWheelSpeed("50%"));
        assertEquals(6, ScrollAdapter.readWheelSpeed("2"));
        assertEquals(6, ScrollAdapter.readWheelSpeed(" 200% "));
        assertEquals(3, ScrollAdapter.readWheelSpeed("invalid"));
        assertEquals(3, ScrollAdapter.readWheelSpeed("NaN"));
        assertEquals(3, ScrollAdapter.readWheelSpeed("Infinity"));
        assertEquals(3, ScrollAdapter.readWheelSpeed("0"));
        assertEquals(3, ScrollAdapter.readWheelSpeed("-50%"));
        assertEquals(0.0003, ScrollAdapter.readWheelSpeed("0.000001"), 1e-12);
    }

    /// Dispatches ordinary wheel pixels without advancing the animation clock.
    private static void sendWheel(Node target, double dx, double dy) {
        target.fireEvent(new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                false, false, false, false, false, false, dx, dy, dx, dy,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, new PickResult(target, 10, 10)));
    }

    /// Attaches a control to a scene and lays it out without displaying a window.
    private static void layout(Region node) {
        node.setManaged(false);
        Pane root = new Pane(node);
        new Scene(root, 300, 240);
        node.resize(300, 240);
        root.applyCss();
        root.layout();
        node.layout();
    }

    /// Runs an operation and propagates assertion failures from the JavaFX application thread.
    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
