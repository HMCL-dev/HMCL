// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXSpinner;
import com.jfoenix.controls.JFXTextField;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3ProgressIndicator;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.LauncherState;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.StyleSheets;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.Controllers;
import org.glavo.m3fx.controls.M3OverlayPane;
import org.glavo.m3fx.controls.M3DialogHandle;
import org.glavo.m3fx.controls.M3Dialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.TextFlow;
import org.glavo.m3fx.animation.M3MotionSettings;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.TaskManagerPage;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.FutureCallback;
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

    /// Window geometry state replaced without installing persistence listeners.
    private static @Nullable Object previousState;

    /// The shared M3FX motion preference before this test class runs.
    private static boolean previousReducedMotion;

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
        Field stateField = SettingsManager.class.getDeclaredField("launcherState");
        stateField.setAccessible(true);
        previousState = stateField.get(null);
        stateField.set(null, new LauncherState());
        onFxThread(() -> {
            previousReducedMotion = M3MotionSettings.isGlobalReducedMotionRequested();
            M3MotionSettings.setGlobalReducedMotionRequested(true);
            return true;
        });
    }

    /// Restores the settings that were present before these tests.
    @AfterAll
    static void restoreSettings() throws Exception {
        Field field = SettingsManager.class.getDeclaredField("launcherSettings");
        field.setAccessible(true);
        field.set(null, previousSettings);
        Field stateField = SettingsManager.class.getDeclaredField("launcherState");
        stateField.setAccessible(true);
        stateField.set(null, previousState);
        onFxThread(() -> {
            M3MotionSettings.setGlobalReducedMotionRequested(previousReducedMotion);
            return true;
        });
    }

    /// Verifies dialog reuse and the task button's idle, running, and pending presentations.
    @Test
    void restoresThroughMainWindow() throws Exception {
        onFxThread(() -> {
            Field field = Controllers.class.getDeclaredField("decorator");
            field.setAccessible(true);
            @Nullable Object previous = field.get(null);
            Decorator decorator = new Decorator(new StackPane(), stage -> { });
            showStage(new Scene(decorator.getRoot(), 800, 600));
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
                pane.runInBackground();
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                Node spinner = Objects.requireNonNull(tasksButton.getGraphic());
                assertInstanceOf(JFXSpinner.class, spinner);
                pane.restore();
                pane.restore();
                assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                assertSame(icon, tasksButton.getGraphic());
                assertTrue(pane.isDialogShowing());
                assertSame(progress, pane.getCenter());
                assertNotNull(pane.getParent());
                assertEquals(1, executor.starts);

                ControlledExecutor secondExecutor = new ControlledExecutor();
                second.setExecutor(secondExecutor, false);
                secondExecutor.start();
                Controllers.dialog(second);
                second.runInBackground();
                Node nextSpinner = Objects.requireNonNull(tasksButton.getGraphic());
                assertInstanceOf(JFXSpinner.class, nextSpinner);
                assertNotSame(spinner, nextSpinner);
                pane.runInBackground();
                pane.restore();
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                assertSame(nextSpinner, tasksButton.getGraphic());

                AtomicInteger actions = new AtomicInteger();
                pane.runInBackground();
                assertFalse(pane.runOrDeferCompletionAction("Install", "Ready to install", actions::incrementAndGet));
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                assertSame(nextSpinner, tasksButton.getGraphic());
                second.restore();
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                SVGPath pendingIcon = assertInstanceOf(SVGPath.class, tasksButton.getGraphic());
                assertEquals(SVG.PENDING.getPath(), pendingIcon.getContent());
                second.runInBackground();
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
                pane.fireEvent(new DialogCloseEvent());
                pane.setExecutor(null);
                second.fireEvent(new DialogCloseEvent());
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
            @Nullable M3Button cancel = (M3Button) dialog.lookup(".m3-button");
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
            M3OverlayPane container = createDialogHost();
            container.showDialog(Controllers.createDialog(dialog));
            executor.start();
            return dialog;
        });
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            onFxThread(() -> {
                AtomicInteger backgroundCalls = new AtomicInteger();
                pane.setOnBackground(backgroundCalls::incrementAndGet);
                Node progress = pane.getCenter();
                M3OverlayPane container = createDialogHost();
                for (int i = 0; i < 3; i++) {
                    pane.runInBackground();
                    pane.runInBackground();
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    assertFalse(pane.isDialogShowing());
                    assertFalse(executor.isCancelled());
                    assertTrue(TaskManager.getInstance().getTasks().contains(pane));
                    // Reuse the same pane through the dialog stack used by Controllers.dialog.
                    container.showDialog(Controllers.createDialog(pane));
                    pane.restore();
                    assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                    pane.restore();
                    assertSame(progress, pane.getCenter());
                    assertNotNull(pane.getParent());
                }
                assertEquals(1, backgroundCalls.get());
                pane.runInBackground();
                Label other = new Label("Another dialog");
                container.showDialog(Controllers.createDialog(other));
                return true;
            });
        } finally {
            release.countDown();
        }
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertFalse(pane.isDialogShowing());
            assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
            pane.restore();
            assertFalse(pane.isDialogShowing());
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
                showStage(new Scene(decorator.getRoot(), 800, 600));
                field.set(null, decorator);
                TaskExecutorDialogPane first = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
                TaskExecutorDialogPane second = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
                try {
                    first.setExecutor(Task.runAsync(() -> assertTrue(release.await(10, TimeUnit.SECONDS))).executor());
                    Controllers.dialog(first);
                    completions.add(first.startExecutor());
                    first.runInBackground();
                    second.setExecutor(Task.runAsync(executions::incrementAndGet).executor());
                    Controllers.dialog(second);
                    completions.add(second.startExecutor());
                    assertTrue(second.queuedProperty().get());
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    second.runInBackground();
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    assertFalse(second.isDialogShowing());
                    second.restore();
                    second.restore();
                    assertTrue(second.isDialogShowing());
                    assertTrue(second.queuedProperty().get());
                    assertEquals(0, executions.get());
                    second.runInBackground();
                    first.setExecutor(null);
                    assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                } finally {
                    first.fireEvent(new DialogCloseEvent());
                    second.fireEvent(new DialogCloseEvent());
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
            M3OverlayPane container = createDialogHost();
            container.showDialog(Controllers.createDialog(dialog));
            dialog.runInBackground();
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
            M3OverlayPane container = createDialogHost();
            container.showDialog(Controllers.createDialog(dialog));
            executor.start();
            dialog.runInBackground();
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
                assertFalse(pane.isDialogShowing());
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
            M3OverlayPane container = createDialogHost();
            container.showDialog(Controllers.createDialog(dialog));
            executor.start();
            if (restore) {
                dialog.runInBackground();
                container.showDialog(Controllers.createDialog(dialog));
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
            assertFalse(pane.isDialogShowing());
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
            M3OverlayPane container = createDialogHost();
            container.showDialog(Controllers.createDialog(dialog));
            executor.start();
            dialog.runInBackground();
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
            M3OverlayPane container = createDialogHost();
            Label first = new Label("First");
            Label second = new Label("Second");
            M3DialogHandle firstHandle = container.showDialog(Controllers.createDialog(first));
            first.fireEvent(new DialogCloseEvent());
            M3DialogHandle secondHandle = container.showDialog(Controllers.createDialog(second));
            first.fireEvent(new DialogCloseEvent());
            assertFalse(firstHandle.requestClose());
            assertTrue(secondHandle.isShowing());
            assertFalse(firstHandle.isShowing());
            second.fireEvent(new DialogCloseEvent());
            return true;
        });
    }

    /// Verifies restoring during the exit animation transfers content without retaining the old close handler.
    @Test
    void restoresBeforeExitAnimationFinishes() throws Exception {
        onFxThread(() -> {
            M3OverlayPane host = createDialogHost();
            TaskExecutorDialogPane pane = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            ControlledExecutor executor = new ControlledExecutor();
            pane.setExecutor(executor, false);
            executor.start();
            M3MotionSettings.setGlobalReducedMotionRequested(false);
            try {
                var first = Controllers.createDialog(pane);
                M3DialogHandle firstHandle = host.showDialog(first);
                pane.runInBackground();
                var second = Controllers.createDialog(pane);
                M3DialogHandle secondHandle = host.showDialog(second);
                assertNull(first.getDialogPane().getContent());
                assertTrue(pane.isDialogShowing());
                assertFalse(firstHandle.requestClose());
                assertTrue(secondHandle.isShowing());
                assertEquals(1, executor.starts);
                pane.fireEvent(new DialogCloseEvent());
                assertFalse(pane.isDialogShowing());
            } finally {
                pane.setExecutor(null);
                M3MotionSettings.setGlobalReducedMotionRequested(true);
            }
            return true;
        });
    }

    /// Verifies nested dialogs, deferred messages, and completion while the main window is hidden.
    @Test
    void retainsDialogsAcrossWindowHiding() throws Exception {
        onFxThread(() -> {
            Decorator decorator = new Decorator(new StackPane(), stage -> { });
            Stage stage = new Stage();
            stages.add(stage);
            Scene scene = decorator.attachStage(stage);
            M3ThemeManager.install(scene, M3Theme.defaultTheme());
            Label first = new Label("First");
            Label second = new Label("Second");
            Label deferred = new Label("Deferred");
            try {
                decorator.showDialog(first);
                assertNull(first.getScene());
                stage.show();
                assertSame(scene, first.getScene());
                decorator.showDialog(second);
                decorator.showDialogLater(deferred);
                assertSame(scene, second.getScene());
                assertNull(deferred.getScene());
                second.fireEvent(new DialogCloseEvent());
                assertSame(scene, first.getScene());
                assertNull(deferred.getScene());

                stage.hide();
                assertNull(first.getScene());
                stage.show();
                assertSame(scene, first.getScene());
                stage.hide();
                first.fireEvent(new DialogCloseEvent());
                stage.show();
                assertNull(first.getScene());
                assertSame(scene, deferred.getScene());
                deferred.fireEvent(new DialogCloseEvent());

                decorator.showToast("First message");
                decorator.showToast("Second message");
                M3OverlayPane host = decorator.getOverlayPane();
                assertEquals("First message", Objects.requireNonNull(host.getSnackbar()).getText());
                assertEquals(1, host.getSnackbarQueue().size());
                host.dismissSnackbar();
                assertEquals("Second message", Objects.requireNonNull(host.getSnackbar()).getText());
                host.dismissAllSnackbars();

                M3Dialog nativeFirst = new MessageDialogBuilder("First", "Title", MessageType.INFO).ok(null).build();
                M3Dialog nativeSecond = new MessageDialogBuilder("Second", "Title", MessageType.INFO).ok(null).build();
                decorator.showDialog(nativeFirst);
                decorator.showDialogLater(nativeSecond);
                decorator.showDialogLater(nativeSecond);
                assertNull(nativeSecond.getDialogPane().getScene());
                nativeFirst.getDialogPane().getActions().getFirst().fire();
                assertSame(scene, nativeSecond.getDialogPane().getScene());
                stage.hide();
                stage.show();
                assertSame(scene, nativeSecond.getDialogPane().getScene());
                decorator.getRoot().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertNull(nativeSecond.getDialogPane().getScene());
            } finally {
                stage.hide();
                decorator.detachStage();
            }
            return true;
        });
    }

    /// Verifies M3 surfaces and distinguishes title-area dragging from blocked modal-content dragging.
    @Test
    void usesMaterialSurfaceAndPreservesTitleDragging() throws Exception {
        onFxThread(() -> {
            Decorator decorator = new Decorator(new StackPane(), stage -> { });
            Stage stage = new Stage();
            stages.add(stage);
            Scene scene = decorator.attachStage(stage);
            StyleSheets.init(scene, decorator.getContentRoot());
            try {
                stage.show();
                for (M3Dialog dialog : List.of(
                        new MessageDialogBuilder("Message", "Title", MessageType.INFO).ok(null).build(),
                        Controllers.createDialog(new TaskExecutorDialogPane(TaskCancellationAction.NORMAL)))) {
                    decorator.showDialog(dialog);
                    Region content = dialog.getDialogPane();
                    decorator.getRoot().applyCss();
                    decorator.getRoot().layout();
                    Region surface = (Region) Objects.requireNonNull(scene.lookup(".m3-dialog-surface"));
                    assertEquals(28, surface.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius());
                    if (dialog.getDialogPane().getContent() instanceof TaskExecutorDialogPane taskPane)
                        assertTrue(taskPane.getBackground().getFills().stream()
                                .allMatch(fill -> Color.TRANSPARENT.equals(fill.getFill())));

                    Node scrim = Objects.requireNonNull(scene.lookup(".m3-scrim"));
                    double x = stage.getX();
                    double y = stage.getY();
                    dragAt(scrim, 80, 80);
                    assertEquals(x, stage.getX());
                    assertEquals(y, stage.getY());

                    Node title = Objects.requireNonNull(scene.lookup(".jfx-decorator-title")).getParent();
                    Bounds titleBounds = title.localToScene(title.getBoundsInLocal());
                    dragAt(scrim, titleBounds.getCenterX(), titleBounds.getCenterY());
                    assertEquals(x + 50, stage.getX(), 1);
                    assertEquals(y + 50, stage.getY(), 1);

                    x = stage.getX();
                    y = stage.getY();
                    Node button = Objects.requireNonNull(scene.lookup(".jfx-decorator-button"));
                    Bounds buttonBounds = button.localToScene(button.getBoundsInLocal());
                    dragAt(scrim, buttonBounds.getCenterX(), buttonBounds.getCenterY());
                    Bounds contentBounds = content.localToScene(content.getBoundsInLocal());
                    dragAt(content, contentBounds.getCenterX(), contentBounds.getCenterY());
                    assertEquals(x, stage.getX());
                    assertEquals(y, stage.getY());
                    if (dialog.getDialogPane().getContent() instanceof TaskExecutorDialogPane taskPane)
                        taskPane.fireEvent(new DialogCloseEvent());
                    else
                        dialog.getDialogPane().getActions().getFirst().fire();
                }
            } finally {
                stage.hide();
                decorator.detachStage();
            }
            return true;
        });
    }

    /// Verifies dismiss-before-confirm layout, keyboard cancellation, and native action handling.
    @Test
    void messageActionsUseNativeDialogLifecycle() throws Exception {
        onFxThread(() -> {
            M3OverlayPane host = createDialogHost();
            AtomicInteger accepted = new AtomicInteger();
            AtomicInteger cancelled = new AtomicInteger();
            M3Dialog question = new MessageDialogBuilder("Body", null, MessageType.QUESTION)
                    .yesOrNo(accepted::incrementAndGet, cancelled::incrementAndGet).build();
            assertEquals(MessageType.QUESTION.getDisplayName(), question.getDialogPane().getHeadline());
            assertEquals("Body", question.getDialogPane().getSupportingText());
            assertNull(question.getDialogPane().getContent());
            M3Button noButton = question.getDialogPane().getActions().getFirst();
            M3Button yesButton = question.getDialogPane().getActions().getLast();
            assertEquals(i18n("button.no"), noButton.getText());
            assertTrue(noButton.isCancelButton());
            assertEquals(i18n("button.yes"), yesButton.getText());
            assertTrue(yesButton.isDefaultButton());
            M3Dialog literal = new MessageDialogBuilder("3 < 5", "Title", MessageType.INFO).build();
            assertEquals("3 < 5", literal.getDialogPane().getSupportingText());
            assertNull(literal.getDialogPane().getContent());
            M3DialogHandle handle = host.showDialog(question);
            host.applyCss();
            host.layout();
            assertTrue(noButton.localToScene(noButton.getBoundsInLocal()).getMaxX()
                    <= yesButton.localToScene(yesButton.getBoundsInLocal()).getMinX());
            question.getDialogPane().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false));
            assertFalse(handle.isShowing());
            assertEquals(0, accepted.get());
            assertEquals(1, cancelled.get());

            M3Button action = new M3Button("Continue", M3ButtonVariant.TEXT);
            action.setDisable(true);
            action.setOnAction(event -> accepted.incrementAndGet());
            M3Dialog confirmation = new MessageDialogBuilder("Body", "Title", MessageType.WARNING)
                    .actionOrCancel(action, cancelled::incrementAndGet).build();
            assertSame(action, confirmation.getDialogPane().getActions().getLast());
            assertTrue(confirmation.getDialogPane().getActions().getFirst().isCancelButton());
            M3DialogHandle confirmationHandle = host.showDialog(confirmation);
            KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false);
            action.fireEvent(enter);
            assertTrue(confirmationHandle.isShowing());
            assertEquals(0, accepted.get());
            action.setDisable(false);
            action.setOnAction(event -> event.consume());
            action.fire();
            assertTrue(confirmationHandle.isShowing());
            action.setOnAction(event -> accepted.incrementAndGet());
            action.requestFocus();
            action.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
            assertFalse(confirmationHandle.isShowing());
            assertEquals(1, accepted.get());

            M3Dialog notice = new MessageDialogBuilder("Body", "Title", MessageType.INFO)
                    .addHyperLink("Learn more", "https://example.com")
                    .ok(null).addCancel("Do not show again", null).build();
            assertEquals(List.of("Learn more", "Do not show again", i18n("button.ok")),
                    notice.getDialogPane().getActions().stream().map(M3Button::getText).toList());
            assertTrue(notice.getDialogPane().getActions().get(1).isCancelButton());
            assertFalse(notice.getDialogPane().getActions().getLast().isCancelButton());
            assertTrue(notice.getDialogPane().getActions().getLast().isDefaultButton());

            M3Dialog rich = new MessageDialogBuilder("<b>Bold</b> <a href=\"https://example.com\">Link</a>",
                    "Title", MessageType.INFO).ok(null).build();
            TextFlow flow = assertInstanceOf(TextFlow.class, rich.getDialogPane().getContent());
            assertTrue(flow.getChildren().stream().anyMatch(node -> node.getStyleClass().contains("hyperlink")));
            M3Dialog longMessage = new MessageDialogBuilder("Line\n".repeat(100), "Title", MessageType.ERROR).ok(null).build();
            ScrollPane longBody = assertInstanceOf(ScrollPane.class, longMessage.getDialogPane().getContent());
            Label longText = assertInstanceOf(Label.class, longBody.getContent());
            assertEquals("Line\n".repeat(100), longText.getText());
            assertTrue(longText.isWrapText());
            M3Dialog wrappedMessage = new MessageDialogBuilder("Long text ".repeat(100), "Title", MessageType.INFO).build();
            ScrollPane wrappedBody = assertInstanceOf(ScrollPane.class, wrappedMessage.getDialogPane().getContent());
            assertInstanceOf(Label.class, wrappedBody.getContent());
            return true;
        });
    }

    /// Uses native dialog actions while preserving field validation, rejection, and keyboard cancellation.
    @Test
    void inputDialogMaterialButtonsPreserveSubmissionLifecycle() throws Exception {
        onFxThread(() -> {
            M3OverlayPane host = createDialogHost();
            AtomicInteger submissions = new AtomicInteger();
            InputDialog input = new InputDialog("Name", "Example", (value, handler) -> {
                assertEquals("Example", value);
                if (submissions.incrementAndGet() == 1)
                    handler.reject("Try again");
                else
                    handler.resolve();
            }, new Validator("Required", value -> !value.isEmpty()));
            M3DialogHandle handle = host.showDialog(input);
            host.applyCss();
            host.layout();
            assertEquals("Name", input.getDialogPane().getHeadline());
            M3Button accept = input.getDialogPane().getActions().getLast();
            M3Button cancel = input.getDialogPane().getActions().getFirst();
            JFXTextField field = assertInstanceOf(JFXTextField.class, input.getDialogPane().lookup(".jfx-text-field"));
            Label warning = assertInstanceOf(Label.class, input.getDialogPane().lookup(".input-dialog-error"));
            assertEquals(M3ButtonVariant.TEXT, accept.getVariant());
            assertEquals(M3ButtonVariant.TEXT, cancel.getVariant());
            assertTrue(accept.isDefaultButton());
            assertTrue(cancel.isCancelButton());
            assertFalse(warning.isManaged());

            field.setText("");
            assertTrue(accept.isDisabled());
            accept.fire();
            assertEquals(0, submissions.get());
            field.setText("Example");
            assertFalse(accept.isDisabled());
            field.fireEvent(new ActionEvent());
            assertTrue(handle.isShowing());
            assertFalse(input.getCompletableFuture().isDone());
            assertNull(accept.getGraphic());
            assertEquals("Try again", warning.getText());
            assertTrue(warning.isVisible());
            assertTrue(warning.isManaged());
            assertTrue(warning.isWrapText());

            accept.requestFocus();
            accept.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
            assertEquals(2, submissions.get());
            assertEquals("Example", input.getCompletableFuture().getNow(null));
            assertFalse(handle.isShowing());

            InputDialog cancelled = new InputDialog(null, null, (value, handler) -> fail("Unexpected submission"));
            M3DialogHandle cancelledHandle = host.showDialog(cancelled);
            host.applyCss();
            host.layout();
            assertEquals("", cancelled.getDialogPane().getHeadline());
            cancelled.getDialogPane().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false));
            assertFalse(cancelledHandle.isShowing());
            assertFalse(cancelled.getCompletableFuture().isDone());
            return true;
        });
    }

    /// Handles background callback results without duplicate submissions or cancelling the accepted-text future.
    @Test
    void inputDialogSupportsAsynchronousSubmission() throws Exception {
        var handlers = new ArrayList<FutureCallback.ResultHandler>();
        InputDialog input = onFxThread(() -> {
            M3OverlayPane host = createDialogHost();
            InputDialog dialog = new InputDialog("Name", "Example", (value, handler) -> handlers.add(handler));
            host.showDialog(dialog);
            host.applyCss();
            host.layout();
            M3Button accept = dialog.getDialogPane().getActions().getLast();
            accept.fire();
            assertTrue(accept.isDisabled());
            assertInstanceOf(M3ProgressIndicator.class, accept.getGraphic());
            accept.fire();
            assertEquals(1, handlers.size());
            assertNotNull(dialog.getDialogPane().getScene());
            return dialog;
        });
        handlers.getFirst().reject("Try again");
        onFxThread(() -> {
            M3Button accept = input.getDialogPane().getActions().getLast();
            Label warning = assertInstanceOf(Label.class, input.getDialogPane().lookup(".input-dialog-error"));
            assertEquals("Try again", warning.getText());
            assertFalse(accept.isDisabled());
            assertNull(accept.getGraphic());
            accept.fire();
            assertEquals(2, handlers.size());
            assertFalse(warning.isManaged());
            input.getDialogPane().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false));
            assertNull(input.getDialogPane().getScene());
            assertFalse(input.getCompletableFuture().isDone());
            return true;
        });
        handlers.getLast().resolve();
        assertEquals("Example", input.getCompletableFuture().get(5, TimeUnit.SECONDS));
        onFxThread(() -> {
            assertNull(input.getDialogPane().getScene());
            return true;
        });
    }

    /// Dispatches a complete drag gesture with a 50-pixel screen displacement.
    private static void dragAt(Node target, double sceneX, double sceneY) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED, sceneX, sceneY, 200, 200,
                MouseButton.NONE, 0, false, false, false, false,
                false, false, false, false, false, false, new PickResult(target, sceneX, sceneY)));
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, sceneX, sceneY, 200, 200,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, new PickResult(target, sceneX, sceneY)));
        for (int offset : new int[]{0, 50}) {
            target.fireEvent(new MouseEvent(MouseEvent.MOUSE_DRAGGED, sceneX + offset, sceneY + offset,
                    200 + offset, 200 + offset, MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, false, false, false,
                    new PickResult(target, sceneX + offset, sceneY + offset)));
        }
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_RELEASED, sceneX + 50, sceneY + 50, 250, 250,
                MouseButton.PRIMARY, 1, false, false, false, false,
                false, false, false, false, false, false, new PickResult(target, sceneX + 50, sceneY + 50)));
    }

    /// Verifies the legacy date picker closes and reopens through the M3FX modal host.
    @Test
    void datePickerUsesOverlayDialog() throws Exception {
        onFxThread(() -> {
            M3OverlayPane host = createDialogHost();
            com.jfoenix.controls.JFXDatePicker picker = new com.jfoenix.controls.JFXDatePicker();
            picker.setOverLay(true);
            host.setContent(picker);
            host.applyCss();
            picker.show();
            assertEquals(1, host.lookupAll(".m3-dialog-pane").size());
            picker.hide();
            assertTrue(host.lookupAll(".m3-dialog-pane").isEmpty());
            picker.show();
            assertEquals(1, host.lookupAll(".m3-dialog-pane").size());
            @Nullable Node calendar = host.lookup(".date-picker-popup");
            assertNotNull(calendar);
            calendar.fireEvent(new DialogCloseEvent());
            assertFalse(picker.isShowing());
            assertTrue(host.lookupAll(".m3-dialog-pane").isEmpty());
            return true;
        });
    }

    /// Windows created by this test, closed after each case.
    private final List<Stage> stages = new ArrayList<>();

    /// Creates an attached overlay host required by M3FX dialog presentations.
    private M3OverlayPane createDialogHost() {
        M3OverlayPane host = new M3OverlayPane();
        showStage(new Scene(host, 800, 600));
        return host;
    }

    /// Installs the M3 theme and shows a test window.
    private void showStage(Scene scene) {
        M3ThemeManager.install(scene, M3Theme.defaultTheme());
        Stage stage = new Stage();
        stage.setScene(scene);
        stages.add(stage);
        stage.show();
    }

    /// Closes all windows owned by the completed case.
    @AfterEach
    void closeStages() throws Exception {
        onFxThread(() -> {
            stages.forEach(Stage::close);
            stages.clear();
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
