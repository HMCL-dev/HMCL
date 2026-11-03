// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.event.EventDispatcher;
import javafx.event.EventType;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.skin.ScrollPaneSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.FloatScrollBarSkin;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/// Exercises scrolling through real JavaFX event dispatch and laid-out control skins.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
@Isolated("Temporarily supplies settings while initializing the floating skin's animation policy")
final class ScrollUtilsTest {
    /// Initializes skin animation policy using temporary in-memory settings without accessing persisted settings.
    @BeforeAll
    static void initializeAnimationPolicy() throws Exception {
        Field field = SettingsManager.class.getDeclaredField("launcherSettings");
        field.setAccessible(true);
        @Nullable Object previous = field.get(null);
        if (previous == null)
            field.set(null, new LauncherSettings());
        try {
            AnimationUtils.init();
        } finally {
            field.set(null, previous);
        }
    }

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
            send(pane.getContent(), ScrollEvent.SCROLL_STARTED, 0, 0, false, false, false);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -72.5, false, false, false);
            assertEquals(168.5, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -40, false, false, true);
            assertEquals(208.5, offset(pane, false), 0.001);
            return true;
        });
    }

    /// Horizontal lists map indirect vertical gestures to their length, while touch remains directional.
    @Test
    void horizontalFlowMapsTrackpadGestures() throws Exception {
        onFxThread(() -> {
            VirtualFlow<?> flow = flow(false);
            send(flow, ScrollEvent.SCROLL_STARTED, 0, 0, false, false, false);
            send(flow, ScrollEvent.SCROLL, 0, -60, false, false, false);
            IndexedCell<?> first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(60, first.getIndex() * 24 - first.getLayoutX(), 0.001);
            send(flow, ScrollEvent.SCROLL_FINISHED, 0, 0, false, false, false);
            send(flow, ScrollEvent.SCROLL, 0, -20, false, true, false);
            first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(80, first.getIndex() * 24 - first.getLayoutX(), 0.001);

            VirtualFlow<?> touch = flow(false);
            send(touch, ScrollEvent.SCROLL_STARTED, 0, 0, true, false, false);
            send(touch, ScrollEvent.SCROLL, 0, -60, true, false, false);
            assertEquals(0, touch.getPosition(), 0.001);
            send(touch, ScrollEvent.SCROLL, -60, 0, true, false, false);
            first = requireNonNull(touch.getFirstVisibleCell());
            assertEquals(60, first.getIndex() * 24 - first.getLayoutX(), 0.001);
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

    /// Multiple wheel inputs share the next animation frame without losing accepted distance.
    @Test
    void wheelBurstsWaitForAnimationFrame() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            VirtualFlow<?> flow = flow(true);
            for (int i = 0; i < 3; i++) {
                send(pane.getContent(), ScrollEvent.SCROLL, 0, -40, false, false, true);
                send(flow, ScrollEvent.SCROLL, 0, -40, false, false, true);
            }
            assertEquals(0, offset(pane, false), 0.001);
            assertEquals(0, flow.getPosition(), 0.001);
            finishWheel(pane);
            finishWheel(flow);
            assertEquals(120, offset(pane, false), 0.001);
            IndexedCell<?> first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(216, first.getIndex() * 24 - first.getLayoutY(), 0.001);
            return true;
        });
    }

    /// Unmarked pixel-only wheel events, including GTK input, are animated after a finished gesture too.
    @Test
    void pixelOnlyWheelRemainsAnimated() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -40, false, false, false);
            assertEquals(0, offset(pane, false), 0.001);
            finishWheel(pane);
            assertEquals(40, offset(pane, false), 0.001);

            send(pane.getContent(), ScrollEvent.SCROLL_STARTED, 0, 0, false, false, false);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -12, false, false, false);
            assertEquals(52, offset(pane, false), 0.001);
            send(pane.getContent(), ScrollEvent.SCROLL_FINISHED, 0, 0, false, false, false);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -40, false, false, false);
            assertEquals(52, offset(pane, false), 0.001);
            finishWheel(pane);
            assertEquals(92, offset(pane, false), 0.001);

            VirtualFlow<?> flow = flow(true);
            send(flow, ScrollEvent.SCROLL, 0, -40, false, false, false);
            assertEquals(0, flow.getPosition(), 0.001);
            finishWheel(flow);
            IndexedCell<?> first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(40, first.getIndex() * 24 - first.getLayoutY(), 0.001);
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

    /// Reversing at an edge cancels queued movement even before the first animation pulse.
    @Test
    void wheelReversalAtBoundaryCancelsQueue() throws Exception {
        onFxThread(() -> {
            for (boolean end : new boolean @Unmodifiable []{false, true}) {
                double delta = end ? 80 : -80;
                ScrollPane pane = pane(800, 1600);
                pane.setHvalue(end ? 1 : 0);
                pane.setVvalue(end ? 1 : 0);
                double x = offset(pane, true);
                double y = offset(pane, false);
                send(pane.getContent(), ScrollEvent.SCROLL, delta, delta, false, false, false);
                send(pane.getContent(), ScrollEvent.SCROLL, -delta, -delta, false, false, false);
                finishWheel(pane);
                assertEquals(x, offset(pane, true), 0.001);
                assertEquals(y, offset(pane, false), 0.001);

                VirtualFlow<?> flow = flow(true);
                flow.setPosition(end ? 1 : 0);
                flow.layout();
                send(flow, ScrollEvent.SCROLL, 0, delta, false, false, false);
                send(flow, ScrollEvent.SCROLL, 0, -delta, false, false, false);
                finishWheel(flow);
                assertEquals(end ? 1 : 0, flow.getPosition(), 0.001);
            }
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL, -80, -80, false, false, false);
            send(pane.getContent(), ScrollEvent.SCROLL, 80, 0, false, false, false);
            finishWheel(pane);
            assertEquals(0, offset(pane, true), 0.001);
            assertEquals(80, offset(pane, false), 0.001);
            return true;
        });
    }

    /// A reversal handed to an ancestor cannot leave the inner pane moving the opposite way.
    @Test
    void nestedWheelReversalCancelsInnerQueue() throws Exception {
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
            outer.setVvalue(0.5);
            double before = offset(outer, false);
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -80, false, false, false);
            send(inner.getContent(), ScrollEvent.SCROLL, 0, 80, false, false, false);
            finishWheel(inner);
            finishWheel(outer);
            assertEquals(0, offset(inner, false), 0.001);
            assertEquals(before - 80, offset(outer, false), 0.001);
            return true;
        });
    }

    /// Descendant consumption cannot hide a wheel reversal from an ancestor's queued animation.
    @Test
    void nestedWheelReversalCancelsOuterQueue() throws Exception {
        onFxThread(() -> {
            for (boolean horizontal : new boolean @Unmodifiable []{false, true}) {
                ScrollPane @Unmodifiable [] panes = nestedPanes();
                ScrollPane inner = panes[0];
                ScrollPane outer = panes[1];
                inner.setHvalue(1);
                inner.setVvalue(1);
                double before = offset(inner, horizontal);
                send(inner.getContent(), ScrollEvent.SCROLL, horizontal ? -80 : 0, horizontal ? 0 : -80,
                        false, false, false);
                send(inner.getContent(), ScrollEvent.SCROLL, horizontal ? 80 : 0, horizontal ? 0 : 80,
                        false, false, false);
                finishWheel(inner);
                finishWheel(outer);
                assertEquals(before - 80, offset(inner, horizontal), 0.001);
                assertEquals(0, offset(outer, horizontal), 0.001);
            }
            return true;
        });
    }

    /// A pane already queued to an edge passes the next wheel input to its ancestor before a pulse.
    @Test
    void nestedWheelHandsOffAfterQueueReachesEdge() throws Exception {
        onFxThread(() -> {
            for (boolean horizontal : new boolean @Unmodifiable []{false, true}) {
                for (boolean end : new boolean @Unmodifiable []{false, true}) {
                    ScrollPane @Unmodifiable [] panes = nestedPanes();
                    ScrollPane inner = panes[0];
                    ScrollPane outer = panes[1];
                    inner.setHvalue(1);
                    inner.setVvalue(1);
                    double size = offset(inner, horizontal);
                    double value = end ? 1 - 10 / size : 10 / size;
                    if (horizontal)
                        inner.setHvalue(value);
                    else
                        inner.setVvalue(value);
                    outer.setHvalue(0.5);
                    outer.setVvalue(0.5);
                    double before = offset(outer, horizontal);
                    double delta = end ? -80 : 80;
                    for (int i = 0; i < 2; i++)
                        send(inner.getContent(), ScrollEvent.SCROLL, horizontal ? delta : 0, horizontal ? 0 : delta,
                                false, false, false);
                    finishWheel(inner);
                    finishWheel(outer);
                    assertEquals(end ? size : 0, offset(inner, horizontal), 0.001);
                    assertEquals(before - delta, offset(outer, horizontal), 0.001);
                }
            }
            return true;
        });
    }

    /// A nested virtual list also reserves the remaining pixels when its boundary cells are visible.
    @Test
    void nestedFlowHandsOffAfterQueueReachesEdge() throws Exception {
        onFxThread(() -> {
            for (boolean end : new boolean @Unmodifiable []{false, true}) {
                ListView<String> list = new ListView<>();
                list.setFixedCellSize(24);
                list.setPrefSize(200, 180);
                for (int i = 0; i < 100; i++)
                    list.getItems().add("Cell " + i);
                Pane content = new Pane(list);
                content.setMinSize(800, 1600);
                content.setPrefSize(800, 1600);
                ScrollPane outer = new ScrollPane(content);
                ScrollUtils.addSmoothScrolling(outer);
                layout(outer);
                VirtualFlow<?> flow = (VirtualFlow<?>) requireNonNull(list.lookup(".virtual-flow"));
                ScrollUtils.addSmoothScrolling(flow);
                flow.setPosition(end ? 1 : 0);
                flow.layout();
                // Touch skins do not scroll hidden bars until input reveals them.
                for (Node child : flow.getChildrenUnmodifiable()) {
                    if (child instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL)
                        bar.setVisible(true);
                }
                assertEquals(end ? -10 : 10, flow.scrollPixels(end ? -10 : 10), 0.001);
                outer.setVvalue(0.5);
                double before = offset(outer, false);
                double delta = end ? -80 : 80;
                for (int i = 0; i < 2; i++)
                    send(flow, ScrollEvent.SCROLL, 0, delta, false, false, false);
                finishWheel(flow);
                finishWheel(outer);
                assertEquals(end ? 1 : 0, flow.getPosition(), 0.001);
                assertEquals(before - delta, offset(outer, false), 0.001);
            }
            return true;
        });
    }

    /// A flow observes direct input consumed by its nested pane and still delivers child mouse drags.
    @Test
    void nestedTouchDoesNotAlsoPanOuterFlow() throws Exception {
        onFxThread(() -> {
            VirtualFlow<?> flow = flow(true);
            flow.setFixedCellSize(180);
            flow.setPannable(true);
            flow.layout();
            IndexedCell<?> cell = requireNonNull(flow.getFirstVisibleCell());
            ScrollPane inner = new ScrollPane(content(800, 1600));
            inner.setPrefSize(200, 120);
            inner.setMaxSize(200, 120);
            inner.setPannable(true);
            ScrollUtils.addSmoothScrolling(inner);
            cell.setGraphic(inner);
            cell.applyCss();
            cell.layout();
            inner.layout();
            Node target = requireNonNull(inner.getContent());
            int[] dragged = {0};
            target.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> dragged[0]++);
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, 50, 50));
            send(target, ScrollEvent.SCROLL, 0, -20, true, false, false);
            assertEquals(20, offset(inner, false), 0.001);
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 50, 30));
            assertEquals(0, flow.getPosition(), 0.001);
            assertEquals(20, offset(inner, false), 0.001);
            assertEquals(1, dragged[0]);
            return true;
        });
    }

    /// Early outer panning is eligible only for the first direct event, even if an inner pane consumes it.
    @Test
    void nestedTouchDiscardsConsumedPanningCredit() throws Exception {
        onFxThread(() -> {
            VirtualFlow<?> flow = flow(true);
            flow.setFixedCellSize(180);
            flow.setPannable(true);
            flow.layout();
            IndexedCell<?> cell = requireNonNull(flow.getFirstVisibleCell());
            ScrollPane inner = new ScrollPane(content(800, 1600));
            inner.setPrefSize(200, 120);
            inner.setMaxSize(200, 120);
            ScrollUtils.addSmoothScrolling(inner);
            cell.setGraphic(inner);
            cell.applyCss();
            cell.layout();
            inner.layout();
            inner.setVvalue(1);
            double size = offset(inner, false);
            inner.setVvalue(1 - 7 / size);
            Node target = requireNonNull(inner.getContent());
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, 50, 50));
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 50, 45));
            assertEquals(-5, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            send(target, ScrollEvent.SCROLL, 0, -12, true, false, false);
            assertEquals(1, inner.getVvalue(), 0.001);
            send(target, ScrollEvent.SCROLL, 0, -2, true, false, false);
            assertEquals(-7, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            return true;
        });
    }

    /// A nested dispatch cannot borrow or erase the panning credit of the direct event already in progress.
    @Test
    void panningCreditIsIsolatedDuringReentrantScroll() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            pane.setPannable(true);
            Node content = requireNonNull(pane.getContent());
            content.fireEvent(mouseAt(content, MouseEvent.MOUSE_PRESSED, true, 50, 50));
            content.fireEvent(mouseAt(content, MouseEvent.MOUSE_DRAGGED, true, 50, 45));
            assertEquals(5, offset(pane, false), 0.001);
            Node viewport = requireNonNull(pane.lookup(".viewport"));
            int[] captured = {0};
            viewport.addEventFilter(ScrollEvent.SCROLL, event -> {
                if (++captured[0] == 1)
                    send(content, ScrollEvent.SCROLL, 0, -2, true, false, false);
            });
            send(content, ScrollEvent.SCROLL, 0, -12, true, false, false);
            assertEquals(14, offset(pane, false), 0.001);
            send(content, ScrollEvent.SCROLL, 0, -3, true, false, false);
            assertEquals(17, offset(pane, false), 0.001);
            return true;
        });
    }

    /// The inner consumer deducts native movement already applied by its outer virtual flow.
    @Test
    void nestedTouchSharesAncestorPanningCredit() throws Exception {
        onFxThread(() -> {
            VirtualFlow<?> flow = flow(true);
            flow.setFixedCellSize(180);
            flow.setPannable(true);
            flow.layout();
            IndexedCell<?> cell = requireNonNull(flow.getFirstVisibleCell());
            ScrollPane inner = new ScrollPane(content(800, 1600));
            inner.setPrefSize(200, 120);
            inner.setMaxSize(200, 120);
            ScrollUtils.addSmoothScrolling(inner);
            cell.setGraphic(inner);
            cell.applyCss();
            cell.layout();
            inner.layout();
            Node target = requireNonNull(inner.getContent());
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, 50, 50));
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 50, 45));
            assertEquals(-5, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            send(target, ScrollEvent.SCROLL, 0, -12, true, false, false);
            assertEquals(7, offset(inner, false), 0.001);
            assertEquals(-5, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            send(target, ScrollEvent.SCROLL, 0, -2, true, false, false);
            assertEquals(9, offset(inner, false), 0.001);

            inner.setVvalue(1);
            flow.setPosition(0);
            flow.layout();
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, 50, 50));
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 50, 45));
            assertEquals(-5, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            send(target, ScrollEvent.SCROLL, 0, -12, true, false, false);
            assertEquals(1, inner.getVvalue(), 0.001);
            assertEquals(-12, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            return true;
        });
    }

    /// Thumb dragging preserves different grab offsets and uses only the thumb's available travel.
    @Test
    void floatingScrollbarPreservesTouchGrabOffset() throws Exception {
        onFxThread(() -> {
            for (Orientation orientation : Orientation.values()) {
                for (double grab : new double @Unmodifiable []{0.2, 0.5, 0.8}) {
                    ScrollBar bar = new ScrollBar();
                    bar.setOrientation(orientation);
                    bar.setMin(10);
                    bar.setMax(30);
                    bar.setVisibleAmount(10);
                    bar.setValue(10);
                    bar.setSkin(new FloatScrollBarSkin(bar));
                    layout(bar);
                    boolean vertical = orientation == Orientation.VERTICAL;
                    bar.resize(vertical ? 12 : 208, vertical ? 208 : 12);
                    bar.layout();
                    Node thumb = requireNonNull(bar.lookup(".thumb"));
                    Node track = requireNonNull(bar.lookup(".track"));
                    assertEquals(200, vertical ? track.getBoundsInLocal().getHeight() : track.getBoundsInLocal().getWidth(), 0.001);
                    assertEquals(100, vertical ? thumb.getBoundsInLocal().getHeight() : thumb.getBoundsInLocal().getWidth(), 0.001);
                    Point2D press = thumb.localToScene(
                            thumb.getBoundsInLocal().getMinX() + thumb.getBoundsInLocal().getWidth() * grab,
                            thumb.getBoundsInLocal().getMinY() + thumb.getBoundsInLocal().getHeight() * grab);
                    thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, true, press.getX(), press.getY()));
                    sendDirectAt(thumb, press.getX() + (vertical ? 0 : 5), press.getY() + (vertical ? 5 : 0));
                    assertEquals(11, bar.getValue(), 0.001);
                    sendDirectAt(thumb, press.getX() + (vertical ? 0 : 10), press.getY() + (vertical ? 10 : 0));
                    assertEquals(12, bar.getValue(), 0.001);
                    sendDirectAt(thumb, press.getX(), press.getY());
                    assertEquals(10, bar.getValue(), 0.001);
                    thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_RELEASED, true, press.getX(), press.getY()));
                    send(thumb, ScrollEvent.SCROLL_FINISHED, 0, 0, true, false, false);
                    send(thumb, ScrollEvent.SCROLL, 5, 5, true, true, false);
                    assertEquals(10, bar.getValue(), 0.001);
                    bar.setValue(14);
                    Point2D nextPress = thumb.localToScene(thumb.getBoundsInLocal().getCenterX(), thumb.getBoundsInLocal().getCenterY());
                    thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, true, nextPress.getX(), nextPress.getY()));
                    sendDirectAt(thumb, nextPress.getX() + (vertical ? 0 : 5), nextPress.getY() + (vertical ? 5 : 0));
                    assertEquals(15, bar.getValue(), 0.001);
                }
            }
            return true;
        });
    }

    /// A thumb press keeps its grab offset when gesture recognition picks the track as its target.
    @Test
    void floatingScrollbarPreservesGrabWhenGestureTargetsTrack() throws Exception {
        onFxThread(() -> {
            for (Orientation orientation : Orientation.values()) {
                for (double initial : new double @Unmodifiable []{10, 14}) {
                    ScrollBar bar = new ScrollBar();
                    bar.setOrientation(orientation);
                    bar.setMin(10);
                    bar.setMax(30);
                    bar.setVisibleAmount(10);
                    bar.setValue(initial);
                    bar.setSkin(new FloatScrollBarSkin(bar));
                    layout(bar);
                    boolean vertical = orientation == Orientation.VERTICAL;
                    bar.resize(vertical ? 12 : 208, vertical ? 208 : 12);
                    bar.layout();
                    Node thumb = requireNonNull(bar.lookup(".thumb"));
                    Node track = requireNonNull(bar.lookup(".track"));
                    Point2D press = thumb.localToScene(
                            thumb.getBoundsInLocal().getMinX() + thumb.getBoundsInLocal().getWidth() * (vertical ? 0.5 : 0.98),
                            thumb.getBoundsInLocal().getMinY() + thumb.getBoundsInLocal().getHeight() * (vertical ? 0.98 : 0.5));
                    thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, true, press.getX(), press.getY()));
                    double x = press.getX() + (vertical ? 0 : 5);
                    double y = press.getY() + (vertical ? 5 : 0);
                    assertFalse(thumb.contains(thumb.sceneToLocal(x, y)));
                    sendDirectAt(track, ScrollEvent.SCROLL_STARTED, x, y);
                    sendDirectAt(track, x, y);
                    assertEquals(initial + 1, bar.getValue(), 0.001);
                    sendDirectAt(track, x + (vertical ? 0 : 5), y + (vertical ? 5 : 0));
                    assertEquals(initial + 2, bar.getValue(), 0.001);
                    sendDirectAt(track, ScrollEvent.SCROLL_FINISHED, x, y);

                    Point2D trackPress = track.localToScene(vertical ? 3 : 150, vertical ? 150 : 3);
                    sendDirectAt(track, ScrollEvent.SCROLL_STARTED, trackPress.getX(), trackPress.getY());
                    sendDirectAt(track, trackPress.getX(), trackPress.getY());
                    assertEquals(25, bar.getValue(), 0.001);
                    sendDirectAt(track, ScrollEvent.SCROLL_FINISHED, trackPress.getX(), trackPress.getY());
                }
            }
            return true;
        });
    }

    /// The application's floating skin responds to direct input on the thumb and track in both orientations.
    @Test
    void floatingScrollbarHandlesTouch() throws Exception {
        ScrollPane @Unmodifiable [] panes = onFxThread(() -> {
            ScrollPane @Unmodifiable [] result = {
                    new ScrollPane(content(800, 1600)), new ScrollPane(content(800, 1600))
            };
            for (ScrollPane pane : result) {
                pane.setSkin(new ScrollPaneSkin(pane));
                // Install the application skin before CSS creates native skins, whose handlers survive disposal on JavaFX 17.
                for (Node node : pane.lookupAll(".scroll-bar")) {
                    ScrollBar bar = (ScrollBar) node;
                    bar.setSkin(new FloatScrollBarSkin(bar));
                }
                ScrollUtils.addSmoothScrolling(pane);
                layout(pane);
                send(pane.getContent(), ScrollEvent.SCROLL, 0, -1, false, false, true);
                finishWheel(pane);
            }
            return result;
        });
        // Touch skins reveal and size overlay scrollbars on the next animation pulse.
        TimeUnit.MILLISECONDS.sleep(100);
        onFxThread(() -> {
            for (Orientation orientation : Orientation.values()) {
                ScrollPane pane = panes[orientation == Orientation.HORIZONTAL ? 0 : 1];
                pane.requestLayout();
                pane.layout();
                ScrollBar bar = pane.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                        .filter(node -> node.getOrientation() == orientation).findFirst().orElseThrow();
                assertInstanceOf(FloatScrollBarSkin.class, bar.getSkin());
                assertTrue(bar.isVisible());
                Node thumb = requireNonNull(bar.lookup(".thumb"));
                Node track = requireNonNull(bar.lookup(".track"));
                for (Node target : new Node @Unmodifiable []{thumb, track}) {
                    bar.setValue(bar.getMin());
                    Point2D press = thumb.localToScene(thumb.getBoundsInLocal().getCenterX(), thumb.getBoundsInLocal().getCenterY());
                    target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, press.getX(), press.getY()));
                    Point2D point = track.localToScene(track.getBoundsInLocal().getWidth() * 0.75,
                            track.getBoundsInLocal().getHeight() * 0.75);
                    target.fireEvent(new ScrollEvent(ScrollEvent.SCROLL,
                            point.getX(), point.getY(), point.getX(), point.getY(),
                            false, false, false, false, true, false, 4, 4, 4, 4,
                            ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                            ScrollEvent.VerticalTextScrollUnits.NONE, 0, 1,
                            new PickResult(target, point.getX(), point.getY())));
                    double length = orientation == Orientation.VERTICAL ? track.getBoundsInLocal().getHeight() : track.getBoundsInLocal().getWidth();
                    double thumbLength = orientation == Orientation.VERTICAL ? thumb.getBoundsInLocal().getHeight() : thumb.getBoundsInLocal().getWidth();
                    double fraction = target == thumb ? (0.75 * length - thumbLength / 2) / (length - thumbLength) : 0.75;
                    double expected = bar.getMin() + MathUtils.clamp(fraction, 0, 1) * (bar.getMax() - bar.getMin());
                    assertEquals(expected, bar.getValue(), 0.001);
                    assertTrue(orientation == Orientation.VERTICAL ? pane.getVvalue() > 0 : pane.getHvalue() > 0);
                    target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, point.getX(), point.getY()));
                    assertEquals(expected, bar.getValue(), 0.001);
                    target.fireEvent(mouseAt(target, MouseEvent.MOUSE_RELEASED, true, point.getX(), point.getY()));
                    sendDirectAt(target, ScrollEvent.SCROLL_FINISHED, point.getX(), point.getY());
                }
            }
            return true;
        });
    }

    /// Floating touch handlers respect custom ranges and bindings, leave wheels alone, and detach on disposal.
    @Test
    void floatingScrollbarTouchLifecycle() throws Exception {
        onFxThread(() -> {
            ScrollBar bar = new ScrollBar();
            bar.setMin(10);
            bar.setMax(30);
            bar.setVisibleAmount(4);
            bar.setValue(10);
            FloatScrollBarSkin skin = new FloatScrollBarSkin(bar);
            bar.setSkin(skin);
            layout(bar);
            Node track = requireNonNull(bar.lookup(".track"));
            Point2D point = track.localToScene(track.getBoundsInLocal().getWidth() / 2,
                    track.getBoundsInLocal().getHeight() / 2);
            ScrollEvent direct = new ScrollEvent(ScrollEvent.SCROLL,
                    point.getX(), point.getY(), point.getX(), point.getY(),
                    false, false, false, false, true, false, 4, 4, 4, 4,
                    ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                    ScrollEvent.VerticalTextScrollUnits.NONE, 0, 1,
                    new PickResult(track, point.getX(), point.getY()));
            track.fireEvent(direct);
            assertEquals(20, bar.getValue(), 0.001);
            send(track, ScrollEvent.SCROLL, 40, 0, false, false, false);
            assertEquals(20, bar.getValue(), 0.001);
            bar.valueProperty().bind(new javafx.beans.property.SimpleDoubleProperty(15));
            track.fireEvent(direct.copyFor(null, track));
            assertEquals(15, bar.getValue(), 0.001);
            bar.valueProperty().unbind();
            skin.dispose();
            track.fireEvent(direct.copyFor(null, track));
            assertEquals(15, bar.getValue(), 0.001);
            return true;
        });
    }

    /// Virtual-flow panning before recognition is counted only once in either orientation.
    @Test
    void virtualFlowDeductsPanningBeforeRecognition() throws Exception {
        onFxThread(() -> {
            for (boolean vertical : new boolean @Unmodifiable []{false, true}) {
                VirtualFlow<?> flow = flow(vertical);
                flow.setPannable(true);
                flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_PRESSED, true, 100, 100));
                flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_DRAGGED, true, vertical ? 100 : 95, vertical ? 95 : 100));
                IndexedCell<?> first = requireNonNull(flow.getFirstVisibleCell());
                assertEquals(-5, vertical ? first.getLayoutY() : first.getLayoutX(), 0.001);
                send(flow, ScrollEvent.SCROLL_STARTED, 0, 0, true, false, false);
                send(flow, ScrollEvent.SCROLL, vertical ? 0 : -12, vertical ? -12 : 0, true, false, false);
                first = requireNonNull(flow.getFirstVisibleCell());
                assertEquals(-12, vertical ? first.getLayoutY() : first.getLayoutX(), 0.001);
                send(flow, ScrollEvent.SCROLL, vertical ? 0 : -6, vertical ? -6 : 0, true, false, false);
                flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_DRAGGED, true, 82, 82));
                first = requireNonNull(flow.getFirstVisibleCell());
                assertEquals(-18, vertical ? first.getLayoutY() : first.getLayoutX(), 0.001);
            }
            return true;
        });
    }

    /// Recognition preserves both axes across repeated native drags and variable-size cell recycling.
    @Test
    void virtualFlowMeasuresPanningAcrossCells() throws Exception {
        onFxThread(() -> {
            ListView<String> list = new ListView<>();
            list.setCellFactory(view -> {
                ListCell<String> cell = new ListCell<>();
                cell.setMinHeight(Region.USE_PREF_SIZE);
                cell.setMaxHeight(Region.USE_PREF_SIZE);
                cell.setMinWidth(900);
                cell.setPrefWidth(900);
                cell.indexProperty().addListener((observable, oldIndex, index) ->
                        cell.setPrefHeight(20 + Math.floorMod(index.intValue(), 3) * 10));
                return cell;
            });
            for (int i = 0; i < 100; i++)
                list.getItems().add("Cell " + i);
            layout(list);
            VirtualFlow<?> flow = (VirtualFlow<?>) requireNonNull(list.lookup(".virtual-flow"));
            ScrollUtils.addSmoothScrolling(flow);
            flow.setPannable(true);
            ScrollBar breadth = flow.getChildrenUnmodifiable().stream()
                    .filter(node -> node instanceof ScrollBar bar && bar.getOrientation() == Orientation.HORIZONTAL)
                    .map(ScrollBar.class::cast).findFirst().orElseThrow();
            breadth.setVisible(true);
            flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_PRESSED, true, 150, 150));
            flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_DRAGGED, true, 147, 108));
            flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_DRAGGED, true, 145, 85));
            IndexedCell<?> first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(2, first.getIndex());
            assertEquals(-15, first.getLayoutY(), 0.001);
            assertEquals(5, breadth.getValue(), 0.001);
            send(flow, ScrollEvent.SCROLL, -12, -92, true, false, false);
            first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(3, first.getIndex());
            assertEquals(-2, first.getLayoutY(), 0.001);
            assertEquals(12, breadth.getValue(), 0.001);
            return true;
        });
    }

    /// Recognition deducts only available native movement, and release clears that credit.
    @Test
    void virtualFlowPanningCreditRespectsEdgesAndRelease() throws Exception {
        onFxThread(() -> {
            VirtualFlow<?> flow = flow(true);
            flow.setPannable(true);
            send(flow, ScrollEvent.SCROLL, 0, -3, true, false, false);
            flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_PRESSED, true, 100, 100));
            flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_DRAGGED, true, 100, 105));
            assertEquals(0, flow.getPosition(), 0.001);
            send(flow, ScrollEvent.SCROLL, 0, 12, true, false, false);
            assertEquals(0, flow.getPosition(), 0.001);
            flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_PRESSED, true, 100, 100));
            flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_DRAGGED, true, 100, 95));
            flow.fireEvent(mouseAt(flow, MouseEvent.MOUSE_RELEASED, true, 100, 95));
            send(flow, ScrollEvent.SCROLL, 0, -12, true, false, false);
            assertEquals(-17, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
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
            ScrollPane narrow = pane(100, 1600);
            narrow.setPannable(true);
            Node viewport = requireNonNull(narrow.lookup(".viewport"));
            viewport.fireEvent(mouse(viewport, MouseEvent.MOUSE_PRESSED, true));
            send(viewport, ScrollEvent.SCROLL, 0, -80, true, false, false);
            viewport.fireEvent(mouse(viewport, MouseEvent.MOUSE_DRAGGED, true));
            assertEquals(80, offset(narrow, false), 0.001);
            VirtualFlow<?> flow = flow(true);
            flow.setPannable(true);
            flow.fireEvent(mouse(flow, MouseEvent.MOUSE_PRESSED, true));
            send(flow, ScrollEvent.SCROLL, 0, -12, true, false, false);
            flow.fireEvent(mouse(flow, MouseEvent.MOUSE_DRAGGED, true));
            assertEquals(-12, requireNonNull(flow.getFirstVisibleCell()).getLayoutY(), 0.001);
            return true;
        });
    }

    /// The first direct delta includes movement already applied before native gesture recognition.
    @Test
    void touchDeductsPanningBeforeRecognition() throws Exception {
        onFxThread(() -> {
            for (boolean viewportTarget : new boolean @Unmodifiable []{false, true}) {
                for (boolean started : new boolean @Unmodifiable []{false, true}) {
                    ScrollPane pane = pane(800, 1600);
                    pane.setPannable(true);
                    Node target = requireNonNull(viewportTarget ? pane.lookup(".viewport") : pane.getContent());
                    target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, 100, 100));
                    target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 97, 95));
                    assertEquals(3, offset(pane, true), 0.001);
                    assertEquals(5, offset(pane, false), 0.001);
                    target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 96, 93));
                    assertEquals(4, offset(pane, true), 0.001);
                    assertEquals(7, offset(pane, false), 0.001);
                    if (started)
                        send(target, ScrollEvent.SCROLL_STARTED, 0, 0, true, false, false);
                    send(target, ScrollEvent.SCROLL, -8, -12, true, false, false);
                    assertEquals(8, offset(pane, true), 0.001);
                    assertEquals(12, offset(pane, false), 0.001);
                    send(target, ScrollEvent.SCROLL, -4, -6, true, false, false);
                    target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 88, 82));
                    assertEquals(12, offset(pane, true), 0.001);
                    assertEquals(18, offset(pane, false), 0.001);
                }
            }
            return true;
        });
    }

    /// A delta already applied by native panning is consumed without reaching ancestors a second time.
    @Test
    void fullyPannedTouchDeltaDoesNotBubble() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            pane.setPannable(true);
            Node target = requireNonNull(pane.getContent());
            int[] bubbled = {0};
            pane.getParent().addEventHandler(ScrollEvent.SCROLL, event -> bubbled[0]++);
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, 100, 100));
            target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 100, 95));
            send(target, ScrollEvent.SCROLL, 0, -5, true, false, false);
            assertEquals(5, offset(pane, false), 0.001);
            assertEquals(0, bubbled[0]);
            return true;
        });
    }

    /// Releasing the pointer, changing position, or starting a new press discards old panning credit.
    @Test
    void previousPanningDoesNotAffectNewInput() throws Exception {
        onFxThread(() -> {
            for (int interruption = 0; interruption < 4; interruption++) {
                ScrollPane pane = pane(800, 1600);
                pane.setPannable(true);
                Node target = requireNonNull(pane.getContent());
                target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, 100, 100));
                target.fireEvent(mouseAt(target, MouseEvent.MOUSE_DRAGGED, true, 100, 95));
                assertEquals(5, offset(pane, false), 0.001);
                switch (interruption) {
                    case 0 -> target.fireEvent(mouseAt(target, MouseEvent.MOUSE_RELEASED, true, 100, 95));
                    case 1 -> target.fireEvent(mouseAt(target, MouseEvent.MOUSE_PRESSED, true, 100, 95));
                    case 2 -> pane.setVvalue(0.5);
                    case 3 -> send(target, ScrollEvent.SCROLL_FINISHED, 0, 0, true, false, false);
                }
                double before = offset(pane, false);
                send(target, ScrollEvent.SCROLL, 0, -12, true, false, false);
                assertEquals(before + 12, offset(pane, false), 0.001);
            }
            return true;
        });
    }

    /// Touch-generated drags reach slider thumbs even when the surrounding pane also scrolls.
    @Test
    void touchPreservesSliderDragging() throws Exception {
        onFxThread(() -> {
            for (double width : new double @Unmodifiable []{280, 800}) {
                double expected = 0;
                for (boolean smooth : new boolean @Unmodifiable []{false, true}) {
                    Slider slider = new Slider(0, 100, 50);
                    slider.setPrefWidth(280);
                    Pane content = new Pane(slider);
                    content.setMinSize(width, 1600);
                    content.setPrefSize(width, 1600);
                    ScrollPane pane = new ScrollPane(content);
                    pane.setPannable(true);
                    if (smooth)
                        ScrollUtils.addSmoothScrolling(pane);
                    layout(pane);
                    Node thumb = requireNonNull(slider.lookup(".thumb"));
                    Point2D pressed = slider.localToScene(140, 10);
                    thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, true, pressed.getX(), pressed.getY()));
                    send(thumb, ScrollEvent.SCROLL, -12, 0, true, false, false);
                    pane.layout();
                    Point2D dragged = slider.localToScene(110, 10);
                    thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_DRAGGED, true, dragged.getX(), dragged.getY()));
                    assertTrue(slider.getValue() < 50,
                            "width=" + width + ", smooth=" + smooth + ", value=" + slider.getValue());
                    if (smooth)
                        assertEquals(expected, slider.getValue(), 0.001);
                    else
                        expected = slider.getValue();
                }
            }
            return true;
        });
    }

    /// Virtual-flow panning filters do not prevent touch dragging inside a cell control.
    @Test
    void touchPreservesVirtualCellDragging() throws Exception {
        onFxThread(() -> {
            VirtualFlow<?> flow = flow(true);
            flow.setPannable(true);
            IndexedCell<?> cell = requireNonNull(flow.getFirstVisibleCell());
            Slider slider = new Slider(0, 100, 50);
            slider.setPrefWidth(200);
            cell.setGraphic(slider);
            cell.applyCss();
            cell.layout();
            Node thumb = requireNonNull(slider.lookup(".thumb"));
            thumb.fireEvent(mouse(thumb, MouseEvent.MOUSE_PRESSED, true));
            send(thumb, ScrollEvent.SCROLL, 0, -12, true, false, false);
            thumb.fireEvent(mouse(thumb, MouseEvent.MOUSE_DRAGGED, true));
            assertTrue(slider.getValue() < 50);
            assertEquals(-12, cell.getLayoutY(), 0.001);
            return true;
        });
    }

    /// Direct input on either scrollbar or its track retains native touch-position semantics.
    @Test
    void touchPreservesScrollbarPositioning() throws Exception {
        onFxThread(() -> {
            for (Orientation orientation : Orientation.values()) {
                for (boolean track : new boolean @Unmodifiable []{false, true}) {
                    ScrollPane pane = new ScrollPane(content(800, 1600));
                    layout(pane);
                    ScrollBar bar = pane.lookupAll(".scroll-bar").stream()
                            .map(ScrollBar.class::cast)
                            .filter(node -> node.getOrientation() == orientation).findFirst().orElseThrow();
                    Node target = track ? requireNonNull(bar.lookup(".track")) : bar;
                    Point2D point = target.localToScene(target.getLayoutBounds().getWidth() / 2,
                            target.getLayoutBounds().getHeight() / 2);
                    ScrollEvent event = new ScrollEvent(ScrollEvent.SCROLL,
                            point.getX(), point.getY(), point.getX(), point.getY(),
                            false, false, false, false, true, false, -4, -4, -4, -4,
                            ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                            ScrollEvent.VerticalTextScrollUnits.NONE, 0, 1,
                            new PickResult(target, point.getX(), point.getY()));
                    target.fireEvent(event);
                    double expected = bar.getValue();
                    assertTrue(expected > 0.25);
                    bar.setValue(bar.getMin());
                    ScrollUtils.addSmoothScrolling(pane);
                    target.fireEvent(event.copyFor(null, target));
                    assertEquals(expected, bar.getValue(), 0.001);
                }
            }
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

    /// Unused inertia reaches the ancestor until the tail expires or a press cancels it.
    @Test
    void nestedPanesForwardLiveInertia() throws Exception {
        ScrollPane @Unmodifiable [] panes = onFxThread(() -> {
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
            inner.setVvalue(inner.getVmax());
            send(inner.getContent(), ScrollEvent.SCROLL_STARTED, 0, 0, true, false, false);
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -40, true, false, false);
            send(inner.getContent(), ScrollEvent.SCROLL_FINISHED, 0, 0, true, false, false);
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -20, true, true, false);
            assertEquals(60, offset(outer, false), 0.001);
            assertEquals(inner.getVmax(), inner.getVvalue(), 0.001);
            return new ScrollPane @Unmodifiable []{inner, outer};
        });
        TimeUnit.MILLISECONDS.sleep(250);
        onFxThread(() -> {
            ScrollPane inner = panes[0];
            ScrollPane outer = panes[1];
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -20, true, true, false);
            assertEquals(60, offset(outer, false), 0.001);
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -40, true, false, false);
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -20, true, true, false);
            assertEquals(120, offset(outer, false), 0.001);
            inner.fireEvent(mouse(inner, MouseEvent.MOUSE_PRESSED, true));
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -20, true, true, false);
            assertEquals(120, offset(outer, false), 0.001);
            return true;
        });
    }

    /// A new gesture allows the ancestor to receive inertia even if all finger movement stayed inside.
    @Test
    void nestedPanesAcceptInertiaAsFirstMovement() throws Exception {
        onFxThread(() -> {
            for (boolean direct : new boolean @Unmodifiable []{false, true}) {
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
                inner.setVvalue(0.99);
                inner.fireEvent(mouse(inner, MouseEvent.MOUSE_PRESSED, direct));
                send(inner.getContent(), ScrollEvent.SCROLL_STARTED, 0, 0, direct, false, false);
                send(inner.getContent(), ScrollEvent.SCROLL, 0, -20, direct, false, false);
                assertEquals(inner.getVmax(), inner.getVvalue(), 0.001);
                assertEquals(0, offset(outer, false), 0.001);
                send(inner.getContent(), ScrollEvent.SCROLL_FINISHED, 0, 0, direct, false, false);
                send(inner.getContent(), ScrollEvent.SCROLL, 0, -20, direct, true, false);
                assertEquals(20, offset(outer, false), 0.001);
            }
            return true;
        });
    }

    /// Inertia keeps its original age when the inner pane reaches its boundary mid-tail.
    @Test
    void nestedPanesPreserveInertiaAge() throws Exception {
        long[] started = new long[2];
        ScrollPane @Unmodifiable [] panes = onFxThread(() -> {
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
            inner.setVvalue(0.99);
            send(inner.getContent(), ScrollEvent.SCROLL_STARTED, 0, 0, true, false, false);
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -1, true, false, false);
            send(inner.getContent(), ScrollEvent.SCROLL_FINISHED, 0, 0, true, false, false);
            started[0] = System.nanoTime();
            send(inner.getContent(), ScrollEvent.SCROLL, 0, -20, true, true, false);
            started[1] = System.nanoTime();
            assertEquals(inner.getVmax(), inner.getVvalue(), 0.001);
            assertEquals(0, offset(outer, false), 0.001);
            return new ScrollPane @Unmodifiable []{inner, outer};
        });
        TimeUnit.MILLISECONDS.sleep(120);
        onFxThread(() -> {
            long before = System.nanoTime();
            send(panes[0].getContent(), ScrollEvent.SCROLL, 0, -20, true, true, false);
            long after = System.nanoTime();
            double minimum = 20 * Math.pow(Math.max(0, 1 - (after - started[0]) / 200_000_000.0), 2);
            double maximum = 20 * Math.pow(Math.max(0, 1 - (before - started[1]) / 200_000_000.0), 2);
            double actual = offset(panes[1], false);
            assertTrue(actual >= minimum - 0.001 && actual <= maximum + 0.001,
                    "Expected " + minimum + ".." + maximum + " pixels, got " + actual);
            return true;
        });
    }

    /// Boundary handoff uses the original dispatch, preserving filters, handlers, and event targets.
    @Test
    void unusedScrollPassesAncestorsOnce() throws Exception {
        onFxThread(() -> {
            for (boolean viewportTarget : new boolean @Unmodifiable []{false, true}) {
                ScrollPane pane = pane(100, 1600);
                Node target = requireNonNull(viewportTarget ? pane.lookup(".viewport") : pane.getContent());
                Node root = pane.getScene().getRoot();
                int[] captured = {0};
                int[] bubbled = {0};
                root.addEventFilter(ScrollEvent.SCROLL, event -> captured[0]++);
                root.addEventHandler(ScrollEvent.SCROLL, event -> {
                    bubbled[0]++;
                    assertSame(target, event.getTarget());
                    assertEquals(20, event.getDeltaY(), 0.001);
                });
                send(target, ScrollEvent.SCROLL, 0, 20, false, false, true);
                assertEquals(1, captured[0]);
                assertEquals(1, bubbled[0]);
                send(target, ScrollEvent.SCROLL, 0, -20, false, false, true);
                finishWheel(pane);
                assertEquals(2, captured[0]);
                assertEquals(1, bubbled[0]);
                assertEquals(20, offset(pane, false), 0.001);
            }
            return true;
        });
    }

    /// Indirect scrolling over blank viewport space preserves the native scrollbar visibility timer.
    @Test
    void blankViewportPreservesScrollbarVisibility() throws Exception {
        ScrollPane @Unmodifiable [] panes = onFxThread(() -> {
            ScrollPane nativePane = new ScrollPane(content(100, 1600));
            layout(nativePane);
            ScrollPane smoothPane = pane(100, 1600);
            ScrollPane @Unmodifiable [] result = {nativePane, smoothPane};
            for (ScrollPane pane : result)
                send(requireNonNull(pane.lookup(".viewport")), ScrollEvent.SCROLL, 0, -40, false, false, true);
            finishWheel(smoothPane);
            assertTrue(offset(nativePane, false) > 0);
            assertEquals(40, offset(smoothPane, false), 0.001);
            return result;
        });
        TimeUnit.MILLISECONDS.sleep(100);
        onFxThread(() -> {
            for (ScrollPane pane : panes) {
                pane.requestLayout();
                pane.layout();
                ScrollBar bar = pane.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                        .filter(node -> node.getOrientation() == Orientation.VERTICAL).findFirst().orElseThrow();
                assertTrue(bar.isVisible());
            }
            return true;
        });
        TimeUnit.MILLISECONDS.sleep(1100);
        onFxThread(() -> {
            for (ScrollPane pane : panes) {
                pane.requestLayout();
                pane.layout();
                ScrollBar bar = pane.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                        .filter(node -> node.getOrientation() == Orientation.VERTICAL).findFirst().orElseThrow();
                assertEquals(!Platform.isSupported(ConditionalFeature.INPUT_TOUCH), bar.isVisible());
            }
            return true;
        });
    }

    /// Replacing a skin restores its old dispatcher and installs scrolling on the new viewport.
    @Test
    void paneSkinReplacementPreservesScrolling() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = new ScrollPane(content(100, 1600));
            layout(pane);
            Node oldViewport = requireNonNull(pane.lookup(".viewport"));
            EventDispatcher original = requireNonNull(oldViewport.getEventDispatcher());
            ScrollUtils.addSmoothScrolling(pane);
            pane.setSkin(new ReplacementScrollPaneSkin(pane));
            pane.applyCss();
            pane.layout();
            Node viewport = requireNonNull(pane.lookup(".viewport"));
            assertNotSame(oldViewport, viewport);
            assertSame(original, oldViewport.getEventDispatcher());
            int[] captured = {0};
            pane.getScene().getRoot().addEventFilter(ScrollEvent.SCROLL, event -> captured[0]++);
            send(viewport, ScrollEvent.SCROLL, 0, 40, false, false, true);
            assertEquals(1, captured[0]);
            send(viewport, ScrollEvent.SCROLL, 0, -40, false, false, true);
            finishWheel(pane);
            assertEquals(40, offset(pane, false), 0.001);
            Node content = requireNonNull(pane.getContent());
            content.addEventHandler(ScrollEvent.SCROLL, event -> event.consume());
            send(content, ScrollEvent.SCROLL, 0, -40, true, false, false);
            assertEquals(40, offset(pane, false), 0.001);
            return true;
        });
    }

    /// A viewport filter may dispatch another scroll before the original event finishes.
    @Test
    void viewportSupportsReentrantScroll() throws Exception {
        onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            Node viewport = requireNonNull(pane.lookup(".viewport"));
            int[] captured = {0};
            viewport.addEventFilter(ScrollEvent.SCROLL, event -> {
                if (++captured[0] == 1)
                    send(viewport, ScrollEvent.SCROLL, 0, -30, true, false, false);
            });
            send(viewport, ScrollEvent.SCROLL, 0, -20, true, false, false);
            assertEquals(2, captured[0]);
            assertEquals(50, offset(pane, false), 0.001);
            send(viewport, ScrollEvent.SCROLL, 0, -10, true, false, false);
            assertEquals(60, offset(pane, false), 0.001);
            return true;
        });
    }

    /// Completed dispatch and expired skin animations do not retain a detached scrolling pane.
    @Test
    void detachedPaneIsCollectibleAfterScroll() throws Exception {
        WeakReference<ScrollPane> reference = onFxThread(() -> {
            ScrollPane pane = pane(800, 1600);
            send(pane.getContent(), ScrollEvent.SCROLL, 0, -40, false, false, true);
            ((Pane) pane.getParent()).getChildren().clear();
            return new WeakReference<>(pane);
        });
        TimeUnit.MILLISECONDS.sleep(1200);
        onFxThread(() -> {
            for (int i = 0; i < 3; i++)
                System.gc();
            assertNull(reference.get());
            return true;
        });
    }

    /// Horizontal lists retain native vertical wheel movement for cells taller than the viewport.
    @Test
    void horizontalFlowScrollsTallCellsWithWheel() throws Exception {
        onFxThread(() -> {
            ListView<String> list = new ListView<>();
            list.setOrientation(Orientation.HORIZONTAL);
            list.setFixedCellSize(24);
            list.setCellFactory(view -> {
                ListCell<String> cell = new ListCell<>();
                cell.setMinHeight(600);
                cell.setPrefHeight(600);
                return cell;
            });
            for (int i = 0; i < 100; i++)
                list.getItems().add("Cell " + i);
            layout(list);
            VirtualFlow<?> flow = (VirtualFlow<?>) requireNonNull(list.lookup(".virtual-flow"));
            flow.setPosition(1);
            flow.layout();
            ScrollBar bar = flow.getChildrenUnmodifiable().stream()
                    .filter(node -> node instanceof ScrollBar scrollBar && scrollBar.getOrientation() == Orientation.VERTICAL)
                    .map(ScrollBar.class::cast).findFirst().orElseThrow();
            send(flow, ScrollEvent.SCROLL, 0, -40, false, false, true);
            double expected = bar.getValue();
            assertEquals(40, expected, 0.001);
            bar.setValue(0);
            ScrollUtils.addSmoothScrolling(flow);
            send(flow, ScrollEvent.SCROLL, 0, -40, false, false, true);
            assertEquals(0, bar.getValue(), 0.001);
            finishWheel(flow);
            assertEquals(expected, bar.getValue(), 0.001);
            assertEquals(1, flow.getPosition(), 0.001);
            send(flow, ScrollEvent.SCROLL, 0, 40, false, false, true);
            finishWheel(flow);
            assertEquals(0, bar.getValue(), 0.001);
            assertTrue(flow.getPosition() < 1);
            return true;
        });
    }

    /// Fixed-size flows reserve unseen end cells for queued movement before the first animation pulse.
    @Test
    void virtualFlowHandsOffWhenQueueReachesUnseenEdge() throws Exception {
        onFxThread(() -> {
            for (boolean vertical : new boolean @Unmodifiable []{true, false}) {
                for (boolean end : new boolean @Unmodifiable []{true, false}) {
                    for (double initial : new double @Unmodifiable []{end ? 0 : 1, 0.43}) {
                        VirtualFlow<?> flow = flow(vertical);
                        flow.setPosition(initial);
                        flow.layout();
                        if (end)
                            assertTrue(requireNonNull(flow.getLastVisibleCell()).getIndex() < 99);
                        else
                            assertTrue(requireNonNull(flow.getFirstVisibleCell()).getIndex() > 0);
                        int[] bubbled = {0};
                        flow.getScene().getRoot().addEventHandler(ScrollEvent.SCROLL, event -> bubbled[0]++);
                        double before = flow.getPosition();
                        double direction = end ? -1 : 1;
                        sendPages(flow, direction * (vertical ? 10 : 100));
                        assertEquals(0, bubbled[0]);
                        sendPages(flow, direction);
                        assertEquals(1, bubbled[0]);
                        assertEquals(before, flow.getPosition(), 0.001);
                        finishWheel(flow);
                        assertEquals(end ? 1 : 0, flow.getPosition(), 0.001);
                    }
                }
            }
            return true;
        });
    }

    /// Rapid page inputs retain their full distance, including a multi-page event and reversal.
    @Test
    void virtualFlowPreservesQueuedPages() throws Exception {
        onFxThread(() -> {
            VirtualFlow<?> flow = flow(true);
            double page = flow.getHeight();
            for (int i = 0; i < 3; i++)
                sendPages(flow, -1);
            finishWheel(flow);
            IndexedCell<?> first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(3 * page, first.getIndex() * 24 - first.getLayoutY(), 1);
            sendPages(flow, -3);
            finishWheel(flow);
            first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(6 * page, first.getIndex() * 24 - first.getLayoutY(), 1);
            sendPages(flow, 3);
            finishWheel(flow);
            first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(3 * page, first.getIndex() * 24 - first.getLayoutY(), 1);
            sendPages(flow, 20);
            finishWheel(flow);
            assertEquals(0, flow.getPosition(), 0.001);
            sendPages(flow, -1);
            finishWheel(flow);
            first = requireNonNull(flow.getFirstVisibleCell());
            assertEquals(page, first.getIndex() * 24 - first.getLayoutY(), 1);
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

    /// Creates nested installed panes with independent overflow on both axes, inner pane first.
    private static ScrollPane @Unmodifiable [] nestedPanes() {
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
        return new ScrollPane[]{inner, outer};
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

    /// Sends direct positioning input at scene coordinates to a grabbed thumb or track.
    private static void sendDirectAt(Node target, double x, double y) {
        sendDirectAt(target, ScrollEvent.SCROLL, x, y);
    }

    /// Sends a direct gesture lifecycle event at the recognition point's scene coordinates.
    private static void sendDirectAt(Node target, EventType<ScrollEvent> type, double x, double y) {
        target.fireEvent(new ScrollEvent(type, x, y, x, y,
                false, false, false, false, true, false, 5, 5, 5, 5,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 1, new PickResult(target, x, y)));
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

    /// Dispatches wheel input with an explicit page count independent of its pixel delta.
    private static void sendPages(Node target, double pages) {
        target.fireEvent(new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                false, false, false, false, false, false, 0, pages * 40, 0, pages * 40,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.PAGES, pages, 0, new PickResult(target, 10, 10)));
    }

    /// Creates mouse input for interrupting animation or checking synthesized touch compatibility.
    private static MouseEvent mouse(Node target, EventType<MouseEvent> type, boolean synthesized) {
        double position = type == MouseEvent.MOUSE_DRAGGED ? -20 : 10;
        return mouseAt(target, type, synthesized, position, position);
    }

    /// Creates mouse input at explicit scene coordinates, independent of content scrolling.
    private static MouseEvent mouseAt(Node target, EventType<MouseEvent> type, boolean synthesized, double x, double y) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, synthesized, false, true,
                new PickResult(target, x, y));
    }

    /// Uses a distinct skin class so JavaFX 17 also performs an actual replacement.
    @NotNullByDefault
    private static final class ReplacementScrollPaneSkin extends ScrollPaneSkin {
        /// Creates a native scroll pane skin with a distinct runtime type.
        private ReplacementScrollPaneSkin(ScrollPane pane) {
            super(pane);
        }
    }

    /// Completes the installed wheel animation while keeping tests independent of pulse timing.
    private static void finishWheel(Node node) {
        @Nullable ScrollController controller = ScrollUtils.controller(node);
        if (controller != null)
            controller.wheel.handle(System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
    }

    /// Runs an operation and propagates assertion failures from the JavaFX application thread.
    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
