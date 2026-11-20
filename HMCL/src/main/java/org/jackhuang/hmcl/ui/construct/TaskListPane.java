// SPDX-FileCopyrightText: Copyright (C) 2021-2026 The HMCL Authors
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
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.download.game.GameInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public final class TaskListPane extends StackPane {
    private static final Insets DEFAULT_PROGRESS_NODE_PADDING = new Insets(0, 0, 4, 0);
    private static final Insets STAGED_PROGRESS_NODE_PADDING = new Insets(0, 0, 4, 26);

    private @Nullable TaskExecutor executor;
    private final JFXListView<Node> listView = new JFXListView<>();
    /// Detail rows that are currently present in [#listView].
    private final Map<Task<?>, ProgressListNode> nodes = new HashMap<>();
    private final Map<String, StageNode> stageNodes = new HashMap<>();
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

    @FXThread
    private void addStagesHints(@NotNull Collection<Task.StagesHint> hints) {
        for (Task.StagesHint hint : hints) {
            @Nullable StageNode node = stageNodes.get(hint.stage());

            if (node == null) {
                node = new StageNode(hint.stage());
                stageNodes.put(hint.stage(), node);
                listView.getItems().add(node);
            }
            for (String stage : hint.aliases()) {
                stageNodes.put(stage, node);
            }
        }
    }

    @FXThread
    private void updateProgressNodePadding() {
        progressNodePadding.set(stageNodes.isEmpty() ? DEFAULT_PROGRESS_NODE_PADDING : STAGED_PROGRESS_NODE_PADDING);
    }

    /// Returns whether a stage should start with its individual task rows collapsed.
    ///
    /// High-volume download stages can contain thousands of resources. Keeping them collapsed by
    /// default avoids creating a scene-graph node and binding a progress property for every file.
    private static boolean isCollapsedByDefault(@NotNull String stage) {
        int separator = stage.indexOf(':');
        String stageKey = separator >= 0 ? stage.substring(0, separator) : stage;
        return switch (stageKey) {
            case "hmcl.modpack.download", "hmcl.install.assets", "hmcl.install.libraries" -> true;
            default -> false;
        };
    }

    /// Creates and registers the detail row for a running task.
    ///
    /// This method must be called only when the row is actually going to be displayed. Collapsed
    /// stages intentionally keep only a task reference until the user expands them.
    @FXThread
    private @NotNull ProgressListNode createProgressNode(@NotNull Task<?> task, @Nullable StageNode stageNode) {
        @Nullable String localizedName = I18n.translateTaskName(task);
        if (localizedName != null)
            task.setName(localizedName);

        ProgressListNode node = new ProgressListNode(task, stageNode);
        nodes.put(task, node);
        return node;
    }

    /// Appends a detail row after the last row that belongs to the same stage.
    @FXThread
    private void addProgressNode(@NotNull StageNode stageNode, @NotNull ProgressListNode node) {
        int stageIndex = listView.getItems().indexOf(stageNode);
        int index = stageIndex < 0 ? listView.getItems().size() : stageIndex + 1;

        while (index < listView.getItems().size()) {
            Node item = listView.getItems().get(index);
            if (!(item instanceof ProgressListNode progressListNode) || progressListNode.stageNode != stageNode)
                break;
            index++;
        }

        listView.getItems().add(index, node);
    }

    /// Expands or collapses a stage and lazily materializes only the rows needed by the new state.
    @FXThread
    private void toggleStage(@NotNull StageNode stageNode) {
        boolean expanded = !stageNode.isExpanded();
        stageNode.setExpanded(expanded);

        if (expanded) {
            int insertionIndex = listView.getItems().indexOf(stageNode) + 1;
            for (Task<?> task : stageNode.activeTasks) {
                if (!nodes.containsKey(task)) {
                    ProgressListNode node = createProgressNode(task, stageNode);
                    listView.getItems().add(insertionIndex++, node);
                }
            }
        } else {
            listView.getItems().removeIf(item -> {
                if (item instanceof ProgressListNode progressListNode
                        && progressListNode.stageNode == stageNode) {
                    progressListNode.unbind();
                    nodes.remove(progressListNode.task);
                    return true;
                }
                return false;
            });
        }
    }

    public void setExecutor(TaskExecutor executor) {
        this.executor = executor;
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStart() {
                Platform.runLater(() -> {
                    stageNodes.clear();
                    listView.getItems().clear();
                    addStagesHints(executor.getHints());
                    updateProgressNodePadding();
                });
            }

            @Override
            public void onReady(Task<?> task) {
                if (task instanceof Task.StagesHintTask) {
                    Platform.runLater(() -> {
                        addStagesHints(((Task<?>.StagesHintTask) task).getHints());
                        updateProgressNodePadding();
                    });
                }

                if (task.getStage() != null) {
                    Platform.runLater(() -> {
                        @Nullable StageNode node = stageNodes.get(task.getStage());
                        if (node != null)
                            node.begin();
                    });
                }
            }

            @Override
            public void onRunning(Task<?> task) {
                if (!task.getSignificance().shouldShow() || task.getName() == null)
                    return;

                if (task instanceof GameInstallTask
                        && task.getInheritedStage() != null
                        && task.getInheritedStage().startsWith("hmcl.install.game")) {
                    return;
                }

                Platform.runLater(() -> {
                    @Nullable StageNode stageNode = stageNodes.get(task.getInheritedStage());
                    if (stageNode != null) {
                        stageNode.activeTasks.add(task);
                        if (stageNode.isExpanded()) {
                            ProgressListNode node = createProgressNode(task, stageNode);
                            addProgressNode(stageNode, node);
                        }
                    } else {
                        ProgressListNode node = createProgressNode(task, null);
                        listView.getItems().add(0, node);
                    }
                });
            }

            @Override
            public void onFinished(Task<?> task) {
                Platform.runLater(() -> {
                    if (task.getStage() != null) {
                        @Nullable StageNode stageNode = stageNodes.get(task.getStage());
                        if (stageNode != null)
                            stageNode.succeed();
                    }

                    @Nullable StageNode stageNode = stageNodes.get(task.getInheritedStage());
                    if (stageNode != null)
                        stageNode.activeTasks.remove(task);

                    @Nullable ProgressListNode node = nodes.remove(task);
                    if (node != null) {
                        node.unbind();
                        listView.getItems().remove(node);
                    }
                });
            }

            @Override
            public void onFailed(Task<?> task, Throwable throwable) {
                if (task.getStage() != null) {
                    Platform.runLater(() -> {
                        @Nullable StageNode stageNode = stageNodes.get(task.getStage());
                        if (stageNode != null)
                            stageNode.fail();
                    });
                }

                Platform.runLater(() -> {
                    @Nullable StageNode stageNode = stageNodes.get(task.getInheritedStage());
                    if (stageNode != null)
                        stageNode.activeTasks.remove(task);

                    @Nullable ProgressListNode node = nodes.get(task);
                    if (node != null)
                        node.setThrowable(throwable);
                });
            }

            @Override
            public void onPropertiesUpdate(Task<?> task) {
                if (task instanceof Task.CountTask) {
                    runInFX(() -> {
                        @Nullable StageNode stageNode = stageNodes.get(((Task<?>.CountTask) task).getCountStage());
                        if (stageNode != null)
                            stageNode.count();
                    });

                    return;
                }

                if (task.getStage() != null) {
                    int total = tryCast(task.getProperties().get("total"), Integer.class).orElse(0);
                    runInFX(() -> {
                        @Nullable StageNode stageNode = stageNodes.get(task.getStage());
                        if (stageNode != null)
                            stageNode.addTotal(total);
                    });
                }
            }
        });
    }

    private final class Cell extends ListCell<Node> {
        private static final double STATUS_ICON_SIZE = 14;
        private static final Insets PROGRESS_BAR_MARGIN = new Insets(2, 0, 0, 0);

        private final BorderPane pane = new BorderPane();
        private final StackPane left = new StackPane();
        /// Disclosure arrow shown only for stage rows.
        private final javafx.scene.Node expandArrow = SVG.KEYBOARD_ARROW_DOWN.createIcon(16);
        /// Leading icons for a stage row: disclosure arrow plus stage status.
        private final HBox stageLeading = new HBox(4, expandArrow, left);
        private final Label title = new Label();
        private final Label message = new Label();
        private final JFXProgressBar bar = new JFXProgressBar();

        private @Nullable WeakReference<StageNode> prevStageNodeRef;
        private @Nullable StatusChangeListener statusChangeListener;

        private Cell() {
            setPadding(new Insets(0, 0, 4, 0));

            prefWidthProperty().bind(cellWidth);

            FXUtils.setLimitHeight(left, STATUS_ICON_SIZE);
            FXUtils.setLimitWidth(left, STATUS_ICON_SIZE);

            stageLeading.setAlignment(Pos.CENTER_LEFT);
            BorderPane.setMargin(stageLeading, new Insets(0, 12, 0, 0));
            BorderPane.setAlignment(title, Pos.CENTER_LEFT);
            pane.setCenter(title);

            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && getItem() instanceof StageNode stageNode) {
                    toggleStage(stageNode);
                    updateExpandArrow(stageNode);
                    event.consume();
                }
            });

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

        /// Rotates the disclosure arrow: down when expanded, right when collapsed.
        private void updateExpandArrow(StageNode stageNode) {
            expandArrow.setRotate(stageNode.isExpanded() ? 0 : -90);
        }

        @Override
        protected void updateItem(@Nullable Node item, boolean empty) {
            super.updateItem(item, empty);

            pane.paddingProperty().unbind();
            title.textProperty().unbind();
            message.textProperty().unbind();

            bar.setSmoothProgress(false);
            bar.progressProperty().unbind();
            @Nullable StageNode prevStageNode;
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
                setCursor(Cursor.DEFAULT);
            } else if (item instanceof StageNode stageNode) {
                title.textProperty().bind(stageNode.title);
                message.setText("");
                bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

                pane.setPadding(Insets.EMPTY);
                pane.setLeft(stageLeading);
                pane.setRight(message);
                pane.setBottom(null);
                setCursor(Cursor.HAND);

                updateLeftIcon(stageNode.status.get());
                updateExpandArrow(stageNode);
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
                setCursor(Cursor.DEFAULT);
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
        private int runningTasksCount = 0;

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
        /// Whether this stage currently shows its individual task rows.
        private final BooleanProperty expanded;
        /// Significant tasks still running in this stage, retained while the stage is collapsed.
        private final Set<Task<?>> activeTasks = new LinkedHashSet<>();
        private final String message;
        private int count = 0;
        private int total = 0;

        private StageNode(String stage) {
            String stageKey;
            @Nullable String stageValue;

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
            expanded = new SimpleBooleanProperty(this, "expanded", !isCollapsedByDefault(stage));
        }

        private boolean isExpanded() {
            return expanded.get();
        }

        private void setExpanded(boolean expanded) {
            this.expanded.set(expanded);
        }

        private void begin() {
            runningTasksCount++;
            if (status.get() == Status.WAITING || status.get() == Status.SUCCESS) {
                status.set(Status.RUNNING);
            }
        }

        public void succeed() {
            runningTasksCount = Math.max(0, runningTasksCount - 1);

            if (runningTasksCount == 0) {
                status.set(Status.SUCCESS);
            }
        }

        public void fail() {
            runningTasksCount = Math.max(0, runningTasksCount - 1);
            status.set(Status.FAILED);
        }

        public void count() {
            updateCounter(++count, total);
        }

        public void addTotal(int n) {
            this.total += n;
            updateCounter(count, total);
        }

        public void updateCounter(int count, int total) {
            title.setValue(total > 0
                    ? message + " - " + count + "/" + total
                    : message
            );
        }
    }

    private static final class ProgressListNode extends Node {
        private final Task<?> task;
        /// The stage that owns this row, or `null` for a top-level task.
        private final @Nullable StageNode stageNode;
        private final String title;
        private final StringProperty message = new SimpleStringProperty("");
        private final DoubleProperty progress = new SimpleDoubleProperty(0.0);

        private ProgressListNode(Task<?> task, @Nullable StageNode stageNode) {
            this.task = task;
            this.stageNode = stageNode;
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
