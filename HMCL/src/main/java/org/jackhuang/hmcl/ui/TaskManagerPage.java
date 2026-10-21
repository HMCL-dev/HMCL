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

/// Displays active tasks and restores their existing progress dialogs.
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

    /// Binds a list view to the active dialogs.
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

    /// Displays a live task title and an action to restore its dialog.
    @NotNullByDefault
    private static final class TaskCell extends ListCell<TaskExecutorDialogPane> {

        /// The list item whose title is bound to the current dialog.
        private final TwoLineListItem content = new TwoLineListItem();

        /// The row's shared hover and ripple feedback.
        private final RipplerContainer graphic;

        /// Creates a row that restores the current task through its body or trailing action.
        private TaskCell() {
            BorderPane row = new BorderPane();
            row.getStyleClass().add("md-list-cell");
            row.setPadding(new Insets(8));
            row.setCursor(Cursor.HAND);

            JFXSpinner spinner = new JFXSpinner();
            spinner.setRadius(12);

            content.setMinWidth(0);
            content.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            HBox center = new HBox(8, spinner, content);
            center.setAlignment(Pos.CENTER_LEFT);
            center.setMouseTransparent(true);
            row.setCenter(center);

            JFXButton restore = FXUtils.newToggleButton4(SVG.OPEN_IN_NEW);
            restore.setAccessibleText(i18n("task.show"));
            FXUtils.installFastTooltip(restore, i18n("task.show"));
            restore.setOnAction(event -> restoreTask());
            BorderPane.setAlignment(restore, Pos.CENTER);
            BorderPane.setMargin(restore, new Insets(0, 0, 0, 8));
            row.setRight(restore);

            graphic = new RipplerContainer(row);
            graphic.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1)
                    restoreTask();
            });
        }

        /// Restores the current task's dialog if this cell contains an item.
        private void restoreTask() {
            @Nullable TaskExecutorDialogPane pane = getItem();
            if (pane != null)
                pane.restore();
        }

        @Override
        protected void updateItem(@Nullable TaskExecutorDialogPane pane, boolean empty) {
            super.updateItem(pane, empty);
            graphic.releaseRippleImmediately();
            content.titleProperty().unbind();
            if (empty || pane == null) {
                content.setTitle("");
                setGraphic(null);
            } else {
                content.titleProperty().bind(pane.titleProperty());
                setGraphic(graphic);
            }
        }
    }
}
