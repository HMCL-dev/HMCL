package com.jfoenix.skins;

import com.jfoenix.controls.JFXProgressBar;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.JavaFXLauncher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JFXProgressBarSkinTest {
    private static final double TEST_WIDTH = 100;
    private static final double TEST_HEIGHT = 4;
    private static final double DELTA = 0.001;

    @BeforeAll
    static void startJavaFx() {
        JavaFXLauncher.start();
        assumeTrue(JavaFXLauncher.isStarted(), "JavaFX toolkit is unavailable in this environment");
    }

    @Test
    void determinateZeroProgressHidesActiveIndicatorAndUsesFullTrack() throws Exception {
        runOnFxThreadAndWait(() -> {
            LayoutProbe probe = createProbe(0);

            assertFalse(probe.activeIndicator().isVisible());
            assertEquals(0.0, probe.activeIndicator().getWidth(), DELTA);

            List<Region> visibleTracks = probe.visibleTracks();
            assertEquals(1, visibleTracks.size());
            assertEquals(0.0, visibleTracks.get(0).getLayoutX(), DELTA);
            assertEquals(100.0, visibleTracks.get(0).getWidth(), DELTA);

            assertTrue(probe.stopIndicator().isVisible());
            assertEquals(96.0, probe.stopIndicator().getLayoutX(), DELTA);
            assertEquals(4.0, probe.stopIndicator().getWidth(), DELTA);
        });
    }

    @Test
    void determinateCompleteHidesTrackAndStopIndicator() throws Exception {
        runOnFxThreadAndWait(() -> {
            LayoutProbe probe = createProbe(1.0);

            assertEquals(TEST_WIDTH, probe.activeIndicator().getWidth(), DELTA);
            assertTrue(probe.visibleTracks().isEmpty());
            assertFalse(probe.stopIndicator().isVisible());
        });
    }

    @Test
    void indeterminateStartsWithActiveIndicatorAndNoStopIndicator() throws Exception {
        runOnFxThreadAndWait(() -> {
            LayoutProbe probe = createProbe(ProgressIndicator.INDETERMINATE_PROGRESS);

            assertEquals(0.0, probe.activeIndicator().getLayoutX(), DELTA);
            assertEquals(22.0, probe.activeIndicator().getWidth(), DELTA);

            List<Region> visibleTracks = probe.visibleTracks();
            assertEquals(1, visibleTracks.size());
            assertEquals(26.0, visibleTracks.get(0).getLayoutX(), DELTA);
            assertEquals(74.0, visibleTracks.get(0).getWidth(), DELTA);

            assertFalse(probe.stopIndicator().isVisible());
        });
    }

    @Test
    void determinatePositiveProgressUsesMinimumVisibleWidth() throws Exception {
        runOnFxThreadAndWait(() -> {
            LayoutProbe probe = createProbe(0.01);

            assertTrue(probe.activeIndicator().isVisible());
            assertEquals(0.0, probe.activeIndicator().getLayoutX(), DELTA);
            assertEquals(4.0, probe.activeIndicator().getWidth(), DELTA);

            List<Region> visibleTracks = probe.visibleTracks();
            assertEquals(1, visibleTracks.size());
            assertEquals(8.0, visibleTracks.get(0).getLayoutX(), DELTA);
            assertEquals(92.0, visibleTracks.get(0).getWidth(), DELTA);

            assertTrue(probe.stopIndicator().isVisible());
        });
    }

    private static LayoutProbe createProbe(double progress) {
        JFXProgressBar progressBar = new JFXProgressBar(progress);
        progressBar.setMinSize(TEST_WIDTH, TEST_HEIGHT);
        progressBar.setPrefSize(TEST_WIDTH, TEST_HEIGHT);
        progressBar.setMaxSize(TEST_WIDTH, TEST_HEIGHT);

        Pane root = new Pane(progressBar);
        new Scene(root, TEST_WIDTH, 16);

        root.applyCss();
        progressBar.applyCss();
        progressBar.resizeRelocate(0, 6, TEST_WIDTH, TEST_HEIGHT);
        progressBar.layout();

        Region activeIndicator = lookupRegion(progressBar, ".active-indicator");
        Region stopIndicator = lookupRegion(progressBar, ".stop-indicator");
        List<Region> tracks = progressBar.lookupAll(".track").stream()
                .map(Region.class::cast)
                .sorted(Comparator.comparingDouble(Node::getLayoutX))
                .toList();

        return new LayoutProbe(activeIndicator, tracks, stopIndicator);
    }

    private static Region lookupRegion(JFXProgressBar progressBar, String selector) {
        Node node = progressBar.lookup(selector);
        assertNotNull(node, () -> "Missing node for selector " + selector);
        return (Region) node;
    }

    private static void runOnFxThreadAndWait(CheckedRunnable runnable) throws Exception {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for JavaFX work to finish");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private record LayoutProbe(Region activeIndicator, List<Region> tracks, Region stopIndicator) {
        private List<Region> visibleTracks() {
            return tracks.stream().filter(Node::isVisible).toList();
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
