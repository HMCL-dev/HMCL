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
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
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
    private final VBox failedContainer = new VBox(10);

    private final Node activeEmpty = createEmptyBox(SVG.CHECKLIST, i18n("task.empty.running"));

    /// Live aggregate download speed (global, cross-task — see FetchTask.SPEED_EVENT).
    private final StringProperty speedText = new SimpleStringProperty("");
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // strong ref keeping the weak event registration alive
    private final Consumer<FetchTask.SpeedEvent> speedHandler;

    /// Estimated time remaining ("约 m:ss"). Approximate — derived from how fast the aggregate
    /// progress is advancing, not from bytes (there is no reliable total byte count). Empty when it
    /// can't be estimated (indeterminate, stalled, or not enough samples yet).
    private final StringProperty etaText = new SimpleStringProperty("");
    private double etaLastProgress = -1;
    private long etaLastNanos = 0;
    private double etaVelocity = 0; // progress-fraction per second, smoothed

    /// Live count of in-flight download connections (polled once a second alongside the ETA).
    private final StringProperty threadText = new SimpleStringProperty("");

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

        // Sample the aggregate every second to estimate remaining time. Cheap; runs for the app's
        // lifetime like the ring, and early-returns when nothing determinate is running.
        Timeline etaSampler = new Timeline(new KeyFrame(Duration.seconds(1), e -> sampleEta(center)));
        etaSampler.setCycleCount(Timeline.INDEFINITE);
        etaSampler.play();
    }

    private void sampleEta(TaskCenter center) {
        threadText.set(i18n("task.overview.threads", FetchTask.getActiveDownloadThreads()));

        double p = center.runningProgressProperty().get();
        long now = System.nanoTime();
        if (p < 0 || p >= 1) {
            etaText.set("");
            etaLastProgress = -1;
            etaVelocity = 0;
            return;
        }
        if (etaLastProgress >= 0 && now > etaLastNanos) {
            double dt = (now - etaLastNanos) / 1e9;
            double dp = p - etaLastProgress;
            if (dp > 0 && dt > 0) {
                double instant = dp / dt;
                etaVelocity = etaVelocity <= 0 ? instant : etaVelocity * 0.7 + instant * 0.3;
            }
        }
        etaLastProgress = p;
        etaLastNanos = now;

        double seconds = etaVelocity > 1e-5 ? (1 - p) / etaVelocity : -1;
        etaText.set(seconds > 0 && seconds < 24 * 3600 ? formatDuration((long) seconds) : "");
    }

    private static String formatDuration(long seconds) {
        if (seconds >= 3600)
            return String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // ── overview (left) ───────────────────────────────────────────────

    private Node createOverviewPane(TaskCenter center) {
        VBox box = new VBox(18);
        box.getStyleClass().add("task-overview");
        box.setPadding(new Insets(24, 16, 24, 16));
        box.setAlignment(Pos.CENTER); // content centers itself, so the idle ring sits in the middle
        FXUtils.setLimitWidth(box, 210);

        // Custom progress ring: sweeping pulse highlight, turn-green + check-mark on completion.
        // driveFrom() binds the progress and runs the idle → active → completion → idle state machine;
        // the title-bar indicator drives its ring through the same method so the two stay in sync.
        TaskOverviewRing ring = new TaskOverviewRing();
        ring.driveFrom(center);
        StackPane ringPane = new StackPane(ring);
        ringPane.setMinHeight(120);

        // Shown only while idle (labels the pause glyph); when active the % lives in the ring center.
        Label idleCaption = new Label(i18n("task.overview.idle"));
        idleCaption.getStyleClass().add("task-overview-caption");

        VBox stats = new VBox(10);
        stats.setAlignment(Pos.CENTER);
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

        // Estimated time remaining (approximate — see sampleEta).
        stats.getChildren().add(createStatRow(SVG.UPDATE, Bindings.createStringBinding(
                () -> etaText.get().isEmpty() ? "—" : i18n("task.overview.eta", etaText.get()), etaText)));

        // Live count of in-flight download connections (updated by the 1s sampler).
        stats.getChildren().add(createStatRow(SVG.SETTINGS, threadText));

        // Batch action: cancel every active task.
        JFXButton cancelAllButton = new JFXButton(i18n("task.cancel_all"));
        cancelAllButton.getStyleClass().add("dialog-cancel");
        cancelAllButton.setOnAction(e -> center.cancelAll());

        // Idle collapses the stats + cancel to nothing; the ring (its center shows the % or the pause
        // glyph) is self-explanatory, so no separate caption is needed. The center-aligned VBox then
        // centers the lone ring in the panel.
        Runnable updateIdle = () -> {
            boolean active = !center.getEntries().isEmpty();
            idleCaption.setVisible(!active);
            idleCaption.setManaged(!active);
            stats.setVisible(active);
            stats.setManaged(active);
            cancelAllButton.setVisible(active);
            cancelAllButton.setManaged(active);
        };
        updateIdle.run();
        center.getEntries().addListener((ListChangeListener<TaskCenter.Entry>) c -> updateIdle.run());

        box.getChildren().addAll(ringPane, idleCaption, stats, cancelAllButton);

        // Wrap in padding so the tinted panel sits inset like the content card on the right.
        // VGrow so it fills the full height of the left VBox (otherwise the card stops at content
        // height and the window background shows through below it).
        StackPane wrapper = new StackPane(box);
        wrapper.setPadding(new Insets(12, 6, 12, 12));
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    private static HBox createStatRow(SVG icon, javafx.beans.value.ObservableStringValue text) {
        Node iconNode = icon.createIcon(14);
        iconNode.setOpacity(0.75);
        Label label = new Label();
        label.getStyleClass().add("task-stat-label");
        label.textProperty().bind(text);
        HBox row = new HBox(8, iconNode, label);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // ── content (right): active stream + collapsible failures ────────

    private Node createContentPane(TaskCenter center) {
        // Active task list: its own scroll area, grows to fill the space above the failures, with a
        // scrollbar of its own when the list overflows.
        activeContainer.setPadding(new Insets(16));
        ScrollPane activeScroll = new ScrollPane(activeContainer);
        activeScroll.setFitToWidth(true);
        activeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(activeScroll, Priority.ALWAYS);
        // Fill the viewport when the list is short (so the empty-state box centers via its VGrow) but
        // still overflow-and-scroll when it is tall — can't use fitToHeight, which would kill scrolling.
        activeScroll.viewportBoundsProperty().addListener((o, ov, nv) ->
                activeContainer.setMinHeight(nv.getHeight()));

        VBox column = new VBox();
        column.getChildren().addAll(activeScroll, createFailedSection(center, column.heightProperty()));

        BorderPane contentWrapper = new BorderPane();
        contentWrapper.getStyleClass().add("card-non-transparent");
        contentWrapper.setPadding(new Insets(4));
        contentWrapper.setCenter(column);

        StackPane centerPane = new StackPane(contentWrapper);
        centerPane.setPadding(new Insets(12));
        return centerPane;
    }

    /// Collapsible failures pinned to the bottom. Expanding animates the body open; it sizes to its
    /// content but is capped at half the content height, with its own scrollbar beyond that.
    private Node createFailedSection(TaskCenter center, javafx.beans.value.ObservableDoubleValue regionHeight) {
        BooleanProperty expanded = new SimpleBooleanProperty(false);

        // A single caret that rotates 0°→90° instead of swapping glyphs (smoother).
        Label arrow = new Label("▸");
        arrow.getStyleClass().add("task-stat-label");
        expanded.addListener((o, was, now) -> {
            RotateTransition rotate = new RotateTransition(Duration.millis(200), arrow);
            rotate.setToAngle(now ? 90 : 0);
            rotate.play();
        });

        Label title = new Label();
        title.getStyleClass().add("task-stat-label");
        title.textProperty().bind(Bindings.createStringBinding(
                () -> i18n("task.failed.section", center.getFailedTasks().size()),
                center.getFailedTasks()));

        // Kept managed (reserving its slot) so the header doesn't jump size on expand; just fades.
        JFXButton clearButton = new JFXButton(i18n("task.clear"));
        clearButton.getStyleClass().add("dialog-cancel");
        clearButton.setOnAction(e -> {
            center.clearFailed();
            e.consume();
        });
        clearButton.setOpacity(0);
        clearButton.setMouseTransparent(true);
        expanded.addListener((o, was, now) -> {
            FadeTransition fade = new FadeTransition(Duration.millis(200), clearButton);
            fade.setToValue(now ? 1 : 0);
            fade.play();
            clearButton.setMouseTransparent(!now);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // Left-aligned but vertically centered within the bar (CENTER_LEFT), clear button on the right.
        HBox header = new HBox(8, arrow, title, spacer, clearButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("task-failed-header");
        header.setOnMouseClicked(e -> expanded.set(!expanded.get()));

        failedContainer.setPadding(new Insets(12));
        ScrollPane body = new ScrollPane(failedContainer);
        body.setFitToWidth(true);
        body.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        body.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        body.setMinHeight(0);

        // Body height = min(content, half the region), scaled by the open fraction (0 collapsed →
        // 1 expanded). Use the container's PREFERRED height, not its laid-out getHeight(): a collapsed
        // body has a 0-height viewport, and a ScrollPane won't lay out content in a 0-height viewport,
        // so getHeight() would stay 0 → target 0 → body never opens (until a full re-layout). Depend
        // on the failed list and body width so it recomputes as cards are added and layout settles.
        DoubleProperty open = new SimpleDoubleProperty(0);
        var target = Bindings.createDoubleBinding(
                () -> {
                    double width = body.getWidth() > 0 ? body.getWidth() : -1;
                    return Math.min(failedContainer.prefHeight(width) + 4, regionHeight.get() * 0.5);
                },
                center.getFailedTasks(), regionHeight, body.widthProperty());
        body.prefHeightProperty().bind(target.multiply(open));
        body.maxHeightProperty().bind(target.multiply(open));

        expanded.addListener((o, was, now) -> new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(open, now ? 1 : 0, Interpolator.EASE_BOTH))).play());

        VBox section = new VBox(header, body);

        // The whole section only exists while there is something to inspect; collapse it when cleared.
        javafx.beans.binding.BooleanBinding hasFailed = Bindings.isNotEmpty(center.getFailedTasks());
        section.visibleProperty().bind(hasFailed);
        section.managedProperty().bind(hasFailed);
        center.getFailedTasks().addListener((ListChangeListener<TaskCenter.Entry>) c -> {
            if (center.getFailedTasks().isEmpty())
                expanded.set(false);
        });
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
        boolean cancelled = entry.getException() instanceof CancellationException
                || entry.getStatus() == TaskCenter.Status.CANCELLED;

        VBox card = new VBox();
        card.getStyleClass().add("task-card");

        Label titleLabel = new Label(entry.getDisplayText());
        titleLabel.getStyleClass().add("title-label");

        // Subtitle: the (first line of the) error for a failure, or the cancelled note.
        Label subtitle = new Label(failureSummary(entry, cancelled));
        subtitle.getStyleClass().add("subtitle-label");
        subtitle.setMaxWidth(Double.MAX_VALUE);

        VBox titleBox = new VBox(2, titleLabel, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.setMaxWidth(Double.MAX_VALUE);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label kindTag = createKindTag(entry.getKind());
        if (kindTag != null)
            header.getChildren().add(kindTag);
        Label statusTag = new Label(i18n(cancelled ? "task.status.cancelled" : "task.status.failed"));
        statusTag.getStyleClass().addAll("tag", cancelled ? "tag-cancelled" : "tag-failed");
        header.getChildren().addAll(statusTag, titleBox);
        card.getChildren().add(header);

        if (!cancelled)
            card.setOnMouseClicked(e -> showFailedTaskDialog(entry));
        else
            card.setStyle("-fx-cursor: default;"); // cancelled cards aren't clickable

        return card;
    }

    private static String failureSummary(TaskCenter.Entry entry, boolean cancelled) {
        if (cancelled)
            return i18n("task.cancelled");
        Throwable ex = entry.getException();
        String message = ex != null ? ex.getLocalizedMessage() : null;
        if (message == null || message.isBlank())
            return i18n("task.failed.no_exception");
        message = message.strip();
        int newline = message.indexOf('\n');
        if (newline >= 0)
            message = message.substring(0, newline);
        return message.length() > 80 ? message.substring(0, 80) + "…" : message;
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
