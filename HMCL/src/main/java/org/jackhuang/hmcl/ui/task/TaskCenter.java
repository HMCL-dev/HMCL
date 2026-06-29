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
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.util.concurrent.CancellationException;

/// Central scheduler for "managed" (backgroundable) tasks.
///
/// Design (single serial queue):
///  - Every managed task lives here from the moment it is submitted; a dialog is merely a *view*
///    onto a task, not the owner of its lifecycle. Whether a task is shown in the foreground or
///    runs headless in the background, it is the *same* entry in the *same* queue.
///  - At most one task runs at a time (`running`). The scheduler attaches its completion listener
///    *before* starting the executor, so the start/finish race that could orphan a task (and
///    permanently block the queue) cannot happen.
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
        private final ReadOnlyObjectWrapper<Status> status = new ReadOnlyObjectWrapper<>(Status.QUEUED);

        /// Whether a foreground dialog is currently presenting this task. Read by presenters at
        /// terminal time to decide between an in-dialog message and a background notification.
        /// FX-thread only.
        private boolean foregroundShown;

        Entry(TaskExecutor executor, String title, String detail, TaskKind kind, String name) {
            this.executor = executor;
            this.title = title;
            this.detail = detail;
            this.kind = kind != null ? kind : TaskKind.OTHER;
            this.name = name;
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

        public Status getStatus() {
            return status.get();
        }

        public ReadOnlyObjectProperty<Status> statusProperty() {
            return status.getReadOnlyProperty();
        }

        public Throwable getException() {
            return executor.getException();
        }

        public boolean isForegroundShown() {
            return foregroundShown;
        }

        public void setForegroundShown(boolean foregroundShown) {
            this.foregroundShown = foregroundShown;
        }
    }

    /// Single source of truth. The extractor makes the list fire "update" change events when an
    /// entry's status changes, so the derived [FilteredList]s below re-filter automatically.
    private final ObservableList<Entry> tasks =
            FXCollections.observableArrayList(entry -> new Observable[]{entry.statusProperty()});

    private final FilteredList<Entry> activeTasks = new FilteredList<>(tasks, e -> e.getStatus().isActive());
    private final FilteredList<Entry> succeededTasks = new FilteredList<>(tasks, e -> e.getStatus() == Status.SUCCEEDED);
    private final FilteredList<Entry> failedTasks =
            new FilteredList<>(tasks, e -> e.getStatus() == Status.FAILED || e.getStatus() == Status.CANCELLED);

    private Entry running;

    private static void checkFxThread() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("TaskCenter must be accessed from the JavaFX Application Thread");
    }

    /// All active (queued + running) tasks, ordered by submission. Live view.
    public ObservableList<Entry> getEntries() {
        return activeTasks;
    }

    public ObservableList<Entry> getSucceededTasks() {
        return succeededTasks;
    }

    public ObservableList<Entry> getFailedTasks() {
        return failedTasks;
    }

    public Entry getRunningEntry() {
        return running;
    }

    /// Submits a managed task. Returns the (possibly pre-existing, de-duplicated) entry — never
    /// silently drops a task. The caller may attach a dialog to the returned entry.
    ///
    /// The executor must **not** have been started yet; the scheduler owns starting it.
    public Entry submit(TaskExecutor executor, String title, String detail, TaskKind kind, String name) {
        checkFxThread();

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

        Entry entry = new Entry(executor, title, detail, kind, name);
        tasks.add(entry);
        tryStartNext();
        return entry;
    }

    private void tryStartNext() {
        if (running != null)
            return;

        for (Entry entry : tasks) {
            if (entry.getStatus() != Status.QUEUED)
                continue;

            running = entry;
            entry.status.set(Status.RUNNING);

            // Attach the completion listener BEFORE starting so a fast-finishing task cannot
            // complete before we are listening.
            entry.executor.addTaskListener(new TaskListener() {
                @Override
                public void onStop(boolean success, TaskExecutor executor) {
                    Platform.runLater(() -> onStopped(entry));
                }
            });
            entry.executor.start();
            return;
        }
    }

    private void onStopped(Entry entry) {
        checkFxThread();

        if (running == entry)
            running = null;

        // Idempotent: a cancelled-while-queued entry is already terminal; ignore the late onStop.
        if (!entry.getStatus().isTerminal()) {
            Throwable exception = entry.getException();
            Status result;
            if (exception instanceof CancellationException)
                result = Status.CANCELLED;
            else if (exception == null)
                result = Status.SUCCEEDED;
            else
                result = Status.FAILED;
            entry.status.set(result);
        }

        tryStartNext();
    }

    /// Cancels a task whether it is queued or running.
    public void cancel(Entry entry) {
        checkFxThread();

        switch (entry.getStatus()) {
            case QUEUED:
                // Never started; mark terminal so the scheduler skips it. running is untouched.
                entry.status.set(Status.CANCELLED);
                break;
            case RUNNING:
                // onStopped() will finalize the status as CANCELLED via the CancellationException.
                entry.executor.cancel();
                break;
            default:
                break;
        }
    }

    /// Removes all succeeded entries from the history.
    public void clearSucceeded() {
        checkFxThread();
        tasks.removeIf(e -> e.getStatus() == Status.SUCCEEDED);
    }

    /// Removes all failed/cancelled entries from the history.
    public void clearFailed() {
        checkFxThread();
        tasks.removeIf(e -> e.getStatus() == Status.FAILED || e.getStatus() == Status.CANCELLED);
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
