/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXProgressBar;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.download.fabric.FabricInstallTask;
import org.jackhuang.hmcl.download.forge.ForgeInstallTask;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameInstallTask;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderInstallTask;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.game.HMCLModpackExportTask;
import org.jackhuang.hmcl.game.HMCLModpackInstallTask;
import org.jackhuang.hmcl.mod.MinecraftInstanceTask;
import org.jackhuang.hmcl.mod.ModpackInstallTask;
import org.jackhuang.hmcl.mod.ModpackUpdateTask;
import org.jackhuang.hmcl.mod.curse.CurseCompletionTask;
import org.jackhuang.hmcl.mod.curse.CurseInstallTask;
import org.jackhuang.hmcl.mod.multimc.MultiMCModpackInstallTask;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskStages;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class TaskListPane extends StackPane {
    private TaskExecutor executor;
    private final AdvancedListBox listBox = new AdvancedListBox();
    private final Map<Task<?>, ProgressListNode> nodes = new HashMap<>();
    private final List<StageNode> stageNodes = new ArrayList<>();
    private final ObjectProperty<Insets> progressNodePadding = new SimpleObjectProperty<>(Insets.EMPTY);

    public TaskListPane() {
        listBox.setSpacing(0);

        getChildren().setAll(listBox);
    }

    public void setExecutor(TaskExecutor executor) {
        TaskStages stages = executor.getStages();
        this.executor = executor;
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStart() {
                Platform.runLater(() -> {
                    stageNodes.clear();
                    listBox.clear();
                    stageNodes.addAll(stages.getStages().stream().map(StageNode::new).collect(Collectors.toList()));
                    stageNodes.forEach(listBox::add);

                    if (stages.getStages().isEmpty()) progressNodePadding.setValue(new Insets(0, 0, 8, 0));
                    else progressNodePadding.setValue(new Insets(0, 0, 8, 26));
                });
            }

            @Override
            public void onReady(Task<?> task) {
                if (task instanceof Task.StageTask) {
                    Platform.runLater(() -> {
                        stageNodes.stream().filter(x -> x.stage.equals(task.getStage())).findAny().ifPresent(StageNode::begin);
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
                    task.setName(i18n("install.installer.install", i18n("install.installer.game")));
                } else if (task instanceof ForgeInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.forge")));
                } else if (task instanceof LiteLoaderInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.liteloader")));
                } else if (task instanceof OptiFineInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.optifine")));
                } else if (task instanceof FabricInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.fabric")));
                } else if (task instanceof CurseCompletionTask) {
                    task.setName(i18n("modpack.type.curse.completion"));
                } else if (task instanceof ModpackInstallTask) {
                    task.setName(i18n("modpack.installing"));
                } else if (task instanceof ModpackUpdateTask) {
                    task.setName(i18n("modpack.update"));
                } else if (task instanceof CurseInstallTask) {
                    task.setName(i18n("modpack.install", i18n("modpack.type.curse")));
                } else if (task instanceof MultiMCModpackInstallTask) {
                    task.setName(i18n("modpack.install", i18n("modpack.type.multimc")));
                } else if (task instanceof HMCLModpackInstallTask) {
                    task.setName(i18n("modpack.install", i18n("modpack.type.hmcl")));
                } else if (task instanceof HMCLModpackExportTask) {
                    task.setName(i18n("modpack.export"));
                } else if (task instanceof MinecraftInstanceTask) {
                    task.setName(i18n("modpack.scan"));
                }

                ProgressListNode node = new ProgressListNode(task);
                nodes.put(task, node);
                Platform.runLater(() -> {
                    StageNode stageNode = stageNodes.stream().filter(x -> x.stage.equals(task.getStage())).findAny().orElse(null);
                    listBox.add(listBox.indexOf(stageNode) + 1, node);
                });
            }

            @Override
            public void onFinished(Task<?> task) {
                if (task instanceof Task.StageTask) {
                    Platform.runLater(() -> {
                        stageNodes.stream().filter(x -> x.stage.equals(task.getStage())).findAny().ifPresent(StageNode::succeed);
                    });
                }

                ProgressListNode node = nodes.remove(task);
                if (node == null)
                    return;
                node.unbind();
                Platform.runLater(() -> {
                    listBox.remove(node);
                });
            }

            @Override
            public void onFailed(Task<?> task, Throwable throwable) {
                if (task instanceof Task.StageTask) {
                    Platform.runLater(() -> {
                        stageNodes.stream().filter(x -> x.stage.equals(task.getStage())).findAny().ifPresent(StageNode::fail);
                    });
                }
                ProgressListNode node = nodes.remove(task);
                if (node == null)
                    return;
                Platform.runLater(() -> {
                    node.setThrowable(throwable);
                });
            }
        });
    }

    private class StageNode extends BorderPane {
        private final String stage;
        private final Label title = new Label();
        private boolean started = false;

        public StageNode(String stage) {
            this.stage = stage;

            title.setText(executor.getStages().localize(stage));
            BorderPane.setAlignment(title, Pos.CENTER_LEFT);
            BorderPane.setMargin(title, new Insets(0, 0, 0, 8));
            setPadding(new Insets(0, 0, 8, 4));
            setCenter(title);
            setLeft(FXUtils.limitingSize(SVG.dotsHorizontal(Theme.blackFillBinding(), 14, 14), 14, 14));
        }

        public void begin() {
            if (started) return;
            started = true;
            setLeft(FXUtils.limitingSize(SVG.arrowRight(Theme.blackFillBinding(), 14, 14), 14, 14));
        }

        public void fail() {
            setLeft(FXUtils.limitingSize(SVG.close(Theme.blackFillBinding(), 14, 14), 14, 14));
        }

        public void succeed() {
            setLeft(FXUtils.limitingSize(SVG.check(Theme.blackFillBinding(), 14, 14), 14, 14));
        }
    }

    private class ProgressListNode extends BorderPane {
        private final JFXProgressBar bar = new JFXProgressBar();
        private final Label title = new Label();
        private final Label state = new Label();
        private final DoubleBinding binding = Bindings.createDoubleBinding(() ->
                        getWidth() - getPadding().getLeft() - getPadding().getRight() - 100,
                paddingProperty(), widthProperty());

        public ProgressListNode(Task<?> task) {
            bar.progressProperty().bind(task.progressProperty());
            title.setText(task.getName());
            state.textProperty().bind(task.messageProperty());

            setLeft(title);
            setRight(state);
            setBottom(bar);

            bar.minWidthProperty().bind(binding);
            bar.prefWidthProperty().bind(binding);
            bar.maxWidthProperty().bind(binding);

            paddingProperty().bind(progressNodePadding);
        }

        public void unbind() {
            bar.progressProperty().unbind();
            state.textProperty().unbind();
        }

        public void setThrowable(Throwable throwable) {
            unbind();
            state.setText(throwable.getLocalizedMessage());
            bar.setProgress(0);
        }
    }
}
