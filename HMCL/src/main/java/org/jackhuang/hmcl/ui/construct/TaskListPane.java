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
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskHandle;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays running tasks, retained failures, and the stages reported by a handle.
public final class TaskListPane extends StackPane {
    private static final Insets DEFAULT_PROGRESS_NODE_PADDING = new Insets(0, 0, 4, 0);
    private static final Insets STAGED_PROGRESS_NODE_PADDING = new Insets(0, 0, 4, 26);

    /// The handle whose queued notifications may update the list.
    private @Nullable TaskHandle handle;
    private final JFXListView<Node> listView = new JFXListView<>();
    private final Map<Task<?>, ProgressListNode> nodes = new HashMap<>();
    /// Maps identifiers and their display aliases to stage rows.
    private final Map<String, StageNode> stageNodes = new HashMap<>();
    /// Tracks the outermost active stages contributing to each display row.
    private final Map<TaskStage, StageNode> activeStages = new HashMap<>();
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

    /// Returns the display row for an identifier or appends a row for a newly observed stage.
    @FXThread
    private StageNode stageNode(String id) {
        return stageNodes.computeIfAbsent(id, key -> {
            StageNode node = new StageNode(key);
            listView.getItems().add(node);
            progressNodePadding.set(STAGED_PROGRESS_NODE_PADDING);
            return node;
        });
    }

    /// Appends planned stages in declaration order and retains aliases declared by enclosing tasks.
    @FXThread
    private void declareStages(@Unmodifiable List<Task.StagesHint> hints) {
        for (Task.StagesHint hint : hints) {
            StageNode node = stageNode(hint.stage());
            for (String alias : hint.aliases())
                stageNodes.putIfAbsent(alias, node);
        }
    }

    /// Localizes the task name if it should appear as a separate progress row.
    private static boolean prepareTaskRow(Task<?> task, @Nullable TaskStage stage) {
        if (!task.getSignificance().shouldShow() || task.getName() == null)
            return false;
        if (task instanceof GameInstallTask && stage != null && stage.id().startsWith("hmcl.install.game"))
            return false;
        @Nullable String localizedName = I18n.translateTaskName(task);
        if (localizedName != null)
            task.setName(localizedName);
        return true;
    }

    /// Displays a task below its stage or at the top of the list when it has no stage.
    @FXThread
    private ProgressListNode addTaskRow(Task<?> task, @Nullable TaskStage stage) {
        ProgressListNode node = new ProgressListNode(task);
        int index = stage == null ? 0 : listView.getItems().indexOf(stageNode(stage.id())) + 1;
        listView.getItems().add(index, node);
        return node;
    }

    /// Displays subsequent stage and task events from the supplied handle.
    public void setHandle(TaskHandle handle) {
        this.handle = handle;
        handle.addTaskListener(new TaskListener() {
            /// Queues a change on the JavaFX thread while this handle is attached.
            private void update(Runnable action) {
                Platform.runLater(() -> {
                    if (TaskListPane.this.handle == handle)
                        action.run();
                });
            }

            /// Clears progress from the previous execution.
            @Override
            public void onExecutionStarted(TaskHandle handle) {
                update(() -> {
                    nodes.values().forEach(ProgressListNode::unbind);
                    nodes.clear();
                    stageNodes.clear();
                    activeStages.clear();
                    listView.getItems().clear();
                    progressNodePadding.set(DEFAULT_PROGRESS_NODE_PADDING);
                });
            }

            @Override
            public void onStagesDeclared(@Unmodifiable List<Task.StagesHint> hints) {
                update(() -> declareStages(hints));
            }

            @Override
            public void onStageStarted(TaskStage stage) {
                update(() -> {
                    StageNode node = stageNode(stage.id());
                    // An enclosing stage on this row owns the outcome, including any failures it handles.
                    for (@Nullable TaskStage parent = stage.parent(); parent != null; parent = parent.parent()) {
                        if (activeStages.get(parent) == node)
                            return;
                    }
                    activeStages.put(stage, node);
                    node.begin();
                });
            }

            @Override
            public void onStageFinished(TaskStage stage, @Nullable Throwable failure) {
                update(() -> {
                    @Nullable StageNode node = activeStages.remove(stage);
                    if (node != null)
                        node.finish(failure == null);
                });
            }

            /// Adds visible running tasks to their stage.
            @Override
            public void onTaskStarted(Task<?> task, @Nullable TaskStage stage) {
                if (prepareTaskRow(task, stage))
                    update(() -> nodes.put(task, addTaskRow(task, stage)));
            }

            /// Removes successful tasks and retains visible failures, including failures before the body starts.
            @Override
            public void onTaskFinished(Task<?> task, @Nullable TaskStage stage, @Nullable Throwable failure) {
                update(() -> {
                    @Nullable ProgressListNode node = nodes.remove(task);
                    if (node == null) {
                        if (failure == null || !prepareTaskRow(task, stage))
                            return;
                        node = addTaskRow(task, stage);
                    }
                    if (failure == null) {
                        node.unbind();
                        listView.getItems().remove(node);
                    } else {
                        node.setThrowable(failure);
                    }
                });
            }

            /// Applies both increments to the display row for this stage.
            @Override
            public void onStageProgress(TaskStage stage, int completedDelta, int totalDelta) {
                update(() -> stageNode(stage.id()).addProgress(completedDelta, totalDelta));
            }
        });
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

    private static abstract class Node {

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

        private StageNode(String stage) {
            String stageKey;
            String stageValue;

            int idx = stage.indexOf(':');
            if (idx >= 0) {
                stageKey = stage.substring(0, idx);
                stageValue = stage.substring(idx + 1);
            } else {
                stageKey = stage;
                stageValue = null;
            }

            // CHECKSTYLE:OFF
            // @formatter:off
            message = switch (stageKey) {
                case "hmcl.modpack" ->                  i18n("install.modpack");
                case "hmcl.modpack.download" ->         i18n("launch.state.modpack");
                case "hmcl.install.assets" ->           i18n("assets.download");
                case "hmcl.install.libraries" ->        i18n("libraries.download");
                case "hmcl.install.game" ->             i18n("install.installer.install", i18n("install.installer.game") + " " + stageValue);
                case "hmcl.install.forge" ->            i18n("install.installer.install", i18n("install.installer.forge") + " " + stageValue);
                case "hmcl.install.cleanroom" ->        i18n("install.installer.install", i18n("install.installer.cleanroom") + " " + stageValue);
                case "hmcl.install.neoforge" ->         i18n("install.installer.install", i18n("install.installer.neoforge") + " " + stageValue);
                case "hmcl.install.liteloader" ->       i18n("install.installer.install", i18n("install.installer.liteloader") + " " + stageValue);
                case "hmcl.install.optifine" ->         i18n("install.installer.install", i18n("install.installer.optifine") + " " + stageValue);
                case "hmcl.install.fabric" ->           i18n("install.installer.install", i18n("install.installer.fabric") + " " + stageValue);
                case "hmcl.install.fabric-api" ->       i18n("install.installer.install", i18n("install.installer.fabric-api") + " " + stageValue);
                case "hmcl.install.legacyfabric" ->     i18n("install.installer.install", i18n("install.installer.legacyfabric") + " " + stageValue);
                case "hmcl.install.legacyfabric-api" -> i18n("install.installer.install", i18n("install.installer.legacyfabric-api") + " " + stageValue);
                case "hmcl.install.quilt" ->            i18n("install.installer.install", i18n("install.installer.quilt") + " " + stageValue);
                case "hmcl.install.quilt-api" ->        i18n("install.installer.install", i18n("install.installer.quilt-api") + " " + stageValue);
                default -> i18n(stageKey);
            };
            // @formatter:on
            // CHECKSTYLE:ON

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
        private final DoubleProperty progress = new SimpleDoubleProperty(0.0);

        private ProgressListNode(Task<?> task) {
            this.title = task.getName();
            progress.bind(task.progressProperty());
        }

        public void unbind() {
            progress.unbind();
        }

        public void setThrowable(Throwable throwable) {
            unbind();
            message.set(throwable.getLocalizedMessage());
            progress.set(0.);
        }
    }
}
