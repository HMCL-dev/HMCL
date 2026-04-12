/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.download.DifferentDownloadTask2OneTask;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.download.DownloadEntry;

import static org.jackhuang.hmcl.util.i18n.I18n.formatSpeed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DownloadTaskList extends DownloadListPage implements PageAware{
    private final ObservableList<DownloadEntry> entries = FXCollections.observableArrayList();
    private final ListView<DownloadEntry> listView = new ListView<>(entries);
    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    public ObservableList<DownloadEntry> getEntries() {
        return entries;
    }

    public DownloadTaskList() {
        super(null);
        listView.setCellFactory(lv -> new DownloadEntryCell());
    }

    public void cleanupFinishedTasks() {
        Platform.runLater(() -> {
            entries.removeIf(entry -> {
                String status = entry.getStatus();
                return DownloadEntry.STATUS_DONE.equals(status) || (status != null && status.startsWith(DownloadEntry.STATUS_FAILED));
            });
        });
    }

    @Override
    public void onPageShown() {
        DifferentDownloadTask2OneTask.setActiveDownloadTaskList(this);
    }

    @Override
    public void onPageHidden() {
        //打开页面时运行
//        cleanupFinishedTasks();
        // DifferentDownloadTask2OneTask.setActiveDownloadTaskList(null);
    }

    @Override
    public void loadVersion(Profile profile, String version) {}

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DownloadTaskListSkin(this);
    }

    private static class DownloadTaskListSkin extends SkinBase<DownloadTaskList> {
        protected DownloadTaskListSkin(DownloadTaskList control) {
            super(control);
            BorderPane pane = new BorderPane();
            pane.setCenter(control.listView);

            HBox bottomBar = new HBox(10);
            HBox labelBar = new HBox(10);

            Insets insets = new Insets(10);

            bottomBar.setAlignment(Pos.CENTER_RIGHT);
            bottomBar.setPadding(insets);

            labelBar.setAlignment(Pos.CENTER_LEFT);
            labelBar.setPadding(new Insets(5, 10, 5, 10));

            Label globalSpeedLabel = new Label();
            globalSpeedLabel.setAlignment(Pos.CENTER_LEFT);
            globalSpeedLabel.setStyle("-fx-text-fill: black; -fx-font-size: 1em;");

            Button clearBtn = new JFXButton(i18n("download.task.button.clear_uesless"));
            clearBtn.getStyleClass().add("small-button");
            clearBtn.setOnAction(e -> control.cleanupFinishedTasks());

            Button pauseAllBtn = new JFXButton(i18n("download.task.button.pause_all"));
            pauseAllBtn.setOnAction(e -> control.getEntries().stream()
                    .filter(entry -> DownloadEntry.STATUS_RUNNING.equals(entry.getStatus()))
                    .forEach(entry -> entry.getExecutor().cancel()
                    ));

            pauseAllBtn.disableProperty().bind(
                    Bindings.createBooleanBinding(
                            () -> control.getEntries().stream().noneMatch(e -> DownloadEntry.STATUS_RUNNING.equals(e.getStatus())),
                            control.getEntries()
                    )
            );

            Button retryFailedBtn = new JFXButton(i18n("download.task.button.retry_all"));
            retryFailedBtn.setOnAction(e -> control.getEntries().stream()
                    .filter(entry -> entry.getStatus().startsWith(DownloadEntry.STATUS_FAILED))
                    .forEach(entry -> {
                        entry.getExecutor().start();
                        entry.statusProperty().set(DownloadEntry.STATUS_WAITING);
                    }));


            FetchTask.SPEED_EVENT.register(event -> {
                long bytesPerSec = event.getSpeed();
                Platform.runLater(() -> globalSpeedLabel.setText(formatSpeed(bytesPerSec)));
            });

            bottomBar.getChildren().addAll(pauseAllBtn, retryFailedBtn, clearBtn);
            labelBar.getChildren().addAll(globalSpeedLabel);

            VBox bottomContainer = new VBox(5);
            bottomContainer.getChildren().addAll(labelBar, bottomBar);
            pane.setBottom(bottomContainer);

            getChildren().add(pane);
        }
    }

    private static class DownloadEntryCell extends ListCell<DownloadEntry> {
        private final JFXButton actionButton = new JFXButton();
        private final TitledPane titledPane = new TitledPane();
        private final ProgressBar progressBar = new ProgressBar();
        private final Label nameLabel = new Label();
        private final Label statusLabel = new Label();
        private final HBox titleBox = new HBox(8); // 间距8px
        private final Region spacer = new Region();


        private javafx.beans.binding.Binding<Number> maxWidthBinding;
        private EventHandler<ActionEvent> actionHandler;

        public DownloadEntryCell() {
            nameLabel.setMaxWidth(350);
            nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            nameLabel.setStyle("-fx-font-weight: bold;");

            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox.setHgrow(actionButton, Priority.NEVER);

            statusLabel.setStyle("-fx-text-fill: gray;");

            progressBar.setMaxWidth(Double.MAX_VALUE);

            titledPane.setContent(progressBar);
            titledPane.setExpanded(false);
            titledPane.setAnimated(false);
            titledPane.setGraphic(titleBox);
            titledPane.setText(null);

            actionButton.getStyleClass().add("small-button");
            actionButton.setFocusTraversable(false);
            actionButton.setMinWidth(60);
            actionButton.setVisible(false);

            titleBox.getChildren().setAll(nameLabel, spacer, statusLabel, actionButton);
            titleBox.setAlignment(Pos.CENTER_LEFT);

            getStyleClass().add("download-task-cell");
        }
        @Override
        protected void updateItem(DownloadEntry entry, boolean empty) {
            super.updateItem(entry, empty);

            if (actionHandler != null) {
                actionButton.setOnAction(null);
                actionHandler = null;
            }

            if (maxWidthBinding != null) {
                nameLabel.maxWidthProperty().unbind();
                maxWidthBinding = null;
            }

            if (empty || entry == null) {
                setGraphic(null);
                progressBar.progressProperty().unbind();
                nameLabel.textProperty().unbind();
                statusLabel.textProperty().unbind();
                actionButton.setVisible(false);
            } else {
                nameLabel.textProperty().bind(entry.nameProperty());
                statusLabel.textProperty().bind(entry.statusI18NBinding());
                progressBar.progressProperty().bind(entry.progressProperty());

                String status = entry.getStatus();

                if (DownloadEntry.STATUS_RUNNING.equals(status) || DownloadEntry.STATUS_WAITING.equals(status)) {
                    actionButton.setText(i18n("button.cancel"));
                    actionButton.setVisible(true);
                    actionHandler = e -> entry.getExecutor().cancel();
                    actionButton.setOnAction(actionHandler);
                } else if (DownloadEntry.STATUS_FAILED.equals(status)) {
                    actionButton.setText(i18n("button.retry"));
                    actionButton.setVisible(true);
                    actionHandler = e -> {
                        // 重试
                        entry.getExecutor().start();
                        entry.statusProperty().set(DownloadEntry.STATUS_WAITING);
                    };
                    actionButton.setOnAction(actionHandler);
                } else {
                    actionButton.setVisible(false);
                }

                ListView<DownloadEntry> listView = getListView();
                if (listView != null) {
                    maxWidthBinding = Bindings.createDoubleBinding(
                            () -> {
                                double listWidth = listView.getWidth();
                                double reserved = 160;
                                return Math.max(50, listWidth - reserved);
                            },
                            listView.widthProperty()
                    );
                    nameLabel.maxWidthProperty().bind(maxWidthBinding);
                }

                titledPane.setExpanded(DownloadEntry.STATUS_RUNNING.equals(entry.getStatus()));

                setGraphic(titledPane);
            }
        }
    }

    public void addDownloadEntry(TaskExecutor executor, String name) {
        DownloadEntry entry = new DownloadEntry(executor, name);
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onRunning(Task<?> task) {
                Platform.runLater(() -> entry.statusProperty().set(DownloadEntry.STATUS_RUNNING));
            }

            @Override
            public void onPropertiesUpdate(Task<?> task) {
                Object progressObj = task.getProperties().get("progress");
                if (progressObj instanceof Number) {
                    double progress = ((Number) progressObj).doubleValue();
                    Platform.runLater(() -> entry.progressProperty().set(progress));
                }
            }

            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                Platform.runLater(() -> {
                    if (success) {
                        entry.statusProperty().set(DownloadEntry.STATUS_DONE);
                        entry.progressProperty().set(1.0);
                    } else {
                        entry.statusProperty().set(DownloadEntry.STATUS_FAILED);
                    }
                });
            }

            @Override
            public void onFailed(Task<?> task, Throwable throwable) {
                Platform.runLater(() -> {
                    entry.statusProperty().set(DownloadEntry.STATUS_FAILED + ": " + throwable.getMessage());
                    entry.progressProperty().set(1.0);
                });
            }
        });
        executor.start();
        Platform.runLater(() -> entries.add(entry));
    }
}