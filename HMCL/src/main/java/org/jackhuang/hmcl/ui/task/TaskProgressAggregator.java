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

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.value.ChangeListener;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.Lang.tryCast;

/// Headless aggregate-progress computer for a single [TaskExecutor].
///
/// HMCL's task graph never rolls child progress up to the root task (the async executor calls
/// `execute()` directly and never binds parent progress), so the root task's progress property is
/// perpetually -1 for composite installs. This mirrors [org.jackhuang.hmcl.ui.construct.TaskListPane]'s
/// stage/count tracking — but headless — and turns it into a weighted 0~1 progress:
///
///   progress = Σ(weight_i × stageProgress_i) / Σ weight_i
///
/// where each stage's weight is a static empirical estimate (see [#weightOf]) and its own progress is
/// `count/total` while running, or 1 once finished. Tasks without stage hints (a single mod/file
/// download, a Java download, an update check) fall back to the current significant running task's own
/// progress; if nothing reports a determinate value the aggregate stays -1 (indeterminate).
///
/// All state is mutated on the JavaFX Application Thread ([#runInFX]); the [TaskListener] callbacks
/// arrive on worker threads, exactly as TaskListPane handles them.
public final class TaskProgressAggregator {
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(-1);

    /// Files still to be processed across all unfinished counted stages (Σ total-count), for the
    /// Task Center overview. 0 when unknown.
    private final ReadOnlyIntegerWrapper remainingFiles = new ReadOnlyIntegerWrapper(0);

    /// Primary stages in declaration order (aliases are not repeated here).
    private final List<Stage> orderedStages = new ArrayList<>();
    /// Stage lookup by stage string, including aliases (multiple keys may map to the same Stage).
    private final Map<String, Stage> stageIndex = new HashMap<>();

    /// The current significant running task whose own (byte-level) progress we surface. For staged
    /// executors its fraction is folded into its stage's file count — so a single large file makes
    /// the bar creep instead of freezing until the count ticks; for hint-less executors (a single
    /// mod/file download) it is the progress. Pushed through [#leafListener].
    private Task<?> leafSource;
    /// Stage the leaf belongs to (its inherited stage), or null.
    private String leafStage;
    private double leafProgress = -1;
    private final ChangeListener<Number> leafListener = (obs, oldValue, newValue) -> {
        double v = newValue.doubleValue();
        if (v >= 0) {
            leafProgress = v;
            recompute();
        }
    };

    /// Forward-only clamp so a single task's bar never visibly regresses (late totals, source switch).
    private double lastReported = -1;

    public TaskProgressAggregator(TaskExecutor executor) {
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStart() {
                runInFX(() -> {
                    orderedStages.clear();
                    stageIndex.clear();
                    addHints(executor.getHints());
                    recompute();
                });
            }

            @Override
            public void onReady(Task<?> task) {
                runInFX(() -> {
                    if (task instanceof Task.StagesHintTask)
                        addHints(((Task<?>.StagesHintTask) task).getHints());
                    if (task.getStage() != null)
                        stageOf(task.getStage()).begin();
                    recompute();
                });
            }

            @Override
            public void onRunning(Task<?> task) {
                if (!task.getSignificance().shouldShow())
                    return;
                runInFX(() -> setLeafSource(task));
            }

            @Override
            public void onFinished(Task<?> task) {
                runInFX(() -> {
                    if (task.getStage() != null)
                        stageOf(task.getStage()).succeed();
                    if (task == leafSource)
                        clearLeaf();
                    recompute();
                });
            }

            @Override
            public void onFailed(Task<?> task, Throwable throwable) {
                runInFX(() -> {
                    if (task.getStage() != null)
                        stageOf(task.getStage()).fail();
                    if (task == leafSource)
                        clearLeaf();
                    recompute();
                });
            }

            @Override
            public void onPropertiesUpdate(Task<?> task) {
                if (task instanceof Task.CountTask) {
                    runInFX(() -> {
                        Stage stage = stageOf(((Task<?>.CountTask) task).getCountStage());
                        // A count arriving proves the stage is actively working, even if no staged
                        // task ever passed through onReady for it.
                        if (stage.status == Stage.Status.WAITING)
                            stage.begin();
                        stage.count++;
                        recompute();
                    });
                    return;
                }

                if (task.getStage() != null) {
                    int total = tryCast(task.getProperties().get("total"), Integer.class).orElse(0);
                    runInFX(() -> {
                        stageOf(task.getStage()).total += total;
                        recompute();
                    });
                }
            }
        });
    }

    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    public double getProgress() {
        return progress.get();
    }

    public int getRemainingFiles() {
        return remainingFiles.get();
    }

    /// Finalizes the aggregate to 1 (called when the task terminates, success or failure — the work is
    /// over either way, matching how the indicator should settle at 100%).
    public void finish() {
        clearLeaf();
        lastReported = 1;
        progress.set(1);
        remainingFiles.set(0);
    }

    private void addHints(Collection<Task.StagesHint> hints) {
        for (Task.StagesHint hint : hints) {
            Stage stage = stageOf(hint.stage());
            for (String alias : hint.aliases())
                stageIndex.put(alias, stage);
        }
    }

    /// Gets or creates the stage record. Stages may surface at runtime that were never pre-declared
    /// in the hints (e.g. a modpack completion's download counter): adopt them on the fly so their
    /// counts still feed the aggregate — otherwise the known stages all complete and the bar freezes
    /// at a fake 100% while files are still downloading.
    private Stage stageOf(String stageName) {
        Stage stage = stageIndex.get(stageName);
        if (stage == null) {
            stage = new Stage(weightOf(stageName));
            stageIndex.put(stageName, stage);
            orderedStages.add(stage);
        }
        return stage;
    }

    private void setLeafSource(Task<?> task) {
        if (leafSource == task)
            return;
        if (leafSource != null)
            leafSource.progressProperty().removeListener(leafListener);
        leafSource = task;
        leafStage = task.getInheritedStage();
        leafProgress = -1; // the previous leaf's fraction must not leak into the new one
        task.progressProperty().addListener(leafListener);
        double v = task.progressProperty().get();
        if (v >= 0)
            leafProgress = v;
        recompute();
    }

    private void clearLeaf() {
        if (leafSource != null) {
            leafSource.progressProperty().removeListener(leafListener);
            leafSource = null;
            leafStage = null;
            leafProgress = -1;
        }
    }

    private void recompute() {
        double result;
        int remaining = 0;
        if (!orderedStages.isEmpty()) {
            Stage leafOwner = leafStage != null ? stageIndex.get(leafStage) : null;
            double totalWeight = 0, accumulated = 0;
            for (Stage stage : orderedStages) {
                totalWeight += stage.weight;
                double p = stage.progress();
                // Fold the current file's byte fraction into its stage: a single large file then
                // creeps smoothly instead of freezing until the whole-file count ticks. Monotone:
                // on completion the numerator goes from (count + fraction) to (count+1) >= it.
                if (stage == leafOwner && stage.status == Stage.Status.RUNNING
                        && stage.total > 0 && leafProgress > 0) {
                    p = Math.min(1, (stage.count + Math.min(1, leafProgress)) / (double) stage.total);
                }
                accumulated += stage.weight * p;
                if (!stage.isDone())
                    remaining += Math.max(0, stage.total - stage.count);
            }
            result = totalWeight > 0 ? accumulated / totalWeight : 0;
        } else {
            // Hint-less executor: surface the running leaf's own progress; when the leaf just
            // finished (cleared), hold the last shown value instead of flashing indeterminate.
            result = leafProgress >= 0 ? leafProgress : lastReported;
        }
        remainingFiles.set(remaining);

        if (result >= 0) {
            // 100% is reserved for finish(): a still-running task is by definition not done, and
            // stages we failed to account for must not read as completion (e.g. a completion pass
            // whose download counter wasn't pre-declared).
            result = Math.min(result, 0.98);
            // A just-started task would otherwise render a frozen, empty bar (looks stuck); show a
            // visible sliver instead, the same trick other launchers use.
            result = Math.max(result, 0.02);
            if (result < lastReported)
                result = lastReported; // forward-only
            lastReported = result;
        }
        progress.set(result);
    }

    /// Static empirical weight (~expected seconds) per stage, keyed by the part before ':'. Mirrors the
    /// hand-tuned weighting other launchers use; only relative magnitude matters.
    private static double weightOf(String stage) {
        int idx = stage.indexOf(':');
        String key = idx >= 0 ? stage.substring(0, idx) : stage;
        return switch (key) {
            case "hmcl.install.assets" -> 18;
            case "hmcl.modpack.download" -> 15;
            case "hmcl.install.libraries" -> 13;
            case "hmcl.modpack" -> 6;
            case "hmcl.install.game" -> 4;
            case "hmcl.install.forge", "hmcl.install.neoforge", "hmcl.install.cleanroom",
                 "hmcl.install.liteloader", "hmcl.install.optifine",
                 "hmcl.install.fabric", "hmcl.install.fabric-api",
                 "hmcl.install.legacyfabric", "hmcl.install.legacyfabric-api",
                 "hmcl.install.quilt", "hmcl.install.quilt-api" -> 8;
            default -> 3;
        };
    }

    private static final class Stage {
        private enum Status { WAITING, RUNNING, SUCCESS, FAILED }

        private final double weight;
        private int runningTasksCount = 0;
        private int count = 0;
        private int total = 0;
        private Status status = Status.WAITING;

        private Stage(double weight) {
            this.weight = weight;
        }

        private void begin() {
            runningTasksCount++;
            if (status == Status.WAITING || status == Status.SUCCESS)
                status = Status.RUNNING;
        }

        private void succeed() {
            runningTasksCount = Math.max(0, runningTasksCount - 1);
            if (runningTasksCount == 0)
                status = Status.SUCCESS;
        }

        private void fail() {
            runningTasksCount = Math.max(0, runningTasksCount - 1);
            status = Status.FAILED;
        }

        private double progress() {
            return switch (status) {
                case SUCCESS, FAILED -> 1;
                case RUNNING -> total > 0 ? Math.min(1, (double) count / total) : 0;
                case WAITING -> 0;
            };
        }

        private boolean isDone() {
            return status == Status.SUCCESS || status == Status.FAILED;
        }
    }
}
