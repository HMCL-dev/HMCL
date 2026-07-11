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
import com.jfoenix.controls.JFXSpinner;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.main.SettingsPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Single, reused page that visualizes the [TaskCenter], PCL-style: an overview panel on the left
/// (overall ring, speed, counts, remaining files, threads) and the live task stream on the right,
/// with failures in a collapsible section at the bottom. A singleton so its list listeners are
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

    private final VBox activeContainer = new VBox(10);
    private final VBox failedContainer = new VBox(0);

    private final Node activeEmpty = createEmptyBox(SVG.CHECKLIST, i18n("task.empty.running"));

    /// Live aggregate download speed (global, cross-task — see FetchTask.SPEED_EVENT).
    private final StringProperty speedText = new SimpleStringProperty("");
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // strong ref keeping the weak event registration alive
    private final Consumer<FetchTask.SpeedEvent> speedHandler;

    /// Smoothed value driving the overview ring: eased toward the real aggregate so large jumps
    /// (a stage total arriving late, a parallel task joining the average) glide instead of snapping.
    private final DoubleProperty ringProgress = new SimpleDoubleProperty(-1);
    private Timeline ringTimeline;

    private TaskCenterPage() {
        TaskCenter center = TaskCenter.getInstance();

        setLeft(createOverviewPane(center));
        setCenter(createContentPane(center));

        // Incremental binding: cards are cached per entry so a card is created once, not rebuilt on
        // every list change (no flicker; status changes are handled reactively inside the card).
        bindContainer(activeContainer, center.getEntries(), activeEmpty, this::createTaskCard);
        bindContainer(failedContainer, center.getFailedTasks(), null, this::createFailedRow);

        speedHandler = FetchTask.SPEED_EVENT.registerWeak(event -> {
            long speed = event.getSpeed();
            String text = speed > 0 ? I18n.formatSpeed(speed) : "";
            Platform.runLater(() -> speedText.set(text));
        });

        FXUtils.onChangeAndOperate(center.runningProgressProperty(), v -> animateRing(v.doubleValue()));
    }

    // ── overview (left) ───────────────────────────────────────────────

    private Node createOverviewPane(TaskCenter center) {
        VBox box = new VBox(18);
        box.getStyleClass().add("task-overview");
        box.setPadding(new Insets(24, 16, 24, 16));
        box.setAlignment(Pos.TOP_CENTER);
        FXUtils.setLimitWidth(box, 210);

        // Big progress ring with the percentage in the center; a dim placeholder icon when idle.
        JFXSpinner ring = new JFXSpinner();
        ring.getStyleClass().add("task-overview-ring");
        ring.setRadius(52);
        ring.progressProperty().bind(ringProgress);

        Label percentLabel = new Label();
        percentLabel.getStyleClass().add("task-overview-percent");
        percentLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            double p = center.runningProgressProperty().get();
            return p >= 0 ? String.format("%.0f%%", p * 100) : "";
        }, center.runningProgressProperty()));

        Node idleIcon = SVG.CHECKLIST.createIcon(40);
        idleIcon.setOpacity(0.3);

        StackPane ringPane = new StackPane(idleIcon, ring, percentLabel);
        ringPane.setMinHeight(120);

        // Idle (no active tasks): hide the ring so an indeterminate spinner doesn't whirl forever.
        // Listen on the list directly — a local Bindings.isEmpty(...) binding has no strong holder
        // and gets GC'd, silently freezing the idle state.
        Runnable updateIdleState = () -> {
            boolean empty = center.getEntries().isEmpty();
            ring.setVisible(!empty);
            percentLabel.setVisible(!empty);
            idleIcon.setVisible(empty);
        };
        updateIdleState.run();
        center.getEntries().addListener((ListChangeListener<TaskCenter.Entry>) change -> updateIdleState.run());

        Label caption = new Label(i18n("task.overview.progress"));
        caption.getStyleClass().add("task-overview-caption");

        VBox stats = new VBox(10);
        stats.setAlignment(Pos.TOP_LEFT);
        stats.setPadding(new Insets(8, 8, 0, 8));

        // Overall download speed (the only place it is shown — it is a global figure and
        // attributing it to a single task card would mislead when tasks run in parallel).
        stats.getChildren().add(createStatRow(SVG.DOWNLOAD, Bindings.createStringBinding(
                () -> speedText.get().isEmpty() ? "—" : speedText.get(), speedText)));

        // "N running · M queued" — depends on the entries list (update events re-evaluate).
        stats.getChildren().add(createStatRow(SVG.ARROW_FORWARD, Bindings.createStringBinding(() -> {
            int runningCount = 0, queuedCount = 0;
            for (TaskCenter.Entry e : center.getEntries()) {
                if (e.getStatus() == TaskCenter.Status.RUNNING) runningCount++;
                else if (e.getStatus() == TaskCenter.Status.QUEUED) queuedCount++;
            }
            return i18n("task.overview.counts", runningCount, queuedCount);
        }, center.getEntries())));

        // Remaining files across all running tasks.
        stats.getChildren().add(createStatRow(SVG.CHECKLIST, Bindings.createStringBinding(() -> {
            int remaining = center.remainingFilesProperty().get();
            return remaining > 0 ? i18n("task.overview.files", remaining) : "—";
        }, center.remainingFilesProperty())));

        // Download thread count (the configured connection pool size).
        var settings = SettingsManager.settings();
        stats.getChildren().add(createStatRow(SVG.SETTINGS, Bindings.createStringBinding(
                () -> i18n("task.overview.threads", settings.autoDownloadThreadsProperty().get()
                        ? FetchTask.DEFAULT_CONCURRENCY
                        : settings.downloadThreadsProperty().get()),
                settings.autoDownloadThreadsProperty(), settings.downloadThreadsProperty())));

        box.getChildren().addAll(ringPane, caption, stats);
        return box;
    }

    private static HBox createStatRow(SVG icon, javafx.beans.binding.StringBinding text) {
        Node iconNode = icon.createIcon(14);
        iconNode.setOpacity(0.75);
        Label label = new Label();
        label.getStyleClass().add("task-stat-label");
        label.textProperty().bind(text);
        HBox row = new HBox(8, iconNode, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void animateRing(double target) {
        if (ringTimeline != null) {
            ringTimeline.stop();
            ringTimeline = null;
        }
        // Snap for indeterminate transitions and regressions (e.g. a second task joining lowers the
        // average) — only ease forward motion, PCL-style.
        if (target < 0 || ringProgress.get() < 0 || target < ringProgress.get()) {
            ringProgress.set(target);
            return;
        }
        ringTimeline = new Timeline(new KeyFrame(Duration.millis(400),
                new KeyValue(ringProgress, target, Interpolator.EASE_BOTH)));
        ringTimeline.play();
    }

    // ── content (right): active stream + collapsible failures ────────

    private Node createContentPane(TaskCenter center) {
        VBox column = new VBox(12);
        column.setPadding(new Insets(16));

        VBox.setVgrow(activeContainer, Priority.ALWAYS);
        column.getChildren().add(activeContainer);
        column.getChildren().add(createFailedSection(center));

        ScrollPane scrollPane = new ScrollPane(column);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        BorderPane contentWrapper = new BorderPane();
        contentWrapper.getStyleClass().add("card-non-transparent");
        contentWrapper.setPadding(new Insets(4));
        contentWrapper.setCenter(scrollPane);

        StackPane centerPane = new StackPane(contentWrapper);
        centerPane.setPadding(new Insets(12));
        return centerPane;
    }

    private Node createFailedSection(TaskCenter center) {
        BooleanProperty expanded = new SimpleBooleanProperty(false);

        Label arrow = new Label();
        arrow.getStyleClass().add("task-stat-label");
        arrow.textProperty().bind(Bindings.when(expanded).then("▾").otherwise("▸"));

        Label title = new Label();
        title.getStyleClass().add("task-stat-label");
        title.textProperty().bind(Bindings.createStringBinding(
                () -> i18n("task.failed.section", center.getFailedTasks().size()),
                center.getFailedTasks()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        JFXButton clearButton = new JFXButton(i18n("task.clear"));
        clearButton.getStyleClass().add("dialog-cancel");
        clearButton.setOnAction(e -> {
            center.clearFailed();
            e.consume();
        });
        clearButton.visibleProperty().bind(expanded);
        clearButton.managedProperty().bind(expanded);

        HBox header = new HBox(8, arrow, title, spacer, clearButton);
        header.getStyleClass().add("task-failed-header");
        header.setOnMouseClicked(e -> expanded.set(!expanded.get()));

        failedContainer.visibleProperty().bind(expanded);
        failedContainer.managedProperty().bind(expanded);

        VBox section = new VBox(header, failedContainer);

        // The whole section only exists while there is something to inspect.
        javafx.beans.binding.BooleanBinding hasFailed = Bindings.isNotEmpty(center.getFailedTasks());
        section.visibleProperty().bind(hasFailed);
        section.managedProperty().bind(hasFailed);
        return section;
    }

    // ── incremental card binding ──────────────────────────────────────

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
        if (emptyNode == null)
            return;
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

    // ── task card (running / queued) ──────────────────────────────────

    private Node createTaskCard(TaskCenter.Entry entry) {
        TaskCenter center = TaskCenter.getInstance();

        VBox card = new VBox();
        card.getStyleClass().add("task-card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(entry.getDisplayText());
        titleLabel.getStyleClass().add("title-label");

        // Subtitle: the current sub-task while running, or the queue position while waiting.
        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("subtitle-label");
        statusLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            if (entry.getStatus() == TaskCenter.Status.RUNNING) {
                String msg = entry.getStatusMessage();
                return msg != null && !msg.isEmpty() ? msg : i18n("task.status.running");
            }
            if (entry.getStatus() == TaskCenter.Status.QUEUED)
                return i18n("task.queue.position", center.queuePosition(entry));
            return "";
        }, entry.statusProperty(), entry.statusMessageProperty(), center.getEntries()));

        VBox titleBox = new VBox(2, titleLabel, statusLabel);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.setMaxWidth(Double.MAX_VALUE);

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> {
            center.cancel(entry);
            e.consume();
        });
        cancelButton.setOnMouseClicked(javafx.event.Event::consume);

        // The generic OTHER kind carries no information — only show a tag when it means something.
        Label kindTag = createKindTag(entry.getKind());
        if (kindTag != null)
            header.getChildren().add(kindTag);
        header.getChildren().addAll(titleBox, cancelButton);
        card.getChildren().add(header);

        javafx.beans.binding.BooleanBinding isRunning = entry.statusProperty().isEqualTo(TaskCenter.Status.RUNNING);

        // Overall progress bar: the task's weighted aggregate progress (indeterminate at -1),
        // animated by JFXProgressBar's built-in smoothing so count jumps glide.
        JFXProgressBar progressBar = new JFXProgressBar();
        progressBar.setSmoothProgress(true);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.progressProperty().bind(entry.progressProperty());
        progressBar.visibleProperty().bind(isRunning);
        progressBar.managedProperty().bind(isRunning);
        card.getChildren().add(progressBar);

        // Footer: percentage on the right (only when the task reports determinate progress).
        Label percentLabel = new Label();
        percentLabel.getStyleClass().add("task-card-speed");
        percentLabel.textProperty().bind(Bindings.createStringBinding(
                () -> entry.progressProperty().get() >= 0 ? String.format("%.0f%%", entry.progressProperty().get() * 100) : "",
                entry.progressProperty()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(spacer, percentLabel);
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

    // ── failed row ────────────────────────────────────────────────────

    private Node createFailedRow(TaskCenter.Entry entry) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("task-history-cell");

        boolean cancelled = entry.getException() instanceof CancellationException
                || entry.getStatus() == TaskCenter.Status.CANCELLED;
        Node icon = (cancelled ? SVG.CANCEL : SVG.CLOSE).createIcon(14);

        Label label = new Label(entry.getDisplayText());
        label.getStyleClass().add("subtitle-label");
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setMaxWidth(Double.MAX_VALUE);

        Label kindTag = createKindTag(entry.getKind());
        if (kindTag != null)
            row.getChildren().addAll(icon, kindTag, label);
        else
            row.getChildren().addAll(icon, label);

        if (!cancelled) {
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

    /// Tag for meaningful kinds; null for OTHER (a generic "task" label is pure noise).
    private static Label createKindTag(TaskCenter.TaskKind kind) {
        String text = switch (kind == null ? TaskCenter.TaskKind.OTHER : kind) {
            case GAME_INSTALL -> i18n("task.kind.game_install");
            case MODPACK_INSTALL -> i18n("task.kind.modpack_install");
            case JAVA_DOWNLOAD -> i18n("task.kind.java_download");
            case MOD_UPDATE -> i18n("task.kind.mod_update");
            case OTHER -> null;
        };
        if (text == null)
            return null;
        Label tag = new Label(text);
        tag.getStyleClass().add("tag");
        return tag;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
