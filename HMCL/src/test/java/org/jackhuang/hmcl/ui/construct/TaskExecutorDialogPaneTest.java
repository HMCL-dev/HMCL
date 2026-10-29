// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.TaskManagerPage;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.TaskManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Verifies background task lifetimes and reuse of progress dialogs.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class TaskExecutorDialogPaneTest {

    /// Settings replaced temporarily to initialize dialog animation preferences.
    private static @Nullable Object previousSettings;

    /// Installs in-memory settings with synchronous dialog transitions for lifecycle assertions.
    @BeforeAll
    static void prepareSettings() throws Exception {
        Field field = SettingsManager.class.getDeclaredField("launcherSettings");
        field.setAccessible(true);
        previousSettings = field.get(null);
        LauncherSettings settings = new LauncherSettings();
        // The default depends on runtime graphics capabilities and can defer onDialogShown to an animation pulse.
        settings.animationDisabledProperty().set(true);
        field.set(null, settings);
    }

    /// Restores the settings that were present before these tests.
    @AfterAll
    static void restoreSettings() throws Exception {
        Field field = SettingsManager.class.getDeclaredField("launcherSettings");
        field.setAccessible(true);
        field.set(null, previousSettings);
    }

    /// Verifies dialog reuse and the task button's idle, running, and pending presentations.
    @Test
    void restoresThroughMainWindow() throws Exception {
        onFxThread(() -> {
            Field field = Controllers.class.getDeclaredField("decorator");
            field.setAccessible(true);
            @Nullable Object previous = field.get(null);
            Decorator decorator = new Decorator(new StackPane(), stage -> { });
            new Scene(decorator.getRoot());
            field.set(null, decorator);
            TaskExecutorDialogPane pane = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            TaskExecutorDialogPane second = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            try {
                ButtonBase tasksButton = decorator.getRoot().lookupAll(".jfx-decorator-button").stream()
                        .filter(node -> i18n("task.list").equals(node.getAccessibleText()))
                        .map(ButtonBase.class::cast).findFirst().orElseThrow();
                Node icon = Objects.requireNonNull(tasksButton.getGraphic());
                ControlledExecutor executor = new ControlledExecutor();
                pane.setExecutor(executor, false);
                executor.start();
                Controllers.dialog(pane);
                assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                assertSame(icon, tasksButton.getGraphic());
                Node progress = pane.getCenter();
                pane.background();
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                Node spinner = Objects.requireNonNull(tasksButton.getGraphic());
                assertInstanceOf(JFXSpinner.class, spinner);
                pane.restore();
                pane.restore();
                assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                assertSame(icon, tasksButton.getGraphic());
                assertTrue(DialogUtils.isShowing(pane));
                assertSame(progress, pane.getCenter());
                @Nullable JFXDialogPane stack = (JFXDialogPane) pane.getProperties()
                        .get(DialogUtils.PROPERTY_PARENT_PANE_REF);
                assertNotNull(stack);
                assertEquals(1, stack.size());
                assertSame(pane, stack.peek().orElseThrow());
                assertEquals(1, executor.starts);

                ControlledExecutor secondExecutor = new ControlledExecutor();
                second.setExecutor(secondExecutor, false);
                secondExecutor.start();
                Controllers.dialog(second);
                second.background();
                Node nextSpinner = Objects.requireNonNull(tasksButton.getGraphic());
                assertInstanceOf(JFXSpinner.class, nextSpinner);
                assertNotSame(spinner, nextSpinner);
                pane.background();
                pane.restore();
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                assertSame(nextSpinner, tasksButton.getGraphic());

                AtomicInteger actions = new AtomicInteger();
                pane.background();
                assertFalse(pane.runOrDeferCompletionAction("Install", "Ready to install", actions::incrementAndGet));
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                assertSame(nextSpinner, tasksButton.getGraphic());
                second.restore();
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                SVGPath pendingIcon = assertInstanceOf(SVGPath.class, tasksButton.getGraphic());
                assertEquals(SVG.PENDING.getPath(), pendingIcon.getContent());
                second.background();
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                Node resumedSpinner = assertInstanceOf(JFXSpinner.class, tasksButton.getGraphic());
                assertNotSame(nextSpinner, resumedSpinner);
                pane.runPendingAction();
                assertEquals(1, actions.get());
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                assertSame(resumedSpinner, tasksButton.getGraphic());

                assertFalse(second.runOrDeferCompletionAction("Install", "Ready to install", actions::incrementAndGet));
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                assertEquals(SVG.PENDING.getPath(), assertInstanceOf(SVGPath.class, tasksButton.getGraphic()).getContent());
                second.setExecutor(null);
                assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                assertSame(icon, tasksButton.getGraphic());
            } finally {
                DialogUtils.close(pane);
                pane.setExecutor(null);
                DialogUtils.close(second);
                second.setExecutor(null);
                field.set(null, previous);
            }
            return true;
        });
    }

    /// Verifies disabled cancellation remains disabled and an enabled action runs only once.
    @Test
    void cancellationHonorsActionAndWaitsForCompletion() throws Exception {
        TaskExecutorDialogPane pane = onFxThread(() -> {
            TaskExecutorDialogPane dialog = new TaskExecutorDialogPane(TaskCancellationAction.NO_CANCEL);
            ControlledExecutor executor = new ControlledExecutor();
            dialog.setExecutor(executor, false);
            executor.start();
            @Nullable ButtonBase cancel = (ButtonBase) dialog.lookup(".dialog-cancel");
            assertNotNull(cancel);
            assertTrue(cancel.isDisabled());
            cancel.fire();
            assertFalse(executor.isCancelled());
            dialog.setCancel(null);
            assertTrue(cancel.isDisabled());
            AtomicInteger cancellations = new AtomicInteger();
            dialog.setCancel(ignored -> cancellations.incrementAndGet());
            assertFalse(cancel.isDisabled());
            cancel.fire();
            cancel.fire();
            assertTrue(executor.isCancelled());
            assertEquals(1, cancellations.get());
            assertTrue(TaskManager.getInstance().getTasks().contains(dialog));
            executor.stop(false);
            return dialog;
        });
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            return true;
        });
    }

    /// Verifies hidden work continues and its completion removes only its own dialog entry.
    @Test
    void backgroundTaskContinuesUntilCompletion() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        TaskExecutor executor = Task.runAsync(() -> {
            executions.incrementAndGet();
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
        }).executor();
        TaskExecutorDialogPane pane = onFxThread(() -> {
            TaskExecutorDialogPane dialog = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            dialog.setExecutor(executor);
            executor.addTaskListener(new TaskListener() {
                /// Signals after the pane's completion callback has been queued.
                @Override
                public void onStop(boolean success, TaskExecutor completed) {
                    stopped.countDown();
                }
            });
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            executor.start();
            return dialog;
        });
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            onFxThread(() -> {
                AtomicInteger backgroundCalls = new AtomicInteger();
                pane.setOnBackground(backgroundCalls::incrementAndGet);
                Node progress = pane.getCenter();
                StackPane container = new StackPane();
                new Scene(container);
                for (int i = 0; i < 3; i++) {
                    pane.background();
                    pane.background();
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    assertFalse(DialogUtils.isShowing(pane));
                    assertFalse(executor.isCancelled());
                    assertTrue(TaskManager.getInstance().getTasks().contains(pane));
                    // Reuse the same pane through the dialog stack used by Controllers.dialog.
                    DialogUtils.show(container, pane);
                    DialogUtils.show(container, pane);
                    assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                    pane.restore();
                    assertSame(progress, pane.getCenter());
                    @Nullable JFXDialogPane stack = (JFXDialogPane) pane.getProperties()
                            .get(DialogUtils.PROPERTY_PARENT_PANE_REF);
                    assertNotNull(stack);
                    assertEquals(1, stack.size());
                }
                assertEquals(1, backgroundCalls.get());
                pane.background();
                Label other = new Label("Another dialog");
                DialogUtils.show(container, other);
                return true;
            });
        } finally {
            release.countDown();
        }
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertFalse(DialogUtils.isShowing(pane));
            assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
            pane.restore();
            assertFalse(DialogUtils.isShowing(pane));
            return true;
        });
        assertEquals(1, executions.get());
        assertNull(executor.getException());
    }

    /// Verifies hiding or restoring a queued dialog cannot start it ahead of background work.
    @Test
    void queuedDialogCanBeHiddenAndRestored() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        List<CompletableFuture<Boolean>> completions = new ArrayList<>();
        try {
            onFxThread(() -> {
                Field field = Controllers.class.getDeclaredField("decorator");
                field.setAccessible(true);
                @Nullable Object previous = field.get(null);
                Decorator decorator = new Decorator(new StackPane(), stage -> { });
                new Scene(decorator.getRoot());
                field.set(null, decorator);
                TaskExecutorDialogPane first = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
                TaskExecutorDialogPane second = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
                try {
                    first.setExecutor(Task.runAsync(() -> assertTrue(release.await(10, TimeUnit.SECONDS))).executor());
                    Controllers.dialog(first);
                    completions.add(first.startExecutor());
                    first.background();
                    second.setExecutor(Task.runAsync(executions::incrementAndGet).executor());
                    Controllers.dialog(second);
                    completions.add(second.startExecutor());
                    assertTrue(second.queuedProperty().get());
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    second.background();
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    assertFalse(DialogUtils.isShowing(second));
                    second.restore();
                    second.restore();
                    assertTrue(DialogUtils.isShowing(second));
                    assertTrue(second.queuedProperty().get());
                    assertEquals(0, executions.get());
                    second.background();
                    first.setExecutor(null);
                    assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                } finally {
                    DialogUtils.close(first);
                    DialogUtils.close(second);
                    first.setExecutor(null);
                    second.setExecutor(null);
                    field.set(null, previous);
                }
                return true;
            });
        } finally {
            release.countDown();
        }
        for (CompletableFuture<Boolean> completion : completions)
            assertTrue(completion.get(10, TimeUnit.SECONDS));
        assertEquals(1, executions.get());
    }

    /// Verifies both successful and failed completion remove hidden tasks from the list.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completionRemovesHiddenTask(boolean success) throws Exception {
        TaskExecutorDialogPane pane = onFxThread(() -> {
            TaskExecutorDialogPane dialog = new TaskExecutorDialogPane(TaskCancellationAction.NO_CANCEL);
            ControlledExecutor executor = new ControlledExecutor();
            dialog.setExecutor(executor, false);
            executor.start();
            assertTrue(TaskManager.getInstance().getTasks().contains(dialog));
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            dialog.background();
            assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
            assertThrows(UnsupportedOperationException.class,
                    () -> TaskManager.getInstance().getTasks().clear());
            executor.stop(success);
            return dialog;
        });
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
            return true;
        });
    }

    /// Verifies a visible task list switches from progress to a retained, single-use install action.
    @Test
    void backgroundCompletionWaitsForInstallAction() throws Exception {
        AtomicInteger installations = new AtomicInteger();
        TaskManagerPage page = onFxThread(() -> {
            TaskManagerPage result = new TaskManagerPage();
            new Scene(result, 600, 400);
            return result;
        });
        TaskExecutorDialogPane pane = onFxThread(() -> {
            TaskExecutorDialogPane dialog = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            dialog.setTitle("Example modpack");
            ControlledExecutor executor = new ControlledExecutor();
            dialog.setExecutor(executor);
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            executor.start();
            dialog.background();
            page.applyCss();
            page.layout();
            assertFalse(page.lookupAll(".jfx-spinner").isEmpty());
            assertFalse(dialog.runOrDeferCompletionAction(i18n("button.install"), i18n("modpack.install.ready"),
                    installations::incrementAndGet));
            executor.stop(true);
            return dialog;
        });
        onFxThread(() -> {
            try {
                assertEquals(0, installations.get());
                assertFalse(DialogUtils.isShowing(pane));
                assertTrue(TaskManager.getInstance().getTasks().contains(pane));
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                page.applyCss();
                page.layout();
                ButtonBase install = page.lookupAll(".jfx-button").stream()
                        .filter(ButtonBase.class::isInstance)
                        .map(ButtonBase.class::cast)
                        .filter(button -> i18n("button.install").equals(button.getText()))
                        .findFirst().orElseThrow();
                assertTrue(page.lookupAll(".label").stream().anyMatch(node -> node instanceof Label label
                        && i18n("modpack.install.ready").equals(label.getText())));
                assertTrue(page.lookupAll(".jfx-spinner").isEmpty());
                install.fire();
                pane.runPendingAction();
                assertEquals(1, installations.get());
                assertNull(pane.pendingActionProperty().get());
                assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            } finally {
                pane.setExecutor(null);
            }
            return true;
        });
    }

    /// Verifies foreground completion, including a restored dialog, continues immediately.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void foregroundCompletionRunsImmediately(boolean restore) throws Exception {
        TaskExecutorDialogPane pane = onFxThread(() -> {
            TaskExecutorDialogPane dialog = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            ControlledExecutor executor = new ControlledExecutor();
            dialog.setExecutor(executor);
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            executor.start();
            if (restore) {
                dialog.background();
                DialogUtils.show(container, dialog);
            }
            AtomicInteger installations = new AtomicInteger();
            assertTrue(dialog.runOrDeferCompletionAction("Install", "Ready to install", installations::incrementAndGet));
            assertEquals(1, installations.get());
            assertNull(dialog.pendingActionProperty().get());
            executor.stop(true);
            return dialog;
        });
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertFalse(DialogUtils.isShowing(pane));
            return true;
        });
    }

    /// Verifies failed completion and detachment discard a deferred action without invoking it.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unsuccessfulCompletionDiscardsPendingAction(boolean detach) throws Exception {
        AtomicInteger installations = new AtomicInteger();
        TaskExecutorDialogPane pane = onFxThread(() -> {
            TaskExecutorDialogPane dialog = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            ControlledExecutor executor = new ControlledExecutor();
            dialog.setExecutor(executor);
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            executor.start();
            dialog.background();
            assertFalse(dialog.runOrDeferCompletionAction("Install", "Ready to install", installations::incrementAndGet));
            if (detach)
                dialog.setExecutor(null);
            executor.stop(false);
            return dialog;
        });
        onFxThread(() -> {
            assertNull(pane.pendingActionProperty().get());
            pane.runPendingAction();
            assertEquals(0, installations.get());
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
            return true;
        });
    }

    /// Verifies late completion from a detached executor cannot remove its replacement.
    @Test
    void staleCompletionDoesNotRemoveReplacement() throws Exception {
        TaskExecutorDialogPane pane = onFxThread(() -> {
            TaskExecutorDialogPane dialog = new TaskExecutorDialogPane(null);
            ControlledExecutor previous = new ControlledExecutor();
            dialog.setExecutor(previous, false);
            previous.start();
            previous.stop(true);
            ControlledExecutor replacement = new ControlledExecutor();
            dialog.setExecutor(replacement, false);
            replacement.start();
            dialog.setExecutor(replacement, false);
            return dialog;
        });
        onFxThread(() -> {
            assertTrue(TaskManager.getInstance().getTasks().contains(pane));
            assertEquals(1, TaskManager.getInstance().getTasks().stream().filter(it -> it == pane).count());
            pane.setExecutor(null);
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            return true;
        });
    }

    /// Verifies closing detached content cannot close an unrelated dialog or run its callback twice.
    @Test
    void closingHiddenDialogIsIdempotent() throws Exception {
        onFxThread(() -> {
            StackPane container = new StackPane();
            new Scene(container);
            Label first = new Label("First");
            Label second = new Label("Second");
            DialogUtils.show(container, first);
            DialogUtils.close(first);
            DialogUtils.show(container, second);
            DialogUtils.close(first);
            assertTrue(DialogUtils.isShowing(second));
            assertFalse(DialogUtils.isShowing(first));
            assertNull(first.getProperties().get(DialogUtils.PROPERTY_PARENT_PANE_REF));
            DialogUtils.close(second);
            return true;
        });
    }

    /// Executes an operation on the JavaFX thread and waits for its result.
    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> future = new FutureTask<>(action);
        Platform.runLater(future);
        return future.get(30, TimeUnit.SECONDS);
    }

    /// Emits executor lifecycle events under test control without scheduling work.
    @NotNullByDefault
    private static final class ControlledExecutor extends TaskExecutor {

        /// The number of times execution has been requested.
        private int starts;

        /// Creates an executor with an unused root task.
        private ControlledExecutor() {
            super(Task.runAsync(() -> { }));
        }

        @Override
        public TaskExecutor start() {
            starts++;
            taskListeners.forEach(TaskListener::onStart);
            return this;
        }

        /// Rejects synchronous execution, which this test executor does not support.
        @Override
        public boolean test() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        /// Reports completion with the supplied result.
        private void stop(boolean success) {
            taskListeners.forEach(listener -> listener.onStop(success, this));
        }
    }
}
