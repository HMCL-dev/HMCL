/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.download.cleanroom.CleanroomInstallTask;
import org.jackhuang.hmcl.download.fabric.FabricAPIInstallTask;
import org.jackhuang.hmcl.download.fabric.FabricInstallTask;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.download.forge.ForgeOldInstallTask;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameInstallTask;
import org.jackhuang.hmcl.download.java.mojang.MojangJavaDownloadTask;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricInstallTask;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderInstallTask;
import org.jackhuang.hmcl.download.neoforge.NeoForgeInstallTask;
import org.jackhuang.hmcl.download.neoforge.NeoForgeOldInstallTask;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.download.quilt.QuiltAPIInstallTask;
import org.jackhuang.hmcl.download.quilt.QuiltInstallTask;
import org.jackhuang.hmcl.game.HMCLModpackInstallTask;
import org.jackhuang.hmcl.java.JavaInstallTask;
import org.jackhuang.hmcl.mod.MinecraftInstanceTask;
import org.jackhuang.hmcl.mod.ModpackInstallTask;
import org.jackhuang.hmcl.mod.ModpackUpdateTask;
import org.jackhuang.hmcl.mod.curse.CurseCompletionTask;
import org.jackhuang.hmcl.mod.curse.CurseInstallTask;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackCompletionTask;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackExportTask;
import org.jackhuang.hmcl.mod.modrinth.ModrinthCompletionTask;
import org.jackhuang.hmcl.mod.modrinth.ModrinthInstallTask;
import org.jackhuang.hmcl.mod.modrinth.ModrinthModpackExportTask;
import org.jackhuang.hmcl.mod.multimc.MultiMCModpackExportTask;
import org.jackhuang.hmcl.mod.multimc.MultiMCModpackInstallTask;
import org.jackhuang.hmcl.mod.server.ServerModpackCompletionTask;
import org.jackhuang.hmcl.mod.server.ServerModpackExportTask;
import org.jackhuang.hmcl.mod.server.ServerModpackLocalInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.FXThread;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class TaskListPane extends StackPane {
    private static final Insets DEFAULT_PROGRESS_NODE_PADDING = new Insets(0, 0, 8, 0);
    private static final Insets STAGED_PROGRESS_NODE_PADDING = new Insets(0, 0, 8, 26);

    private TaskExecutor executor;
    private final JFXListView<Node> listView = new JFXListView<>();
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
    private void addStages(@NotNull Collection<String> stages) {
        for (String stage : stages) {
            stageNodes.computeIfAbsent(stage, s -> {
                StageNode node = new StageNode(stage);
                listView.getItems().add(node);
                return node;
            });
        }
    }

    @FXThread
    private void updateProgressNodePadding() {
        progressNodePadding.set(stageNodes.isEmpty() ? DEFAULT_PROGRESS_NODE_PADDING : STAGED_PROGRESS_NODE_PADDING);
    }

    public void setExecutor(TaskExecutor executor) {
        this.executor = executor;
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStart() {
                Platform.runLater(() -> {
                    stageNodes.clear();
                    listView.getItems().clear();
                    addStages(executor.getStages());
                    updateProgressNodePadding();
                });
            }

            @Override
            public void onReady(Task<?> task) {
                if (task instanceof Task.StagesHintTask) {
                    Platform.runLater(() -> {
                        addStages(((Task<?>.StagesHintTask) task).getStages());
                        updateProgressNodePadding();
                    });
                }

                if (task.getStage() != null) {
                    Platform.runLater(() -> {
                        StageNode node = stageNodes.get(task.getStage());
                        if (node != null)
                            node.begin();
                    });
                }
            }

            @Override
            public void onRunning(Task<?> task) {
                if (!task.getSignificance().shouldShow() || task.getName() == null)
                    return;

                if (task instanceof GameAssetDownloadTask) {
                    task.setName(i18n("assets.download_all"));
                } else if (task instanceof GameInstallTask) {
                    if (task.getInheritedStage() != null && task.getInheritedStage().startsWith("hmcl.install.game"))
                        return;
                    task.setName(i18n("install.installer.install", i18n("install.installer.game")));
                } else if (task instanceof CleanroomInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.cleanroom")));
                } else if (task instanceof LegacyFabricInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.legacyfabric")));
                } else if (task instanceof ForgeNewInstallTask || task instanceof ForgeOldInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.forge")));
                } else if (task instanceof NeoForgeInstallTask || task instanceof NeoForgeOldInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.neoforge")));
                } else if (task instanceof LiteLoaderInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.liteloader")));
                } else if (task instanceof OptiFineInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.optifine")));
                } else if (task instanceof FabricInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.fabric")));
                } else if (task instanceof FabricAPIInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.fabric-api")));
                } else if (task instanceof QuiltInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.quilt")));
                } else if (task instanceof QuiltAPIInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.quilt-api")));
                } else if (task instanceof CurseCompletionTask || task instanceof ModrinthCompletionTask || task instanceof ServerModpackCompletionTask || task instanceof McbbsModpackCompletionTask) {
                    task.setName(i18n("modpack.completion"));
                } else if (task instanceof ModpackInstallTask) {
                    task.setName(i18n("modpack.installing"));
                } else if (task instanceof ModpackUpdateTask) {
                    task.setName(i18n("modpack.update"));
                } else if (task instanceof CurseInstallTask) {
                    task.setName(i18n("modpack.installing.given", i18n("modpack.type.curse")));
                } else if (task instanceof MultiMCModpackInstallTask) {
                    task.setName(i18n("modpack.installing.given", i18n("modpack.type.multimc")));
                } else if (task instanceof ModrinthInstallTask) {
                    task.setName(i18n("modpack.installing.given", i18n("modpack.type.modrinth")));
                } else if (task instanceof ServerModpackLocalInstallTask) {
                    task.setName(i18n("install.installing") + ": " + i18n("modpack.type.server"));
                } else if (task instanceof HMCLModpackInstallTask) {
                    task.setName(i18n("modpack.installing.given", i18n("modpack.type.hmcl")));
                } else if (task instanceof McbbsModpackExportTask || task instanceof MultiMCModpackExportTask || task instanceof ServerModpackExportTask || task instanceof ModrinthModpackExportTask) {
                    task.setName(i18n("modpack.export"));
                } else if (task instanceof MinecraftInstanceTask) {
                    task.setName(i18n("modpack.scan"));
                } else if (task instanceof MojangJavaDownloadTask) {
                    task.setName(i18n("download.java"));
                } else if (task instanceof JavaInstallTask) {
                    task.setName(i18n("java.installing"));
                }

                Platform.runLater(() -> {
                    ProgressListNode node = new ProgressListNode(task);
                    nodes.put(task, node);
                    StageNode stageNode = stageNodes.get(task.getInheritedStage());
                    listView.getItems().add(listView.getItems().indexOf(stageNode) + 1, node);
                });
            }

            @Override
            public void onFinished(Task<?> task) {
                Platform.runLater(() -> {
                    if (task.getStage() != null) {
                        StageNode stageNode = stageNodes.get(task.getStage());
                        if (stageNode != null)
                            stageNode.succeed();
                    }

                    ProgressListNode node = nodes.remove(task);
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
                        StageNode stageNode = stageNodes.get(task.getStage());
                        if (stageNode != null)
                            stageNode.fail();
                    });
                }
                ProgressListNode node = nodes.remove(task);
                if (node != null)
                    Platform.runLater(() -> node.setThrowable(throwable));
            }

            @Override
            public void onPropertiesUpdate(Task<?> task) {
                if (task instanceof Task.CountTask) {
                    runInFX(() -> {
                        StageNode stageNode = stageNodes.get(((Task<?>.CountTask) task).getCountStage());
                        if (stageNode != null)
                            stageNode.count();
                    });

                    return;
                }

                if (task.getStage() != null) {
                    int total = tryCast(task.getProperties().get("total"), Integer.class).orElse(0);
                    runInFX(() -> {
                        StageNode stageNode = stageNodes.get(task.getStage());
                        if (stageNode != null)
                            stageNode.addTotal(total);
                    });
                }
            }
        });
    }

    private final class Cell extends ListCell<Node> {
        private static final double STATUS_ICON_SIZE = 14;

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

        private void begin() {
            if (status.get() == Status.WAITING) {
                status.set(Status.RUNNING);
            }
        }

        public void fail() {
            status.set(Status.FAILED);
        }

        public void succeed() {
            status.set(Status.SUCCESS);
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
