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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXSpinner;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.TaskManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays active tasks and pending completion actions.
@NotNullByDefault
public final class TaskManagerPage extends Control implements DecoratorPage {

    /// Navigation metadata for the task list.
    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("task.list")));

    /// Creates a page backed by the application-wide task manager.
    public TaskManagerPage() {
        getStyleClass().add("content-background");
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected SkinBase<TaskManagerPage> createDefaultSkin() {
        return new Skin(this);
    }

    /// Binds a list view to active dialogs and pending completion actions.
    @NotNullByDefault
    private static final class Skin extends SkinBase<TaskManagerPage> {

        /// Creates the task list and its empty-state message.
        private Skin(TaskManagerPage control) {
            super(control);
            StackPane container = new StackPane();
            container.setPadding(new Insets(10));
            container.getStyleClass().add("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");

            JFXListView<TaskExecutorDialogPane> list = new JFXListView<>();
            list.setItems(TaskManager.getInstance().getTasks());
            list.setCellFactory(view -> new TaskCell());
            list.setPadding(Insets.EMPTY);
            list.getStyleClass().add("no-horizontal-scrollbar");
            FXUtils.ignoreEvent(list, KeyEvent.KEY_PRESSED, event -> event.getCode() == KeyCode.ESCAPE);

            StackPane placeholder = new StackPane(new Label(i18n("task.list.empty")));
            placeholder.getStyleClass().add("notice-pane");
            list.setPlaceholder(placeholder);

            ComponentList.setVgrow(list, Priority.ALWAYS);
            root.getContent().setAll(list);
            container.getChildren().setAll(root);
            getChildren().setAll(container);
        }
    }

    /// Displays a live task title and an action to restore progress or continue a completed task.
    @NotNullByDefault
    private static final class TaskCell extends ListCell<@Nullable TaskExecutorDialogPane> {

        /// The list item whose title is bound to the current dialog.
        private final TwoLineListItem content = new TwoLineListItem();

        /// The row's shared hover and ripple feedback.
        private final RipplerContainer graphic;

        /// Displays progress while running and a completion icon while awaiting user input.
        private final StackPane statusIcon = new StackPane();

        /// Restores progress or invokes the pending completion action.
        private final JFXButton actionButton = FXUtils.newToggleButton4(SVG.ARROW_FORWARD);

        /// Describes the current row action.
        private final Tooltip actionTooltip = new Tooltip();

        /// Creates a row that activates the current task through its body or trailing action.
        private TaskCell() {
            BorderPane row = new BorderPane();
            row.getStyleClass().add("md-list-cell");
            row.setPadding(new Insets(8));
            row.setCursor(Cursor.HAND);

            content.setMinWidth(0);
            content.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            HBox center = new HBox(8, statusIcon, content);
            center.setAlignment(Pos.CENTER_LEFT);
            center.setMouseTransparent(true);
            row.setCenter(center);

            FXUtils.installFastTooltip(actionButton, actionTooltip);
            actionButton.setOnAction(event -> activateTask());
            // A completed action can remove this row and reuse its cell before the click bubbles.
            actionButton.setOnMouseClicked(event -> event.consume());
            BorderPane.setAlignment(actionButton, Pos.CENTER);
            BorderPane.setMargin(actionButton, new Insets(0, 0, 0, 8));
            row.setRight(actionButton);

            graphic = new RipplerContainer(row);
            graphic.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1)
                    activateTask();
            });
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

        /// Rebinds a reused cell and updates its progress or pending-action presentation.
        @Override
        protected void updateItem(@Nullable TaskExecutorDialogPane pane, boolean empty) {
            super.updateItem(pane, empty);
            graphic.releaseRippleImmediately();
            content.titleProperty().unbind();
            content.setSubtitle(null);
            if (empty || pane == null) {
                content.setTitle("");
                statusIcon.getChildren().clear();
                setGraphic(null);
            } else {
                content.titleProperty().bind(pane.titleProperty());
                @Nullable TaskExecutorDialogPane.PendingAction action = pane.pendingActionProperty().get();
                actionButton.getStyleClass().removeAll("toggle-icon4", "jfx-button-border");
                if (action != null) {
                    actionButton.getStyleClass().add("jfx-button-border");
                    statusIcon.getChildren().setAll(SVG.CHECK_CIRCLE.createIcon(24));
                    content.setSubtitle(action.description());
                    actionButton.setGraphic(null);
                    actionButton.setText(action.text());
                    actionButton.setAccessibleText(action.text());
                    actionTooltip.setText(action.text());
                } else {
                    actionButton.getStyleClass().add("toggle-icon4");
                    if (statusIcon.getChildren().isEmpty() || !(statusIcon.getChildren().get(0) instanceof JFXSpinner)) {
                        JFXSpinner spinner = new JFXSpinner();
                        spinner.setRadius(12);
                        statusIcon.getChildren().setAll(spinner);
                    }
                    actionButton.setGraphic(SVG.ARROW_FORWARD.createIcon());
                    actionButton.setText(null);
                    actionButton.setAccessibleText(i18n("task.show"));
                    actionTooltip.setText(i18n("task.show"));
                }
                setGraphic(graphic);
            }
        }
    }
}
