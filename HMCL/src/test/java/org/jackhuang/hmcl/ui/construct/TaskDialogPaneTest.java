// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.TaskListPage;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jackhuang.hmcl.util.TaskManager;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.junit.jupiter.api.Assertions.*;

/// Exercises task dialogs and their actions through the JavaFX task list.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class TaskDialogPaneTest {

    /// Original launcher settings restored when the test window is no longer used.
    private static @Nullable LauncherSettings previousSettings;

    /// Supplies the main window's theme settings without loading a user profile.
    @BeforeAll
    static void installWindowSettings() throws ReflectiveOperationException {
        Field settings = SettingsManager.class.getDeclaredField("launcherSettings");
        settings.setAccessible(true);
        previousSettings = (LauncherSettings) settings.get(null);
        settings.set(null, new LauncherSettings());
    }

    /// Restores the launcher configuration after exercising the task window.
    @AfterAll
    static void restoreWindowSettings() throws ReflectiveOperationException {
        Field settings = SettingsManager.class.getDeclaredField("launcherSettings");
        settings.setAccessible(true);
        settings.set(null, previousSettings);
    }

    /// The task list can restore a running dialog or invoke its deferred action once it finishes.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completionActionFollowsDialogVisibility(boolean restore) throws Exception {
        AtomicInteger actions = new AtomicInteger();
        TaskDialogPane pane = onFxThread(() -> {
            Field field = Controllers.class.getDeclaredField("decorator");
            field.setAccessible(true);
            @Nullable Object previous = field.get(null);
            Decorator decorator = new Decorator(new StackPane(), stage -> {});
            new Scene(decorator.getRoot());
            field.set(null, decorator);
            try {
                TaskDialogPane dialog = new TaskDialogPane(null);
                dialog.setTask(Task.runAsync(Schedulers.javafx(), () ->
                        dialog.runOrDeferCompletionAction("Install", "Ready", actions::incrementAndGet)));
                Controllers.dialog(dialog);
                dialog.startTask();
                dialog.runInBackground();
                dialog.runInBackground();
                assertFalse(DialogUtils.isShowing(dialog));
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                if (restore) {
                    pressTaskAction(i18n("task.show"));
                    assertTrue(DialogUtils.isShowing(dialog));
                    assertFalse(dialog.runningInBackgroundProperty().get());
                }
                return dialog;
            } finally {
                field.set(null, previous);
            }
        });
        assertTrue(onFxThread(pane::startTask).get(10, TimeUnit.SECONDS));
        onFxThread(() -> {
            assertEquals(restore ? 1 : 0, actions.get());
            if (!restore) {
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                pressTaskAction("Install");
            }
            pane.runPendingAction();
            assertEquals(1, actions.get());
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            return true;
        });
    }

    /// Failure and detachment discard deferred actions without closing a different visible dialog.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void discardsInactiveCompletionAction(boolean detach) throws Exception {
        TaskDialogPane pane = onFxThread(() -> new TaskDialogPane(null));
        Label nextDialog = onFxThread(() -> new Label("Next"));
        CompletableFuture<Boolean> completion = onFxThread(() -> {
            pane.setTask(Task.runAsync(Schedulers.javafx(), () -> {
                assertFalse(pane.runOrDeferCompletionAction("Install", "Ready", () -> fail("Unexpected action")));
                if (detach) {
                    pane.setTask(null);
                    return;
                }
                throw new IOException("Download failed");
            }));
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, pane);
            CompletableFuture<Boolean> submitted = pane.startTask();
            pane.runInBackground();
            DialogUtils.show(container, nextDialog);
            return submitted;
        });
        assertEquals(detach, completion.get(10, TimeUnit.SECONDS));
        onFxThread(() -> {
            assertNull(pane.pendingActionProperty().get());
            pane.runPendingAction();
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            DialogUtils.close(pane);
            assertTrue(DialogUtils.isShowing(nextDialog));
            DialogUtils.close(nextDialog);
            return true;
        });
    }

    /// A queued finish callback belongs to the task that emitted it, even if the pane is reused.
    @Test
    void staleCompletionLeavesReplacementVisible() throws Exception {
        CompletableFuture<Integer> release = new CompletableFuture<>();
        TaskDialogPane pane = onFxThread(() -> {
            TaskDialogPane dialog = new TaskDialogPane(null);
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            Task<?> previous = Task.completed(0);
            dialog.setTask(previous);
            assertTrue(previous.test());
            dialog.setTask(Task.fromCompletableFuture(release));
            dialog.startTask();
            return dialog;
        });
        try {
            onFxThread(() -> {
                assertTrue(DialogUtils.isShowing(pane));
                assertTrue(TaskManager.getInstance().getTasks().contains(pane));
                return true;
            });
        } finally {
            release.complete(0);
        }
        assertTrue(onFxThread(pane::startTask).get(10, TimeUnit.SECONDS));
        assertFalse(onFxThread(() -> DialogUtils.isShowing(pane)));
    }

    /// Started tasks retain an error row only when cancellation also reports a cleanup failure.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledTaskRowReportsCleanupFailure(boolean failedCleanup) throws Exception {
        InterruptedException interrupted = new InterruptedException("Cancelled");
        if (failedCleanup)
            interrupted.addSuppressed(new IOException("Could not close file"));
        Task<?> task = Task.runAsync(() -> { throw interrupted; }).setSignificance(TaskSignificance.MAJOR);
        ListView<?> rows = onFxThread(() -> {
            TaskListPane pane = new TaskListPane();
            pane.setTask(task);
            assertFalse(task.test());
            return assertInstanceOf(ListView.class, pane.lookup(".list-view"));
        });
        assertEquals(failedCleanup ? 1 : 0, onFxThread(() -> rows.getItems().size()).intValue());
        CancellationException cancellation = assertInstanceOf(CancellationException.class, task.exceptionNow());
        assertEquals(failedCleanup, I18n.localizeErrorMessage(cancellation).contains("Could not close file"));
    }

    /// Activates the visible task-list button with the supplied accessibility label.
    private static void pressTaskAction(String label) {
        TaskListPage page = new TaskListPage();
        new Scene(page, 600, 400);
        page.applyCss();
        page.layout();
        page.lookupAll(".button").stream().filter(ButtonBase.class::isInstance).map(ButtonBase.class::cast)
                .filter(button -> label.equals(button.getAccessibleText())).findFirst().orElseThrow().fire();
    }

    /// Runs after previously queued JavaFX callbacks.
    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> future = new FutureTask<>(action);
        Platform.runLater(future);
        return future.get(10, TimeUnit.SECONDS);
    }
}
