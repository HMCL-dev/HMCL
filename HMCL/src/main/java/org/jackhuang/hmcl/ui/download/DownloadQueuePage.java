// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TaskListPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays queued, active, and completed downloads.
@NotNullByDefault
public final class DownloadQueuePage extends StackPane implements DecoratorPage {
    /// Queue whose tasks are displayed by this page.
    private final DownloadQueue queue;
    /// Content container for stable per-entry progress views.
    private final VBox entriesPane = new VBox(8);
    /// Rows keyed by their queue entries to preserve task progress listeners.
    private final Map<DownloadQueue.Entry, QueueItem> rows = new HashMap<>();
    /// Empty-state message shown when there are no entries.
    private final Label emptyLabel = new Label(i18n("download.list.empty"));
    /// Page state used by the launcher decorator.
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("download.list"), -1));

    /// Creates a page bound to the shared download queue.
    public DownloadQueuePage(DownloadQueue queue) {
        this.queue = queue;
        getStyleClass().add("gray-background");

        JFXButton clearButton = FXUtils.newBorderButton(i18n("download.list.clear_completed"));
        clearButton.setOnAction(event -> queue.clearCompleted());

        Label title = new Label(i18n("download.list"));
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: BOLD;");
        HBox header = new HBox(12, title, clearButton);
        header.setAlignment(Pos.CENTER_LEFT);

        emptyLabel.setPadding(new Insets(20, 0, 0, 0));
        VBox content = new VBox(12, header, entriesPane);
        content.setPadding(new Insets(12));
        VBox.setVgrow(entriesPane, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        getChildren().setAll(scrollPane);

        queue.getEntries().addListener((ListChangeListener<DownloadQueue.Entry>) change -> updateEntries());
        updateEntries();
    }

    /// Returns the decorator state for this page.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /// Synchronizes visible rows with the queue while reusing existing row controls.
    private void updateEntries() {
        rows.keySet().retainAll(queue.getEntries());
        entriesPane.getChildren().setAll(queue.getEntries().stream()
                .map(entry -> rows.computeIfAbsent(entry, QueueItem::new))
                .toList());
        if (queue.getEntries().isEmpty())
            entriesPane.getChildren().add(emptyLabel);
    }

    /// Displays one queue entry and its task-level progress.
    @NotNullByDefault
    private final class QueueItem extends VBox {
        /// Queue entry represented by this row.
        private final DownloadQueue.Entry entry;
        /// Status text bound to the entry lifecycle.
        private final Label statusLabel = new Label();
        /// Action button for cancelling or removing this entry.
        private final JFXButton actionButton = FXUtils.newBorderButton("");
        /// Toggle for expanding or collapsing detailed progress.
        private final JFXButton detailsButton = FXUtils.newBorderButton("");
        /// Whether detailed task progress is expanded.
        private final BooleanProperty expanded = new SimpleBooleanProperty(false);
        /// Detailed progress display for the task executor.
        private final TaskListPane taskListPane;

        /// Creates a row and attaches the task progress listener before it can start.
        private QueueItem(DownloadQueue.Entry entry) {
            super(6);
            this.entry = entry;
            getStyleClass().add("card-non-transparent");
            setPadding(new Insets(12));

            Label title = new Label(entry.getTitle());
            title.setWrapText(true);
            HBox.setHgrow(title, Priority.ALWAYS);

            statusLabel.textProperty().bind(Bindings.createStringBinding(() -> switch (entry.getStatus()) {
                case WAITING -> i18n("download.list.waiting");
                case RUNNING -> i18n("download.list.running");
                case CANCELLING -> i18n("download.list.cancelling");
                case SUCCEEDED -> i18n("download.list.succeeded");
                case FAILED -> i18n("download.list.failed");
                case CANCELLED -> i18n("download.list.cancelled");
            }, entry.statusProperty()));

            actionButton.textProperty().bind(Bindings.createStringBinding(() -> switch (entry.getStatus()) {
                case WAITING, RUNNING -> i18n("download.list.cancel");
                case CANCELLING -> i18n("download.list.cancelling");
                case SUCCEEDED, FAILED, CANCELLED -> i18n("download.list.remove");
            }, entry.statusProperty()));
            actionButton.disableProperty().bind(Bindings.equal(entry.statusProperty(), DownloadQueue.Status.CANCELLING));
            actionButton.setOnAction(event -> {
                if (entry.getStatus() == DownloadQueue.Status.WAITING || entry.getStatus() == DownloadQueue.Status.RUNNING)
                    queue.cancel(entry);
                else if (entry.getStatus() != DownloadQueue.Status.CANCELLING)
                    queue.remove(entry);
            });

            detailsButton.setGraphic(SVG.KEYBOARD_ARROW_DOWN.createIcon(16));
            FXUtils.installFastTooltip(detailsButton, i18n("download.list.details"));
            detailsButton.setOnAction(event -> {
                expanded.set(!expanded.get());
                detailsButton.setRotate(expanded.get() ? 180 : 0);
            });

            HBox heading = new HBox(12, title, statusLabel, detailsButton, actionButton);
            heading.setAlignment(Pos.CENTER_LEFT);

            taskListPane = entry.getProgressPane();
            taskListPane.setPrefHeight(140);
            taskListPane.visibleProperty().bind(expanded);
            taskListPane.managedProperty().bind(taskListPane.visibleProperty());

            getChildren().setAll(heading, taskListPane);
        }
    }
}