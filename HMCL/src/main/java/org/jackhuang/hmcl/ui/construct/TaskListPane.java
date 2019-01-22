/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.download.forge.ForgeInstallTask;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderInstallTask;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.game.HMCLModpackExportTask;
import org.jackhuang.hmcl.game.HMCLModpackInstallTask;
import org.jackhuang.hmcl.mod.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;

import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class TaskListPane extends StackPane {
    private final JFXListView<Task> listBox = new JFXListView<>();
    private final Map<Task, ProgressListNode> nodes = new HashMap<>();
    private final ReadOnlyIntegerWrapper finishedTasks = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper totTasks = new ReadOnlyIntegerWrapper();

    public TaskListPane() {
        getChildren().setAll(listBox);

        listBox.setPadding(Insets.EMPTY);
        listBox.setCellFactory(listView -> new ProgressListNode());
    }

    public ReadOnlyIntegerProperty finishedTasksProperty() {
        return finishedTasks.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty totTasksProperty() {
        return totTasks.getReadOnlyProperty();
    }

    public void setExecutor(TaskExecutor executor) {
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStart() {
                Platform.runLater(() -> {
                    listBox.getItems().clear();
                    finishedTasks.set(0);
                    totTasks.set(0);
                });
            }

            @Override
            public void onReady(Task task) {
                Platform.runLater(() -> totTasks.set(totTasks.getValue() + 1));
            }

            @Override
            public void onRunning(Task task) {
                if (!task.getSignificance().shouldShow())
                    return;

                if (task instanceof GameAssetDownloadTask) {
                    task.setName(i18n("assets.download_all"));
                } else if (task instanceof ForgeInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.forge")));
                } else if (task instanceof LiteLoaderInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.liteloader")));
                } else if (task instanceof OptiFineInstallTask) {
                    task.setName(i18n("install.installer.install", i18n("install.installer.optifine")));
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

                Platform.runLater(() -> listBox.getItems().add(task));
            }

            @Override
            public void onFinished(Task task) {
                Platform.runLater(() -> {
                    if (listBox.getItems().remove(task))
                        finishedTasks.set(finishedTasks.getValue() + 1);
                });
            }
        });
    }

    private static class ProgressListNode extends ListCell<Task> {
        private final BorderPane borderPane = new BorderPane();
        private final JFXProgressBar bar = new JFXProgressBar();
        private final Label title = new Label();
        private final Label state = new Label();

        {
            borderPane.setLeft(title);
            borderPane.setRight(state);
            borderPane.setBottom(bar);
            borderPane.setMinWidth(0);
            borderPane.setPrefWidth(1);

            setPadding(Insets.EMPTY);

            bar.minWidthProperty().bind(widthProperty());
            bar.prefWidthProperty().bind(widthProperty());
            bar.maxWidthProperty().bind(widthProperty());
        }

        @Override
        protected void updateItem(Task item, boolean empty) {
            boolean wasEmpty = isEmpty();
            Task oldTask = getItem();

            if (!wasEmpty && oldTask != null) {
                bar.progressProperty().unbind();
                state.textProperty().unbind();
            }

            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(borderPane);
                bar.visibleProperty().bind(Bindings.createBooleanBinding(() -> item.progressProperty().get() != -1, item.progressProperty()));
                bar.progressProperty().bind(item.progressProperty());
                state.textProperty().bind(Bindings.createObjectBinding(() -> {
                    if (item.getState() == Task.TaskState.FAILED) {
                        return item.getLastException().getLocalizedMessage();
                    } else {
                        return item.messageProperty().get();
                    }
                }, item.messageProperty(), item.stateProperty()));
                title.setText(item.getName());
            }
        }
    }
}
