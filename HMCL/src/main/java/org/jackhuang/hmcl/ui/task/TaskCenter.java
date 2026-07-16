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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/// Central scheduler for "managed" (backgroundable) tasks.
///
/// Design (bounded-concurrency queue):
///  - Every managed task lives here from the moment it is submitted; a dialog is merely a *view*
///    onto a task, not the owner of its lifecycle. Whether a task is shown in the foreground or
///    runs headless in the background, it is the *same* entry in the *same* queue.
///  - Up to N tasks run at once (`running`), where N is the configured concurrency limit. A task may
///    only join the running set if its resource key is distinct from every runner's; a null key is
///    treated as exclusive (serial), so tasks without an assigned key never run in parallel. The
///    scheduler attaches its completion listener *before* starting the executor, so the start/finish
///    race that could orphan a task (and permanently block the queue) cannot happen.
///  - This class performs **no** user-facing UI (no toasts / dialogs). Presenters observe
///    [Entry#statusProperty] and decide what to show. This keeps notification policy out of the
///    scheduler and avoids double-messaging.
///
/// Threading: all mutating/query methods must be called on the JavaFX Application Thread. The
/// completion callback re-dispatches onto the FX thread before touching any state.
public final class TaskCenter {
    private static final TaskCenter INSTANCE = new TaskCenter();

    public static TaskCenter getInstance() {
        return INSTANCE;
    }

    private TaskCenter() {
    }

    public enum TaskKind {
        GAME_INSTALL,
        MODPACK_INSTALL,
        JAVA_DOWNLOAD,
        MOD_UPDATE,
        OTHER
    }

    /// Resource key for tasks that mutate a game repository (installs, version add/remove/rename): all
    /// such tasks share one key so they never run concurrently, protecting the shared repository
    /// caches and libraries/assets metadata. Coarse on purpose — refined per game directory later.
    public static final String RESOURCE_KEY_REPO = "repo-write";

    /// Resource key for Java runtime downloads (shared global Java directory).
    public static final String RESOURCE_KEY_JAVA = "java";

    /// Resource key scoping a task to a single game instance (its mods/resourcepacks/saves). The same
    /// instance serializes; different instances run in parallel. Returns null (exclusive) if id is null.
    public static String instanceResourceKey(String instanceId) {
        return instanceId == null ? null : "instance:" + instanceId;
    }

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED;

        public boolean isTerminal() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED;
        }

        public boolean isActive() {
            return this == QUEUED || this == RUNNING;
        }
    }

    /// A single managed task. Immutable metadata + an observable [Status].
    public static final class Entry {
        private final TaskExecutor executor;
        private final String title;
        private final String detail;
        private final TaskKind kind;
        private final String name;
        /// Resource key gating concurrency: two entries with equal (or null) keys never run at the
        /// same time. Null means "exclusive" — conflicts with every other task.
        private final String resourceKey;
        /// Headless aggregate-progress computer for this task's executor (0~1, or -1 indeterminate).
        private final TaskProgressAggregator aggregator;
        private final ReadOnlyObjectWrapper<Status> status = new ReadOnlyObjectWrapper<>(Status.QUEUED);

        /// A human-readable line describing what the task is doing right now (the current significant
        /// sub-task), e.g. "Downloading libraries". Updated by the scheduler while running.
        private final ReadOnlyStringWrapper statusMessage = new ReadOnlyStringWrapper("");

        /// Optional detailed-failure presenter supplied by the submitter (e.g. a wizard's
        /// {@code FailureCallback}). When the user inspects a failed entry in the Task Center, this
        /// is invoked instead of the raw stack trace so the friendly, actionable error dialog is
        /// preserved. The argument is a continuation run after the dialog is dismissed. FX-thread only.
        private Consumer<Runnable> failurePresenter;

        Entry(TaskExecutor executor, String title, String detail, TaskKind kind, String name, String resourceKey) {
            this.executor = executor;
            this.title = title;
            this.detail = detail;
            this.kind = kind != null ? kind : TaskKind.OTHER;
            this.name = name;
            this.resourceKey = resourceKey;
            this.aggregator = new TaskProgressAggregator(executor);
        }

        public TaskExecutor getExecutor() {
            return executor;
        }

        public String getTitle() {
            return title;
        }

        public String getDetail() {
            return detail;
        }

        /// Detail if present, otherwise the title. Never null unless both are null.
        public String getDisplayText() {
            return detail != null ? detail : title;
        }

        public TaskKind getKind() {
            return kind;
        }

        public String getName() {
            return name;
        }

        public String getResourceKey() {
            return resourceKey;
        }

        /// Aggregate 0~1 progress of this task (or -1 indeterminate). Bound by task cards.
        public ReadOnlyDoubleProperty progressProperty() {
            return aggregator.progressProperty();
        }

        public Status getStatus() {
            return status.get();
        }

        public ReadOnlyObjectProperty<Status> statusProperty() {
            return status.getReadOnlyProperty();
        }

        public String getStatusMessage() {
            return statusMessage.get();
        }

        public ReadOnlyStringProperty statusMessageProperty() {
            return statusMessage.getReadOnlyProperty();
        }

        public Throwable getException() {
            return executor.getException();
        }

        public Consumer<Runnable> getFailurePresenter() {
            return failurePresenter;
        }

        public void setFailurePresenter(Consumer<Runnable> failurePresenter) {
            this.failurePresenter = failurePresenter;
        }
    }

    /// Single source of truth. The extractor makes the list fire "update" change events when an
    /// entry's status changes, so the derived [FilteredList]s below re-filter automatically.
    private final ObservableList<Entry> tasks =
            FXCollections.observableArrayList(entry -> new Observable[]{entry.statusProperty()});

    private final FilteredList<Entry> activeTasks = new FilteredList<>(tasks, e -> e.getStatus().isActive());
    private final FilteredList<Entry> failedTasks =
            new FilteredList<>(tasks, e -> e.getStatus() == Status.FAILED || e.getStatus() == Status.CANCELLED);

    /// Currently running entries (up to the configured concurrency limit).
    private final Set<Entry> running = new LinkedHashSet<>();

    /// Aggregate progress for the title-bar download indicator: the equal-weight average of all
    /// running tasks' aggregate progress (each computed by a [TaskProgressAggregator]), or -1
    /// (indeterminate) when nothing running reports a determinate value.
    private final ReadOnlyDoubleWrapper runningProgress = new ReadOnlyDoubleWrapper(-1);

    /// Recomputes [#runningProgress] whenever any running task's aggregate progress changes.
    private final InvalidationListener runningProgressListener = o -> recomputeRunningProgress();

    public ReadOnlyDoubleProperty runningProgressProperty() {
        return runningProgress.getReadOnlyProperty();
    }

    /// Files still to be downloaded/processed across all running tasks, for the overview panel.
    private final ReadOnlyIntegerWrapper remainingFiles = new ReadOnlyIntegerWrapper(0);

    public ReadOnlyIntegerProperty remainingFilesProperty() {
        return remainingFiles.getReadOnlyProperty();
    }

    private static void checkFxThread() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("TaskCenter must be accessed from the JavaFX Application Thread");
    }

    /// All active (queued + running) tasks, ordered by submission. Live view.
    public ObservableList<Entry> getEntries() {
        return activeTasks;
    }

    public ObservableList<Entry> getFailedTasks() {
        return failedTasks;
    }

    /// 1-based position of a queued entry among all QUEUED entries (submission order), for
    /// "queued, position N" hints. 0 if the entry is not queued.
    public int queuePosition(Entry entry) {
        checkFxThread();
        int position = 0;
        for (Entry e : tasks) {
            if (e.getStatus() != Status.QUEUED)
                continue;
            position++;
            if (e == entry)
                return position;
        }
        return 0;
    }

    /// Submits a managed task. Returns the (possibly pre-existing, de-duplicated) entry — never
    /// silently drops a task. The caller may attach a dialog to the returned entry.
    ///
    /// The executor must **not** have been started yet; the scheduler owns starting it.
    public Entry submit(TaskExecutor executor, String title, String detail, TaskKind kind, String name, String resourceKey) {
        checkFxThread();
        registerConcurrencyListener();

        // De-duplicate by executor identity.
        for (Entry e : activeTasks)
            if (e.getExecutor() == executor)
                return e;

        // De-duplicate installs by (kind, name).
        if (kind != null && name != null) {
            Entry existing = findActiveInstall(kind, name);
            if (existing != null)
                return existing;
        }

        // De-duplicate by detail (a stable per-task description). Title is intentionally NOT used
        // as a fallback key here: many unrelated tasks share a generic title (e.g. "Downloading"),
        // and merging them would silently drop distinct work.
        if (detail != null) {
            for (Entry e : activeTasks)
                if (detail.equals(e.getDetail()))
                    return e;
        }

        Entry entry = new Entry(executor, title, detail, kind, name, resourceKey);
        tasks.add(entry);
        tryStartNext();
        return entry;
    }

    private boolean concurrencyListenerRegistered;

    /// Lazily (settings are not loaded when this singleton is constructed) pumps the scheduler when
    /// the user raises the concurrency limit — otherwise blocked QUEUED tasks would sit idle until
    /// the next submit()/onStopped() happens to call tryStartNext().
    private void registerConcurrencyListener() {
        if (concurrencyListenerRegistered)
            return;
        concurrencyListenerRegistered = true;
        SettingsManager.settings().backgroundTaskConcurrencyProperty()
                .addListener((InvalidationListener) o -> Platform.runLater(this::tryStartNext));
    }

    private int concurrencyLimit() {
        return Math.max(1, SettingsManager.settings().backgroundTaskConcurrencyProperty().get());
    }

    /// Whether {@code entry} may run alongside the current runners: its resource key must be non-null
    /// and distinct from every runner's. A null key is exclusive, so it only starts while idle.
    private boolean canRunConcurrently(Entry entry) {
        String key = entry.getResourceKey();
        if (key == null)
            return running.isEmpty();
        for (Entry r : running) {
            String rk = r.getResourceKey();
            if (rk == null || rk.equals(key))
                return false;
        }
        return true;
    }

    private void tryStartNext() {
        for (Entry entry : tasks) {
            if (running.size() >= concurrencyLimit())
                break;
            if (entry.getStatus() != Status.QUEUED)
                continue;
            if (canRunConcurrently(entry)) {
                start(entry);
            } else if (entry.getResourceKey() == null) {
                // A blocked exclusive task must not be starved: it needs the running set to drain,
                // so don't let later-submitted tasks keep jumping ahead of it. Blocked *keyed*
                // entries are safe to skip past — within one key the queue order is FIFO anyway.
                break;
            }
        }
    }

    private void start(Entry entry) {
        running.add(entry);
        entry.aggregator.progressProperty().addListener(runningProgressListener);

        // Attach the completion listener BEFORE starting so a fast-finishing task cannot
        // complete before we are listening.
        entry.executor.addTaskListener(new TaskListener() {
            @Override
            public void onRunning(Task<?> task) {
                // Surface the current significant sub-task name as the entry's status line.
                if (!task.getSignificance().shouldShow())
                    return;
                String name = task.getName();
                // Filter debugging default names: many Task combinators default their name to the
                // creation site (Task.getCaller(), e.g. "…GameInstallTask.execute(GameInstallTask.java:77)"),
                // which must not leak into the UI as a status line.
                if (name == null || name.equals(task.getClass().getName())
                        || name.contains(".java:") || name.endsWith("(Unknown Source)"))
                    return;
                Platform.runLater(() -> entry.statusMessage.set(name));
            }

            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                Platform.runLater(() -> onStopped(entry));
            }
        });
        entry.executor.start();
        // Flip to RUNNING only after start(): a status listener reacting synchronously to the
        // transition (e.g. calling cancel) must find the executor already started, or
        // executor.cancel() would throw on the not-yet-created future.
        entry.status.set(Status.RUNNING);
        recomputeRunningProgress();
    }

    private void recomputeRunningProgress() {
        double sum = 0;
        int count = 0;
        int remaining = 0;
        for (Entry entry : running) {
            double p = entry.aggregator.getProgress();
            if (p >= 0) {
                sum += p;
                count++;
            }
            remaining += entry.aggregator.getRemainingFiles();
        }
        runningProgress.set(count > 0 ? sum / count : -1);
        remainingFiles.set(remaining);
    }

    private void onStopped(Entry entry) {
        checkFxThread();

        running.remove(entry);
        entry.aggregator.progressProperty().removeListener(runningProgressListener);
        entry.aggregator.finish();
        recomputeRunningProgress();

        // Idempotent: a cancelled-while-queued entry is already terminal; ignore the late onStop.
        if (!entry.getStatus().isTerminal()) {
            Throwable exception = entry.getException();
            Status result;
            // Check isCancelled() explicitly: some executor paths (a CompletableFutureTask root)
            // swallow the CancellationException and leave getException() null, which would
            // otherwise misreport a user-cancelled task as SUCCEEDED.
            if (entry.executor.isCancelled() || exception instanceof CancellationException)
                result = Status.CANCELLED;
            else if (exception == null)
                result = Status.SUCCEEDED;
            else
                result = Status.FAILED;
            entry.status.set(result);
        }

        // Successful tasks leave no history: the result (an installed version, a downloaded file) is
        // its own record — a "completed" log would only accumulate. Failures stay for inspection.
        if (entry.getStatus() == Status.SUCCEEDED)
            tasks.remove(entry);

        trimHistory();
        tryStartNext();
    }

    /// Terminal entries stay in [#tasks] (each pinning its executor and full task graph) until the
    /// user clears them; cap the history so a long session doesn't retain memory without bound.
    private static final int MAX_HISTORY = 128;

    private void trimHistory() {
        int terminal = 0;
        for (Entry e : tasks)
            if (e.getStatus().isTerminal())
                terminal++;
        if (terminal <= MAX_HISTORY)
            return;
        Iterator<Entry> iterator = tasks.iterator();
        while (iterator.hasNext() && terminal > MAX_HISTORY) {
            if (iterator.next().getStatus().isTerminal()) {
                iterator.remove();
                terminal--;
            }
        }
    }

    /// Cancels a task whether it is queued or running.
    public void cancel(Entry entry) {
        checkFxThread();

        switch (entry.getStatus()) {
            case QUEUED:
                // Never started; mark terminal so the scheduler skips it. running is untouched.
                // No onStop will ever come for this executor, so settle the aggregator here.
                entry.status.set(Status.CANCELLED);
                entry.aggregator.finish();
                trimHistory();
                break;
            case RUNNING:
                // onStopped() will finalize the status as CANCELLED via the CancellationException.
                entry.executor.cancel();
                break;
            default:
                break;
        }
    }

    /// Removes all failed/cancelled entries from the history.
    public void clearFailed() {
        checkFxThread();
        tasks.removeIf(e -> e.getStatus() == Status.FAILED || e.getStatus() == Status.CANCELLED);
    }

    /// Cancels every active (queued + running) task. Snapshots first: cancel() flips statuses, which
    /// mutates the activeTasks view mid-iteration.
    public void cancelAll() {
        checkFxThread();
        for (Entry entry : new ArrayList<>(activeTasks))
            cancel(entry);
    }

    private Entry findActiveInstall(TaskKind kind, String name) {
        for (Entry entry : activeTasks) {
            if (entry.getKind() != kind || entry.getName() == null)
                continue;
            if (nameEquals(entry.getName(), name))
                return entry;
        }
        return null;
    }

    /// Whether an install of the given (kind, name) is already queued or running. Used by name
    /// validators to forbid scheduling a duplicate install.
    public boolean hasQueuedInstallName(TaskKind kind, String name) {
        checkFxThread();
        if (name == null || kind == null)
            return false;
        return findActiveInstall(kind, name) != null;
    }

    private static boolean nameEquals(String a, String b) {
        return OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                ? a.equalsIgnoreCase(b)
                : a.equals(b);
    }
}
