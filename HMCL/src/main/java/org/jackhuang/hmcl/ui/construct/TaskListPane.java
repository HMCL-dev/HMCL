// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXProgressBar;
import javafx.application.Platform;
import javafx.beans.WeakListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.download.game.GameInstallTask;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.InvocationDispatcher;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.ui.DialogUtils.shouldReportCancellation;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays running tasks, retained failures, and the stages reported by a root task.
public final class TaskListPane extends StackPane {
    private static final Insets DEFAULT_PROGRESS_NODE_PADDING = new Insets(0, 0, 4, 0);
    private static final Insets STAGED_PROGRESS_NODE_PADDING = new Insets(0, 0, 4, 26);

    /// The root task whose queued notifications may update the list.
    private @Nullable Task<?> rootTask;
    private final JFXListView<Node> listView = new JFXListView<>();
    /// The listener receiving updates for this pane.
    private @Nullable TaskListener listener;
    /// Maps each stage to its row for the current execution.
    private final Map<TaskStage, StageNode> stageNodes = new HashMap<>();
    /// Tracks the outermost active stage scopes contributing to each display row.
    private final Map<TaskStage.Scope, StageNode> activeScopes = new HashMap<>();
    private final ObjectProperty<Insets> progressNodePadding = new SimpleObjectProperty<>(Insets.EMPTY);
    private final DoubleProperty cellWidth = new SimpleDoubleProperty();

    public TaskListPane() {
        listView.setPadding(new Insets(12, 0, 0, 0));
        listView.setCellFactory(l -> new Cell());
        listView.setSelectionModel(null);
        FXUtils.onChangeAndOperate(listView.widthProperty(), width -> {
            double w = width.doubleValue();
            cellWidth.set(w <= 12.0 ? w : w - 12.0);
        });

        getChildren().setAll(listView);
    }

    /// Returns the display row for a stage, appending it when first observed.
    @FXThread
    private StageNode stageNode(TaskStage stage) {
        return stageNodes.computeIfAbsent(stage, key -> {
            StageNode node = new StageNode(key);
            listView.getItems().add(node);
            progressNodePadding.set(STAGED_PROGRESS_NODE_PADDING);
            return node;
        });
    }

    /// Appends new rows in declaration order. Each stage keeps its first row.
    @FXThread
    private void declareStages(@Unmodifiable List<TaskStage.Hint> hints) {
        for (TaskStage.Hint hint : hints) {
            StageNode node = stageNode(hint.stage());
            for (TaskStage groupedStage : hint.groupedStages())
                stageNodes.putIfAbsent(groupedStage, node);
        }
    }

    /// Returns the game component whose installation this stage represents.
    private static @Nullable GameComponentType installComponent(TaskStage stage) {
        String prefix = "hmcl.install.";
        return stage.key().startsWith(prefix) ? GameComponentType.fromPatchId(stage.key().substring(prefix.length())) : null;
    }

    /// Returns whether the task should appear as a separate progress row.
    private static boolean shouldShowTaskRow(Task<?> task, TaskStage.@Nullable Scope scope) {
        return task.getSignificance().shouldShow()
                && !(task instanceof GameInstallTask && scope != null && installComponent(scope.stage()) == GameComponentType.GAME);
    }

    /// Displays a task below its stage or at the top of the list when it has no stage.
    @FXThread
    private void addTaskRow(ProgressListNode node, TaskStage.@Nullable Scope scope) {
        int index = scope == null ? 0 : listView.getItems().indexOf(stageNode(scope.stage())) + 1;
        listView.getItems().add(index, node);
    }

    /// Clears the display and observes subsequent events from the supplied root task, or detaches on null.
    @FXThread
    public void setTask(@Nullable Task<?> rootTask) {
        if (this.rootTask == rootTask)
            return;
        if (this.rootTask != null && listener != null)
            this.rootTask.removeTaskListener(listener);
        this.rootTask = rootTask;
        listener = null;
        stageNodes.clear();
        activeScopes.clear();
        listView.getItems().clear();
        progressNodePadding.set(DEFAULT_PROGRESS_NODE_PADDING);
        if (rootTask == null)
            return;
        listener = new TaskListener() {
            /// Displayable tasks that can still receive progress from workers.
            private final Map<Task<?>, TaskRowUpdate> running = new ConcurrentHashMap<>();

            /// Creates the display row only when the JavaFX thread needs it.
            private final class TaskRowUpdate {
                /// Title captured when the task is first reported.
                private final String title;
                /// Stage under which the row belongs.
                private final TaskStage.@Nullable Scope scope;
                /// Applies only the latest pending row change on the JavaFX thread.
                private final InvocationDispatcher<Runnable> updates = InvocationDispatcher.runOn(action -> update(action), Runnable::run);
                /// Displayed row, accessed only on the JavaFX thread.
                private @Nullable ProgressListNode row;
                /// Prevents a worker holding this row from replacing its final update.
                private boolean finished;

                /// Captures the display details before the task body runs or its rejection is reported.
                TaskRowUpdate(Task<?> task, TaskStage.@Nullable Scope scope) {
                    this.title = I18n.translateTaskName(task);
                    this.scope = scope;
                }

                /// Returns the displayed row, adding it on first use.
                @FXThread
                private ProgressListNode show() {
                    if (row == null) {
                        row = new ProgressListNode(title);
                        addTaskRow(row, scope);
                    }
                    return row;
                }

                /// Shows the latest fraction on the row.
                synchronized void report(double fraction) {
                    if (!finished)
                        updates.accept(() -> show().progress.set(fraction));
                }

                /// Replaces any pending progress with the final display change.
                synchronized void finish(@Nullable Throwable failure) {
                    finished = true;
                    updates.accept(() -> {
                        if (failure != null)
                            show().setThrowable(failure);
                        else if (row != null)
                            listView.getItems().remove(row);
                    });
                }
            }

            /// Queues a change on the JavaFX thread while this root task is attached.
            private void update(Runnable action) {
                Platform.runLater(() -> {
                    if (listener == this)
                        action.run();
                });
            }

            @Override
            public void onStagesDeclared(@Unmodifiable List<TaskStage.Hint> hints) {
                update(() -> declareStages(hints));
            }

            @Override
            public void onStageStarted(TaskStage.Scope scope) {
                update(() -> {
                    StageNode node = stageNode(scope.stage());
                    // An enclosing stage on this row owns the outcome, including any failures it handles.
                    for (TaskStage.@Nullable Scope parent = scope.parent(); parent != null; parent = parent.parent()) {
                        if (activeScopes.get(parent) == node)
                            return;
                    }
                    activeScopes.put(scope, node);
                    node.begin();
                });
            }

            @Override
            public void onStageFinished(TaskStage.Scope scope, @Nullable Throwable failure) {
                update(() -> {
                    @Nullable StageNode node = activeScopes.remove(scope);
                    if (node != null)
                        node.finish(failure == null);
                });
            }

            /// Adds visible running tasks to their stage.
            @Override
            public void onTaskStarted(Task<?> task, TaskStage.@Nullable Scope scope) {
                if (shouldShowTaskRow(task, scope)) {
                    TaskRowUpdate pending = new TaskRowUpdate(task, scope);
                    running.put(task, pending);
                    pending.report(ProgressIndicator.INDETERMINATE_PROGRESS);
                }
            }

            /// Submits the latest progress for a visible row to the JavaFX thread.
            @Override
            public void onTaskProgress(Task<?> task, double fraction) {
                @Nullable TaskRowUpdate pending = running.get(task);
                if (pending != null)
                    pending.report(fraction);
            }

            /// Removes successful tasks and retains visible failures, including failures before the body starts.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                @Nullable TaskRowUpdate pending = running.remove(task);
                if (pending == null) {
                    if (failure == null || !shouldShowTaskRow(task, scope))
                        return;
                    if (failure instanceof CancellationException cancellation && !shouldReportCancellation(cancellation))
                        return;
                    pending = new TaskRowUpdate(task, scope);
                }
                pending.finish(failure);
            }

            /// Applies both increments to the display row for this stage.
            @Override
            public void onStageProgress(TaskStage.Scope scope, int completedDelta, int totalDelta) {
                update(() -> stageNode(scope.stage()).addProgress(completedDelta, totalDelta));
            }
        };
        rootTask.addTaskListener(listener);
    }

    private final class Cell extends ListCell<Node> {
        private static final double STATUS_ICON_SIZE = 14;
        private static final Insets PROGRESS_BAR_MARGIN = new Insets(2, 0, 0, 0);

        private final BorderPane pane = new BorderPane();
        private final StackPane left = new StackPane();
        private final Label title = new Label();
        private final Label message = new Label();
        private final JFXProgressBar bar = new JFXProgressBar();

        private WeakReference<StageNode> prevStageNodeRef;
        private StatusChangeListener statusChangeListener;

        private Cell() {
            setPadding(new Insets(0, 0, 4, 0));

            prefWidthProperty().bind(cellWidth);

            FXUtils.setLimitHeight(left, STATUS_ICON_SIZE);
            FXUtils.setLimitWidth(left, STATUS_ICON_SIZE);

            BorderPane.setAlignment(left, Pos.CENTER_LEFT);
            BorderPane.setMargin(left, new Insets(0, 12, 0, 0));
            BorderPane.setAlignment(title, Pos.CENTER_LEFT);
            pane.setCenter(title);

            DoubleBinding barWidth = Bindings.createDoubleBinding(() -> {
                Insets padding = pane.getPadding();
                Insets insets = pane.getInsets();
                return pane.getWidth() - padding.getLeft() - padding.getRight() - insets.getLeft() - insets.getRight();
            }, pane.paddingProperty(), pane.widthProperty());
            bar.minWidthProperty().bind(barWidth);
            bar.prefWidthProperty().bind(barWidth);
            bar.maxWidthProperty().bind(barWidth);
            BorderPane.setMargin(bar, PROGRESS_BAR_MARGIN);

            setGraphic(pane);
        }

        private void updateLeftIcon(StageNode.Status status) {
            left.getChildren().setAll(status.svg.createIcon(STATUS_ICON_SIZE));
        }

        @Override
        protected void updateItem(Node item, boolean empty) {
            super.updateItem(item, empty);

            pane.paddingProperty().unbind();
            title.textProperty().unbind();
            message.textProperty().unbind();

            bar.setSmoothProgress(false);
            bar.progressProperty().unbind();
            StageNode prevStageNode;
            if (prevStageNodeRef != null && (prevStageNode = prevStageNodeRef.get()) != null)
                prevStageNode.status.removeListener(statusChangeListener);

            if (item instanceof ProgressListNode progressListNode) {
                title.setText(progressListNode.title);
                message.textProperty().bind(progressListNode.message);
                bar.progressProperty().bind(progressListNode.progress);

                pane.paddingProperty().bind(progressNodePadding);
                pane.setLeft(null);
                pane.setRight(message);
                pane.setBottom(bar);
            } else if (item instanceof StageNode stageNode) {
                title.textProperty().bind(stageNode.title);
                message.setText("");
                bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

                pane.setPadding(Insets.EMPTY);
                pane.setLeft(left);
                pane.setRight(message);
                pane.setBottom(null);

                updateLeftIcon(stageNode.status.get());
                if (statusChangeListener == null)
                    statusChangeListener = new StatusChangeListener(this);
                stageNode.status.addListener(statusChangeListener);
                prevStageNodeRef = new WeakReference<>(stageNode);
            } else { // item == null
                title.setText("");
                message.setText("");
                bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                pane.setPadding(Insets.EMPTY);
                pane.setLeft(null);
                pane.setRight(null);
                pane.setBottom(null);
            }

            bar.setSmoothProgress(true);
        }
    }

    private static final class StatusChangeListener implements ChangeListener<StageNode.Status>, WeakListener {

        private final WeakReference<Cell> cellRef;

        private StatusChangeListener(Cell cell) {
            this.cellRef = new WeakReference<>(cell);
        }

        @Override
        public boolean wasGarbageCollected() {
            return cellRef.get() == null;
        }

        @Override
        public void changed(ObservableValue<? extends StageNode.Status> observable,
                            StageNode.Status oldValue,
                            StageNode.Status newValue) {
            Cell cell = cellRef.get();
            if (cell == null) {
                if (observable != null)
                    observable.removeListener(this);
                return;
            }
            cell.updateLeftIcon(newValue);
        }
    }

    private static sealed abstract class Node {

    }

    private static final class StageNode extends Node {
        /// Number of independent stages that have started on this row and have yet to finish.
        private int activeStageCount;

        private enum Status {
            WAITING(SVG.MORE_HORIZ),
            RUNNING(SVG.ARROW_FORWARD),
            SUCCESS(SVG.CHECK),
            FAILED(SVG.CLOSE);

            private final SVG svg;

            Status(SVG svg) {
                this.svg = svg;
            }
        }

        private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.WAITING);
        private final StringProperty title = new SimpleStringProperty();
        private final String message;
        private int count = 0;
        private int total = 0;

        /// Creates a row labeled with its localized stage name and component version when supplied.
        private StageNode(TaskStage stage) {
            @Nullable GameComponentType component = installComponent(stage);
            if (component != null) {
                String name = i18n("install.installer." + component.getPatchId());
                @Nullable String version = stage.qualifier();
                message = i18n("install.installer.install", version == null ? name : name + " " + version);
            } else {
                message = switch (stage.key()) {
                    case "hmcl.modpack" -> i18n("install.modpack");
                    case "hmcl.modpack.download" -> i18n("launch.state.modpack");
                    case "hmcl.install.assets" -> i18n("assets.download");
                    case "hmcl.install.libraries" -> i18n("libraries.download");
                    default -> i18n(stage.key());
                };
            }

            title.set(message);
        }

        /// Marks an independent stage running, retaining any failure already reported on this row.
        private void begin() {
            activeStageCount++;
            if (status.get() != Status.FAILED)
                status.set(Status.RUNNING);
        }

        /// Records a failure immediately or marks the row successful after all its stages succeed.
        private void finish(boolean success) {
            activeStageCount--;
            if (!success)
                status.set(Status.FAILED);
            else if (activeStageCount == 0 && status.get() != Status.FAILED)
                status.set(Status.SUCCESS);
        }

        /// Accumulates progress from all stages sharing this row.
        private void addProgress(int completedDelta, int totalDelta) {
            count += completedDelta;
            total += totalDelta;
            title.setValue(total > 0
                    ? message + " - " + count + "/" + total
                    : message
            );
        }
    }

    private static final class ProgressListNode extends Node {
        private final String title;
        private final StringProperty message = new SimpleStringProperty("");
        /// The progress shown on this row, initially indeterminate.
        private final DoubleProperty progress = new SimpleDoubleProperty(ProgressIndicator.INDETERMINATE_PROGRESS);

        /// Creates a row with the task's display title.
        private ProgressListNode(String title) {
            this.title = title;
        }

        /// Retains the failure message on the row and resets its progress.
        public void setThrowable(Throwable throwable) {
            message.set(throwable.getLocalizedMessage());
            progress.set(0.);
        }
    }
}
