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
import com.jfoenix.controls.JFXProgressBar;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.main.SettingsPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Single, reused page that visualizes the [TaskCenter]. A singleton so its list listeners are
/// registered exactly once (navigating here repeatedly must not accumulate listeners or stale
/// off-screen views).
public final class TaskCenterPage extends DecoratorAnimatedPage implements DecoratorPage {
    private static TaskCenterPage instance;

    /// Must be called on the FX thread.
    public static TaskCenterPage getInstance() {
        if (instance == null)
            instance = new TaskCenterPage();
        return instance;
    }

    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("task.manage")));

    private final TransitionPane transitionPane = new TransitionPane();

    private final TabHeader.Tab<ScrollPane> runningTab = new TabHeader.Tab<>("taskRunningTab");
    private final TabHeader.Tab<ScrollPane> completedTab = new TabHeader.Tab<>("taskCompletedTab");
    private final TabHeader.Tab<ScrollPane> failedTab = new TabHeader.Tab<>("taskFailedTab");

    private final VBox runningContainer = new VBox(10);
    private final VBox completedContainer = new VBox(8);
    private final VBox failedContainer = new VBox(8);

    private final Node runningEmpty = createEmptyBox(SVG.CHECKLIST, i18n("task.empty.running"));
    private final Node completedEmpty = createEmptyBox(SVG.CHECK, i18n("task.empty.completed"));
    private final Node failedEmpty = createEmptyBox(SVG.CLOSE, i18n("task.empty.failed"));

    /// Live aggregate download speed; running cards bind their speed label to it.
    private final StringProperty speedText = new SimpleStringProperty("");
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // strong ref keeping the weak event registration alive
    private final Consumer<FetchTask.SpeedEvent> speedHandler;

    private TaskCenterPage() {
        runningTab.setNodeSupplier(this::createRunningPane);
        completedTab.setNodeSupplier(this::createCompletedPane);
        failedTab.setNodeSupplier(this::createFailedPane);

        TabHeader tabHeader = new TabHeader(transitionPane, runningTab, completedTab, failedTab);
        tabHeader.select(runningTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("task.manage").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tabHeader, runningTab, i18n("task.running"), SVG.ARROW_FORWARD)
                .addNavigationDrawerTab(tabHeader, completedTab, i18n("task.completed"), SVG.CHECK)
                .addNavigationDrawerTab(tabHeader, failedTab, i18n("task.failed"), SVG.CLOSE);

        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);

        BorderPane contentWrapper = new BorderPane();
        contentWrapper.getStyleClass().add("card-non-transparent");
        contentWrapper.setPadding(new Insets(12));
        contentWrapper.setCenter(transitionPane);

        StackPane centerPane = new StackPane(contentWrapper);
        centerPane.setPadding(new Insets(12));
        setCenter(centerPane);

        // Incremental binding: cards are cached per entry so a running card's TaskListPane (which
        // attaches a listener to its executor) is created once, not rebuilt on every list change.
        TaskCenter center = TaskCenter.getInstance();
        bindContainer(runningContainer, center.getEntries(), runningEmpty, this::createRunningCard);
        bindContainer(completedContainer, center.getSucceededTasks(), completedEmpty, e -> createHistoryItem(e, true));
        bindContainer(failedContainer, center.getFailedTasks(), failedEmpty, e -> createHistoryItem(e, false));

        speedHandler = FetchTask.SPEED_EVENT.registerWeak(event -> {
            long speed = event.getSpeed();
            String text = speed > 0 ? I18n.formatSpeed(speed) : "";
            Platform.runLater(() -> speedText.set(text));
        });
    }

    private static void bindContainer(VBox container, ObservableList<TaskCenter.Entry> source,
                                      Node emptyNode, Function<TaskCenter.Entry, Node> cardFactory) {
        Map<TaskCenter.Entry, Node> cache = new IdentityHashMap<>();

        for (TaskCenter.Entry entry : source)
            container.getChildren().add(cache.computeIfAbsent(entry, cardFactory));
        updateEmptyState(container, emptyNode, source.isEmpty());

        // Incremental: only add/remove the cards that actually changed. Status changes (e.g. a task
        // going QUEUED -> RUNNING) arrive as "update" events which we ignore — the card binds to the
        // entry reactively, so there is no need to rebuild (and no flicker).
        source.addListener((ListChangeListener<TaskCenter.Entry>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (TaskCenter.Entry entry : change.getRemoved()) {
                        Node node = cache.remove(entry);
                        if (node != null)
                            container.getChildren().remove(node);
                    }
                }
                if (change.wasAdded()) {
                    for (TaskCenter.Entry entry : change.getAddedSubList())
                        container.getChildren().add(cache.computeIfAbsent(entry, cardFactory));
                }
            }
            updateEmptyState(container, emptyNode, source.isEmpty());
        });
    }

    private static void updateEmptyState(VBox container, Node emptyNode, boolean empty) {
        if (empty) {
            if (!container.getChildren().contains(emptyNode))
                container.getChildren().add(emptyNode);
        } else {
            container.getChildren().remove(emptyNode);
        }
    }

    private static Node createEmptyBox(SVG icon, String text) {
        VBox box = new VBox();
        box.getStyleClass().add("task-empty-box");
        Node iconNode = icon.createIcon(48);
        iconNode.setOpacity(0.35);
        Label label = new Label(text);
        label.getStyleClass().add("task-empty-label");
        box.getChildren().addAll(iconNode, label);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private ScrollPane createRunningPane() {
        ScrollPane scrollPane = new ScrollPane(runningContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        runningContainer.setPadding(new Insets(12));
        return scrollPane;
    }

    private static HBox createClearToolbar(Runnable onClear) {
        HBox toolbar = new HBox();
        toolbar.getStyleClass().add("task-toolbar");
        toolbar.setPickOnBounds(false);

        JFXButton btn = createToolbarButton2(i18n("task.clear"), SVG.DELETE, onClear);

        toolbar.getChildren().setAll(btn);
        return toolbar;
    }

    private ScrollPane createCompletedPane() {
        VBox wrapper = new VBox(8);
        wrapper.setPadding(new Insets(12));

        HBox toolbar = createClearToolbar(() -> TaskCenter.getInstance().clearSucceeded());

        wrapper.getChildren().addAll(toolbar, completedContainer);
        VBox.setVgrow(completedContainer, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(wrapper);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        return scrollPane;
    }

    private ScrollPane createFailedPane() {
        VBox wrapper = new VBox(8);
        wrapper.setPadding(new Insets(12));

        HBox toolbar = createClearToolbar(() -> TaskCenter.getInstance().clearFailed());

        wrapper.getChildren().addAll(toolbar, failedContainer);
        VBox.setVgrow(failedContainer, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(wrapper);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        return scrollPane;
    }

    // ── running card ─────────────────────────────────────────────────

    private Node createRunningCard(TaskCenter.Entry entry) {
        VBox card = new VBox();
        card.getStyleClass().add("task-card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label kindTag = createKindTag(entry.getKind());

        Label titleLabel = new Label(entry.getDisplayText());
        titleLabel.getStyleClass().add("title-label");

        // Subtitle: what the task is doing right now (current sub-task), else the status word.
        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("subtitle-label");
        statusLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            if (entry.getStatus() == TaskCenter.Status.RUNNING) {
                String msg = entry.getStatusMessage();
                return msg != null && !msg.isEmpty() ? msg : i18n("task.status.running");
            }
            return i18n("task.waiting");
        }, entry.statusProperty(), entry.statusMessageProperty()));

        VBox titleBox = new VBox(2, titleLabel, statusLabel);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.setMaxWidth(Double.MAX_VALUE);

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> {
            TaskCenter.getInstance().cancel(entry);
            e.consume();
        });
        cancelButton.setOnMouseClicked(javafx.event.Event::consume);

        header.getChildren().addAll(kindTag, titleBox, cancelButton);
        card.getChildren().add(header);

        javafx.beans.binding.BooleanBinding isRunning = entry.statusProperty().isEqualTo(TaskCenter.Status.RUNNING);

        // Overall progress bar (indeterminate when the task graph reports no aggregate progress).
        javafx.beans.property.ReadOnlyDoubleProperty rootProgress = entry.getExecutor().getRootTask().progressProperty();
        JFXProgressBar progressBar = new JFXProgressBar();
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.progressProperty().bind(rootProgress);
        progressBar.visibleProperty().bind(isRunning);
        progressBar.managedProperty().bind(isRunning);
        card.getChildren().add(progressBar);

        // Footer: download speed on the left (hidden when idle), percentage on the right (shown only
        // when the task reports determinate progress).
        Label speedLabel = new Label();
        speedLabel.getStyleClass().add("task-card-speed");
        speedLabel.textProperty().bind(speedText);
        speedLabel.visibleProperty().bind(isRunning.and(speedText.isNotEmpty()));
        speedLabel.managedProperty().bind(speedLabel.visibleProperty());

        Label percentLabel = new Label();
        percentLabel.getStyleClass().add("task-card-speed");
        percentLabel.textProperty().bind(Bindings.createStringBinding(
                () -> rootProgress.get() >= 0 ? String.format("%.0f%%", rootProgress.get() * 100) : "",
                rootProgress));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(speedLabel, spacer, percentLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.visibleProperty().bind(isRunning);
        footer.managedProperty().bind(isRunning);
        card.getChildren().add(footer);

        // Once the task is terminal the card leaves the active list. Drop the bindings so this card
        // can be GC'd instead of being pinned by the long-lived entry's status property.
        entry.statusProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends TaskCenter.Status> obs,
                                TaskCenter.Status old, TaskCenter.Status now) {
                if (now.isTerminal()) {
                    statusLabel.textProperty().unbind();
                    progressBar.progressProperty().unbind();
                    progressBar.visibleProperty().unbind();
                    progressBar.managedProperty().unbind();
                    speedLabel.textProperty().unbind();
                    speedLabel.visibleProperty().unbind();
                    speedLabel.managedProperty().unbind();
                    percentLabel.textProperty().unbind();
                    footer.visibleProperty().unbind();
                    footer.managedProperty().unbind();
                    entry.statusProperty().removeListener(this);
                }
            }
        });

        // Click the card to (re)attach a foreground dialog (with full stage details) to this task.
        card.setOnMouseClicked(e -> Controllers.showManagedTaskDialog(entry, TaskCancellationAction.NORMAL));

        return card;
    }

    // ── history item (completed / failed) ────────────────────────────

    private Node createHistoryItem(TaskCenter.Entry entry, boolean success) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("task-history-cell");

        boolean cancelled = !success && entry.getException() instanceof CancellationException;
        Node icon;
        if (success) {
            icon = SVG.CHECK.createIcon(14);
        } else if (cancelled) {
            icon = SVG.CANCEL.createIcon(14);
        } else {
            icon = SVG.CLOSE.createIcon(14);
        }

        Label kindTag = createKindTag(entry.getKind());

        Label label = new Label(entry.getDisplayText());
        label.getStyleClass().add("subtitle-label");
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setMaxWidth(Double.MAX_VALUE);

        row.getChildren().addAll(icon, kindTag, label);

        if (!success && !cancelled) {
            row.getStyleClass().add("clickable");
            row.setOnMouseClicked(e -> showFailedTaskDialog(entry));
        }

        return row;
    }

    // ── failed task dialog ─────────────────────────────────────────────

    static void showFailedTaskDialog(TaskCenter.Entry entry) {
        Throwable ex = entry.getException();
        if (ex instanceof CancellationException) {
            Controllers.dialog(i18n("task.cancelled"), entry.getTitle(), MessageDialogPane.MessageType.ERROR);
            return;
        }

        // Prefer the submitter's rich failure presenter (e.g. an install wizard's FailureCallback:
        // friendly per-exception messages, retry hints, modpack partial-completion handling). Only
        // fall back to a raw stack trace when the task supplied none (e.g. a plain download).
        Consumer<Runnable> presenter = entry.getFailurePresenter();
        if (presenter != null) {
            presenter.accept(() -> {
            });
            return;
        }

        String message = ex != null
                ? StringUtils.getStackTrace(ex)
                : i18n("task.failed.no_exception");
        Controllers.dialog(new MessageDialogPane.Builder(message, entry.getTitle(), MessageDialogPane.MessageType.ERROR)
                .addAction(i18n("settings.launcher.launcher_log.export"), SettingsPage::exportLogs)
                .ok(null)
                .build());
    }

    // ── kind tag ─────────────────────────────────────────────────────

    private static Label createKindTag(TaskCenter.TaskKind kind) {
        String text = switch (kind == null ? TaskCenter.TaskKind.OTHER : kind) {
            case GAME_INSTALL -> i18n("task.kind.game_install");
            case MODPACK_INSTALL -> i18n("task.kind.modpack_install");
            case JAVA_DOWNLOAD -> i18n("task.kind.java_download");
            case MOD_UPDATE -> i18n("task.kind.mod_update");
            case OTHER -> i18n("task.kind.other");
        };
        Label tag = new Label(text);
        tag.getStyleClass().add("tag");
        return tag;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
