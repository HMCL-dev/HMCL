/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.task;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.SVG;

/// The big Task Center progress ring, drawn from arcs so it can do things a JFXSpinner can't:
///  - a soft, blurred **pulse** highlight that keeps sweeping from the start of the arc to the end of
///    the filled portion (fading in and out at the ends so it never hard-snaps);
///  - a **turn-green** completion transition and a center that **morphs to a check mark**;
///  - an idle **breathing** ring with a pause glyph, and a bobbing download glyph while working with
///    no determinate percentage.
///
/// Feed it a 0~1 [#progressProperty] (or -1 for indeterminate). The owner calls [#setIdle] when there
/// are no tasks and [#playComplete] for the success flourish.
public final class TaskOverviewRing extends StackPane {
    private static final double SIZE = 104, CENTER = 52, RADIUS = 44, WIDTH = 6;
    private static final double PULSE_LEN = 42; // degrees
    private static final Color GREEN = Color.web("#43A047");

    private final DoubleProperty progress = new SimpleDoubleProperty(-1);
    /// The value actually drawn — eased toward [#progress], or driven to 1 by the completion anim.
    private final DoubleProperty displayProgress = new SimpleDoubleProperty(0);
    private final DoubleProperty pulseOffset = new SimpleDoubleProperty(0);

    private final Pane arcLayer;
    private final Arc progressArc, greenArc, pulseArc;

    private final Label percentLabel;
    private final StackPane checkPane;
    private final HBox pauseGlyph;
    private final StackPane downloadGlyph;

    private final Timeline pulse;
    private final Timeline breathe;
    private final RotateTransition spin;
    private final TranslateTransition downloadBob;
    private Timeline displayAnim;
    /// The currently-running completion flourish stage (fill → check pop-in → hold), so a task that
    /// starts mid-flourish can cancel it.
    private Animation completionAnim;

    private boolean indeterminate;
    private boolean completed;
    private boolean idle;

    public TaskOverviewRing() {
        Circle track = new Circle(CENTER, CENTER, RADIUS);
        track.getStyleClass().add("task-ring-track");
        track.setFill(null);
        track.setStrokeWidth(WIDTH);

        progressArc = arc("task-ring-arc");
        greenArc = arc("task-ring-arc");
        greenArc.setStroke(GREEN);
        greenArc.setOpacity(0);
        pulseArc = arc("task-ring-pulse");
        pulseArc.setEffect(new GaussianBlur(5)); // soft edges — the segment reads as a glow, not a bar
        pulseArc.setOpacity(0);

        arcLayer = new Pane(track, progressArc, greenArc, pulseArc);
        arcLayer.setMinSize(SIZE, SIZE);
        arcLayer.setPrefSize(SIZE, SIZE);
        arcLayer.setMaxSize(SIZE, SIZE);

        percentLabel = new Label();
        percentLabel.getStyleClass().add("task-overview-percent");

        SVGPath check = SVG.CHECK.createIcon(new SimpleObjectProperty<>(GREEN));
        check.setScaleX(1.8);
        check.setScaleY(1.8);
        checkPane = new StackPane(check);
        checkPane.setVisible(false);

        pauseGlyph = new HBox(6, pauseBar(), pauseBar());
        pauseGlyph.setAlignment(javafx.geometry.Pos.CENTER);
        pauseGlyph.setVisible(false);

        SVGPath download = SVG.DOWNLOAD.createIcon();
        download.getStyleClass().add("task-ring-glyph");
        download.setScaleX(1.5);
        download.setScaleY(1.5);
        downloadGlyph = new StackPane(download);
        downloadGlyph.setVisible(false);

        getChildren().addAll(arcLayer, percentLabel, checkPane, pauseGlyph, downloadGlyph);
        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);

        // Sweeping highlight: loops forever; refresh() maps the offset onto the filled arc and fades
        // it in/out with a sine so it appears and disappears smoothly rather than snapping.
        pulse = new Timeline(new KeyFrame(Duration.millis(1500), new KeyValue(pulseOffset, 1, Interpolator.LINEAR)));
        pulse.setCycleCount(Animation.INDEFINITE);
        pulseOffset.addListener((o, a, b) -> refresh());
        pulse.play();

        // Idle breathing: the dim track fades in and out.
        breathe = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(arcLayer.opacityProperty(), 0.7)),
                new KeyFrame(Duration.millis(1500), new KeyValue(arcLayer.opacityProperty(), 0.3, Interpolator.EASE_BOTH)));
        breathe.setAutoReverse(true);
        breathe.setCycleCount(Animation.INDEFINITE);

        spin = new RotateTransition(Duration.millis(1100), arcLayer);
        spin.setByAngle(-360);
        spin.setInterpolator(Interpolator.LINEAR);
        spin.setCycleCount(Animation.INDEFINITE);

        downloadBob = new TranslateTransition(Duration.millis(650), downloadGlyph);
        downloadBob.setFromY(-3);
        downloadBob.setToY(3);
        downloadBob.setAutoReverse(true);
        downloadBob.setCycleCount(Animation.INDEFINITE);
        downloadBob.setInterpolator(Interpolator.EASE_BOTH);

        displayProgress.addListener((o, a, b) -> refresh());
        progress.addListener((o, a, b) -> onProgressChanged(b.doubleValue()));
        onProgressChanged(-1);
        updateCenter();
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    private static Arc arc(String styleClass) {
        Arc arc = new Arc(CENTER, CENTER, RADIUS, RADIUS, 90, 0);
        arc.setType(ArcType.OPEN);
        arc.setFill(null);
        arc.setStrokeWidth(WIDTH);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        arc.getStyleClass().add(styleClass);
        return arc;
    }

    private static Rectangle pauseBar() {
        Rectangle bar = new Rectangle(5, 22);
        bar.setArcWidth(4);
        bar.setArcHeight(4);
        bar.getStyleClass().add("task-ring-glyph");
        return bar;
    }

    // ── state ─────────────────────────────────────────────────────────

    private void onProgressChanged(double target) {
        if (idle || completed)
            return;
        if (target < 0) {
            setIndeterminate(true);
            return;
        }
        setIndeterminate(false);
        animateDisplayTo(target);
    }

    private void animateDisplayTo(double target) {
        if (displayAnim != null)
            displayAnim.stop();
        // Snap backwards motion (a new task joining the average lowers it); ease only forward.
        if (target <= displayProgress.get()) {
            displayProgress.set(target);
            return;
        }
        displayAnim = new Timeline(new KeyFrame(Duration.millis(400),
                new KeyValue(displayProgress, target, Interpolator.EASE_BOTH)));
        displayAnim.play();
    }

    private void setIndeterminate(boolean value) {
        if (indeterminate == value)
            return;
        indeterminate = value;
        if (value) {
            if (displayAnim != null)
                displayAnim.stop();
            displayProgress.set(0.25);
            spin.play();
        } else {
            spin.stop();
            arcLayer.setRotate(0);
        }
        updateCenter();
        refresh();
    }

    /// Idle look: a breathing dim ring with a pause glyph, shown when there are no active tasks.
    public void setIdle(boolean value) {
        if (value) {
            if (idle)
                return;
            boolean fromComplete = completed; // coming straight off a completion flourish
            idle = true;
            completed = false;
            greenArc.setOpacity(0);
            if (displayAnim != null)
                displayAnim.stop();
            spin.stop();
            indeterminate = false;
            arcLayer.setRotate(0);
            displayProgress.set(0);
            pulseArc.setOpacity(0);
            breathe.playFromStart();
            if (fromComplete)
                morphCheckToPause();
            else
                updateCenter();
        } else {
            // Force active — runs even if not currently idle: a task starting mid completion-flourish
            // must interrupt it (cancel the flourish, clear any completed/green/check state) instead
            // of being no-op'd and then overridden by the flourish's delayed setIdle(true).
            idle = false;
            completed = false;
            if (completionAnim != null) {
                completionAnim.stop();
                completionAnim = null;
            }
            greenArc.setOpacity(0);
            breathe.stop();
            arcLayer.setOpacity(1);
            checkPane.setVisible(false);
            checkPane.setRotate(0);
            checkPane.setOpacity(1);
            checkPane.setScaleX(1);
            checkPane.setScaleY(1);
            pauseGlyph.setRotate(0);
            pauseGlyph.setOpacity(1);
            onProgressChanged(progress.get());
            updateCenter();
        }
        refresh();
    }

    /// The completion check spins and fades out while the pause glyph spins in — a little "settling
    /// down to idle" transition after everything finishes.
    private void morphCheckToPause() {
        percentLabel.setVisible(false);
        downloadGlyph.setVisible(false);

        RotateTransition checkOut = new RotateTransition(Duration.millis(420), checkPane);
        checkOut.setByAngle(180);
        FadeTransition checkFade = new FadeTransition(Duration.millis(420), checkPane);
        checkFade.setToValue(0);

        pauseGlyph.setVisible(true);
        pauseGlyph.setOpacity(0);
        pauseGlyph.setRotate(-180);
        RotateTransition pauseIn = new RotateTransition(Duration.millis(420), pauseGlyph);
        pauseIn.setToAngle(0);
        FadeTransition pauseFade = new FadeTransition(Duration.millis(420), pauseGlyph);
        pauseFade.setToValue(1);

        ParallelTransition morph = new ParallelTransition(checkOut, checkFade, pauseIn, pauseFade);
        morph.setInterpolator(Interpolator.EASE_BOTH);
        morph.setOnFinished(e -> {
            checkPane.setVisible(false);
            checkPane.setRotate(0);
            checkPane.setOpacity(1);
            checkPane.setScaleX(1);
            checkPane.setScaleY(1);
        });
        morph.play();
    }

    private void refresh() {
        double p = Math.max(0, Math.min(1, displayProgress.get()));
        double deg = p * 360;
        progressArc.setLength(-deg);
        greenArc.setLength(-deg);

        percentLabel.setText(idle || indeterminate || completed ? "" : String.format("%.0f%%", p * 100));

        if (!completed && !indeterminate && !idle && deg > 4) {
            double o = pulseOffset.get();
            double len = Math.min(PULSE_LEN, deg);
            double head = o * deg;
            double start = Math.max(0, Math.min(head - len / 2, deg - len));
            pulseArc.setStartAngle(90 - start);
            pulseArc.setLength(-len);
            // Trapezoidal envelope: bright and steady through the middle, fading gently in over the
            // first 20% of the sweep and out over the last 20% — so it eases at the two ends instead
            // of blinking off.
            double env = o < 0.2 ? o / 0.2 : o > 0.8 ? (1 - o) / 0.2 : 1;
            pulseArc.setOpacity(env * 0.72);
        } else {
            pulseArc.setOpacity(0);
        }
    }

    /// Picks the single visible center glyph for the current state.
    private void updateCenter() {
        Node show = completed ? checkPane
                : idle ? pauseGlyph
                : indeterminate ? downloadGlyph
                : percentLabel;
        percentLabel.setVisible(show == percentLabel);
        checkPane.setVisible(show == checkPane);
        pauseGlyph.setVisible(show == pauseGlyph);
        downloadGlyph.setVisible(show == downloadGlyph);
        if (show == downloadGlyph)
            downloadBob.play();
        else {
            downloadBob.stop();
            downloadGlyph.setTranslateY(0);
        }
    }

    // ── completion flourish ───────────────────────────────────────────

    /// Fill to 100%, fade the ring to green, then morph the center to a check mark. {@code onFinished}
    /// runs after a short hold (the owner then flips to [#setIdle]).
    public void playComplete(Runnable onFinished) {
        if (completed || idle) {
            if (onFinished != null)
                onFinished.run();
            return;
        }
        completed = true;
        setIndeterminate(false);
        pulseArc.setOpacity(0);
        if (displayAnim != null)
            displayAnim.stop();
        updateCenter();
        percentLabel.setVisible(false);

        Timeline fill = new Timeline(new KeyFrame(Duration.millis(360),
                new KeyValue(displayProgress, 1, Interpolator.EASE_BOTH),
                new KeyValue(greenArc.opacityProperty(), 1, Interpolator.EASE_BOTH)));
        completionAnim = fill;
        fill.setOnFinished(e -> {
            if (completed) // not cancelled by a task starting mid-flourish
                morphToCheck(onFinished);
        });
        fill.play();
    }

    private void morphToCheck(Runnable onFinished) {
        checkPane.setOpacity(0);
        checkPane.setScaleX(0.4);
        checkPane.setScaleY(0.4);
        checkPane.setVisible(true);

        // Pop-in with a slight overshoot (0.4 → 1.15 → 1.0). Key frames because an overshoot needs a
        // scale > 1, which Interpolator.SPLINE forbids (all coords must be [0,1]).
        Timeline morph = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(checkPane.scaleXProperty(), 0.4),
                        new KeyValue(checkPane.scaleYProperty(), 0.4),
                        new KeyValue(checkPane.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(checkPane.scaleXProperty(), 1.15, Interpolator.EASE_OUT),
                        new KeyValue(checkPane.scaleYProperty(), 1.15, Interpolator.EASE_OUT),
                        new KeyValue(checkPane.opacityProperty(), 1, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(340),
                        new KeyValue(checkPane.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(checkPane.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)));
        completionAnim = morph;
        morph.setOnFinished(e -> {
            if (!completed) // cancelled mid-flourish
                return;
            PauseTransition hold = new PauseTransition(Duration.millis(900));
            completionAnim = hold;
            hold.setOnFinished(x -> {
                completionAnim = null;
                if (onFinished != null)
                    onFinished.run();
            });
            hold.play();
        });
        morph.play();
    }
}
