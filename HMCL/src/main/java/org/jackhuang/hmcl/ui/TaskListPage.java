// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Subscription;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3ListCell;
import org.glavo.m3fx.controls.M3ListItem;
import org.glavo.m3fx.controls.M3ListView;
import org.glavo.m3fx.controls.M3ProgressIndicator;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.TaskManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays the application-wide active tasks and pending completion actions.
@NotNullByDefault
public final class TaskListPage extends StackPane {

    /// Creates the live task list and its empty-state message.
    public TaskListPage() {
        setPadding(new Insets(10));
        getStyleClass().add("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");

        M3ListView<TaskExecutorDialogPane> list = new M3ListView<>();
        Bindings.bindContent(list.getItems(), TaskManager.getInstance().getTasks());
        list.setCellFactory(TaskCell::new);
        list.setPadding(Insets.EMPTY);

        StackPane placeholder = new StackPane(new Label(i18n("task.list.empty")));
        placeholder.getStyleClass().add("notice-pane");
        var content = FXUtils.withListPlaceholder(list, placeholder);

        ComponentList.setVgrow(content, Priority.ALWAYS);
        root.getContent().setAll(content);
        getChildren().setAll(root);
    }

    /// Displays a live task title and an action to restore progress or continue a completed task.
    @NotNullByDefault
    private static final class TaskCell extends M3ListCell<TaskExecutorDialogPane> {

        /// Observes changes to the displayed task until this cell is reused.
        private Subscription statusSubscription = Subscription.EMPTY;

        /// Centers running and pending indicators in a fixed-size slot to keep row titles aligned.
        private final StackPane statusIcon = new StackPane();

        /// Restores progress or invokes the pending completion action.
        private final M3Button actionButton = new M3Button("", M3ButtonVariant.TEXT);

        /// Describes the current row action.
        private final Tooltip actionTooltip = new Tooltip();

        /// Creates a row that activates the current task through its body or trailing action.
        private TaskCell(M3ListView<TaskExecutorDialogPane> view) {
            super(view);
            FXUtils.setLimitWidth(statusIcon, 32);
            FXUtils.setLimitHeight(statusIcon, 32);
            statusIcon.setMouseTransparent(true);
            FXUtils.installFastTooltip(actionButton, actionTooltip);
            actionButton.setOnAction(event -> {
                event.consume();
                activateTask();
            });
        }

        /// Creates the shared row and its nested task action.
        @Override
        protected M3ListItem createListItem() {
            var row = new M3ListItem();
            row.setLeading(statusIcon);
            row.setTrailing(actionButton);
            row.headlineTextProperty().bind(itemProperty().flatMap(TaskExecutorDialogPane::titleProperty).orElse(""));
            row.setOnAction(event -> {
                if (event.getTarget() == row)
                    activateTask();
            });
            return row;
        }

        /// Invokes a pending action or restores the current task's progress dialog.
        private void activateTask() {
            @Nullable TaskExecutorDialogPane pane = getItem();
            if (pane != null) {
                if (pane.pendingActionProperty().get() != null)
                    pane.runPendingAction();
                else
                    pane.restore();
            }
        }

        /// Releases listeners before the virtual flow assigns another task.
        @Override
        protected void updateItem(@Nullable TaskExecutorDialogPane pane, boolean empty) {
            statusSubscription.unsubscribe();
            super.updateItem(pane, empty);
        }

        /// Observes changes to an existing task even when the list membership stays the same.
        @Override
        protected void updateListItem(M3ListItem row, TaskExecutorDialogPane pane) {
            Runnable refresh = () -> updateStatus(row, pane);
            statusSubscription = Subscription.combine(pane.pendingActionProperty().subscribe(refresh),
                    pane.queuedProperty().subscribe(refresh));
            refresh.run();
        }

        /// Shows the action and status of the current task.
        private void updateStatus(M3ListItem row, TaskExecutorDialogPane pane) {
            @Nullable TaskExecutorDialogPane.PendingAction action = pane.pendingActionProperty().get();
            if (action != null) {
                actionButton.setVariant(M3ButtonVariant.OUTLINED);
                statusIcon.getChildren().setAll(SVG.PENDING.createIcon(24));
                row.setSupportingText(action.description());
                actionButton.setGraphic(null);
                actionButton.setText(action.text());
                actionButton.setAccessibleText(action.text());
                actionTooltip.setText(action.text());
            } else {
                row.setSupportingText("");
                actionButton.setVariant(M3ButtonVariant.TEXT);
                if (pane.queuedProperty().get()) {
                    statusIcon.getChildren().setAll(SVG.PENDING.createIcon(24));
                    row.setSupportingText(i18n("task.queued"));
                } else if (statusIcon.getChildren().isEmpty() || !(statusIcon.getChildren().get(0) instanceof M3ProgressIndicator)) {
                    M3ProgressIndicator spinner = FXUtils.newSpinner();
                    spinner.setIndicatorSize(24);
                    statusIcon.getChildren().setAll(spinner);
                }
                actionButton.setGraphic(SVG.ARROW_FORWARD.createIcon());
                actionButton.setText(null);
                actionButton.setAccessibleText(i18n("task.show"));
                actionTooltip.setText(i18n("task.show"));
            }
        }
    }
}
