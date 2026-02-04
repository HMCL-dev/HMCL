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
package org.jackhuang.hmcl.ui.task;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;

import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class TaskCenterPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("task.manage")));

    private final TransitionPane transitionPane = new TransitionPane();
    private final TabHeader tabHeader;

    private final TabHeader.Tab<ScrollPane> runningTab = new TabHeader.Tab<>("taskRunningTab");
    private final TabHeader.Tab<ScrollPane> completedTab = new TabHeader.Tab<>("taskCompletedTab");
    private final TabHeader.Tab<ScrollPane> failedTab = new TabHeader.Tab<>("taskFailedTab");
    private final TabHeader.Tab<ScrollPane> settingsTab = new TabHeader.Tab<>("taskSettingsTab");

    private final VBox runningContainer = new VBox(8);
    private final VBox completedContainer = new VBox(8);
    private final VBox failedContainer = new VBox(8);

    public TaskCenterPage() {
        runningTab.setNodeSupplier(this::createRunningPane);
        completedTab.setNodeSupplier(this::createCompletedPane);
        failedTab.setNodeSupplier(this::createFailedPane);
        settingsTab.setNodeSupplier(() -> createPlaceholderPane(i18n("task.settings")));

        tabHeader = new TabHeader(transitionPane, runningTab, completedTab, failedTab, settingsTab);
        tabHeader.select(runningTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("task.manage").toUpperCase())
                .addNavigationDrawerTab(tabHeader, runningTab, i18n("task.running"), SVG.ARROW_FORWARD)
                .addNavigationDrawerTab(tabHeader, completedTab, i18n("task.completed"), SVG.CHECK)
                .addNavigationDrawerTab(tabHeader, failedTab, i18n("task.failed"), SVG.CLOSE)
                .addNavigationDrawerTab(tabHeader, settingsTab, i18n("task.settings"), SVG.SETTINGS);

        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);

        BorderPane contentWrapper = new BorderPane();
        contentWrapper.getStyleClass().add("card-non-transparent");
        contentWrapper.setPadding(new Insets(12));
        contentWrapper.setCenter(transitionPane);

        StackPane centerPane = new StackPane(contentWrapper);
        centerPane.setPadding(new Insets(12));
        setCenter(centerPane);
    }

    private ScrollPane createRunningPane() {
        ScrollPane scrollPane = new ScrollPane(runningContainer);
        scrollPane.setFitToWidth(true);
        runningContainer.setPadding(new Insets(12));

        TaskCenter.getInstance().getEntries().addListener((ListChangeListener<? super TaskCenter.Entry>) change -> rebuildRunning());
        rebuildRunning();

        return scrollPane;
    }

    private ScrollPane createCompletedPane() {
        ScrollPane scrollPane = new ScrollPane(completedContainer);
        scrollPane.setFitToWidth(true);
        completedContainer.setPadding(new Insets(12));

        TaskCenter.getInstance().getCompletedEntries()
                .addListener((ListChangeListener<? super TaskCenter.Entry>) change -> rebuildCompleted());
        rebuildCompleted();

        return scrollPane;
    }

    private ScrollPane createFailedPane() {
        ScrollPane scrollPane = new ScrollPane(failedContainer);
        scrollPane.setFitToWidth(true);
        failedContainer.setPadding(new Insets(12));

        TaskCenter.getInstance().getFailedEntries()
                .addListener((ListChangeListener<? super TaskCenter.Entry>) change -> rebuildFailed());
        rebuildFailed();

        return scrollPane;
    }

    private void rebuildRunning() {
        runningContainer.getChildren().clear();
        for (TaskCenter.Entry entry : TaskCenter.getInstance().getEntries()) {
            runningContainer.getChildren().add(createRunningItem(entry));
        }
    }

    private ScrollPane createPlaceholderPane(String text) {
        VBox box = new VBox();
        box.setPadding(new Insets(12));
        box.getChildren().add(new Label(text));
        ScrollPane scrollPane = new ScrollPane(box);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    private void rebuildCompleted() {
        completedContainer.getChildren().clear();
        for (TaskCenter.Entry entry : TaskCenter.getInstance().getCompletedEntries()) {
            Label item = new Label(entry.getDetail() != null ? entry.getDetail() : entry.getTitle());
            item.getStyleClass().add("md-list-cell");
            item.setPadding(new Insets(10, 12, 10, 12));
            completedContainer.getChildren().add(item);
        }
    }

    private void rebuildFailed() {
        failedContainer.getChildren().clear();
        for (TaskCenter.Entry entry : TaskCenter.getInstance().getFailedEntries()) {
            Label item = new Label(entry.getDetail() != null ? entry.getDetail() : entry.getTitle());
            item.getStyleClass().add("md-list-cell");
            item.setPadding(new Insets(10, 12, 10, 12));

            item.setOnMouseClicked(e -> {
                Throwable ex = entry.getExecutor().getException();
                if (ex instanceof CancellationException) {
                    Controllers.dialog("任务由用户取消", entry.getTitle(), MessageDialogPane.MessageType.ERROR);
                } else if (ex != null) {
                    Controllers.dialog(StringUtils.getStackTrace(ex), entry.getTitle(), MessageDialogPane.MessageType.ERROR);
                } else {
                    Controllers.dialog("任务失败（无异常信息）", entry.getTitle(), MessageDialogPane.MessageType.ERROR);
                }
            });

            failedContainer.getChildren().add(item);
        }
    }

    private Node createRunningItem(TaskCenter.Entry entry) {
        HBox row = new HBox(12);
        row.getStyleClass().add("md-list-cell");
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setAlignment(Pos.CENTER_LEFT);

        String text = entry.getDetail() != null ? entry.getDetail() : entry.getTitle();
        Label label = new Label(text);
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setMaxWidth(Double.MAX_VALUE);

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");

        cancelButton.setOnAction(e -> {
            entry.getExecutor().cancel();
            e.consume();
        });
        cancelButton.setOnMouseClicked(e -> e.consume());

        row.getChildren().addAll(label, cancelButton);

        row.setOnMouseClicked(e -> {
            TaskExecutorDialogPane pane = Controllers.taskDialog(entry.getExecutor(), entry.getTitle(), TaskCancellationAction.NORMAL);
            pane.setEscAction(() -> pane.fireEvent(new DialogCloseEvent()));
            pane.refreshTaskList();
        });

        return row;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}