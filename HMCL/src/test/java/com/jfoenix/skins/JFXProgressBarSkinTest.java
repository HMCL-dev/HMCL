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

            assertFalse(probe.stopIndicator().isVisible());
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
            assertEquals(18.0, probe.activeIndicator().getWidth(), DELTA);

            List<Region> visibleTracks = probe.visibleTracks();
            assertEquals(1, visibleTracks.size());
            assertEquals(22.0, visibleTracks.get(0).getLayoutX(), DELTA);
            assertEquals(78.0, visibleTracks.get(0).getWidth(), DELTA);

            assertFalse(probe.stopIndicator().isVisible());
        });
    }

    @Test
    void indeterminateMidFlightShowsTracksOnBothSidesWithFourPixelGap() throws Exception {
        runOnFxThreadAndWait(() -> {
            LayoutProbe probe = createIndeterminateProbe(0.24, 0.68);

            assertEquals(24.0, probe.activeIndicator().getLayoutX(), DELTA);
            assertEquals(44.0, probe.activeIndicator().getWidth(), DELTA);

            List<Region> visibleTracks = probe.visibleTracks();
            assertEquals(2, visibleTracks.size());
            assertEquals(0.0, visibleTracks.get(0).getLayoutX(), DELTA);
            assertEquals(20.0, visibleTracks.get(0).getWidth(), DELTA);
            assertEquals(72.0, visibleTracks.get(1).getLayoutX(), DELTA);
            assertEquals(28.0, visibleTracks.get(1).getWidth(), DELTA);

            assertFalse(probe.stopIndicator().isVisible());
        });
    }

    @Test
    void indeterminateNearExitHidesTrailingTrack() throws Exception {
        runOnFxThreadAndWait(() -> {
            LayoutProbe probe = createIndeterminateProbe(0.86, 1.02);

            assertEquals(86.0, probe.activeIndicator().getLayoutX(), DELTA);
            assertEquals(14.0, probe.activeIndicator().getWidth(), DELTA);

            List<Region> visibleTracks = probe.visibleTracks();
            assertEquals(1, visibleTracks.size());
            assertEquals(0.0, visibleTracks.get(0).getLayoutX(), DELTA);
            assertEquals(82.0, visibleTracks.get(0).getWidth(), DELTA);

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

    private static LayoutProbe createIndeterminateProbe(double startFactor, double endFactor) {
        JFXProgressBar progressBar = new JFXProgressBar(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressBar.setMinSize(TEST_WIDTH, TEST_HEIGHT);
        progressBar.setPrefSize(TEST_WIDTH, TEST_HEIGHT);
        progressBar.setMaxSize(TEST_WIDTH, TEST_HEIGHT);

        Pane root = new Pane(progressBar);
        new Scene(root, TEST_WIDTH, 16);

        root.applyCss();
        progressBar.applyCss();
        ((JFXProgressBarSkin) progressBar.getSkin()).setIndeterminateSegmentForTesting(startFactor, endFactor);
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
