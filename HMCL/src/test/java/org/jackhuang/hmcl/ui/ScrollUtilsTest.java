// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/// Exercises scrolling through real JavaFX event dispatch and laid-out control skins.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class ScrollUtilsTest {
    /// Touch deltas are immediate, independent on both axes, and respect custom value ranges.
    @Test
    void touchTracksPixels() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(1200, 1600);
            pane.setHmin(-2);
            pane.setHmax(3);
            pane.setHvalue(-2);
            pane.setVmin(10);
            pane.setVmax(30);
            pane.setVvalue(10);
            send(pane.getContent(), ScrollEvent.SCROLL, -75, -120, true, false, false);
            assertEquals(75, offset(pane, true), 0.001);
            assertEquals(120, offset(pane, false), 0.001);
            finishWheel(pane);
            assertEquals(120, offset(pane, false), 0.001);
            return true;
        });
    }

    /// Gesture phase markers identify even large, line-labelled trackpad deltas as continuous input.
    @Test
    void trackpadUsesGestureLifecycle() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL_STARTED, 0, 0, false, false, true);
            send(pane.getContent(), ScrollEvent.SCROLL, -24, -96, false, false, true);
            assertEquals(24, offset(pane, true), 0.001);
            assertEquals(96, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL_FINISHED, 0, 0, false, false, true);
            finishWheel(pane);
            assertEquals(96, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -72.5, false, false, false);
            assertEquals(168.5, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -40, false, false, true);
            assertEquals(208.5, offset(pane, false), 0.001);
            return true;
        });
    }

    /// Wheel distance follows the event magnitude and finishes without a generated fling.
    @Test
    void wheelPreservesDistanceAndStops() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -40, false, false, true);
            assertEquals(0, offset(pane, false), 0.001);
            finishWheel(pane);
            assertEquals(40, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -80, false, false, true);
            finishWheel(pane);
            assertEquals(120, offset(pane, false), 0.001);
            finishWheel(pane);
            assertEquals(120, offset(pane, false), 0.001);
            return true;
        });
    }

    /// Fractional text deltas caused by display scaling retain wheel animation and line distances.
    @Test
    void scaledWheelRemainsAnimated() throws Exception {
        onFxThread(() -> {
            for (double scale : new double @Unmodifiable []{1.25, 1.5, 2}) {
                ScrollPane pane = pane(800, 1600);
                sendScaledWheel(pane.getContent(), scale);
                assertEquals(0, offset(pane, true), 0.001);
                assertEquals(0, offset(pane, false), 0.001);
                finishWheel(pane);
                assertEquals(40 / scale, offset(pane, true), 0.001);
                assertEquals(40 / scale, offset(pane, false), 0.001);

                VirtualFlow<?> flow = flow(true);
                sendScaledWheel(flow, scale);
                assertEquals(0, flow.getPosition(), 0.001);
                finishWheel(flow);
                IndexedCell<?> first = requireNonNull(flow.getFirstVisibleCell());
                // Cell layout snaps the final displacement to a pixel boundary.
                assertEquals(72 / scale, first.getIndex() * 24 - first.getLayoutY(), 0.5);
            }
            return true;
        });
    }

    /// Layout with unchanged viewport dimensions preserves queued wheel movement and gesture state.
    @Test
    void layoutPreservesScrolling() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -80, false, false, true);
            pane.requestLayout();
            pane.layout();
            finishWheel(pane);
            assertEquals(80, offset(pane, false), 0.001);

            send(pane.getContent(), ScrollEvent.SCROLL_STARTED, 0, 0, false, false, true);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -96, false, false, true);
            pane.requestLayout();
            pane.layout();
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -96, false, false, true);
            assertEquals(272, offset(pane, false), 0.001);
            return true;
        });
    }

    /// A viewport resize still discards movement queued for the previous dimensions.
    @Test
    void viewportResizeCancelsWheel() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -80, false, false, true);
            pane.resize(320, 260);
            pane.layout();
            finishWheel(pane);
            assertEquals(0, offset(pane, false), 0.001);
            return true;
        });
    }

    /// Reversing direction discards the previous queued movement instead of fighting it.
    @Test
    void wheelReversalCancelsOldDirection() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            pane.setVvalue(0.5);
            double before = offset(pane, false);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -200, false, false, true);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, 80, false, false, true);
            finishWheel(pane);
            assertTrue(offset(pane, false) < before);
            return true;
        });
    }

    /// A mouse press cancels wheel movement and stale native inertia, but allows fresh input.
    @Test
    void pressingStopsScrolling() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -80, false, false, true);
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, false));
            finishWheel(pane);
            assertEquals(0, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -80, false, true, false);
            assertEquals(0, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -12, true, false, false);
            assertEquals(12, offset(pane, false), 0.001);
            return true;
        });
    }

    /// Synthesized mouse dragging must not reapply a touchscreen gesture through a pannable skin.
    @Test
    void touchDoesNotAlsoPanWithMouse() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            pane.setPannable(true);
            Node content = requireNonNull(pane.getContent());
            content.fireEvent(mouse(content, MouseEvent.MOUSE_PRESSED, true));
            send(content, ScrollEvent.SCROLL, 0, -80, true, false, false);
            content.fireEvent(mouse(content, MouseEvent.MOUSE_DRAGGED, true));
            assertEquals(80, offset(pane, false), 0.001);
            VirtualFlow<?> flow = flow(true);
            flow.setPannable(true);
            flow.fireEvent(mouse(flow, MouseEvent.MOUSE_PRESSED, true));
            send(flow, ScrollEvent.SCROLL, 0, -12, true, false, false);
            flow.fireEvent(mouse(flow, MouseEvent.MOUSE_DRAGGED, true));
            assertEquals(-12, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            return true;
        });
    }

    /// External position changes and detaching a pane prevent queued animation from moving it later.
    @Test
    void externalChangesCancelWheel() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -80, false, false, true);
            pane.setVvalue(0.5);
            finishWheel(pane);
            assertEquals(0.5, pane.getVvalue(), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -80, false, false, true);
            ((Pane) pane.getParent()).getChildren().clear();
            finishWheel(pane);
            assertEquals(0.5, pane.getVvalue(), 0.001);
            return true;
        });
    }

    /// Content may initially be absent, be replaced, or be removed without duplicate or stale handlers.
    @Test
    void contentLifecycleAndRepeatedInstallation() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = new ScrollPane();
            ScrollUtils.addSmoothScrolling(pane);
            ScrollUtils.addSmoothScrolling(pane);
            send(pane, ScrollEvent.SCROLL, 0, -20, true, false, false);
            Region oldContent = content(800, 1600);
            pane.setContent(oldContent);
            layout(pane);
            send(oldContent, ScrollEvent.SCROLL, 0, -20, true, false, false);
            assertEquals(20, offset(pane, false), 0.001);
            pane.setContent(content(800, 1600));
            pane.layout();
            pane.setVvalue(0);
            send(oldContent, ScrollEvent.SCROLL, 0, -40, true, false, false);
            assertEquals(0, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -40, true, false, false);
            assertEquals(40, offset(pane, false), 0.001);
            pane.setContent(null);
            finishWheel(pane);
            return true;
        });
    }

    /// Nested panes retain priority and hand new movement to the ancestor after reaching an edge.
    @Test
    void nestedPanesScrollFromInsideOut() throws Exception {
        onFxThread(() -> {
            ScrollPane inner = new ScrollPane(content(600, 900));
            inner.setPrefSize(200, 180);
            inner.setMaxSize(200, 180);
            Pane content = new Pane(inner);
            content.setMinSize(800, 1600);
            content.setPrefSize(800, 1600);
            ScrollPane outer = new ScrollPane(content);
            ScrollUtils.addSmoothScrolling(outer);
            ScrollUtils.addSmoothScrolling(inner);
            layout(outer);
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -40, true, false, false);
            assertEquals(40, offset(inner, false), 0.001);
            assertEquals(0, offset(outer, false), 0.001);
            inner.setVvalue(inner.getVmax());
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -40, true, false, false);
            assertEquals(40, offset(outer, false), 0.001);
            return true;
        });
    }

    /// Virtualized scrolling tracks pixels in either orientation and wheel distances respect cell height.
    @Test
    void virtualFlowSupportsTouchAndWheel() throws Exception {
        onFxThread(() -> {
            for (boolean vertical : new boolean @Unmodifiable []{true, false}) {
                VirtualFlow<?> flow = flow(vertical);
                send(flow, ScrollEvent.SCROLL, vertical ? 0 : -12, vertical ? -12 : 0, true, false, false);
                IndexedCell<?> first = requireNonNull(flow.getFirstVisibleCell());
                assertEquals(-12, vertical ? first.getLayoutY() : first.getLayoutX(), 0.001);
                flow.fireEvent(mouse(flow, MouseEvent.MOUSE_PRESSED, false));
                send(flow, ScrollEvent.SCROLL, 0, -40, false, false, true);
                finishWheel(flow);
                assertTrue(flow.getPosition() > 0);
                first = requireNonNull(flow.getFirstVisibleCell());
                double offset = first.getIndex() * 24 - (vertical ? first.getLayoutY() : first.getLayoutX());
                assertEquals(vertical ? 84 : 52, offset, 0.001);
            }
            return true;
        });
    }

    /// Native inertia has a bounded tail and does not leak through the original skin after it expires.
    @Test
    void nativeInertiaExpires() throws Exception {
        ScrollPane pane = onFxThread(() -> {
            ScrollPane result = pane(800, 1600);
            send(result.getContent(), ScrollEvent.SCROLL, 0, -40, true, false, false);
            send(result.getContent(), ScrollEvent.SCROLL_FINISHED, 0, 0, true, false, false);
            send(result.getContent(), ScrollEvent.SCROLL, 0, -20, true, true, false);
            assertEquals(60, offset(result, false), 0.001);
            return result;
        });
        TimeUnit.MILLISECONDS.sleep(250);
        onFxThread(() -> {
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -20, true, true, false);
            assertEquals(60, offset(pane, false), 0.001);
            return true;
        });
    }

    /// A diagonal gesture also scrolls wide virtualized rows before touch scrollbars become visible.
    @Test
    void virtualFlowTracksBothAxes() throws Exception {
        onFxThread(() -> {
            VirtualFlow<?> flow = flow(true, true);
            send(flow, ScrollEvent.SCROLL, -120, -12, true, false, false);
            ScrollBar bar = flow.getChildrenUnmodifiable().stream()
                    .filter(node -> node instanceof ScrollBar scrollBar && scrollBar.getOrientation() == Orientation.HORIZONTAL)
                    .map(ScrollBar.class::cast).findFirst().orElseThrow();
            assertEquals(120, bar.getValue(), 0.001);
            assertEquals(-12, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            return true;
        });
    }

    /// Creates a fixed-size scroll surface, avoiding layout-dependent content resizing.
    private static Region content(double width, double height) {
        Region result = new Region();
        result.setMinSize(width, height);
        result.setPrefSize(width, height);
        return result;
    }

    /// Creates an installed pane with a real skin and overflowing content.
    private static ScrollPane pane(double width, double height) {
        ScrollPane result = new ScrollPane(content(width, height));
        ScrollUtils.addSmoothScrolling(result);
        layout(result);
        return result;
    }

    /// Creates a laid-out flow with uniform cells for pixel displacement assertions.
    private static VirtualFlow<?> flow(boolean vertical) {
        return flow(vertical, false);
    }

    /// Creates a flow with optional cross-axis overflow.
    private static VirtualFlow<?> flow(boolean vertical, boolean wide) {
        ListView<String> list = new ListView<>();
        list.setOrientation(vertical ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        list.setFixedCellSize(24);
        for (int i = 0; i < 100; i++)
            list.getItems().add("Cell " + i + (wide ? " content".repeat(100) : ""));
        layout(list);
        VirtualFlow<?> result = (VirtualFlow<?>) requireNonNull(list.lookup(".virtual-flow"));
        ScrollUtils.addSmoothScrolling(result);
        ScrollUtils.addSmoothScrolling(result);
        return result;
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

    /// Converts pane values back to pixel displacement for assertions.
    private static double offset(ScrollPane pane, boolean horizontal) {
        Node content = requireNonNull(pane.getContent());
        return horizontal
                ? (pane.getHvalue() - pane.getHmin()) / (pane.getHmax() - pane.getHmin())
                * (content.getLayoutBounds().getWidth() - pane.getViewportBounds().getWidth())
                : (pane.getVvalue() - pane.getVmin()) / (pane.getVmax() - pane.getVmin())
                * (content.getLayoutBounds().getHeight() - pane.getViewportBounds().getHeight());
    }

    /// Dispatches a platform-style scroll event with optional line units and gesture flags.
    private static void send(Node target, EventType<ScrollEvent> type, double dx, double dy,
                             boolean direct, boolean inertia, boolean lines) {
        target.fireEvent(new ScrollEvent(type, 10, 10, 10, 10, false, false, false, false,
                direct, inertia, dx, dy, dx, dy, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                lines ? ScrollEvent.VerticalTextScrollUnits.LINES : ScrollEvent.VerticalTextScrollUnits.NONE,
                lines ? Math.signum(dy) * 3 : 0, direct ? 1 : 0, new PickResult(target, 10, 10)));
    }

    /// Dispatches wheel input whose pixel and text deltas have been divided by the display scale.
    private static void sendScaledWheel(Node target, double scale) {
        target.fireEvent(new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                false, false, false, false, false, false,
                -40 / scale, -40 / scale, -40 / scale, -40 / scale,
                ScrollEvent.HorizontalTextScrollUnits.CHARACTERS, -3 / scale,
                ScrollEvent.VerticalTextScrollUnits.LINES, -3 / scale,
                0, new PickResult(target, 10, 10)));
    }

    /// Creates mouse input for interrupting animation or checking synthesized touch compatibility.
    private static MouseEvent mouse(Node target, EventType<MouseEvent> type, boolean synthesized) {
        double position = type == MouseEvent.MOUSE_DRAGGED ? -20 : 10;
        return new MouseEvent(type, position, position, position, position, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, synthesized, false, true,
                new PickResult(target, position, position));
    }

    /// Advances installed animation timers beyond their end while keeping tests independent of pulse timing.
    private static void finishWheel(Node node) {
        for (Object value : node.getProperties().values()) {
            if (value instanceof AnimationTimer timer)
                timer.handle(System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
        }
    }

    /// Runs an operation and propagates assertion failures from the JavaFX application thread.
    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
